"""Loads the trained pipeline once and exposes predict_default_risk()."""

from __future__ import annotations

import json
import os

import joblib

from features import prepare_single_customer

_MODELS_DIR = os.path.join(os.path.dirname(__file__), "..", "models")

_pipeline = None
_threshold = 0.5


def _load_artifacts():
    global _pipeline, _threshold
    if _pipeline is None:
        _pipeline = joblib.load(os.path.join(_MODELS_DIR, "pipeline.joblib"))
        threshold_path = os.path.join(_MODELS_DIR, "threshold.json")
        if os.path.exists(threshold_path):
            with open(threshold_path) as f:
                _threshold = json.load(f)["best"]["threshold"]
    return _pipeline, _threshold


def predict_default_risk(customer: dict) -> dict:
    """customer: raw fields as in the original dataset (LIMIT_BAL, SEX, EDUCATION,
    MARRIAGE, AGE, PAY_0..PAY_6, BILL_AMT1..6, PAY_AMT1..6)."""
    pipeline, threshold = _load_artifacts()

    row = prepare_single_customer(customer)
    proba = float(pipeline.predict_proba(row)[0, 1])
    prediction = int(proba >= threshold)

    if proba >= 0.66:
        risk_band = "High"
    elif proba >= 0.33:
        risk_band = "Medium"
    else:
        risk_band = "Low"

    return {
        "prediction": "Default" if prediction == 1 else "No Default",
        "default_probability": round(proba, 4),
        "risk_band": risk_band,
        "threshold_used": threshold,
    }


if __name__ == "__main__":
    sample = {
        "LIMIT_BAL": 20000, "SEX": 1, "EDUCATION": 2, "MARRIAGE": 1, "AGE": 24,
        "PAY_0": 2, "PAY_2": 2, "PAY_3": 2, "PAY_4": 0, "PAY_5": 0, "PAY_6": 0,
        "BILL_AMT1": 3913, "BILL_AMT2": 3102, "BILL_AMT3": 689, "BILL_AMT4": 0,
        "BILL_AMT5": 0, "BILL_AMT6": 0,
        "PAY_AMT1": 0, "PAY_AMT2": 689, "PAY_AMT3": 0, "PAY_AMT4": 0,
        "PAY_AMT5": 0, "PAY_AMT6": 0,
    }
    print(predict_default_risk(sample))
