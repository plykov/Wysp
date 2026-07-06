# Magisk priv-app module for Krysp Local

Grants `CAPTURE_AUDIO_OUTPUT` / `CAPTURE_VOICE_COMMUNICATION_OUTPUT` (signature|privileged
permissions) by installing Krysp Local as a systemless priv-app via Magisk, which is what unlocks
[`PrivilegedDualTrackSource`](../app/src/main/kotlin/com/wysp/krysp/android/capture/PrivilegedDualTrackSource.kt)
actually capturing call audio instead of silently falling back to mic+speakerphone.

**Personal, rooted-device use only.** This is not something you distribute - each install target
needs this module built and flashed individually, and it's genuinely device/OEM/Android-version
dependent (see the caveats in `system/etc/permissions/privapp-permissions-*.xml` and in
`android/README.md`'s troubleshooting section).

**On a Galaxy S25 Ultra starting from stock**: see `ROOTING_S25_ULTRA.md` first for the
Snapdragon/Odin/`init_boot.img` rooting steps specific to this device before coming back here.

### Samsung note: `system` vs `system_ext`

Magisk mounts each top-level directory in the module (`system/`, `system_ext/`, `vendor/`, ...)
onto the matching real partition. This module ships with `system/priv-app/` +
`system/etc/permissions/` by default, which is what most devices scan. Samsung's dynamic-partition
builds sometimes expect OEM priv-apps under `system_ext/` instead. If step 6 below shows
`granted=false` after flashing:

1. Check the module didn't already work for another reason (see step 6).
2. Rename the module's `system/` directory to `system_ext/` (keep the same `priv-app/` and
   `etc/permissions/` structure inside it), re-zip, and reflash.
3. **Don't have both `system/` and `system_ext/` present in the same module at once** — that
   installs the same package twice from two different system locations, which can conflict at
   boot. Pick one, test it, only then try the other if needed.

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
