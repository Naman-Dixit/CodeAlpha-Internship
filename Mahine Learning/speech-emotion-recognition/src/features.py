"""
Shared audio preprocessing, MFCC extraction, and augmentation logic.

Imported by train.py, predict.py, the FastAPI service, and the Streamlit app so
that a raw audio clip is turned into model-ready features *identically*
everywhere - this is what prevents train/serve skew.
"""

from __future__ import annotations

import numpy as np
import librosa

SAMPLE_RATE = 22050
N_MFCC = 40
MAX_LEN = 130  # ~3 seconds of frames at hop_length=512, sr=22050

EMOTION_MAP = {
    "01": "neutral", "02": "calm", "03": "happy", "04": "sad",
    "05": "angry", "06": "fearful", "07": "disgust", "08": "surprised",
}


def load_and_trim(path_or_bytes, sr: int = SAMPLE_RATE) -> np.ndarray:
    """Accepts a file path or an in-memory buffer (e.g. from an upload)."""
    y, _ = librosa.load(path_or_bytes, sr=sr)
    y, _ = librosa.effects.trim(y, top_db=25)
    return y


def extract_mfcc_from_array(y: np.ndarray, sr: int = SAMPLE_RATE,
                             n_mfcc: int = N_MFCC, max_len: int = MAX_LEN) -> np.ndarray:
    mfcc = librosa.feature.mfcc(y=y, sr=sr, n_mfcc=n_mfcc)
    if mfcc.shape[1] < max_len:
        mfcc = np.pad(mfcc, ((0, 0), (0, max_len - mfcc.shape[1])), mode="constant")
    else:
        mfcc = mfcc[:, :max_len]
    return mfcc


def extract_features(path_or_bytes, sr: int = SAMPLE_RATE,
                      n_mfcc: int = N_MFCC, max_len: int = MAX_LEN) -> np.ndarray:
    """End-to-end: load audio -> trim silence -> MFCC, fixed shape (n_mfcc, max_len)."""
    y = load_and_trim(path_or_bytes, sr=sr)
    return extract_mfcc_from_array(y, sr=sr, n_mfcc=n_mfcc, max_len=max_len)


# --- Augmentation (apply to TRAINING data only, never to validation/test) ---

def add_noise(y: np.ndarray, noise_factor: float = 0.005) -> np.ndarray:
    noise = np.random.randn(len(y))
    return y + noise_factor * noise


def pitch_shift(y: np.ndarray, sr: int = SAMPLE_RATE, n_steps: int = 2) -> np.ndarray:
    return librosa.effects.pitch_shift(y, sr=sr, n_steps=n_steps)


def time_stretch(y: np.ndarray, rate: float = 1.1) -> np.ndarray:
    return librosa.effects.time_stretch(y, rate=rate)


def augment_and_extract(path: str, sr: int = SAMPLE_RATE) -> list[np.ndarray]:
    """Returns [original, noisy, pitch-shifted, time-stretched] MFCC feature arrays."""
    y = load_and_trim(path, sr=sr)
    variants = [
        y,
        add_noise(y),
        pitch_shift(y, sr, n_steps=int(np.random.choice([-2, 2]))),
        time_stretch(y, rate=float(np.random.choice([0.9, 1.1]))),
    ]
    return [extract_mfcc_from_array(v, sr=sr) for v in variants]


def normalize(features: np.ndarray, mean: float, std: float) -> np.ndarray:
    return (features - mean) / (std + 1e-8)


def prepare_for_model(mfcc: np.ndarray, mean: float, std: float) -> np.ndarray:
    """(n_mfcc, max_len) -> (1, n_mfcc, max_len, 1) batch-and-channel-ready array."""
    normed = normalize(mfcc, mean, std)
    return normed[np.newaxis, ..., np.newaxis]
