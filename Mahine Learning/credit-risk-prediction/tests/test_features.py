import sys
from pathlib import Path

import pandas as pd

sys.path.append(str(Path(__file__).resolve().parent.parent / "src"))

from features import clean_raw, engineer_features, prepare_single_customer


def test_clean_raw_renames_target_and_drops_id():
    df = pd.DataFrame({
        "ID": [1, 2],
        "default.payment.next.month": [0, 1],
        "EDUCATION": [0, 6],
        "MARRIAGE": [0, 1],
    })
    cleaned = clean_raw(df)
    assert "ID" not in cleaned.columns
    assert "default" in cleaned.columns
    assert set(cleaned["EDUCATION"]) == {4}
    assert set(cleaned["MARRIAGE"]) == {3, 1}


def test_engineer_features_adds_expected_columns():
    df = pd.DataFrame({
        "LIMIT_BAL": [10000],
        "AGE": [30],
        "PAY_0": [1], "PAY_2": [0], "PAY_3": [0], "PAY_4": [0], "PAY_5": [0], "PAY_6": [0],
        "BILL_AMT1": [1000], "BILL_AMT2": [1000], "BILL_AMT3": [1000],
        "BILL_AMT4": [1000], "BILL_AMT5": [1000], "BILL_AMT6": [1000],
        "PAY_AMT1": [500], "PAY_AMT2": [500], "PAY_AMT3": [500],
        "PAY_AMT4": [500], "PAY_AMT5": [500], "PAY_AMT6": [500],
    })
    out = engineer_features(df)
    for col in ["credit_utilization", "debt_to_limit_ratio", "payment_to_bill_ratio",
                "num_months_delayed", "max_delay", "age_group", "limit_group"]:
        assert col in out.columns
    assert out.loc[0, "num_months_delayed"] == 1


def test_prepare_single_customer_handles_missing_fields():
    customer = {"LIMIT_BAL": 20000, "SEX": 1, "EDUCATION": 2, "MARRIAGE": 1, "AGE": 25}
    row = prepare_single_customer(customer)
    assert row.shape[0] == 1
    assert "credit_utilization" in row.columns
