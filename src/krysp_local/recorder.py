"""Dual-track recorder: microphone (uplink) + WASAPI loopback (downlink /
call audio), each written to its own WAV file.

Windows-only. Importing this module is safe anywhere; constructing a
`Recorder` off Windows (or without pyaudiowpatch installed) raises a clear
RuntimeError.
"""

from __future__ import annotations

import threading
import wave
from pathlib import Path
from typing import Optional

try:
    import pyaudiowpatch as pyaudio
except ImportError:  # pragma: no cover - only available on Windows
    pyaudio = None

CHUNK_FRAMES = 1024


class _StreamRecorder:
    """Reads one WASAPI input stream into a WAV file on a background thread."""

    def __init__(self, audio: "pyaudio.PyAudio", device_info: dict, out_path: Path):
        self._audio = audio
        self._device_info = device_info
        self._out_path = out_path
        self._stream = None
        self._wav: Optional[wave.Wave_write] = None
        self._thread: Optional[threading.Thread] = None
        self._stop_event = threading.Event()
        self.error: Optional[Exception] = None

    def start(self) -> None:
        channels = max(int(self._device_info["maxInputChannels"]), 1)
        rate = int(self._device_info["defaultSampleRate"])

        self._out_path.parent.mkdir(parents=True, exist_ok=True)
        self._wav = wave.open(str(self._out_path), "wb")
        self._wav.setnchannels(channels)
        self._wav.setsampwidth(self._audio.get_sample_size(pyaudio.paInt16))
        self._wav.setframerate(rate)

        self._stream = self._audio.open(
            format=pyaudio.paInt16,
            channels=channels,
            rate=rate,
            input=True,
            input_device_index=self._device_info["index"],
            frames_per_buffer=CHUNK_FRAMES,
        )
        self._thread = threading.Thread(target=self._run, daemon=True)
        self._thread.start()

    def _run(self) -> None:
        while not self._stop_event.is_set():
            try:
                data = self._stream.read(CHUNK_FRAMES, exception_on_overflow=False)
            except OSError as exc:
                self.error = exc
                break
            self._wav.writeframes(data)

    def stop(self) -> None:
        self._stop_event.set()
        if self._thread:
            self._thread.join(timeout=5)
        if self._stream:
            self._stream.stop_stream()
            self._stream.close()
        if self._wav:
            self._wav.close()


class Recorder:
    """Records a microphone device and a loopback (system output) device to two WAV files."""

    def __init__(self, mic_device_index: int, loopback_device_index: int):
        if pyaudio is None:
            raise RuntimeError(
                "pyaudiowpatch is not installed, or this isn't Windows. "
                "Krysp Local's recorder only works on Windows with WASAPI."
            )
        self._audio = pyaudio.PyAudio()
        self._mic_device_index = mic_device_index
        self._loopback_device_index = loopback_device_index
        self._mic_recorder: Optional[_StreamRecorder] = None
        self._call_recorder: Optional[_StreamRecorder] = None
        self._recording = False

    @property
    def is_recording(self) -> bool:
        return self._recording

    def start(self, mic_wav_path: Path, call_wav_path: Path) -> None:
        if self._recording:
            raise RuntimeError("Recorder is already running.")

        mic_info = self._audio.get_device_info_by_index(self._mic_device_index)
        loopback_info = self._audio.get_device_info_by_index(self._loopback_device_index)

        self._mic_recorder = _StreamRecorder(self._audio, mic_info, mic_wav_path)
        self._call_recorder = _StreamRecorder(self._audio, loopback_info, call_wav_path)
        self._mic_recorder.start()
        self._call_recorder.start()
        self._recording = True

    def stop(self) -> None:
        if not self._recording:
            return
        if self._mic_recorder:
            self._mic_recorder.stop()
        if self._call_recorder:
            self._call_recorder.stop()
        self._recording = False

    def close(self) -> None:
        self.stop()
        self._audio.terminate()
