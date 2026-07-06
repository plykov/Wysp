package com.wysp.krysp.android.capture

import android.content.Context
import android.content.pm.PackageManager

/** Checks whether this device/install actually has the privileged permission granted. */
object CaptureCapability {
    private val PRIVILEGED_PERMISSIONS = listOf(
        "android.permission.CAPTURE_AUDIO_OUTPUT",
        "android.permission.CAPTURE_VOICE_COMMUNICATION_OUTPUT",
    )

    /**
     * True only if this APK was installed as a privileged system app with one of the
     * capture-output permissions actually granted (not just declared in the manifest) - i.e. the
     * Magisk priv-app setup in /android/magisk-module worked. False on any normal, non-rooted
     * install, in which case the caller should fall back to [MicSpeakerphoneSource].
     */
    fun hasPrivilegedCaptureAccess(context: Context): Boolean =
        PRIVILEGED_PERMISSIONS.any {
            context.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED
        }
}
