# Magisk priv-app module for Krysp Local

Grants `CAPTURE_AUDIO_OUTPUT` / `CAPTURE_VOICE_COMMUNICATION_OUTPUT` (signature|privileged
permissions) by installing Krysp Local as a systemless priv-app via Magisk, which is what unlocks
[`PrivilegedDualTrackSource`](../app/src/main/kotlin/com/wysp/krysp/android/capture/PrivilegedDualTrackSource.kt)
actually capturing call audio instead of silently falling back to mic+speakerphone.

**Personal, rooted-device use only.** This is not something you distribute - each install target
needs this module built and flashed individually, and it's genuinely device/OEM/Android-version
dependent (see the caveats in `system/etc/permissions/privapp-permissions-*.xml` and in
`android/README.md`'s troubleshooting section).

## Build & install

1. Build a **release** APK in Android Studio (Build > Generate Signed Bundle/APK), or:
   ```
   ./gradlew :app:assembleRelease
   ```
2. Copy the output APK next to `customize.sh`:
   ```
   cp app/build/outputs/apk/release/app-release-unsigned.apk android/magisk-module/app-release.apk
   ```
   (If unsigned, either sign it yourself or use a debug build - priv-app allowlisting is based on
   package name + install location, not the signing key, but Android still requires *some* valid
   signature to install any APK at all.)
3. Zip the module directory's contents (not the directory itself - `module.prop` must be at the
   zip root):
   ```
   cd android/magisk-module && zip -r ../krysp-privapp-module.zip .
   ```
4. In Magisk Manager: Modules > Install from storage > pick `krysp-privapp-module.zip`.
5. Reboot.
6. Verify the grant actually took:
   ```
   adb shell dumpsys package com.wysp.krysp.android | grep -A1 CAPTURE
   ```
   You should see both permissions listed as `granted=true`. If they show `granted=false` or
   don't appear at all, your OEM's build likely needs an additional vendor-specific allowlist
   entry - check `/vendor/etc/permissions/` on your device for a similar file pattern to copy.

## Uninstall

Remove the module from Magisk Manager and reboot; this restores a normal (non-privileged, mic
fallback only) install.
