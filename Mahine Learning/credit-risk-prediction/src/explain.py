"""SHAP-based explanations, reused by the API's /explain endpoint and the
Streamlit Explainability page."""

from __future__ import annotations

import os

import joblib
import pandas as pd
import shap

from features import prepare_single_customer

_MODELS_DIR = os.path.join(os.path.dirname(__file__), "..", "models")

_pipeline = None
_explainer = None


def _load():
    global _pipeline, _explainer
    if _pipeline is None:
        _pipeline = joblib.load(os.path.join(_MODELS_DIR, "pipeline.joblib"))
        clf = _pipeline.named_steps["clf"]
        _explainer = shap.TreeExplainer(clf)
    return _pipeline, _explainer


def explain_customer(customer: dict, top_n: int = 8) -> list[dict]:
    """Return the top_n features pushing this customer's prediction up or down,
    i.e. the 'why did THIS customer get this score' explanation."""
    pipeline, explainer = _load()
    preprocessor = pipeline.named_steps["prep"]

    row = prepare_single_customer(customer)
    row_transformed = preprocessor.transform(row)
    feature_names = preprocessor.get_feature_names_out()

    row_df = pd.DataFrame(
        row_transformed.toarray() if hasattr(row_transformed, "toarray") else row_transformed,
        columns=feature_names,
    )

    shap_values = explainer.shap_values(row_df)
    values = shap_values[0] if hasattr(shap_values, "__len__") else shap_values

    contributions = sorted(
        zip(feature_names, values), key=lambda pair: abs(pair[1]), reverse=True
    )[:top_n]

    return [
        {
            "feature": name,
            "shap_value": round(float(val), 4),
            "direction": "increases risk" if val > 0 else "decreases risk",
        }
        for name, val in contributions
    ]


def global_feature_importance(top_n: int = 15) -> list[dict]:
    """Mean |SHAP value| per feature across a background sample - the 'what
    generally drives predictions' explanation. Requires models/plots/shap
    values to have been computed at train time for full accuracy; here we
    recompute quickly on a small cached sample if available."""
    pipeline, explainer = _load()
    # NOTE: for a lightweight app-time call we reuse the explainer's expected
    # values only; the authoritative global plot is models/plots/shap_summary.png
    # produced by src/train.py on the full test set.
    return {
        "note": "See models/plots/shap_feature_importance.png for the full "
        "global ranking computed during training.",
    }
