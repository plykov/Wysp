# Rooting a Galaxy S25 Ultra (Snapdragon, unlocked/international unit)

This is a reference walkthrough, not a script I can run for you — I have no access to your
physical device. Cross-check the current state of this against an active S25 Ultra thread on
XDA Forums before doing anything irreversible: Samsung firmware/Magisk tooling shifts over time,
and getting the firmware/model match wrong in the flashing step can hard-brick the device.

## Before you start

- **This wipes the device.** Back up everything first.
- **This permanently trips Knox (`KNOX_WARRANTY_VOID` / status `0x1`).** Not reversible by
  re-locking the bootloader, factory reset, or anything else. Samsung Pay, Secure Folder, and
  some Knox-backed features stop working for good, and it affects hardware warranty coverage for
  those features.
- **Confirm your exact model number** first: Settings > About phone > Model number. The S25 Ultra
  ships as several regional variants (e.g. `SM-S938B` most of the world, `SM-S938U`/`SM-S938U1`
  US unlocked, `SM-S938N` Korea, `SM-S938W` Canada). Firmware must match your exact model **and**
  CSC (region/carrier code) or you risk a bad flash.
- Chipset is Snapdragon 8 Elite across all regions (no Exynos variant this generation), so there's
  one rooting path, not a per-chipset fork.

## 1. Unlock the bootloader

1. Settings > About phone > tap Build number 7 times to enable Developer options.
2. Settings > Developer options: enable **OEM unlocking** and **USB debugging**.
3. Power off. Boot to Download Mode: hold Volume Up + Volume Down, plug in the USB cable while
   held (exact key combo has varied slightly across models/One UI versions — search
   "[your model] download mode" if this doesn't work), then press Volume Up to continue into
   Download Mode.
4. From Download Mode, follow the on-screen prompt to unlock the bootloader (typically Volume Up
   to confirm). The device wipes and reboots.
5. After setup, re-enable Developer options + USB debugging (OEM unlocking should now show as
   already unlocked / the toggle may disappear once unlocked).

## 2. Get matching stock firmware and extract init_boot.img

Modern Samsung devices (this one included, on Android 15/16) use a GKI (Generic Kernel Image)
layout where Magisk patches **`init_boot.img`**, not `boot.img` — the ramdisk moved to its own
partition. Don't reuse older Samsung root guides that reference patching `boot.img` directly.

1. Use [Frija](https://github.com/soupslurpr/Frija) (or SamFirm/samloader) to download the exact
   firmware for your model number + CSC. Do not download a mismatched region's firmware.
2. Extract the downloaded `AP_*.tar.md5` (it's a tar archive despite the extension) and pull out
   `init_boot.img`.

## 3. Patch init_boot.img with Magisk

1. Install the [Magisk app](https://github.com/topjohnwu/Magisk/releases) (official topjohnwu
   releases only) on the phone.
2. Copy `init_boot.img` onto the phone's storage.
3. In Magisk: Install > Select and Patch a File > choose `init_boot.img` > let it patch.
4. Copy the resulting `magisk_patched-<version>-<random>.img` back to your PC and rename it to
   `init_boot.img`.

## 4. Flash the patched image via Odin

Samsung devices don't take `fastboot flash` — you flash through **Odin** in Download Mode.

1. Package the renamed, patched `init_boot.img` into a `.tar` (Odin needs a tar, not a raw
   `.img`): on Linux/macOS, `tar -H ustar -c init_boot.img > init_boot_patched.tar`. On Windows,
   use a GUI tar tool that supports the ustar format, or do this step from WSL/a Linux VM.
2. Boot back into Download Mode (same key combo as step 1.3).
3. Open Odin on the PC, load `init_boot_patched.tar` into the **AP** slot **only** — do not also
   load original BL/CP/CSC files, and leave **Re-Partition unchecked**. You're patching one
   partition, not reflashing the whole device.
4. Start. The device reboots automatically.

## 5. Verify root

Open the Magisk app and confirm it shows itself as properly installed (ramdisk: yes). Install a
root checker or just try `adb shell su -c id` from a PC with USB debugging on — you should see
`uid=0(root)`.

## 6. Now install the priv-app allowlist module

Once root is confirmed working, proceed to `magisk-module/README.md` in this repo to build,
package, and flash the Krysp Local priv-app module that actually grants the call-audio capture
permission.
