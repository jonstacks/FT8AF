// Libusb-backed USB Audio Class isochronous capture.
//
// The user picks "(USB direct)" in Settings → Audio Input. The Java side
// (UsbAudioDevice) opens the device via Android's UsbManager, finds the
// audio streaming interface + iso input endpoint, sets the alt-setting
// (which is what actually turns the audio stream on), and then hands
// the resulting file descriptor + interface params to nativeStart()
// here. We then drive the iso transfers via libusb on a worker thread
// and call back into Java with mono float[] samples ready for the
// FT8 decoder.
//
// We do this instead of using Android's UsbRequest API because that
// API's iso path crashes on some kernels (UsbRequest.initialize()
// returns false and queue() throws IllegalStateException). libusb
// talks to USBDEVFS directly and is much more permissive about
// what the kernel actually accepts.

#include <jni.h>
#include <android/log.h>
#include <libusb.h>

#include <atomic>
#include <cstdint>
#include <cstdlib>
#include <cstring>
#include <thread>
#include <vector>

#define TAG "ft8af_usb_capture"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,  TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN,  TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

namespace {

// Each in-flight iso URB carries this many packets. 8 is a balance between
// latency and syscall overhead — at 48k stereo 16-bit, one packet is ~192
// bytes (1ms of audio), so 8 packets per transfer ≈ 8ms latency.
constexpr int kPacketsPerTransfer = 8;
// Number of transfers we keep submitted at once. With 4 transfers × 8
// packets × 1ms/packet = 32ms of buffering, plenty for the FT8 decoder
// loop which expects a 12kHz mono float stream.
constexpr int kNumTransfers = 4;

struct CaptureSession {
    libusb_context*           ctx       = nullptr;
    libusb_device_handle*     handle    = nullptr;
    JavaVM*                   vm        = nullptr;
    jobject                   callback  = nullptr;   // global ref
    jmethodID                 onData    = nullptr;
    jmethodID                 onStopped = nullptr;

    int                       endpoint        = 0;
    int                       maxPacketSize   = 0;
    int                       inputRate       = 48000;
    int                       inputChannels   = 2;
    int                       inputBytesPerSample = 2;   // 16-bit PCM
    int                       targetRate      = 12000;
    int                       decimationRatio = 4;       // inputRate / targetRate

    std::vector<libusb_transfer*> transfers;
    std::thread                   eventThread;
    std::atomic<bool>             running{false};
    std::atomic<int>              inFlight{0};

    // Sample accumulator for downsampling. We average `decimationRatio`
    // consecutive input samples per output sample (cheap box-filter
    // anti-aliasing); the decoder only needs 12kHz so this is plenty.
    std::vector<float> accumulator;
};

// ----------------------------------------------------------------------------
// JNI helpers
// ----------------------------------------------------------------------------

JNIEnv* attachJniEnv(CaptureSession* s, bool* attached) {
    JNIEnv* env = nullptr;
    int rc = s->vm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6);
    if (rc == JNI_OK) {
        *attached = false;
        return env;
    }
    if (s->vm->AttachCurrentThread(&env, nullptr) == JNI_OK) {
        *attached = true;
        return env;
    }
    LOGE("AttachCurrentThread failed");
    *attached = false;
    return nullptr;
}

void detachIfNeeded(CaptureSession* s, bool attached) {
    if (attached) s->vm->DetachCurrentThread();
}

void emitAudioData(CaptureSession* s, const float* data, int length) {
    bool attached = false;
    JNIEnv* env = attachJniEnv(s, &attached);
    if (!env) return;
    jfloatArray arr = env->NewFloatArray(length);
    if (arr) {
        env->SetFloatArrayRegion(arr, 0, length, data);
        env->CallVoidMethod(s->callback, s->onData, arr, length);
        if (env->ExceptionCheck()) {
            env->ExceptionDescribe();
            env->ExceptionClear();
        }
        env->DeleteLocalRef(arr);
    }
    detachIfNeeded(s, attached);
}

