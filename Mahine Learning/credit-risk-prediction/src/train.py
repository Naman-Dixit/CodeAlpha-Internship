"""
End-to-end training script for the Explainable Credit Risk Prediction System.

Run:
    python src/train.py --data data/UCI_Credit_Card.csv --out models/

Produces, inside --out:
    pipeline.joblib          trained sklearn/imblearn Pipeline (preprocess + SMOTE + XGBoost)
    threshold.json           tuned classification threshold + the metrics that justified it
    metrics.json             full metric comparison across all baseline models + final model
    feature_columns.json     raw column names the pipeline expects
    plots/                   confusion matrix, ROC curves, PR curve, SHAP summary (PNGs)
"""

from __future__ import annotations

import argparse
import json
import os

import joblib
import matplotlib.pyplot as plt
import numpy as np
import pandas as pd
import seaborn as sns
import shap
from imblearn.over_sampling import SMOTE
from imblearn.pipeline import Pipeline as ImbPipeline
from sklearn.ensemble import RandomForestClassifier
from sklearn.linear_model import LogisticRegression
from sklearn.metrics import (
    accuracy_score,
    confusion_matrix,
    f1_score,
    precision_recall_curve,
    precision_score,
    recall_score,
    roc_auc_score,
    roc_curve,
)
from sklearn.model_selection import RandomizedSearchCV, StratifiedKFold, cross_val_score, train_test_split
from sklearn.tree import DecisionTreeClassifier
from xgboost import XGBClassifier

from features import TARGET_COL, clean_raw, engineer_features, get_feature_lists
from preprocessing import build_preprocessor


def load_data(path: str) -> pd.DataFrame:
    df = pd.read_csv(path)
    df = clean_raw(df)
    df = engineer_features(df)
    return df


def baseline_comparison(X_train, y_train, X_test, y_test, preprocessor) -> pd.DataFrame:
    """Fit Logistic Regression / Decision Tree / Random Forest / XGBoost with
    class_weight balancing (no SMOTE yet) so we have an honest baseline table."""
    candidates = {
        "Logistic Regression": LogisticRegression(max_iter=1000, class_weight="balanced", random_state=42),
        "Decision Tree": DecisionTreeClassifier(max_depth=6, class_weight="balanced", random_state=42),
        "Random Forest": RandomForestClassifier(
            n_estimators=300, max_depth=10, class_weight="balanced", random_state=42, n_jobs=-1
        ),
        "XGBoost": XGBClassifier(
            n_estimators=300,
            max_depth=5,
            learning_rate=0.1,
            scale_pos_weight=(y_train == 0).sum() / (y_train == 1).sum(),
            eval_metric="logloss",
            random_state=42,
        ),
    }

    rows = []
    for name, clf in candidates.items():
        pipe = ImbPipeline(steps=[("prep", preprocessor), ("clf", clf)])
        pipe.fit(X_train, y_train)
        proba = pipe.predict_proba(X_test)[:, 1]
        pred = pipe.predict(X_test)
        rows.append(
            {
                "Model": name,
                "Accuracy": accuracy_score(y_test, pred),
                "Precision": precision_score(y_test, pred),
                "Recall": recall_score(y_test, pred),
                "F1": f1_score(y_test, pred),
                "ROC-AUC": roc_auc_score(y_test, proba),
            }
        )
    return pd.DataFrame(rows).set_index("Model").round(4)


def build_search_pipeline(preprocessor) -> ImbPipeline:
    return ImbPipeline(
        steps=[
            ("prep", preprocessor),
            ("smote", SMOTE(random_state=42)),
            ("clf", XGBClassifier(eval_metric="logloss", random_state=42)),
        ]
    )


def tune_hyperparameters(pipeline: ImbPipeline, X_train, y_train) -> RandomizedSearchCV:
    param_distributions = {
        "clf__n_estimators": [200, 300, 400, 600],
        "clf__max_depth": [3, 4, 5, 6, 8],
        "clf__learning_rate": [0.01, 0.03, 0.05, 0.1, 0.2],
        "clf__subsample": [0.7, 0.8, 0.9, 1.0],
        "clf__colsample_bytree": [0.7, 0.8, 0.9, 1.0],
        "smote__k_neighbors": [3, 5, 7],
    }

    cv = StratifiedKFold(n_splits=5, shuffle=True, random_state=42)

    search = RandomizedSearchCV(
        pipeline,
        param_distributions=param_distributions,
        n_iter=25,
        scoring="roc_auc",
        cv=cv,
        n_jobs=-1,
        random_state=42,
        verbose=1,
    )
    search.fit(X_train, y_train)
    return search


