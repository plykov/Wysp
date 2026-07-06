package com.wysp.krysp.android.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.widget.ArrayAdapter
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.wysp.krysp.android.capture.CaptureCapability
import com.wysp.krysp.android.capture.RecordingForegroundService
import com.wysp.krysp.android.databinding.ActivityMainBinding
import com.wysp.krysp.android.storage.RecordingMeta
import com.wysp.krysp.android.storage.RecordingsStore
import com.wysp.krysp.android.transcribe.TranscriptionWorker
import java.io.File
import kotlin.concurrent.thread

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var store: RecordingsStore
    private var recordings: List<RecordingMeta> = emptyList()
    private var selected: RecordingMeta? = null
    private var isRecording = false

    // Path to a whisper.cpp GGML model file. See native/README.md - this is a placeholder path
    // until you've bundled/downloaded a model; transcription will fail with a clear error until then.
    private val modelPath: String by lazy { File(filesDir, "models/ggml-base.en.bin").absolutePath }

    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        // Nothing special: onStartStopClicked() re-checks permissions before actually starting.
    }

    private val projectionLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val intent = Intent(this, RecordingForegroundService::class.java).apply {
            action = RecordingForegroundService.ACTION_START
            if (result.resultCode != RESULT_OK || result.data == null) {
                // User declined the projection prompt (or it's unavailable) - service falls back
                // to MicSpeakerphoneSource automatically since no projection extras are attached.
            } else {
                putExtra(RecordingForegroundService.EXTRA_PROJECTION_RESULT_CODE, result.resultCode)
                putExtra(RecordingForegroundService.EXTRA_PROJECTION_DATA, result.data)
            }
        }
        startForegroundService(intent)
        isRecording = true
        updateRecordingUi()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        store = RecordingsStore(File(filesDir, "recordings"))

        binding.startStopButton.setOnClickListener { onStartStopClicked() }
        binding.transcribeButton.setOnClickListener { onTranscribeClicked() }
        binding.deleteButton.setOnClickListener { onDeleteClicked() }
        binding.recordingsList.setOnItemClickListener { _, _, position, _ ->
            selected = recordings.getOrNull(position)
            showMinutesForSelected()
        }

        isRecording = RecordingForegroundService.currentRecordingId != null
        updateRecordingUi()
        refreshRecordings()
    }

    override fun onResume() {
        super.onResume()
        refreshRecordings()
    }

    private fun onStartStopClicked() {
        if (isRecording) {
            startForegroundService(
                Intent(this, RecordingForegroundService::class.java).apply {
                    action = RecordingForegroundService.ACTION_STOP
                },
            )
            isRecording = false
            updateRecordingUi()
            refreshRecordings()
            return
        }

        val neededPermissions = listOfNotNull(
            Manifest.permission.RECORD_AUDIO,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Manifest.permission.POST_NOTIFICATIONS else null,
        ).filter { ActivityCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }

        if (neededPermissions.isNotEmpty()) {
            permissionLauncher.launch(neededPermissions.toTypedArray())
            binding.statusText.text = "Grant permissions, then tap Start Recording again."
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && CaptureCapability.hasPrivilegedCaptureAccess(this)) {
            // Privileged capture is available - still route through the MediaProjection consent
            // flow (see PrivilegedDualTrackSource's docs for why), then the service picks it up.
            val projectionManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            projectionLauncher.launch(projectionManager.createScreenCaptureIntent())
        } else {
            // No privileged permission on this install - go straight to the mic+speakerphone
            // fallback, no projection prompt needed.
            startForegroundService(
                Intent(this, RecordingForegroundService::class.java).apply {
                    action = RecordingForegroundService.ACTION_START
                },
            )
            isRecording = true
            updateRecordingUi()
        }
    }

    private fun updateRecordingUi() {
        binding.startStopButton.text = if (isRecording) "Stop Recording" else "Start Recording"
        binding.statusText.text = if (isRecording) "Recording..." else "Idle"
    }

    private fun refreshRecordings() {
        recordings = store.listRecordings()
        binding.recordingsList.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_list_item_1,
            recordings.map { "${it.name}  ${if (it.transcribed) "(transcribed)" else ""}" },
        )
    }

    private fun onTranscribeClicked() {
        val meta = selected ?: return
        binding.statusText.text = "Transcribing..."
        thread(name = "transcribe-${meta.id}") {
            try {
                TranscriptionWorker(modelPath).transcribe(meta, store) { progress ->
                    runOnUiThread { binding.statusText.text = progress }
                }
            } catch (e: Exception) {
                runOnUiThread { binding.statusText.text = "Transcription failed: ${e.message}" }
                return@thread
            }
            runOnUiThread {
                binding.statusText.text = "Idle"
                refreshRecordings()
                showMinutesForSelected()
            }
        }
    }

    private fun onDeleteClicked() {
        val meta = selected ?: return
        store.deleteRecording(meta)
        selected = null
        binding.minutesText.text = ""
        refreshRecordings()
    }

    private fun showMinutesForSelected() {
        val meta = selected ?: return
        val minutesFile = File(meta.minutesPath)
        binding.minutesText.text = if (minutesFile.exists()) minutesFile.readText() else "Not transcribed yet."
    }
}