void emitStopped(CaptureSession* s, int code) {
    bool attached = false;
    JNIEnv* env = attachJniEnv(s, &attached);
    if (!env) return;
    env->CallVoidMethod(s->callback, s->onStopped, code);
    if (env->ExceptionCheck()) {
        env->ExceptionDescribe();
        env->ExceptionClear();
    }
    detachIfNeeded(s, attached);
}

// ----------------------------------------------------------------------------
// Iso transfer callback
// ----------------------------------------------------------------------------
//
// libusb invokes this from libusb_handle_events() on our event thread once an
// iso transfer completes (or errors / is cancelled). For each packet that
// returned actual data we convert little-endian int16 samples to mono float
// and feed them into the decimation accumulator. When the accumulator has
// enough samples for a 12kHz block we emit a callback to Java.

void LIBUSB_CALL onTransferComplete(libusb_transfer* xfer) {
    auto* s = static_cast<CaptureSession*>(xfer->user_data);

    if (!s->running.load(std::memory_order_acquire)) {
        s->inFlight.fetch_sub(1, std::memory_order_acq_rel);
        return;
    }

    if (xfer->status != LIBUSB_TRANSFER_COMPLETED) {
        LOGW("iso transfer status=%d (non-fatal; resubmitting)", xfer->status);
        // For NO_DEVICE / CANCELLED we don't resubmit — let the event loop
        // tear down. For other transient errors (TIMED_OUT, STALL) we keep
        // pumping; isochronous is best-effort by spec.
        if (xfer->status == LIBUSB_TRANSFER_NO_DEVICE
                || xfer->status == LIBUSB_TRANSFER_CANCELLED) {
            s->running.store(false, std::memory_order_release);
            s->inFlight.fetch_sub(1, std::memory_order_acq_rel);
            return;
        }
    }

    for (int p = 0; p < xfer->num_iso_packets; ++p) {
        libusb_iso_packet_descriptor& pkt = xfer->iso_packet_desc[p];
        if (pkt.status != LIBUSB_TRANSFER_COMPLETED) continue;
        if (pkt.actual_length == 0) continue;

        const uint8_t* buf = libusb_get_iso_packet_buffer_simple(xfer, p);
        if (!buf) continue;

        // Walk the PCM bytes, converting little-endian int16 → float in
        // [-1, 1]. For stereo we average the channels (FT8 only needs mono);
        // for mono we just copy.
        int bytesPerFrame = s->inputBytesPerSample * s->inputChannels;
        int frames = pkt.actual_length / bytesPerFrame;
        for (int i = 0; i < frames; ++i) {
            const uint8_t* frame = buf + i * bytesPerFrame;
            int16_t l = static_cast<int16_t>(frame[0] | (frame[1] << 8));
            float sample;
            if (s->inputChannels == 2) {
                int16_t r = static_cast<int16_t>(frame[2] | (frame[3] << 8));
                sample = ((float)l + (float)r) * (0.5f / 32768.0f);
            } else {
                sample = (float)l / 32768.0f;
            }
            s->accumulator.push_back(sample);
        }
    }

    // Drain accumulator into target-rate float blocks.
    if (s->decimationRatio > 0
            && (int)s->accumulator.size() >= s->decimationRatio) {
        int outSamples = (int)s->accumulator.size() / s->decimationRatio;
        std::vector<float> out(outSamples);
        for (int i = 0; i < outSamples; ++i) {
            float sum = 0.0f;
            int base = i * s->decimationRatio;
            for (int j = 0; j < s->decimationRatio; ++j) {
                sum += s->accumulator[base + j];
            }
            out[i] = sum / (float)s->decimationRatio;
        }
        int consumed = outSamples * s->decimationRatio;
        s->accumulator.erase(
                s->accumulator.begin(),
                s->accumulator.begin() + consumed);
        emitAudioData(s, out.data(), outSamples);
    }

    // Re-submit this transfer.
    int rc = libusb_submit_transfer(xfer);
    if (rc != 0) {
        LOGW("libusb_submit_transfer failed: %s", libusb_error_name(rc));
        s->running.store(false, std::memory_order_release);
        s->inFlight.fetch_sub(1, std::memory_order_acq_rel);
    }
}

