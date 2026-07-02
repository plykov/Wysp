from datetime import datetime, timedelta
from pathlib import Path

from krysp_local.storage import RecordingsStore


def test_create_recording_sets_up_paths(tmp_path: Path):
    store = RecordingsStore(tmp_path)
    rec = store.create_recording(name="Standup")

    assert rec.name == "Standup"
    assert rec.dir == tmp_path / rec.id
    assert rec.mic_wav == rec.dir / "mic.wav"
    assert rec.call_wav == rec.dir / "call.wav"
    assert not rec.transcribed
    assert (rec.dir / "meta.json").exists()


def test_create_recording_avoids_collisions_in_same_second(tmp_path: Path):
    store = RecordingsStore(tmp_path)
    when = datetime(2026, 7, 2, 9, 0, 0)

    first = store.create_recording(when=when)
    second = store.create_recording(when=when)

    assert first.id != second.id
    assert first.dir.exists()
    assert second.dir.exists()


def test_list_recordings_sorted_newest_first(tmp_path: Path):
    store = RecordingsStore(tmp_path)
    base = datetime(2026, 7, 2, 9, 0, 0)

    older = store.create_recording(name="older", when=base)
    newer = store.create_recording(name="newer", when=base + timedelta(minutes=5))

    listed = store.list_recordings()
    assert [r.id for r in listed] == [newer.id, older.id]


def test_get_recording_round_trips(tmp_path: Path):
    store = RecordingsStore(tmp_path)
    created = store.create_recording(name="1:1")

    fetched = store.get_recording(created.id)

    assert fetched is not None
    assert fetched.name == "1:1"
    assert fetched.mic_wav == created.mic_wav


def test_get_recording_missing_returns_none(tmp_path: Path):
    store = RecordingsStore(tmp_path)
    assert store.get_recording("does-not-exist") is None


def test_mark_transcribed_persists(tmp_path: Path):
    store = RecordingsStore(tmp_path)
    rec = store.create_recording()

    store.mark_transcribed(rec)

    reloaded = store.get_recording(rec.id)
    assert reloaded.transcribed is True


def test_delete_recording_removes_directory(tmp_path: Path):
    store = RecordingsStore(tmp_path)
    rec = store.create_recording()

    store.delete_recording(rec)

    assert not rec.dir.exists()
    assert store.get_recording(rec.id) is None


def test_list_recordings_on_empty_store(tmp_path: Path):
    store = RecordingsStore(tmp_path / "does-not-exist-yet")
    assert store.list_recordings() == []
