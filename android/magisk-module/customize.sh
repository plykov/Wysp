#!/system/bin/sh
# Magisk module installer script. Runs inside Magisk Manager's install flow.
#
# Expects a release APK named `app-release.apk` to sit alongside this script in the module zip
# (see android/magisk-module/README.md for how to build and stage it there before zipping).

APK_NAME="app-release.apk"
PRIVAPP_DIR="$MODPATH/system/priv-app/KryspCallRecorder"

ui_print "- Installing Krysp Local as a systemless priv-app"

if [ ! -f "$MODPATH/$APK_NAME" ]; then
  ui_print "  ! $APK_NAME not found in module zip - aborting."
  ui_print "  ! Build the release APK and place it next to customize.sh before zipping."
  abort "Missing $APK_NAME"
fi

mkdir -p "$PRIVAPP_DIR"
mv "$MODPATH/$APK_NAME" "$PRIVAPP_DIR/KryspCallRecorder.apk"

set_perm_recursive "$MODPATH/system" 0 0 0755 0644
set_perm "$PRIVAPP_DIR/KryspCallRecorder.apk" 0 0 0644

ui_print "- Done. Reboot, then verify with:"
ui_print "    adb shell dumpsys package com.wysp.krysp.android | grep -A1 CAPTURE"