def optimize_threshold(y_true, y_proba) -> dict:
    """Sweep thresholds and pick the one maximizing F1 (a reasonable default;
    swap the objective for recall-at-fixed-precision if the business asks)."""
    thresholds = np.arange(0.05, 0.95, 0.01)
    best = {"threshold": 0.5, "f1": -1}

    sweep = []
    for t in thresholds:
        pred = (y_proba >= t).astype(int)
        f1 = f1_score(y_true, pred)
        sweep.append(
            {
                "threshold": round(float(t), 2),
                "precision": round(precision_score(y_true, pred), 4),
                "recall": round(recall_score(y_true, pred), 4),
                "f1": round(f1, 4),
            }
        )
        if f1 > best["f1"]:
            best = {"threshold": round(float(t), 2), "f1": round(f1, 4)}

    return {"best": best, "sweep": sweep}


def save_plots(y_test, y_proba_final, pred_final, models_metrics: dict[str, tuple], out_dir: str):
    os.makedirs(out_dir, exist_ok=True)

    # Confusion matrix (final tuned model, tuned threshold)
    cm = confusion_matrix(y_test, pred_final)
    plt.figure(figsize=(5, 4))
    sns.heatmap(cm, annot=True, fmt="d", cmap="Blues", xticklabels=["No Default", "Default"], yticklabels=["No Default", "Default"])
    plt.title("Confusion Matrix - Final Tuned Model")
    plt.xlabel("Predicted")
    plt.ylabel("Actual")
    plt.tight_layout()
    plt.savefig(os.path.join(out_dir, "confusion_matrix.png"), dpi=150)
    plt.close()

    # ROC curves across all models
    plt.figure(figsize=(6, 5))
    for name, (proba, _) in models_metrics.items():
        fpr, tpr, _ = roc_curve(y_test, proba)
        auc = roc_auc_score(y_test, proba)
        plt.plot(fpr, tpr, label=f"{name} (AUC={auc:.3f})")
    plt.plot([0, 1], [0, 1], "k--", alpha=0.5)
    plt.xlabel("False Positive Rate")
    plt.ylabel("True Positive Rate")
    plt.title("ROC Curves - Model Comparison")
    plt.legend()
    plt.tight_layout()
    plt.savefig(os.path.join(out_dir, "roc_curves.png"), dpi=150)
    plt.close()

    # Precision-Recall curve (final model) - most informative under imbalance
    precision, recall, _ = precision_recall_curve(y_test, y_proba_final)
    plt.figure(figsize=(6, 5))
    plt.plot(recall, precision)
    plt.xlabel("Recall")
    plt.ylabel("Precision")
    plt.title("Precision-Recall Curve - Final Tuned Model")
    plt.tight_layout()
    plt.savefig(os.path.join(out_dir, "precision_recall_curve.png"), dpi=150)
    plt.close()


def save_shap_summary(best_pipeline: ImbPipeline, X_test: pd.DataFrame, out_dir: str, sample_size: int = 500):
    os.makedirs(out_dir, exist_ok=True)

    preprocessor = best_pipeline.named_steps["prep"]
    clf = best_pipeline.named_steps["clf"]

    X_sample = X_test.sample(min(sample_size, len(X_test)), random_state=42)
    X_transformed = preprocessor.transform(X_sample)

    feature_names = preprocessor.get_feature_names_out()
    X_transformed_df = pd.DataFrame(
        X_transformed.toarray() if hasattr(X_transformed, "toarray") else X_transformed,
        columns=feature_names,
    )

    explainer = shap.TreeExplainer(clf)
    shap_values = explainer.shap_values(X_transformed_df)

    plt.figure()
    shap.summary_plot(shap_values, X_transformed_df, show=False)
    plt.tight_layout()
    plt.savefig(os.path.join(out_dir, "shap_summary.png"), dpi=150, bbox_inches="tight")
    plt.close()

    plt.figure()
    shap.summary_plot(shap_values, X_transformed_df, plot_type="bar", show=False)
    plt.tight_layout()
    plt.savefig(os.path.join(out_dir, "shap_feature_importance.png"), dpi=150, bbox_inches="tight")
    plt.close()


