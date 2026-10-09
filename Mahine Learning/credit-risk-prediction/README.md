# Explainable Credit Risk Prediction System using XGBoost, SHAP & FastAPI

Predicts whether a credit-card customer will default next month, with class-imbalance
handling, a tuned XGBoost model, SHAP-based explainability, a REST API, and a Streamlit
dashboard.

## 1. Problem

Given a customer's credit limit, demographics, and 6 months of billing/payment history,
predict the probability that they will default on their credit card payment next month.

## 2. Motivation

Misjudging credit risk is expensive in both directions: approving credit for someone who
will default costs the lender directly, while rejecting a creditworthy applicant costs
revenue and fairness. A model that only reports accuracy is not good enough here — this
project explicitly handles class imbalance, reports precision/recall/F1/ROC-AUC, and makes
every prediction explainable so a human reviewer can see *why* a customer was flagged.

## 3. Dataset

[Default of Credit Card Clients Dataset](https://www.kaggle.com/datasets/uciml/default-of-credit-card-clients-dataset)
(Kaggle, mirrored from the [UCI ML Repository](https://archive.ics.uci.edu/dataset/350/default+of+credit+card+clients)).

- 30,000 credit card clients, Taiwan, 2005
- Features: credit limit, sex, education, marital status, age, 6 months of repayment
  status, bill amounts, and payment amounts
- Target: `default.payment.next.month` (1 = default, 0 = no default), renamed to `default`

Download it and place the CSV at `data/UCI_Credit_Card.csv` (see [Installation](#11-installation)).

## 4. Architecture

```
                CREDIT RISK AI
                     │
              Financial Dataset
                     │
                    EDA
                     │
            Feature Engineering
                     │
          Data Leakage Prevention  (train/test split BEFORE fitting anything)
                     │
                Preprocessing        (impute → scale/one-hot, inside a Pipeline)
                     │
                Class Imbalance
                /          \\
           class_weight    SMOTE
                \\          /
                     ▼
             Model Training
          ┌──────────┼──────────┐
      Logistic    Random      XGBoost
     Regression   Forest
          └──────────┼──────────┘
                     ▼
            Cross-Validation (5-fold, ROC-AUC)
                     │
          Hyperparameter Tuning (RandomizedSearchCV)
                     │
         Precision / Recall / F1 / ROC-AUC
                     │
            Threshold Optimization
                     │
                   SHAP
             ┌───────┴────────┐
          FastAPI          Streamlit
```

```
credit-risk-prediction/
├── data/                     # place UCI_Credit_Card.csv here (gitignored)
├── notebooks/
│   └── Credit_Scoring_Model_Advanced.ipynb   # exploratory / Colab version
├── src/
│   ├── features.py           # cleaning + feature engineering (shared everywhere)
│   ├── preprocessing.py      # ColumnTransformer (impute, scale, one-hot)
│   ├── train.py               # full training pipeline -> models/
│   ├── predict.py             # predict_default_risk() used by API & Streamlit
│   └── explain.py             # SHAP explanations used by API & Streamlit
├── api/
│   └── main.py                # FastAPI: /predict, /explain, /health
├── app/
│   └── streamlit_app.py       # Overview / Prediction / Explainability pages
├── models/                    # pipeline.joblib, threshold.json, metrics.json, plots/
├── tests/
│   └── test_features.py
├── requirements.txt
└── README.md
```

## 5. EDA

See `notebooks/Credit_Scoring_Model_Advanced.ipynb` for the full exploratory analysis
(class distribution, age/credit-limit distributions, bill/payment boxplots, correlation
matrix). Running `src/train.py` also regenerates model-performance plots into
`models/plots/`: confusion matrix, ROC curves, precision-recall curve, and SHAP summary
plots.

## 6. Models

| Model | Notes |
|---|---|
| Logistic Regression | linear baseline, `class_weight="balanced"` |
| Decision Tree | simple, interpretable baseline |
| Random Forest | ensemble baseline |
| **XGBoost + SMOTE (tuned)** | final model — gradient boosting, SMOTE oversampling, `RandomizedSearchCV` over depth/learning-rate/subsampling |

## 7. Results

Exact numbers depend on your training run and are written to `models/metrics.json` after
running `src/train.py`. That file contains the full baseline comparison table plus the
final tuned model's Accuracy, Precision, Recall, F1, ROC-AUC, and 5-fold cross-validated
ROC-AUC (mean ± std).

## 8. Explainability

`src/explain.py` uses `shap.TreeExplainer` on the trained XGBoost model:
- **Global**: `models/plots/shap_summary.png` and `shap_feature_importance.png` — which
  features generally push predictions toward "default" (e.g. higher credit utilization,
  more months delayed) vs. away from it.
- **Individual**: `explain_customer(customer)` (also exposed as `POST /explain` and on the
  Streamlit "Explainability" page) — the top SHAP contributors for one specific customer's
  prediction.

## 9. API

Start it (after training, see [Run](#12-run)):

```
POST /predict
Content-Type: application/json

{
  "LIMIT_BAL": 50000,
  "SEX": 1,
  "EDUCATION": 2,
  "MARRIAGE": 1,
  "AGE": 30,
  "PAY_0": 0, "PAY_2": 0, "PAY_3": 0, "PAY_4": 0, "PAY_5": 0, "PAY_6": 0,
  "BILL_AMT1": 20000, "BILL_AMT2": 19000, "BILL_AMT3": 18000,
  "BILL_AMT4": 17000, "BILL_AMT5": 16000, "BILL_AMT6": 15000,
  "PAY_AMT1": 2000, "PAY_AMT2": 2000, "PAY_AMT3": 2000,
  "PAY_AMT4": 2000, "PAY_AMT5": 2000, "PAY_AMT6": 2000
}
```

Response:

```json
{
  "prediction": "No Default",
  "default_probability": 0.18,
  "risk_band": "Low",
  "threshold_used": 0.42
}
```

`POST /explain` accepts the same body and returns the top SHAP contributors for that
customer. Interactive docs are auto-generated at `/docs` once the server is running.

## 10. Demo

The Streamlit app has three pages:
- **Overview** — dataset-level stats (total customers, default rate, average credit
  limit/age) and the latest training run's confusion matrix / ROC curves.
- **Prediction** — a form for entering a customer's profile and getting a risk band
  (Low/Medium/High) with the default probability.
- **Explainability** — the SHAP breakdown for the most recent prediction, plus the global
  SHAP summary plot.

## 11. Installation

```bash
git clone <your-repo-url>
cd credit-risk-prediction
pip install -r requirements.txt
```

Download the dataset from
[Kaggle](https://www.kaggle.com/datasets/uciml/default-of-credit-card-clients-dataset)
and save it as `data/UCI_Credit_Card.csv`.

## 12. Run

**1. Train the model** (produces everything in `models/`):

```bash
python src/train.py --data data/UCI_Credit_Card.csv --out models
```

**2. Start the API:**

```bash
uvicorn api.main:app --reload --port 8000
```

**3. Start the dashboard** (in a second terminal):

```bash
streamlit run app/streamlit_app.py
```

**4. Run tests:**

```bash
pytest tests/
```

## License

Dataset: CC BY 4.0 (I-Cheng Yeh, UCI Machine Learning Repository / Kaggle mirror by uciml).
Code: MIT.
