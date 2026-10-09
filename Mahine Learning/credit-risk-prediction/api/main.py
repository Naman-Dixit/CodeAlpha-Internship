"""
FastAPI service for the Explainable Credit Risk Prediction System.

Run locally:
    uvicorn api.main:app --reload --port 8000

Docs:
    http://localhost:8000/docs
"""

import sys
from pathlib import Path

sys.path.append(str(Path(__file__).resolve().parent.parent / "src"))

from fastapi import FastAPI, HTTPException
from pydantic import BaseModel, Field

from explain import explain_customer
from predict import predict_default_risk

app = FastAPI(
    title="Credit Risk Prediction API",
    description="Predicts credit-card default risk and explains individual predictions with SHAP.",
    version="1.0.0",
)


class CustomerData(BaseModel):
    LIMIT_BAL: float = Field(..., example=50000, description="Credit limit (NT dollars)")
    SEX: int = Field(..., example=1, description="1 = male, 2 = female")
    EDUCATION: int = Field(..., example=2, description="1=grad school, 2=university, 3=high school, 4=others")
    MARRIAGE: int = Field(..., example=1, description="1=married, 2=single, 3=others")
    AGE: int = Field(..., example=30)
    PAY_0: int = Field(0, example=0, description="Repayment status, most recent month")
    PAY_2: int = 0
    PAY_3: int = 0
    PAY_4: int = 0
    PAY_5: int = 0
    PAY_6: int = 0
    BILL_AMT1: float = 0
    BILL_AMT2: float = 0
    BILL_AMT3: float = 0
    BILL_AMT4: float = 0
    BILL_AMT5: float = 0
    BILL_AMT6: float = 0
    PAY_AMT1: float = 0
    PAY_AMT2: float = 0
    PAY_AMT3: float = 0
    PAY_AMT4: float = 0
    PAY_AMT5: float = 0
    PAY_AMT6: float = 0


class PredictionResponse(BaseModel):
    prediction: str
    default_probability: float
    risk_band: str
    threshold_used: float


class ExplanationItem(BaseModel):
    feature: str
    shap_value: float
    direction: str


@app.get("/health")
def health():
    return {"status": "ok"}


@app.post("/predict", response_model=PredictionResponse)
def predict(customer: CustomerData):
    try:
        return predict_default_risk(customer.dict())
    except FileNotFoundError:
        raise HTTPException(
            status_code=503,
            detail="Model artifacts not found. Run `python src/train.py` first to produce models/pipeline.joblib.",
        )


@app.post("/explain", response_model=list[ExplanationItem])
def explain(customer: CustomerData, top_n: int = 8):
    try:
        return explain_customer(customer.dict(), top_n=top_n)
    except FileNotFoundError:
        raise HTTPException(
            status_code=503,
            detail="Model artifacts not found. Run `python src/train.py` first.",
        )