// ----------------------------------------------------------------------------
// Event-loop worker thread
// ----------------------------------------------------------------------------

void eventLoop(CaptureSession* s) {
    LOGI("event loop started");
    while (s->running.load(std::memory_order_acquire)) {
        timeval tv{0, 100000};  // 100ms
        int rc = libusb_handle_events_timeout_completed(s->ctx, &tv, nullptr);
        if (rc != 0 && rc != LIBUSB_ERROR_INTERRUPTED) {
            LOGW("libusb_handle_events failed: %s", libusb_error_name(rc));
            break;
        }
        if (s->inFlight.load(std::memory_order_acquire) == 0) {
            // All transfers retired without being re-submitted (e.g., device
            // gone). Exit cleanly so Java can fall back to mic.
            LOGW("all transfers retired; ending capture");
            break;
        }
    }
    s->running.store(false, std::memory_order_release);
    LOGI("event loop exiting; cancelling outstanding transfers");

    // Drain any still-pending transfers so libusb can free them safely.
    for (auto* t : s->transfers) {
        libusb_cancel_transfer(t);
    }
    // Process cancellations.
    while (s->inFlight.load(std::memory_order_acquire) > 0) {
        timeval tv{0, 50000};
        libusb_handle_events_timeout_completed(s->ctx, &tv, nullptr);
    }

    emitStopped(s, 0);
}

}  // namespace

// ----------------------------------------------------------------------------
// JNI entry points
// ----------------------------------------------------------------------------

