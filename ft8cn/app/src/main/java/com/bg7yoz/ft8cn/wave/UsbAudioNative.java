package com.bg7yoz.ft8cn.wave;

import android.util.Log;

/**
 * JNI bridge to {@code libft8af_usb.so}: libusb-backed USB Audio Class
 * isochronous capture.
 *
 * <p>The Java side ({@link UsbAudioDevice}) opens the device, claims the
 * audio streaming interface, finds the iso input endpoint, and sets the
 * alt-setting (which is what actually starts the audio stream) using the
 * stock Android {@code UsbManager} APIs. It then hands the resulting
 * file descriptor + interface parameters to {@link #nativeStart} here.
 * Native runs the iso transfers via libusb and calls back into Java with
 * mono float samples at the target sample rate.
 *
 * <p>We do this instead of using {@link android.hardware.usb.UsbRequest}
 * because that API's iso path silently fails on some kernels — notably
 * the car-dash Android 11 tablets — throwing
 * {@code IllegalStateException: request is not initialized} on first
 * {@code queue()}.
 */
public final class UsbAudioNative {
    private static final String TAG = "UsbAudioNative";
    private static final boolean LIBRARY_LOADED;

    static {
        boolean loaded;
        try {
            System.loadLibrary("ft8af_usb");
            loaded = true;
        } catch (UnsatisfiedLinkError e) {
            Log.w(TAG, "Failed to load libft8af_usb: " + e.getMessage());
            loaded = false;
        }
        LIBRARY_LOADED = loaded;
    }

    private UsbAudioNative() {}

    /** Whether {@code libft8af_usb.so} was loaded at process start. */
    public static boolean isAvailable() {
        return LIBRARY_LOADED;
    }

    /**
     * Callback for audio samples coming off the iso transfer pipeline.
     * Methods are invoked on the libusb event-loop worker thread; callers
     * must not block.
     */
    public interface AudioInputCallback {
        /**
         * Called for each decimated block of mono float samples. The
         * array is allocated fresh per callback; do whatever you need
         * with it before returning, the native side may free it.
         *
         * @param data   mono samples in [-1, 1] at the target sample rate
         * @param length number of valid samples (always == data.length here)
         */
        void onAudioData(float[] data, int length);

        /**
         * Called once when capture ends — either through an explicit
         * {@link #nativeStop} or because all in-flight transfers
         * retired (device gone, kernel refused to keep accepting URBs,
         * etc).
         *
         * @param code 0 for normal exit, non-zero is the libusb error
         *             code from the last failed operation
         */
        void onCaptureStopped(int code);
    }

    /** Sentinel string from native. Phase 1 diagnostic; safe to remove later. */
    public static native String nativeBuildString();

    /**
     * Start an iso capture session.
     *
     * @param fd                  file descriptor from
     *                            {@link android.hardware.usb.UsbDeviceConnection#getFileDescriptor()}.
     *                            Caller retains ownership; do not close it
     *                            while the session is running.
     * @param interfaceNumber     bInterfaceNumber of the audio streaming interface
     *                            (already claimed by the Java side via UsbManager).
     * @param altSetting          alt-setting already activated on that interface
     *                            (informational; native does not re-set it).
     * @param endpointAddress     bEndpointAddress of the iso IN endpoint.
     * @param maxPacketSize       wMaxPacketSize from the endpoint descriptor.
     * @param inputSampleRate     device sample rate (typically 48000).
     * @param inputChannels       1 (mono) or 2 (stereo); stereo is averaged.
     * @param inputBytesPerSample 2 for 16-bit PCM (the only format we handle).
     * @param targetSampleRate    rate we emit to the callback (12000 for FT8).
     * @param callback            invoked from native worker thread.
     * @return                    opaque session handle for {@link #nativeStop},
     *                            or 0 on failure.
     */
    public static native long nativeStart(
            int fd,
            int interfaceNumber,
            int altSetting,
            int endpointAddress,
            int maxPacketSize,
            int inputSampleRate,
            int inputChannels,
            int inputBytesPerSample,
            int targetSampleRate,
            AudioInputCallback callback);

    /** Stop a session started by {@link #nativeStart}. Blocks until the
     *  event thread has drained outstanding URBs and the callback's
     *  {@code onCaptureStopped} has been invoked. */
    public static native void nativeStop(long handle);
}
