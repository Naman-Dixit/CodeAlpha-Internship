"""
Shared data-cleaning and feature-engineering logic.

This module is imported by train.py, predict.py, the FastAPI service, and the
Streamlit app so that a raw customer record is transformed *identically*
everywhere - this is what prevents train/serve skew and data leakage.
"""

from __future__ import annotations

import numpy as np
import pandas as pd

TARGET_COL = "default"

PAY_STATUS_COLS = ["PAY_0", "PAY_2", "PAY_3", "PAY_4", "PAY_5", "PAY_6"]
BILL_COLS = [f"BILL_AMT{i}" for i in range(1, 7)]
PAY_AMT_COLS = [f"PAY_AMT{i}" for i in range(1, 7)]
BASE_CATEGORICAL_COLS = ["SEX", "EDUCATION", "MARRIAGE"]

RAW_FEATURE_COLS = (
    ["LIMIT_BAL"] + BASE_CATEGORICAL_COLS + ["AGE"] + PAY_STATUS_COLS + BILL_COLS + PAY_AMT_COLS
)

ENGINEERED_CATEGORICAL_COLS = BASE_CATEGORICAL_COLS + ["age_group", "limit_group"]


def clean_raw(df: pd.DataFrame) -> pd.DataFrame:
    """Rename target, drop ID, fold undocumented category codes into 'others'."""
    df = df.copy()

    if "default.payment.next.month" in df.columns:
        df = df.rename(columns={"default.payment.next.month": TARGET_COL})

    if "ID" in df.columns:
        df = df.drop(columns=["ID"])

    if "EDUCATION" in df.columns:
        df["EDUCATION"] = df["EDUCATION"].replace({0: 4, 5: 4, 6: 4})
    if "MARRIAGE" in df.columns:
        df["MARRIAGE"] = df["MARRIAGE"].replace({0: 3})

    return df.drop_duplicates()


def engineer_features(df: pd.DataFrame) -> pd.DataFrame:
    """Add financial-history features on top of the raw columns.

    Safe to call on a single-row DataFrame (one customer) or a full batch -
    every computation is row-wise.
    """
    df = df.copy()

    df["avg_bill_amt"] = df[BILL_COLS].mean(axis=1)
    df["avg_pay_amt"] = df[PAY_AMT_COLS].mean(axis=1)

    df["credit_utilization"] = (df["avg_bill_amt"] / df["LIMIT_BAL"]).clip(lower=0)
    df["debt_to_limit_ratio"] = (
        (df["avg_bill_amt"] / df["LIMIT_BAL"]).replace([np.inf, -np.inf], 0).fillna(0)
    )

    ratio = df["avg_pay_amt"] / df["avg_bill_amt"].replace(0, np.nan)
    df["payment_to_bill_ratio"] = ratio.replace([np.inf, -np.inf], np.nan).fillna(0)

    df["num_months_delayed"] = (df[PAY_STATUS_COLS] > 0).sum(axis=1)
    df["max_delay"] = df[PAY_STATUS_COLS].max(axis=1)

    df["age_group"] = pd.cut(
        df["AGE"], bins=[0, 25, 35, 45, 55, 200],
        labels=["age_under_25", "age_26_35", "age_36_45", "age_46_55", "age_56_plus"],
    ).astype(str)

    df["limit_group"] = pd.cut(
        df["LIMIT_BAL"],
        bins=[-1, 50000, 150000, 300000, np.inf],
        labels=["low", "medium", "high", "very_high"],
    ).astype(str)

    return df


def get_feature_lists(df: pd.DataFrame) -> tuple[list[str], list[str]]:
    """Return (numeric_features, categorical_features) for the ColumnTransformer."""
    categorical_features = [c for c in ENGINEERED_CATEGORICAL_COLS if c in df.columns]
    exclude = set(categorical_features) | {TARGET_COL}
    numeric_features = [c for c in df.columns if c not in exclude]
    return numeric_features, categorical_features


def prepare_single_customer(customer: dict) -> pd.DataFrame:
    """Turn a raw customer dict (as received by the API) into a one-row,
    feature-engineered DataFrame ready for the trained pipeline."""
    row = pd.DataFrame([customer])

    for col in RAW_FEATURE_COLS:
        if col not in row.columns:
            row[col] = 0  # sensible neutral default for a missing field

    row = clean_raw(row)
    row = engineer_features(row)
    return row
