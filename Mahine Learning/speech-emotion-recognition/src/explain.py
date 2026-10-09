"""Attention-weight explainability: which time frames of the clip drove the prediction."""

from __future__ import annotations

import numpy as np

from features import extract_features, prepare_for_model
from predict import _load_artifacts


def explain_emotion(audio_path_or_bytes) -> dict:
    """Returns the predicted emotion plus the per-time-frame attention weights,
    so you can plot 'which parts of the clip mattered most' - the model's own
    answer to 'why did you predict this emotion?'"""
    model, label_encoder, norm_stats = _load_artifacts()

    mfcc = extract_features(
        audio_path_or_bytes, n_mfcc=norm_stats["n_mfcc"], max_len=norm_stats["max_len"]
    )
    x = prepare_for_model(mfcc, norm_stats["mean"], norm_stats["std"])

    # Call the model directly (eager) rather than model.predict(), so the
    # AdditiveAttention layer's `last_attention_weights` reflects this exact input.
    proba = model(x, training=False).numpy()[0]
    pred_idx = int(np.argmax(proba))
    emotion = label_encoder.inverse_transform([pred_idx])[0]

    attention_layer = model.get_layer("attention")
    weights = attention_layer.last_attention_weights.numpy()[0, :, 0]  # (time_steps,)

    return {
        "emotion": emotion,
        "confidence": round(float(proba[pred_idx]), 4),
        "attention_weights": [round(float(w), 5) for w in weights],
    }
