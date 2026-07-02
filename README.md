# Krysp Local

A private, fully local Windows app for recording and transcribing your online
calls (Zoom, Meet, Teams, etc.). It is a deliberately scoped-down alternative
to [Krisp](https://krisp.ai): no noise cancellation, no accent conversion, no
cloud — just reliable microphone + call-audio capture and offline
transcription.

## Krisp architecture research (what we're borrowing from, and what we're not)

Krisp works by installing a **virtual audio driver** that inserts itself
between your physical devices and whatever conferencing app you use:

- **Krisp Microphone** (virtual mic) sits between your physical mic and the
  call app — this is the *uplink* path.
- **Krisp Speaker** (virtual speaker) sits between the call app and your
  physical speakers/headphones — this is the *downlink* path.

Two separate on-device ML pipelines run on those streams:

- **Noise cancellation**: sub-20ms latency, runs on both uplink and downlink,
  includes de-reverberation (room echo removal). This is what makes it usable
  in real time without perceptible lag.
- **Accent conversion**: ~200ms latency, downlink-only ("for the listener"),
  region-specific models (India→US, Philippines→US, LatAM→US), no
  enrollment/training needed.

For meeting notes, Krisp does **on-device ASR for English** (near-real-time,
~15% of one CPU core) precisely *because* it already owns the audio stream at
the driver level — it doesn't need to record anything to transcribe it. Other
languages fall back to **server-based transcription**. Krisp's privacy claim
is that raw audio never leaves the device unless you opt into cloud features.

Sources: [Krisp accent conversion](https://krisp.ai/ai-accent-conversion/),
[Krisp AI Meeting Assistant / Deepgram writeup](https://deepgram.com/voice-ai-apps/krisp-ai-meeting-assistant),
[Krisp bot-free meeting assistant blog](https://krisp.ai/blog/krisps-ai-meeting-assistant-transcription-notes/).

### What this project keeps, and what it deliberately drops

| Krisp capability | Krysp Local |
|---|---|
| Virtual mic/speaker driver (kernel-level audio insertion) | **Dropped.** Requires a signed kernel driver; out of scope for a private hobby build. We use plain WASAPI capture instead — no driver install, no admin rights. |
| Real-time noise cancellation | **Dropped**, per request. Use a dedicated, decent microphone instead of relying on ML cleanup — the app is built around that assumption. |
| Accent conversion | **Dropped**, per request. |
| On-device English ASR + cloud ASR for other languages | **Kept, local-only.** Transcription runs entirely on-device via [faster-whisper](https://github.com/SYSTRAN/faster-whisper) (CTranslate2 Whisper). No server-based fallback — everything stays on the laptop. |
| Meeting recording | Krisp explicitly avoids recording (it transcribes the live stream). **We do the opposite on purpose**: this app *records first* (mic + call audio as separate tracks), then transcribes the recording. That's simpler to build without a driver, and gives you a durable audio archive alongside the transcript. |

### How call audio is captured without a virtual driver

Krisp needs a virtual *speaker* to see the incoming call audio before it hits
your headphones. We get the same audio a simpler way: **WASAPI loopback
capture** on the real output device. Whatever plays out of your speakers/
headphones during a call (i.e. the other participants) is captured as its own
track, while your microphone is captured as a second, independent track. No
driver install, no admin rights, works with any conferencing app because it
operates below the app layer at the OS audio level — same principle as Krisp,
simpler mechanism.

## Architecture

```
                 ┌────────────────────┐
 Dedicated mic → │  WASAPI input       │─┐
                 └────────────────────┘ │      ┌───────────────┐
                                         ├────▶ │  Recorder      │──▶ mic.wav
                 ┌────────────────────┐ │      │  (dual stream) │──▶ system.wav
 Speakers/output │  WASAPI loopback    │─┘      └───────────────┘
 (call audio)  → │  capture             │
                 └────────────────────┘

      mic.wav ──▶ faster-whisper ──▶ segments (labeled "You")      ┐
   system.wav ──▶ faster-whisper ──▶ segments (labeled "Call")     ├─▶ merged, time-sorted transcript.txt / .json
                                                                    ┘
```

- `krysp_local/audio_devices.py` — enumerate WASAPI input + loopback devices.
- `krysp_local/recorder.py` — dual-track capture (mic + loopback) to WAV, via
  [PyAudioWPatch](https://github.com/s0d3s/PyAudioWPatch) (Windows-only WASAPI
  loopback fork of PyAudio).
- `krysp_local/transcriber.py` — offline transcription with faster-whisper,
  per-track, then merged into one chronological, speaker-labeled transcript.
- `krysp_local/storage.py` — recordings + transcripts live under
  `~/KryspLocal/recordings/<timestamp>/` with a `meta.json` sidecar. Pure
  Python, no OS-specific dependency.
- `krysp_local/gui.py` — minimal Tkinter UI: pick devices, start/stop
  recording, browse past recordings, transcribe, read transcript. Tkinter
  ships with standard Python on Windows, so there's nothing extra to install
  for the UI itself.
- `krysp_local/main.py` — entry point.

Everything after audio capture is pure Python and OS-independent; only
`recorder.py`/`audio_devices.py` (WASAPI) are Windows-specific, and they're
isolated behind a small interface so the rest of the app doesn't need to know.

## Privacy model

- No network calls anywhere in this app. Transcription is 100% local
  (faster-whisper runs on-device). There is no cloud fallback — if a language
  transcribes poorly on the local model, that's a model-size tradeoff you
  control (see below), not a server call.
- The **only** internet access this project needs is a one-time download of
  Whisper model weights from Hugging Face on first run (cached locally after
  that under `~/.cache/huggingface`). If you need zero-network-ever, download
  the model on another machine and copy the cache dir over, or swap in an
  offline engine like [Vosk](https://alphacephei.com/vosk/) — `transcriber.py`
  is written as a small pluggable interface for exactly this reason.
- Recordings and transcripts are written only to your local disk
  (`~/KryspLocal/recordings/`).

## Setup (Windows)

Requires Python 3.10+ (3.11 recommended) on Windows 10/11.

```powershell
py -m venv .venv
.venv\Scripts\activate
pip install -r requirements.txt
python -m krysp_local.main
```

First transcription run will download a Whisper model (`small` by default,
~500MB) from Hugging Face — after that it's fully offline. Change the model
size in the GUI settings or via `KRYSP_MODEL_SIZE` env var
(`tiny`/`base`/`small`/`medium`) to trade speed vs. accuracy for your laptop.

### Building a standalone .exe

```powershell
scripts\build_windows_exe.bat
```

Produces `dist\KryspLocal.exe` via PyInstaller — no Python install required
to run it on another Windows machine.

## Usage

1. Launch the app, pick your **microphone** and the **output device you hear
   the call through** (this is what gets loopback-captured as "call audio").
2. Hit **Start Recording** before joining/at the start of your call, **Stop**
   when done. Two WAV files are saved (your mic, the call audio).
3. Select the recording in the list and hit **Transcribe**. This runs fully
   offline and produces a merged transcript labeled `You:` / `Call:` by
   timestamp.

## Tests

```
pytest tests/
```

Only the OS-independent logic (`storage.py`, transcript merging) is unit
tested here — `recorder.py`/`audio_devices.py` need real WASAPI devices on
Windows and aren't covered by automated tests in this repo.
