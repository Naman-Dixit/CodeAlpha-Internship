"""Loads the trained model once and exposes predict_emotion()."""

from __future__ import annotations

import os
import pickle

import numpy as np
from tensorflow.keras.models import load_model

from features import extract_features, prepare_for_model
from model import AdditiveAttention

_MODELS_DIR = os.path.join(os.path.dirname(__file__), "..", "models")

_model = None
_label_encoder = None
_norm_stats = None


def _load_artifacts():
    global _model, _label_encoder, _norm_stats
    if _model is None:
        _model = load_model(
            os.path.join(_MODELS_DIR, "emotion_model.keras"),
            custom_objects={"AdditiveAttention": AdditiveAttention},
        )
        with open(os.path.join(_MODELS_DIR, "label_encoder.pkl"), "rb") as f:
            _label_encoder = pickle.load(f)
        with open(os.path.join(_MODELS_DIR, "norm_stats.pkl"), "rb") as f:
            _norm_stats = pickle.load(f)
    return _model, _label_encoder, _norm_stats


def predict_emotion(audio_path_or_bytes) -> dict:
    """audio_path_or_bytes: a file path, or a file-like object (e.g. an uploaded
    WAV file from FastAPI/Streamlit) that librosa.load can read."""
    model, label_encoder, norm_stats = _load_artifacts()

    mfcc = extract_features(
        audio_path_or_bytes, n_mfcc=norm_stats["n_mfcc"], max_len=norm_stats["max_len"]
    )
    x = prepare_for_model(mfcc, norm_stats["mean"], norm_stats["std"])

    proba = model.predict(x, verbose=0)[0]
    pred_idx = int(np.argmax(proba))
    emotion = label_encoder.inverse_transform([pred_idx])[0]

    all_probs = {
        cls: round(float(p), 4) for cls, p in zip(label_encoder.classes_, proba)
    }

    return {
        "emotion": emotion,
        "confidence": round(float(proba[pred_idx]), 4),
        "all_probabilities": all_probs,
    }


if __name__ == "__main__":
    import sys

    if len(sys.argv) < 2:
        print("Usage: python predict.py <path_to_wav_file>")
    else:
        print(predict_emotion(sys.argv[1]))
