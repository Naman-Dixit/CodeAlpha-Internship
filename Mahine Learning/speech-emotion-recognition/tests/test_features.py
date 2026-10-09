import sys
from pathlib import Path

import numpy as np

sys.path.append(str(Path(__file__).resolve().parent.parent / "src"))

from dataset import build_file_index
from features import EMOTION_MAP, extract_mfcc_from_array, normalize


def test_emotion_map_covers_all_eight_codes():
    assert len(EMOTION_MAP) == 8
    assert EMOTION_MAP["03"] == "happy"
    assert EMOTION_MAP["05"] == "angry"


def test_extract_mfcc_from_array_shape_is_fixed():
    sr = 22050
    y = np.random.randn(sr * 2).astype(np.float32)  # 2 seconds of noise
    mfcc = extract_mfcc_from_array(y, sr=sr, n_mfcc=40, max_len=130)
    assert mfcc.shape == (40, 130)


def test_extract_mfcc_pads_short_clips():
    sr = 22050
    y = np.random.randn(int(sr * 0.3)).astype(np.float32)  # very short clip
    mfcc = extract_mfcc_from_array(y, sr=sr, n_mfcc=40, max_len=130)
    assert mfcc.shape == (40, 130)


def test_normalize_uses_given_stats():
    arr = np.array([1.0, 2.0, 3.0])
    normed = normalize(arr, mean=2.0, std=1.0)
    assert np.allclose(normed, [-1.0, 0.0, 1.0])


def test_build_file_index_parses_ravdess_filenames(tmp_path):
    # Simulate a RAVDESS-style filename: modality-vocal-emotion-intensity-statement-rep-actor
    fake_file = tmp_path / "03-01-05-01-02-01-12.wav"
    fake_file.write_bytes(b"RIFF....")  # content doesn't matter for filename parsing

    df = build_file_index(str(tmp_path))
    assert len(df) == 1
    row = df.iloc[0]
    assert row["emotion"] == "angry"
    assert row["actor"] == 12
    assert row["sex"] == "female"  # even actor number