extern "C" JNIEXPORT jlong JNICALL
Java_com_bg7yoz_ft8cn_wave_UsbAudioNative_nativeStart(
        JNIEnv* env,
        jclass /*clazz*/,
        jint fd,
        jint /*interfaceNumber*/,
        jint /*altSetting*/,
        jint endpointAddress,
        jint maxPacketSize,
        jint inputSampleRate,
        jint inputChannels,
        jint inputBytesPerSample,
        jint targetSampleRate,
        jobject callback) {

    // The Java side has already opened the device, claimed the interface,
    // and set the alt-setting via Android's UsbManager. We don't need libusb
    // to enumerate or open anything — just wrap the existing fd.
    libusb_set_option(nullptr, LIBUSB_OPTION_NO_DEVICE_DISCOVERY);

    libusb_context* ctx = nullptr;
    int rc = libusb_init(&ctx);
    if (rc != 0) {
        LOGE("libusb_init: %s", libusb_error_name(rc));
        return 0;
    }

    libusb_device_handle* handle = nullptr;
    rc = libusb_wrap_sys_device(ctx, (intptr_t)fd, &handle);
    if (rc != 0 || !handle) {
        LOGE("libusb_wrap_sys_device(fd=%d): %s", fd, libusb_error_name(rc));
        libusb_exit(ctx);
        return 0;
    }

    auto* s = new CaptureSession();
    s->ctx                 = ctx;
    s->handle              = handle;
    env->GetJavaVM(&s->vm);
    s->callback            = env->NewGlobalRef(callback);
    s->endpoint            = endpointAddress;
    s->maxPacketSize       = maxPacketSize;
    s->inputRate           = inputSampleRate;
    s->inputChannels       = inputChannels > 0 ? inputChannels : 2;
    s->inputBytesPerSample = inputBytesPerSample > 0 ? inputBytesPerSample : 2;
    s->targetRate          = targetSampleRate;
    s->decimationRatio     = inputSampleRate / targetSampleRate;
    s->accumulator.reserve(inputSampleRate);  // 1s of headroom

    jclass cbClass = env->GetObjectClass(callback);
    s->onData    = env->GetMethodID(cbClass, "onAudioData",       "([FI)V");
    s->onStopped = env->GetMethodID(cbClass, "onCaptureStopped",  "(I)V");
    if (!s->onData || !s->onStopped) {
        LOGE("callback methods not found on UsbAudioNative$AudioInputCallback");
        env->DeleteGlobalRef(s->callback);
        libusb_close(handle);
        libusb_exit(ctx);
        delete s;
        return 0;
    }

    // Allocate and submit the iso transfers. Iso bandwidth is fixed by spec
    // so we use maxPacketSize as the per-packet length — short reads are
    // fine, libusb sets actual_length per packet on completion.
    s->transfers.reserve(kNumTransfers);
    s->running.store(true, std::memory_order_release);

    for (int i = 0; i < kNumTransfers; ++i) {
        libusb_transfer* xfer = libusb_alloc_transfer(kPacketsPerTransfer);
        if (!xfer) {
            LOGE("libusb_alloc_transfer failed at i=%d", i);
            continue;
        }
        int bufLen = s->maxPacketSize * kPacketsPerTransfer;
        auto* buf = (uint8_t*)std::malloc(bufLen);
        if (!buf) {
            libusb_free_transfer(xfer);
            continue;
        }
        libusb_fill_iso_transfer(
                xfer, handle, (unsigned char)s->endpoint,
                buf, bufLen, kPacketsPerTransfer,
                onTransferComplete, s, /*timeout=*/0);
        libusb_set_iso_packet_lengths(xfer, s->maxPacketSize);
        // libusb_free_transfer with LIBUSB_TRANSFER_FREE_BUFFER frees buf for us.
        xfer->flags = LIBUSB_TRANSFER_FREE_BUFFER;

        int rcSub = libusb_submit_transfer(xfer);
        if (rcSub != 0) {
            LOGE("submit transfer i=%d: %s", i, libusb_error_name(rcSub));
            libusb_free_transfer(xfer);
            continue;
        }
        s->transfers.push_back(xfer);
        s->inFlight.fetch_add(1, std::memory_order_acq_rel);
    }

    if (s->transfers.empty()) {
        LOGE("no transfers could be submitted; aborting");
        s->running.store(false, std::memory_order_release);
        env->DeleteGlobalRef(s->callback);
        libusb_close(handle);
        libusb_exit(ctx);
        delete s;
        return 0;
    }

    LOGI("started capture: fd=%d ep=0x%02x maxPkt=%d inputRate=%d ch=%d targetRate=%d "
         "decim=%d transfers=%zu packets/xfer=%d",
         fd, s->endpoint, s->maxPacketSize, s->inputRate, s->inputChannels,
         s->targetRate, s->decimationRatio, s->transfers.size(), kPacketsPerTransfer);

    s->eventThread = std::thread(eventLoop, s);
    return reinterpret_cast<jlong>(s);
}

extern "C" JNIEXPORT void JNICALL
Java_com_bg7yoz_ft8cn_wave_UsbAudioNative_nativeStop(
        JNIEnv* env, jclass /*clazz*/, jlong handlePtr) {
    if (handlePtr == 0) return;
    auto* s = reinterpret_cast<CaptureSession*>(handlePtr);

    s->running.store(false, std::memory_order_release);
    libusb_interrupt_event_handler(s->ctx);  // wake the event loop

    if (s->eventThread.joinable()) {
        s->eventThread.join();
    }

    // The event loop frees its own transfers via LIBUSB_TRANSFER_FREE_BUFFER
    // after cancellation completes. We just need to free the transfer
    // structures themselves.
    for (auto* t : s->transfers) {
        libusb_free_transfer(t);
    }
    s->transfers.clear();

    if (s->handle) libusb_close(s->handle);
    if (s->ctx)    libusb_exit(s->ctx);
    if (s->callback) env->DeleteGlobalRef(s->callback);
    delete s;
    LOGI("stopped capture");
}
