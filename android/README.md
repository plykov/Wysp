# Krysp Local — Android

A private, on-device Android call recorder + transcriber: records your microphone and (given
root) the actual call audio from Zoom/WeChat/Telegram/etc., transcribes fully on-device with
Whisper, and produces a Markdown "minutes" doc with a summary and action items. No cloud calls
anywhere. Personal use on your own device.

## The core constraint: Android blocks capturing another app's VoIP call audio

This is the single most important thing to understand before touching this code.

Android's public `AudioPlaybackCaptureConfiguration` API (the only sanctioned way for a normal app
to capture another app's audio, via `MediaProjection`) **explicitly excludes** any stream tagged
`AudioAttributes.USAGE_VOICE_COMMUNICATION` — and that's exactly the tag Zoom, WeChat, and
Telegram apply to call audio. This is a deliberate anti-surveillance protection, not an oversight.
The exclusion is lifted only for apps holding the signature|privileged permission
`CAPTURE_AUDIO_OUTPUT` or `CAPTURE_VOICE_COMMUNICATION_OUTPUT` — which normal (Play Store) apps
cannot be granted. Those permissions exist for exactly this purpose (Pixel's own call recording,
Assistant call-screening/transcription use them), but getting them requires the APK to be
installed as a **system-privileged app**, which in practice means a rooted device with a Magisk
systemless priv-app module (see `magisk-module/`) or building your own signed ROM/AOSP tree.

There is no portable, Play-Store-distributable way to do this. This project is built for **your
own rooted device only**, not for distribution.

### The two capture paths this app implements

| | Works without root | Captures Zoom/WeChat/Telegram audio directly | Speaker separation |
|---|---|---|---|
| `MicSpeakerphoneSource` (always available) | Yes | No — captures your mic while the call is on speakerphone, so it picks up the far end acoustically, not digitally | Heuristic pause-based turn-splitting only (`Speaker 1` / `Speaker 2`, not "you" vs "them") |
| `PrivilegedDualTrackSource` (needs Magisk priv-app setup) | No | Yes, via `AudioPlaybackCaptureConfiguration` filtered to `USAGE_VOICE_COMMUNICATION`, gated by the privileged permission | Real: mic uplink and call downlink are two independent tracks, so "You" vs "Call" is exact |

The app auto-detects which is available at runtime (`CaptureCapability.hasPrivilegedCaptureAccess`)
and falls back to the mic+speakerphone path if the privileged permission isn't granted — so it's
still usable (with lower fidelity and approximate speaker splitting) on a non-rooted install.

**A note on legal/consent risk**: recording calls without the other party's knowledge or consent
is illegal in many jurisdictions (two-party/all-party consent laws) regardless of the technical
mechanism. This app doesn't build in a bypass for that — the foreground-service notification
required by Android for any ongoing mic capture stays visible for the whole recording as an
honest signal, and it's on you to make sure recording is appropriate for the calls you're on.

## Architecture

```
                                    ┌─────────────────────────────┐
 Mic (your voice)          ───────▶│ MicSpeakerphoneSource         │──▶ mixed.wav (single track)
 (speakerphone picks up            │  (always available)           │
  the far end acoustically)        └─────────────────────────────┘
                                              or
 Mic uplink            ───────▶┌─────────────────────────────┐
                                │ PrivilegedDualTrackSource     │──▶ you.wav
 AudioPlaybackCapture   ───────│  (needs Magisk priv-app)      │──▶ call.wav
 (USAGE_VOICE_COMMUNICATION)   └─────────────────────────────┘

                    WhisperBridge (JNI → whisper.cpp, on-device)
                                    │
                                    ▼
                  core module: mergeSegments / naiveDiarize
                                    │
                                    ▼
        core module: extractActionItems + summarize (extractive, on-device)
                                    │
                                    ▼
                         formatMinutes() → minutes.md
```

- **`core/`** — pure Kotlin/JVM module, no Android dependency, unit tested (`./gradlew :core:test`):
  - `Transcript.kt` — `TranscriptSegment`, `mergeSegments` (chronological merge of the two-track path).
  - `Diarization.kt` — `naiveDiarize`: pause-based turn-splitting heuristic for the single-mixed-track
    fallback. **Not real speaker diarization** (no voice embeddings/clustering) — see its doc comment.
  - `ActionItems.kt` — rule-based (regex cue phrases: "I'll...", "can you...", due-date hints),
    fully offline. Will have false positives/negatives vs. an LLM-based extractor by design.
  - `Summary.kt` — lightweight extractive summarizer (frequency-scored sentence selection), because
    a good abstractive summary needs an LLM that's heavy to run on a phone. Swap in a MediaPipe
    LLM / llama.cpp-backed generator later for higher-quality abstractive minutes.
  - `Minutes.kt` — combines the above into one Markdown doc.
