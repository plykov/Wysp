"""Offline transcription of recorded tracks, and merging them into one timeline.

`merge_segments` is pure and OS-independent (unit tested). The actual speech
recognition (`transcribe_recording`) needs faster-whisper installed and is
exercised manually on Windows, since it needs real audio + a downloaded model.
"""

from __future__ import annotations

from dataclasses import dataclass
from pathlib import Path
from typing import Callable, Optional

from . import config
from .storage import Recording, RecordingsStore


@dataclass
class TranscriptSegment:
    track: str  # config.MIC_TRACK or config.CALL_TRACK
    start: float  # seconds
    end: float
    text: str

    @property
    def label(self) -> str:
        return config.TRACK_LABELS.get(self.track, self.track)


def merge_segments(
    mic_segments: list[TranscriptSegment], call_segments: list[TranscriptSegment]
) -> list[TranscriptSegment]:
    """Interleave two already-chronological segment lists into one timeline, sorted by start time."""
    merged = list(mic_segments) + list(call_segments)
    merged.sort(key=lambda s: s.start)
    return merged


def format_transcript(segments: list[TranscriptSegment]) -> str:
    lines = []
    for seg in segments:
        timestamp = _format_timestamp(seg.start)
        lines.append(f"[{timestamp}] {seg.label}: {seg.text.strip()}")
    return "\n".join(lines)


def _format_timestamp(seconds: float) -> str:
    total = int(seconds)
    hours, remainder = divmod(total, 3600)
    minutes, secs = divmod(remainder, 60)
    if hours:
        return f"{hours:02d}:{minutes:02d}:{secs:02d}"
    return f"{minutes:02d}:{secs:02d}"


class WhisperEngine:
    """Thin wrapper around faster-whisper so it can be swapped for another local engine."""

    def __init__(self, model_size: str = config.DEFAULT_MODEL_SIZE):
        self.model_size = model_size
        self._model = None

    def _load(self):
        if self._model is None:
            from faster_whisper import WhisperModel

            self._model = WhisperModel(self.model_size, device="auto", compute_type="auto")
        return self._model

    def transcribe_wav(self, wav_path: Path, track: str) -> list[TranscriptSegment]:
        model = self._load()
        segments, _info = model.transcribe(str(wav_path), vad_filter=True)
        return [
            TranscriptSegment(track=track, start=seg.start, end=seg.end, text=seg.text)
            for seg in segments
        ]


def transcribe_recording(
    recording: Recording,
    store: RecordingsStore,
    engine: Optional[WhisperEngine] = None,
    progress_cb: Optional[Callable[[str], None]] = None,
) -> list[TranscriptSegment]:
    """Transcribe both tracks of a recording, merge, and persist to disk."""
    engine = engine or WhisperEngine()
    notify = progress_cb or (lambda _msg: None)

    notify("Transcribing your microphone track...")
    mic_segments = engine.transcribe_wav(recording.mic_wav, config.MIC_TRACK) if recording.mic_wav.exists() else []

    notify("Transcribing call audio track...")
    call_segments = (
        engine.transcribe_wav(recording.call_wav, config.CALL_TRACK) if recording.call_wav.exists() else []
    )

    merged = merge_segments(mic_segments, call_segments)

    notify("Saving transcript...")
    recording.transcript_txt.write_text(format_transcript(merged), encoding="utf-8")
    recording.transcript_json.write_text(
        _segments_to_json(merged),
        encoding="utf-8",
    )
    store.mark_transcribed(recording)
    notify("Done.")
    return merged


def _segments_to_json(segments: list[TranscriptSegment]) -> str:
    import json

    return json.dumps(
        [{"track": s.track, "start": s.start, "end": s.end, "text": s.text} for s in segments],
        indent=2,
    )
