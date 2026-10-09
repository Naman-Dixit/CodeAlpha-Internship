"""
Streamlit front end for the Explainable Credit Risk Prediction System.

Run:
    streamlit run app/streamlit_app.py
"""

import os
import sys
from pathlib import Path

import pandas as pd
import streamlit as st

sys.path.append(str(Path(__file__).resolve().parent.parent / "src"))

from explain import explain_customer
from features import clean_raw
from predict import predict_default_risk

st.set_page_config(page_title="Credit Risk AI", page_icon="\U0001F3E6", layout="wide")

DATA_PATH = Path(__file__).resolve().parent.parent / "data" / "UCI_Credit_Card.csv"

page = st.sidebar.radio("Navigate", ["Overview", "Prediction", "Explainability"])

st.sidebar.markdown("---")
st.sidebar.caption("Explainable Credit Risk Prediction System — XGBoost + SHAP + FastAPI")


def load_overview_data():
    if DATA_PATH.exists():
        df = pd.read_csv(DATA_PATH)
        df = clean_raw(df)
        return df
    return None


if page == "Overview":
    st.title("Credit Risk Prediction — Overview")
    df = load_overview_data()

    if df is None:
        st.info(
            "Place `UCI_Credit_Card.csv` in the `data/` folder to see live dataset stats here. "
            "Get it from the [Kaggle dataset page]"
            "(https://www.kaggle.com/datasets/uciml/default-of-credit-card-clients-dataset)."
        )
    else:
        col1, col2, col3, col4 = st.columns(4)
        col1.metric("Total Customers", f"{len(df):,}")
        col2.metric("Default Rate", f"{df['default'].mean() * 100:.1f}%")
        col3.metric("Average Credit Limit", f"{df['LIMIT_BAL'].mean():,.0f} NT$")
        col4.metric("Average Age", f"{df['AGE'].mean():.1f}")

        st.subheader("Default Rate by Age Group")
        age_bins = pd.cut(df["AGE"], bins=[0, 25, 35, 45, 55, 100])
        age_default_rate = df.groupby(age_bins, observed=True)["default"].mean()
        age_default_rate.index = age_default_rate.index.astype(str)
        st.bar_chart(age_default_rate)

        st.subheader("Default Rate by Education Level")
        st.bar_chart(df.groupby("EDUCATION")["default"].mean())

    plots_dir = Path(__file__).resolve().parent.parent / "models" / "plots"
    if plots_dir.exists():
        st.subheader("Model Performance (from last training run)")
        cols = st.columns(2)
        for i, name in enumerate(["confusion_matrix.png", "roc_curves.png"]):
            path = plots_dir / name
            if path.exists():
                cols[i % 2].image(str(path), use_container_width=True)


elif page == "Prediction":
    st.title("Predict Default Risk")
    st.caption("Enter a customer's financial profile to score their default risk.")

    with st.form("prediction_form"):
        c1, c2, c3 = st.columns(3)
        with c1:
            limit_bal = st.number_input("Credit Limit (NT$)", min_value=0, value=50000, step=1000)
            sex = st.selectbox("Sex", options=[1, 2], format_func=lambda x: "Male" if x == 1 else "Female")
            education = st.selectbox(
                "Education", options=[1, 2, 3, 4],
                format_func=lambda x: {1: "Graduate School", 2: "University", 3: "High School", 4: "Other"}[x],
            )
            marriage = st.selectbox(
                "Marital Status", options=[1, 2, 3],
                format_func=lambda x: {1: "Married", 2: "Single", 3: "Other"}[x],
            )
            age = st.number_input("Age", min_value=18, max_value=100, value=30)

        with c2:
            st.markdown("**Repayment status, last 6 months**")
            st.caption("-1 = paid duly, 1+ = months delayed")
            pay_0 = st.number_input("Most recent month (PAY_0)", value=0, min_value=-2, max_value=8)
            pay_2 = st.number_input("PAY_2", value=0, min_value=-2, max_value=8)
            pay_3 = st.number_input("PAY_3", value=0, min_value=-2, max_value=8)
            pay_4 = st.number_input("PAY_4", value=0, min_value=-2, max_value=8)
            pay_5 = st.number_input("PAY_5", value=0, min_value=-2, max_value=8)
            pay_6 = st.number_input("PAY_6", value=0, min_value=-2, max_value=8)

        with c3:
            st.markdown("**Average bill & payment amounts**")
            avg_bill = st.number_input("Average monthly bill (NT$)", min_value=0, value=5000, step=100)
            avg_pay = st.number_input("Average monthly payment (NT$)", min_value=0, value=3000, step=100)

        submitted = st.form_submit_button("Predict Risk")

    if submitted:
        customer = {
            "LIMIT_BAL": limit_bal, "SEX": sex, "EDUCATION": education, "MARRIAGE": marriage, "AGE": age,
            "PAY_0": pay_0, "PAY_2": pay_2, "PAY_3": pay_3, "PAY_4": pay_4, "PAY_5": pay_5, "PAY_6": pay_6,
            **{f"BILL_AMT{i}": avg_bill for i in range(1, 7)},
            **{f"PAY_AMT{i}": avg_pay for i in range(1, 7)},
        }

        try:
            result = predict_default_risk(customer)
            st.session_state["last_customer"] = customer
            st.session_state["last_result"] = result

            color = {"Low": "green", "Medium": "orange", "High": "red"}[result["risk_band"]]
            st.markdown(f"### Risk band: :{color}[{result['risk_band']}]")
            col1, col2 = st.columns(2)
            col1.metric("Prediction", result["prediction"])
            col2.metric("Default Probability", f"{result['default_probability'] * 100:.1f}%")
            st.caption(f"Decision threshold used: {result['threshold_used']}")
            st.info("Go to the **Explainability** tab to see why this prediction was made.")
        except FileNotFoundError:
            st.error("Model artifacts not found. Run `python src/train.py` first to produce models/pipeline.joblib.")


elif page == "Explainability":
    st.title("Why was this prediction made?")

    if "last_customer" not in st.session_state:
        st.info("Make a prediction on the **Prediction** tab first, then come back here.")
    else:
        customer = st.session_state["last_customer"]
        result = st.session_state["last_result"]

        st.write(f"Prediction: **{result['prediction']}** (probability of default: {result['default_probability']*100:.1f}%)")

        try:
            contributions = explain_customer(customer, top_n=10)
            contrib_df = pd.DataFrame(contributions)
            contrib_df["abs_value"] = contrib_df["shap_value"].abs()
            contrib_df = contrib_df.sort_values("abs_value")

            st.subheader("Top factors behind this individual prediction")
            st.bar_chart(contrib_df.set_index("feature")["shap_value"])
            st.dataframe(contrib_df[["feature", "shap_value", "direction"]], use_container_width=True)
        except FileNotFoundError:
            st.error("Model artifacts not found. Run `python src/train.py` first.")

    plots_dir = Path(__file__).resolve().parent.parent / "models" / "plots"
    shap_summary = plots_dir / "shap_summary.png"
    if shap_summary.exists():
        st.subheader("Global explanation — what generally drives predictions")
        st.image(str(shap_summary), use_container_width=True)
