package com.wysp.krysp.android.capture

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager

/**
 * Explicit runtime check right before constructing an AudioRecord, so it's a clear
 * IllegalStateException with an actionable message instead of a SecurityException from deep
 * inside the platform. Also what makes this permission-safe from lint's point of view: it can't
 * see that MainActivity checks RECORD_AUDIO before ever starting RecordingForegroundService,
 * since that's a different component - and permissions can in principle be revoked mid-session
 * anyway, so checking again here is the right thing to do regardless of lint.
 */
internal fun requireRecordAudioPermission(context: Context) {
    check(context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
        "RECORD_AUDIO permission not granted."
    }
}
