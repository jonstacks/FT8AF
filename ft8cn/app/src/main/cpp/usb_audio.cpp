// Native USB audio capture stub. Phase 1: just confirms the JNI bridge
// works. Phase 2 will wire in libusb + isochronous capture so the raw
// USB Audio Class path can survive on hosts where Android's UsbRequest
// can't drive iso transfers (notably automotive Android variants).

#include <jni.h>
#include <android/log.h>
#include <string>

#define TAG "ft8af_usb_native"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)

extern "C" JNIEXPORT jstring JNICALL
Java_com_bg7yoz_ft8cn_wave_UsbAudioNative_nativeBuildString(
        JNIEnv *env, jclass /* clazz */) {
    // Phase 1 sentinel — replaced by libusb version + capabilities once
    // we vendor the library in Phase 2.
    std::string s = "ft8af_usb stub Phase 1 (libusb not yet wired)";
    LOGI("nativeBuildString -> %s", s.c_str());
    return env->NewStringUTF(s.c_str());
}
