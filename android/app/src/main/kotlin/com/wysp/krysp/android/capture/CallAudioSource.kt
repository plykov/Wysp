package com.wysp.krysp.android.capture

import java.io.File

data class CaptureOutput(val youWav: File, val callWav: File?)

/**
 * A source of call audio. Two implementations:
 *  - [MicSpeakerphoneSource]: always available, single mixed mono track (you + whatever the
 *    phone speaker plays back, picked up by the mic). Works with any calling app, no special
 *    permissions.
 *  - [PrivilegedDualTrackSource]: requires this APK to be installed as a privileged system app
 *    with CAPTURE_AUDIO_OUTPUT / CAPTURE_VOICE_COMMUNICATION_OUTPUT granted (see
 *    /android/magisk-module). Produces two independently-attributed tracks.
 */
interface CallAudioSource {
    val producesSeparateTracks: Boolean

    /** Starts capture, writing into new file(s) under [outputDir]. Throws if capture can't start. */
    fun start(outputDir: File): CaptureOutput

    fun stop()
}

/** Whether this source can actually be used right now (permission-wise) on this device. */
interface SourceAvailability {
    fun isAvailable(): Boolean
}
