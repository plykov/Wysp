"""App-wide configuration and defaults."""

import os
from pathlib import Path

DEFAULT_DATA_DIR = Path(os.environ.get("KRYSP_DATA_DIR", Path.home() / "KryspLocal"))
RECORDINGS_DIR = DEFAULT_DATA_DIR / "recordings"

SAMPLE_RATE = 16000
CHANNELS_PER_TRACK = 1
SAMPLE_WIDTH_BYTES = 2  # 16-bit PCM

VALID_MODEL_SIZES = ("tiny", "base", "small", "medium", "large-v3")
DEFAULT_MODEL_SIZE = os.environ.get("KRYSP_MODEL_SIZE", "small")
if DEFAULT_MODEL_SIZE not in VALID_MODEL_SIZES:
    DEFAULT_MODEL_SIZE = "small"

MIC_TRACK = "you"
CALL_TRACK = "call"
TRACK_LABELS = {MIC_TRACK: "You", CALL_TRACK: "Call"}


def ensure_data_dirs() -> None:
    RECORDINGS_DIR.mkdir(parents=True, exist_ok=True)
