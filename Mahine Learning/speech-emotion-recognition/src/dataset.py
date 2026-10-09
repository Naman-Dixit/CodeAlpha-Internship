"""Builds a labeled file index from a RAVDESS dataset folder."""

from __future__ import annotations

import glob
import os

import pandas as pd

from features import EMOTION_MAP


def build_file_index(root_dir: str) -> pd.DataFrame:
    """Walk root_dir for .wav files named per the RAVDESS convention:
    modality-vocalchannel-emotion-intensity-statement-repetition-actor.wav
    e.g. 03-01-06-01-02-01-12.wav
    """
    rows = []
    for path in glob.glob(os.path.join(root_dir, "**", "*.wav"), recursive=True):
        filename = os.path.basename(path)
        parts = filename.replace(".wav", "").split("-")
        if len(parts) != 7:
            continue
        _modality, _vocal_channel, emotion_code, intensity, _statement, _repetition, actor = parts
        rows.append(
            {
                "path": path,
                "emotion": EMOTION_MAP.get(emotion_code, "unknown"),
                "intensity": "strong" if intensity == "02" else "normal",
                "actor": int(actor),
                "sex": "female" if int(actor) % 2 == 0 else "male",
            }
        )
    return pd.DataFrame(rows)
