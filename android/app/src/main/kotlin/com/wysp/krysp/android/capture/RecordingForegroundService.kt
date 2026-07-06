package com.wysp.krysp.android.capture

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.wysp.krysp.android.storage.RecordingMeta
import com.wysp.krysp.android.storage.RecordingsStore
import java.io.File

/**
 * Foreground service so recording survives backgrounding and - importantly - so there's always a
 * persistent, visible notification while a mic is in use. Android already enforces a mic-in-use
 * indicator at the OS level for any app doing this; this service's own notification adds a plain
 * text reminder on top of that, consistent with treating recording-notice as a feature, not an
 * afterthought.
 */
class RecordingForegroundService : Service() {

    companion object {
        const val ACTION_START = "com.wysp.krysp.android.action.START_RECORDING"
        const val ACTION_STOP = "com.wysp.krysp.android.action.STOP_RECORDING"
        const val EXTRA_PROJECTION_RESULT_CODE = "projection_result_code"
        const val EXTRA_PROJECTION_DATA = "projection_data"

        private const val CHANNEL_ID = "krysp_recording"
        private const val NOTIFICATION_ID = 1

        var currentRecordingId: String? = null
            private set
    }

    private var source: CallAudioSource? = null
    private lateinit var store: RecordingsStore
    private var activeMeta: RecordingMeta? = null

    override fun onCreate() {
        super.onCreate()
        store = RecordingsStore(File(filesDir, "recordings"))
        createNotificationChannel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startRecording(intent)
            ACTION_STOP -> stopRecording()
        }
        return START_NOT_STICKY
    }

    private fun startRecording(intent: Intent) {
        if (source != null) return // already recording

        val chosenSource = buildSource(intent)
        val meta = store.createRecording(producesSeparateTracks = chosenSource.producesSeparateTracks)
        val output = chosenSource.start(File(meta.youWavPath).parentFile!!)

        source = chosenSource
        activeMeta = meta
        currentRecordingId = meta.id

        startForeground(NOTIFICATION_ID, buildNotification())
    }

    private fun buildSource(intent: Intent): CallAudioSource {
        val resultCode = intent.getIntExtra(EXTRA_PROJECTION_RESULT_CODE, -1)
        val projectionData = intent.getParcelableExtra<Intent>(EXTRA_PROJECTION_DATA)

        val canUsePrivileged = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            CaptureCapability.hasPrivilegedCaptureAccess(this) &&
            resultCode != -1 &&
            projectionData != null

        if (!canUsePrivileged) return MicSpeakerphoneSource()

        val projectionManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val projection: MediaProjection = projectionManager.getMediaProjection(resultCode, projectionData!!)
        return try {
            PrivilegedDualTrackSource(projection)
        } catch (e: Exception) {
            projection.stop()
            MicSpeakerphoneSource()
        }
    }

    private fun stopRecording() {
        source?.stop()
        source = null
        activeMeta?.let { /* transcription is triggered explicitly by the user from the UI */ }
        activeMeta = null
        currentRecordingId = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Call recording",
            NotificationManager.IMPORTANCE_LOW,
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val stopIntent = Intent(this, RecordingForegroundService::class.java).apply { action = ACTION_STOP }
        val stopPendingIntent = PendingIntent.getService(
            this, 0, stopIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Krysp Local is recording")
            .setContentText("Tap Stop when your call ends.")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_media_pause, "Stop", stopPendingIntent)
            .build()
    }

    override fun onDestroy() {
        source?.stop()
        source = null
        super.onDestroy()
    }
}
