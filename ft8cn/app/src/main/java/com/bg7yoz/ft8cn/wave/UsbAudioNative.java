package com.bg7yoz.ft8cn.wave;

import android.util.Log;

/**
 * JNI bridge to the native USB audio module ({@code libft8af_usb.so}).
 *
 * <p>Phase 1: only exposes a build-string sentinel so the rest of the
 * app can confirm the native library loaded successfully. Phase 2 will
 * add capture entry points (open/start/stop) backed by libusb so the
 * raw USB Audio Class path can work on hosts where Android's
 * {@link android.hardware.usb.UsbRequest} can't drive isochronous
 * transfers.
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
     * Phase-1 sanity check. Returns a sentinel string from native so
     * the caller can confirm JNI invocation works. Throws
     * {@link UnsatisfiedLinkError} if the library failed to load.
     */
    public static native String nativeBuildString();
}
