"""Minimal Tkinter UI: pick devices, record, browse past recordings, transcribe."""

from __future__ import annotations

import threading
import time
import tkinter as tk
from tkinter import messagebox, ttk

from . import audio_devices, config
from .storage import Recording, RecordingsStore
from .transcriber import WhisperEngine, transcribe_recording


class KryspLocalApp:
    def __init__(self, root: tk.Tk):
        self.root = root
        self.root.title("Krysp Local")
        self.root.geometry("720x560")

        config.ensure_data_dirs()
        self.store = RecordingsStore(config.RECORDINGS_DIR)
        self.recorder = None
        self.current_recording: Recording | None = None
        self._record_start_time: float | None = None
        self._timer_job = None
        self._engine = WhisperEngine()

        self.mics: list[audio_devices.AudioDevice] = []
        self.loopbacks: list[audio_devices.AudioDevice] = []
        self._devices_available = True

        self._build_widgets()
        self._load_devices()
        self._refresh_recordings()

    # ---- layout -----------------------------------------------------

    def _build_widgets(self) -> None:
        device_frame = ttk.LabelFrame(self.root, text="Devices")
        device_frame.pack(fill="x", padx=10, pady=(10, 5))

        ttk.Label(device_frame, text="Microphone:").grid(row=0, column=0, sticky="w", padx=5, pady=5)
        self.mic_var = tk.StringVar()
        self.mic_combo = ttk.Combobox(device_frame, textvariable=self.mic_var, state="readonly", width=50)
        self.mic_combo.grid(row=0, column=1, padx=5, pady=5)

        ttk.Label(device_frame, text="Call audio (output device):").grid(
            row=1, column=0, sticky="w", padx=5, pady=5
        )
        self.loopback_var = tk.StringVar()
        self.loopback_combo = ttk.Combobox(
            device_frame, textvariable=self.loopback_var, state="readonly", width=50
        )
        self.loopback_combo.grid(row=1, column=1, padx=5, pady=5)

        ttk.Button(device_frame, text="Refresh devices", command=self._load_devices).grid(
            row=0, column=2, rowspan=2, padx=5
        )

        record_frame = ttk.Frame(self.root)
        record_frame.pack(fill="x", padx=10, pady=5)

        self.record_button = ttk.Button(record_frame, text="Start Recording", command=self._toggle_recording)
        self.record_button.pack(side="left")

        self.status_var = tk.StringVar(value="Idle")
        ttk.Label(record_frame, textvariable=self.status_var).pack(side="left", padx=10)

        list_frame = ttk.LabelFrame(self.root, text="Recordings")
        list_frame.pack(fill="both", expand=True, padx=10, pady=5)

        columns = ("name", "created_at", "transcribed")
        self.tree = ttk.Treeview(list_frame, columns=columns, show="headings", height=8)
        for col, label, width in (
            ("name", "Name", 220),
            ("created_at", "Created", 180),
            ("transcribed", "Transcribed", 100),
        ):
            self.tree.heading(col, text=label)
            self.tree.column(col, width=width)
        self.tree.pack(fill="both", expand=True, side="left")
        self.tree.bind("<<TreeviewSelect>>", self._on_select_recording)

        scrollbar = ttk.Scrollbar(list_frame, orient="vertical", command=self.tree.yview)
        self.tree.configure(yscrollcommand=scrollbar.set)
        scrollbar.pack(side="right", fill="y")

        action_frame = ttk.Frame(self.root)
        action_frame.pack(fill="x", padx=10, pady=5)
        ttk.Button(action_frame, text="Transcribe", command=self._transcribe_selected).pack(side="left")
        ttk.Button(action_frame, text="Delete", command=self._delete_selected).pack(side="left", padx=5)

        transcript_frame = ttk.LabelFrame(self.root, text="Transcript")
        transcript_frame.pack(fill="both", expand=True, padx=10, pady=(5, 10))
        self.transcript_text = tk.Text(transcript_frame, wrap="word", height=10)
        self.transcript_text.pack(fill="both", expand=True)

    # ---- devices ------------------------------------------------------

    def _load_devices(self) -> None:
        try:
            self.mics = audio_devices.list_microphones()
            self.loopbacks = audio_devices.list_loopback_devices()
            self._devices_available = True
        except RuntimeError:
            self.mics, self.loopbacks = [], []
            self._devices_available = False
            self.status_var.set("Audio capture unavailable (Windows + WASAPI required)")

        self.mic_combo["values"] = [d.name for d in self.mics]
        self.loopback_combo["values"] = [d.name for d in self.loopbacks]
        if self.mics:
            self.mic_combo.current(0)
        if self.loopbacks:
            self.loopback_combo.current(0)
        self.record_button.configure(state="normal" if self._devices_available else "disabled")

    # ---- recording ------------------------------------------------------

    def _toggle_recording(self) -> None:
        if self.recorder is not None and self.recorder.is_recording:
            self._stop_recording()
        else:
            self._start_recording()

    def _start_recording(self) -> None:
        if not self.mics or not self.loopbacks:
            messagebox.showerror("Krysp Local", "No microphone/loopback device selected.")
            return

        from .recorder import Recorder  # imported lazily: Windows-only module

        mic_device = self.mics[self.mic_combo.current()]
        loopback_device = self.loopbacks[self.loopback_combo.current()]

        self.current_recording = self.store.create_recording()
        self.recorder = Recorder(mic_device.index, loopback_device.index)
        self.recorder.start(self.current_recording.mic_wav, self.current_recording.call_wav)

        self._record_start_time = time.monotonic()
        self.record_button.configure(text="Stop Recording")
        self._tick_timer()

    def _stop_recording(self) -> None:
        if self.recorder:
            self.recorder.stop()
            self.recorder.close()
        self.recorder = None
        self._record_start_time = None
        if self._timer_job:
            self.root.after_cancel(self._timer_job)
            self._timer_job = None
        self.record_button.configure(text="Start Recording")
        self.status_var.set("Recording saved.")
        self._refresh_recordings()

    def _tick_timer(self) -> None:
        if self._record_start_time is None:
            return
        elapsed = int(time.monotonic() - self._record_start_time)
        mins, secs = divmod(elapsed, 60)
        self.status_var.set(f"Recording... {mins:02d}:{secs:02d}")
        self._timer_job = self.root.after(500, self._tick_timer)

    # ---- recordings list ------------------------------------------------------

    def _refresh_recordings(self) -> None:
        self.tree.delete(*self.tree.get_children())
        for rec in self.store.list_recordings():
            self.tree.insert(
                "", "end", iid=rec.id, values=(rec.name, rec.created_at, "yes" if rec.transcribed else "no")
            )

    def _selected_recording(self) -> Recording | None:
        selection = self.tree.selection()
        if not selection:
            return None
        return self.store.get_recording(selection[0])

    def _on_select_recording(self, _event=None) -> None:
        rec = self._selected_recording()
        self.transcript_text.delete("1.0", "end")
        if rec and rec.transcribed and rec.transcript_txt.exists():
            self.transcript_text.insert("1.0", rec.transcript_txt.read_text(encoding="utf-8"))

    def _transcribe_selected(self) -> None:
        rec = self._selected_recording()
        if not rec:
            messagebox.showinfo("Krysp Local", "Select a recording first.")
            return

        def worker() -> None:
            def progress(msg: str) -> None:
                self.root.after(0, self.status_var.set, msg)

            try:
                transcribe_recording(rec, self.store, engine=self._engine, progress_cb=progress)
            except Exception as exc:  # surfaced to the user, not swallowed
                self.root.after(0, messagebox.showerror, "Transcription failed", str(exc))
                return
            self.root.after(0, self._refresh_recordings)
            self.root.after(0, self._on_select_recording)

        threading.Thread(target=worker, daemon=True).start()

    def _delete_selected(self) -> None:
        rec = self._selected_recording()
        if not rec:
            return
        if messagebox.askyesno("Krysp Local", f"Delete recording '{rec.name}'?"):
            self.store.delete_recording(rec)
            self._refresh_recordings()
            self.transcript_text.delete("1.0", "end")


def run() -> None:
    root = tk.Tk()
    KryspLocalApp(root)
    root.mainloop()