- **`app/`** — the Android application:
  - `capture/` — `CallAudioSource` interface + both implementations + `RecordingForegroundService`
    (the mic-in-use notification is not optional — Android enforces it, and it doubles as a
    recording-in-progress indicator).
  - `transcribe/` — `WhisperBridge` (JNI) + `TranscriptionWorker` (wires capture output → whisper →
    `core` → `minutes.md`).
  - `storage/` — `RecordingsStore`: `<filesDir>/recordings/<id>/` + `meta.json` sidecar, deliberately
    mirroring the Windows companion app's `storage.py` (see `/README.md` at the repo root) so the
    two projects are structurally recognizable as the same idea on two platforms.
  - `ui/` — one `MainActivity`: start/stop, recordings list, transcribe, view minutes.
- **`native/`** — JNI shim around whisper.cpp (not vendored; see `native/README.md`).
- **`magisk-module/`** — the priv-app allowlist module for the root-based capture path.

## Setup

### 1. Whisper model + native build

whisper.cpp isn't vendored in this repo. See `native/README.md`: add it as a submodule, uncomment
the `externalNativeBuild` block in `app/build.gradle.kts`, and get a GGML model file (e.g.
`ggml-base.en.bin`) onto the device — bundle it in assets or download once on first run.

### 2. Gradle wrapper

`gradle/wrapper/gradle-wrapper.properties` is checked in, but the wrapper jar itself isn't (this
was built in a sandboxed environment without access to `services.gradle.org` to fetch it). Either:
- Open the project in Android Studio — it can regenerate the wrapper automatically, or
- Run `gradle wrapper` yourself once you have normal internet access.

### 3. (Optional) Root-based dual-track capture

Only if you want real "You" vs "Call" track separation instead of the mic+speakerphone fallback.
See `magisk-module/README.md` for the full build → zip → flash → verify steps. Skip this entirely
to just use the always-available mic+speakerphone path — the app works fine without it.

**On a Galaxy S25 Ultra** (Snapdragon 8 Elite, Android 15/16 — this project's reference device):
`magisk-module/ROOTING_S25_ULTRA.md` covers the device-specific rooting steps (unlocked/
international units only — most US carrier variants disable OEM bootloader unlocking entirely,
which makes this whole path a dead end on those units). It's a GKI device, so Magisk patches
`init_boot.img`, and flashing goes through Odin rather than fastboot. Bootloader unlock wipes the
device and permanently trips Knox — read the warnings in that doc before starting.

### 4. Build & run

```
./gradlew :app:assembleDebug
```
or open in Android Studio and hit Run. Grant microphone + notification permissions on first
launch.

## Tests / build verification

```
./gradlew :core:test        # 21 unit tests: merge, diarization heuristic, action items, summary, minutes
./gradlew :app:assembleDebug # compiles + links the full app module
./gradlew :app:lintDebug     # 0 errors
```

All three were run for real during development (Android SDK platform 34 + build-tools 34.0.0,
no emulator/device needed for this level of verification) — `:core:test`'s 21 tests pass,
`:app:assembleDebug` produces a working `app-debug.apk` (confirmed via `aapt dump badging`:
correct package, permissions, and `MainActivity` registered as launcher), and `:app:lintDebug`
passes with zero errors (the `AudioRecord`/`AudioPlaybackCaptureConfiguration` permission calls
are guarded by an explicit runtime check — see `capture/PermissionCheck.kt` — that lint's static
analysis can't trace through a helper function, hence the two `@SuppressLint` annotations in
`MicSpeakerphoneSource`/`PrivilegedDualTrackSource`).

What compiling and linting *doesn't* prove: that `PrivilegedDualTrackSource` actually receives
non-silent audio from `AudioPlaybackCaptureConfiguration` once privileged, that the JNI shim in
`native/whisper_jni.cpp` links and runs correctly against a real whisper.cpp checkout, or that the
UI behaves correctly — those need a real device (see `android/README.md`'s capture-path table and
`native/README.md`'s status notes for what's genuinely unverified vs. what's now confirmed to
build).

## Privacy model

- No network calls anywhere in the app's runtime path. Transcription and summarization both run
  on-device (whisper.cpp + the `core` module's extractive summarizer).
- The only network access this project ever needs is a one-time Whisper model download, if you
  choose the download-on-first-run approach instead of bundling the model in assets.
- Recordings, transcripts, and minutes are written only to app-private storage
  (`<filesDir>/recordings/`), never uploaded anywhere.
