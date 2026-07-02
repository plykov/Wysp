from krysp_local import config
from krysp_local.transcriber import TranscriptSegment, format_transcript, merge_segments


def _seg(track, start, end, text):
    return TranscriptSegment(track=track, start=start, end=end, text=text)


def test_merge_segments_interleaves_by_start_time():
    mic = [_seg(config.MIC_TRACK, 0.0, 2.0, "hey can you hear me"), _seg(config.MIC_TRACK, 5.0, 6.5, "great")]
    call = [_seg(config.CALL_TRACK, 2.5, 4.5, "yes loud and clear")]

    merged = merge_segments(mic, call)

    assert [s.text.strip() for s in merged] == [
        "hey can you hear me",
        "yes loud and clear",
        "great",
    ]


def test_merge_segments_labels_tracks():
    mic = [_seg(config.MIC_TRACK, 0.0, 1.0, "hi")]
    call = [_seg(config.CALL_TRACK, 1.0, 2.0, "hi back")]

    merged = merge_segments(mic, call)

    assert merged[0].label == "You"
    assert merged[1].label == "Call"


def test_merge_segments_empty_inputs():
    assert merge_segments([], []) == []


def test_format_transcript_includes_timestamp_label_and_text():
    segments = [_seg(config.MIC_TRACK, 65.0, 67.0, " hello there ")]

    text = format_transcript(segments)

    assert text == "[01:05] You: hello there"


def test_format_transcript_hours_shown_when_needed():
    segments = [_seg(config.CALL_TRACK, 3725.0, 3730.0, "still going")]

    text = format_transcript(segments)

    assert text.startswith("[01:02:05] Call:")
