import sys
from pathlib import Path

import numpy as np

sys.path.append(str(Path(__file__).resolve().parent.parent / "app"))

from decoder import batch_greedy_decode, greedy_decode
from preprocessing import preprocess_image, to_grayscale_ink


def test_to_grayscale_ink_inverts_light_background():
    light_bg = np.full((10, 10), 230, dtype="uint8")  # light background, no ink
    result = to_grayscale_ink(light_bg)
    assert result.mean() < 230  # should have been inverted toward dark


def test_to_grayscale_ink_uses_alpha_channel_for_rgba():
    rgba = np.zeros((10, 10, 4), dtype="uint8")
    rgba[2:5, 2:5, 3] = 255  # ink drawn via alpha
    result = to_grayscale_ink(rgba)
    assert result[3, 3] == 255
    assert result[0, 0] == 0


def test_preprocess_image_output_shape():
    canvas = np.zeros((150, 700, 4), dtype="uint8")
    canvas[40:110, 50:600, 3] = 255
    x = preprocess_image(canvas, img_height=32, img_width=256)
    assert x.shape == (1, 1, 32, 256)
    assert x.dtype == np.float32
    assert 0.0 <= x.min() and x.max() <= 1.0


def test_greedy_decode_collapses_repeats_and_removes_blanks():
    idx_to_char = {str(i): c for i, c in enumerate("abc")}
    blank_idx = 3
    # sequence: a a blank b b b c -> should decode to "abc"
    log_probs = np.full((7, 4), -10.0)
    path = [0, 0, 3, 1, 1, 1, 2]
    for t, idx in enumerate(path):
        log_probs[t, idx] = 0.0
    assert greedy_decode(log_probs, idx_to_char, blank_idx) == "abc"


def test_greedy_decode_preserves_doubled_letter_when_separated_by_blank():
    idx_to_char = {str(i): c for i, c in enumerate("hel")}  # h=0, e=1, l=2
    blank_idx = 3
    # "hello" needs a blank between the two l's: h e l blank l o(not in vocab, use l again)
    path = [0, 1, 2, 3, 2]
    log_probs = np.full((5, 4), -10.0)
    for t, idx in enumerate(path):
        log_probs[t, idx] = 0.0
    assert greedy_decode(log_probs, idx_to_char, blank_idx) == "hell"


def test_batch_greedy_decode_returns_one_string_per_sample():
    idx_to_char = {str(i): c for i, c in enumerate("ab")}
    blank_idx = 2
    batch = np.full((3, 4, 3), -10.0)
    for sample in batch:
        sample[0, 0] = 0.0
        sample[1, 2] = 0.0
        sample[2, 1] = 0.0
        sample[3, 2] = 0.0
    results = batch_greedy_decode(batch, idx_to_char, blank_idx)
    assert len(results) == 3
    assert all(r == "ab" for r in results)
