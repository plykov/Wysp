"""Enumerate Windows WASAPI audio devices: real microphones and loopback
(system output) devices.

Windows-only (relies on PyAudioWPatch's WASAPI loopback support). Importing
this module is safe on any OS; calling its functions off Windows raises a
clear RuntimeError instead of failing on import, so the rest of the app can
be imported/tested anywhere.
"""

from __future__ import annotations

from dataclasses import dataclass

try:
    import pyaudiowpatch as pyaudio

    _IMPORT_ERROR: Exception | None = None
except ImportError as exc:  # pragma: no cover - only available on Windows
    pyaudio = None
    _IMPORT_ERROR = exc


@dataclass
class AudioDevice:
    index: int
    name: str
    is_loopback: bool
    channels: int
    sample_rate: int


def _require_pyaudio() -> None:
    if pyaudio is None:
        raise RuntimeError(
            "pyaudiowpatch is not available "
            f"({_IMPORT_ERROR}). Run 'pip install -r requirements.txt' "
            "in your Windows Python environment, then use Refresh devices."
        )


def list_microphones() -> list[AudioDevice]:
    """Real (non-loopback) WASAPI input devices — i.e. actual microphones."""
    _require_pyaudio()
    devices = []
    with pyaudio.PyAudio() as p:
        wasapi_index = p.get_host_api_info_by_type(pyaudio.paWASAPI)["index"]
        for i in range(p.get_device_count()):
            info = p.get_device_info_by_index(i)
            if info["hostApi"] != wasapi_index:
                continue
            if info.get("isLoopbackDevice"):
                continue
            if info["maxInputChannels"] <= 0:
                continue
            devices.append(_to_audio_device(info, is_loopback=False))
    return devices


def list_loopback_devices() -> list[AudioDevice]:
    """WASAPI loopback devices: 'listen in' on an output device, used to capture call audio."""
    _require_pyaudio()
    devices = []
    with pyaudio.PyAudio() as p:
        for info in p.get_loopback_device_info_generator():
            devices.append(_to_audio_device(info, is_loopback=True))
    return devices


def default_microphone() -> AudioDevice | None:
    _require_pyaudio()
    with pyaudio.PyAudio() as p:
        wasapi_info = p.get_host_api_info_by_type(pyaudio.paWASAPI)
        default_index = wasapi_info.get("defaultInputDevice", -1)
        if default_index is None or default_index < 0:
            return None
        info = p.get_device_info_by_index(default_index)
        return _to_audio_device(info, is_loopback=False)


def default_loopback_device() -> AudioDevice | None:
    """The loopback device mirroring the current default output (speakers/headphones)."""
    _require_pyaudio()
    with pyaudio.PyAudio() as p:
        wasapi_info = p.get_host_api_info_by_type(pyaudio.paWASAPI)
        default_output_index = wasapi_info.get("defaultOutputDevice", -1)
        if default_output_index is None or default_output_index < 0:
            return None
        default_speakers = p.get_device_info_by_index(default_output_index)
        if not default_speakers.get("isLoopbackDevice"):
            for loopback in p.get_loopback_device_info_generator():
                if default_speakers["name"] in loopback["name"]:
                    default_speakers = loopback
                    break
            else:
                return None
        return _to_audio_device(default_speakers, is_loopback=True)


def _to_audio_device(info: dict, is_loopback: bool) -> AudioDevice:
    return AudioDevice(
        index=info["index"],
        name=info["name"],
        is_loopback=is_loopback,
        channels=int(info["maxInputChannels"]),
        sample_rate=int(info["defaultSampleRate"]),
    )
