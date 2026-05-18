# FT8AF Project Instructions

## Build & Deploy

After making code changes, always build and install on the connected device.

The WSL shell has no Linux JDK, so `./gradlew` fails with `JAVA_HOME is not set`.
Use the Windows wrapper instead — it picks up the Android Studio JBR automatically:

```
cd ft8cn && cmd.exe /c "gradlew.bat installDebug"
```

The user's phone is a Pixel 8 (transport_id changes; identify by `adb devices -l`
model `Pixel_8`). An Android emulator is usually also attached — Gradle's install
step will print `TimeoutException`/`Unknown API Level` warnings for the emulator
and still successfully install on the phone. `Installed on 1 device.` near the end
of the output means the phone got the APK; ignore the emulator noise.

When multiple devices are attached, target the phone explicitly with `-s` and the
phone's serial (from `adb devices`). adb itself lives at
`/mnt/c/Users/burns/AppData/Local/Android/Sdk/platform-tools/adb.exe`.

## Debug logs

The app writes a structured event log to
`/sdcard/Android/data/com.bg7yoz.ft8cn/files/debug.log` via `fileLog()` in
`ComposeMainActivity.kt` — CAT serial sends/recvs, USB attach events,
autoConnect attempts, band/frequency changes, etc. This is usually the most
useful source. Pull it with:

```
adb -s <phone-serial> pull /sdcard/Android/data/com.bg7yoz.ft8cn/files/debug.log /tmp/
```

For runtime detail not in `debug.log` (audio recording loop, system USB events,
crashes), use `adb logcat`. Useful tags: `FT8SignalListener`, `MicRecorder`,
`UsbAudioDevice`, `CableConnector`, `CableSerialPort`, `UsbHostManager`,
`UsbAlsaManager`. The app's `applicationId` is `com.bg7yoz.ft8cn` — pid-filter
with `--pid=$(adb shell pidof com.bg7yoz.ft8cn)` when you only want app-internal
lines.