def main(data_path: str, out_dir: str):
    os.makedirs(out_dir, exist_ok=True)
    plots_dir = os.path.join(out_dir, "plots")

    print("Loading and preparing data...")
    df = load_data(data_path)
    numeric_features, categorical_features = get_feature_lists(df)

    X = df.drop(columns=[TARGET_COL])
    y = df[TARGET_COL]

    X_train, X_test, y_train, y_test = train_test_split(
        X, y, test_size=0.2, random_state=42, stratify=y
    )

    preprocessor = build_preprocessor(numeric_features, categorical_features)

    print("Running baseline model comparison (Logistic Regression / Decision Tree / Random Forest / XGBoost)...")
    baseline_df = baseline_comparison(X_train, y_train, X_test, y_test, preprocessor)
    print(baseline_df)

    print("Building SMOTE + XGBoost pipeline and running 5-fold cross-validation...")
    search_pipeline = build_search_pipeline(preprocessor)
    cv = StratifiedKFold(n_splits=5, shuffle=True, random_state=42)
    cv_scores = cross_val_score(search_pipeline, X_train, y_train, cv=cv, scoring="roc_auc", n_jobs=-1)
    print(f"CV ROC-AUC: mean={cv_scores.mean():.4f}  std={cv_scores.std():.4f}")

    print("Tuning hyperparameters with RandomizedSearchCV (this can take a few minutes)...")
    search = tune_hyperparameters(search_pipeline, X_train, y_train)
    best_pipeline = search.best_estimator_
    print("Best params:", search.best_params_)

    y_proba = best_pipeline.predict_proba(X_test)[:, 1]

    print("Optimizing decision threshold...")
    threshold_result = optimize_threshold(y_test, y_proba)
    best_threshold = threshold_result["best"]["threshold"]
    print("Best threshold:", threshold_result["best"])

    final_pred = (y_proba >= best_threshold).astype(int)
    final_metrics = {
        "Accuracy": round(accuracy_score(y_test, final_pred), 4),
        "Precision": round(precision_score(y_test, final_pred), 4),
        "Recall": round(recall_score(y_test, final_pred), 4),
        "F1": round(f1_score(y_test, final_pred), 4),
        "ROC-AUC": round(roc_auc_score(y_test, y_proba), 4),
        "CV_ROC_AUC_mean": round(float(cv_scores.mean()), 4),
        "CV_ROC_AUC_std": round(float(cv_scores.std()), 4),
    }
    print("Final tuned model metrics @ optimized threshold:", final_metrics)

    print("Saving plots (confusion matrix, ROC, PR curve)...")
    models_for_roc = {"Tuned XGBoost + SMOTE": (y_proba, final_pred)}
    save_plots(y_test, y_proba, final_pred, models_for_roc, plots_dir)

    print("Computing SHAP explanations...")
    save_shap_summary(best_pipeline, X_test, plots_dir)

    print("Saving artifacts...")
    joblib.dump(best_pipeline, os.path.join(out_dir, "pipeline.joblib"))

    with open(os.path.join(out_dir, "threshold.json"), "w") as f:
        json.dump(threshold_result, f, indent=2)

    with open(os.path.join(out_dir, "metrics.json"), "w") as f:
        json.dump(
            {
                "baseline_comparison": baseline_df.reset_index().to_dict(orient="records"),
                "final_tuned_model": final_metrics,
                "best_hyperparameters": search.best_params_,
            },
            f,
            indent=2,
        )

    with open(os.path.join(out_dir, "feature_columns.json"), "w") as f:
        json.dump(
            {"numeric_features": numeric_features, "categorical_features": categorical_features},
            f,
            indent=2,
        )

    print(f"Done. Artifacts saved to {out_dir}/")


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--data", default="data/UCI_Credit_Card.csv")
    parser.add_argument("--out", default="models")
    args = parser.parse_args()
    main(args.data, args.out)
