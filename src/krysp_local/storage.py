"""Local-disk storage for recordings and their transcripts.

Pure stdlib, no OS-specific dependency, so it can be exercised in tests
without any real audio hardware.
"""

from __future__ import annotations

import json
from dataclasses import asdict, dataclass, field
from datetime import datetime
from pathlib import Path
from typing import Optional


@dataclass
class Recording:
    id: str
    name: str
    created_at: str
    dir: Path
    mic_wav: Path
    call_wav: Path
    transcript_txt: Path
    transcript_json: Path
    transcribed: bool = False

    def to_meta_dict(self) -> dict:
        d = asdict(self)
        d["dir"] = str(self.dir)
        d["mic_wav"] = str(self.mic_wav)
        d["call_wav"] = str(self.call_wav)
        d["transcript_txt"] = str(self.transcript_txt)
        d["transcript_json"] = str(self.transcript_json)
        return d

    @staticmethod
    def from_meta_dict(d: dict) -> "Recording":
        return Recording(
            id=d["id"],
            name=d["name"],
            created_at=d["created_at"],
            dir=Path(d["dir"]),
            mic_wav=Path(d["mic_wav"]),
            call_wav=Path(d["call_wav"]),
            transcript_txt=Path(d["transcript_txt"]),
            transcript_json=Path(d["transcript_json"]),
            transcribed=d.get("transcribed", False),
        )


class RecordingsStore:
    """Manages `<base_dir>/<recording_id>/` folders, each with a meta.json sidecar."""

    META_FILENAME = "meta.json"

    def __init__(self, base_dir: Path):
        self.base_dir = Path(base_dir)

    def _new_id(self, when: Optional[datetime] = None) -> str:
        when = when or datetime.now()
        return when.strftime("%Y-%m-%d_%H-%M-%S")

    def create_recording(self, name: Optional[str] = None, when: Optional[datetime] = None) -> Recording:
        when = when or datetime.now()
        rid = self._new_id(when)
        rdir = self.base_dir / rid
        # Guard against two recordings started in the same second.
        suffix = 1
        base_rdir = rdir
        while rdir.exists():
            rdir = Path(f"{base_rdir}-{suffix}")
            suffix += 1
        rid = rdir.name
        rdir.mkdir(parents=True, exist_ok=False)

        recording = Recording(
            id=rid,
            name=name or rid,
            created_at=when.isoformat(timespec="seconds"),
            dir=rdir,
            mic_wav=rdir / "mic.wav",
            call_wav=rdir / "call.wav",
            transcript_txt=rdir / "transcript.txt",
            transcript_json=rdir / "transcript.json",
            transcribed=False,
        )
        self._save_meta(recording)
        return recording

    def _meta_path(self, rdir: Path) -> Path:
        return rdir / self.META_FILENAME

    def _save_meta(self, recording: Recording) -> None:
        meta_path = self._meta_path(recording.dir)
        meta_path.write_text(json.dumps(recording.to_meta_dict(), indent=2))

    def list_recordings(self) -> list[Recording]:
        if not self.base_dir.exists():
            return []
        recordings = []
        for child in self.base_dir.iterdir():
            if not child.is_dir():
                continue
            meta_path = self._meta_path(child)
            if not meta_path.exists():
                continue
            try:
                data = json.loads(meta_path.read_text())
                recordings.append(Recording.from_meta_dict(data))
            except (json.JSONDecodeError, KeyError):
                continue
        recordings.sort(key=lambda r: r.created_at, reverse=True)
        return recordings

    def get_recording(self, recording_id: str) -> Optional[Recording]:
        rdir = self.base_dir / recording_id
        meta_path = self._meta_path(rdir)
        if not meta_path.exists():
            return None
        return Recording.from_meta_dict(json.loads(meta_path.read_text()))

    def mark_transcribed(self, recording: Recording) -> Recording:
        recording.transcribed = True
        self._save_meta(recording)
        return recording

    def delete_recording(self, recording: Recording) -> None:
        import shutil

        if recording.dir.exists() and recording.dir.is_relative_to(self.base_dir):
            shutil.rmtree(recording.dir)
