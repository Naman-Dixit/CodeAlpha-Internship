"""
End-to-end training script for Real-Time Speech Emotion Recognition
using MFCC, CNN-BiLSTM & Attention.

Run:
    python src/train.py --data data/ravdess --out models/

Produces, inside --out:
    emotion_model.keras     trained CNN-BiLSTM-Attention model
    label_encoder.pkl       maps class indices <-> emotion names
    norm_stats.pkl          feature normalization stats (mean/std) fit on TRAIN only
    metrics.json            classification report + per-emotion F1 for both models
    plots/                  confusion matrices, F1-per-emotion bar chart, training curves
"""

from __future__ import annotations

import argparse
import json
import os
import pickle

import matplotlib.pyplot as plt
import numpy as np
import pandas as pd
import seaborn as sns
from sklearn.metrics import classification_report, confusion_matrix, f1_score
from sklearn.model_selection import train_test_split
from sklearn.preprocessing import LabelEncoder
from tensorflow.keras.callbacks import EarlyStopping, ReduceLROnPlateau
from tensorflow.keras.utils import to_categorical

from dataset import build_file_index
from features import N_MFCC, MAX_LEN, augment_and_extract, extract_features, normalize
from model import build_cnn, build_cnn_bilstm_attention


def prepare_data(data_dir: str):
    df = build_file_index(data_dir)
    if len(df) == 0:
        raise FileNotFoundError(
            f"No .wav files matching the RAVDESS naming convention found under {data_dir}. "
            "Download the dataset from Kaggle and extract it there."
        )

    actors = df["actor"].unique()
    train_actors, test_actors = train_test_split(actors, test_size=0.2, random_state=42)
    train_df = df[df["actor"].isin(train_actors)].reset_index(drop=True)
    test_df = df[df["actor"].isin(test_actors)].reset_index(drop=True)

    label_encoder = LabelEncoder()
    label_encoder.fit(df["emotion"])

    print(f"Total clips: {len(df)}  |  Train clips: {len(train_df)}  |  Test clips: {len(test_df)}")
    print("Classes:", list(label_encoder.classes_))

    print("Extracting test features (no augmentation)...")
    X_test = np.array([extract_features(p) for p in test_df["path"]])
    y_test_enc = label_encoder.transform(test_df["emotion"])

    print("Extracting + augmenting train features (original + 3 augmented copies per clip)...")
    X_train, y_train = [], []
    for _, row in train_df.iterrows():
        for feat in augment_and_extract(row["path"]):
            X_train.append(feat)
            y_train.append(row["emotion"])
    X_train = np.array(X_train)
    y_train_enc = label_encoder.transform(y_train)

    mean, std = float(X_train.mean()), float(X_train.std())
    X_train_norm = normalize(X_train, mean, std)[..., np.newaxis]
    X_test_norm = normalize(X_test, mean, std)[..., np.newaxis]

    num_classes = len(label_encoder.classes_)
    y_train_cat = to_categorical(y_train_enc, num_classes=num_classes)
    y_test_cat = to_categorical(y_test_enc, num_classes=num_classes)

    print("X_train:", X_train_norm.shape, " X_test:", X_test_norm.shape)

    return {
        "X_train": X_train_norm, "y_train_cat": y_train_cat, "y_train_enc": y_train_enc,
        "X_test": X_test_norm, "y_test_cat": y_test_cat, "y_test_enc": y_test_enc,
        "label_encoder": label_encoder, "mean": mean, "std": std, "num_classes": num_classes,
    }


def train_model(model, X_train, y_train_cat, X_test, y_test_cat, epochs: int = 60):
    model.compile(optimizer="adam", loss="categorical_crossentropy", metrics=["accuracy"])
    callbacks = [
        EarlyStopping(monitor="val_loss", patience=8, restore_best_weights=True),
        ReduceLROnPlateau(monitor="val_loss", factor=0.5, patience=4),
    ]
    history = model.fit(
        X_train, y_train_cat,
        validation_data=(X_test, y_test_cat),
        epochs=epochs, batch_size=32, callbacks=callbacks, verbose=2,
    )
    return history


def evaluate_and_plot(model, X_test, y_test_enc, label_encoder, name: str, plots_dir: str):
    os.makedirs(plots_dir, exist_ok=True)
    y_pred_proba = model.predict(X_test, verbose=0)
    y_pred = np.argmax(y_pred_proba, axis=1)

    report = classification_report(y_test_enc, y_pred, target_names=label_encoder.classes_, output_dict=True)
    print(f"=== {name} ===")
    print(classification_report(y_test_enc, y_pred, target_names=label_encoder.classes_))

    cm = confusion_matrix(y_test_enc, y_pred)
    plt.figure(figsize=(7, 6))
    sns.heatmap(cm, annot=True, fmt="d", cmap="Blues",
                xticklabels=label_encoder.classes_, yticklabels=label_encoder.classes_)
    plt.title(f"Confusion Matrix - {name}")
    plt.xlabel("Predicted")
    plt.ylabel("Actual")
    plt.xticks(rotation=45)
    plt.tight_layout()
    safe_name = name.lower().replace(" ", "_")
    plt.savefig(os.path.join(plots_dir, f"confusion_matrix_{safe_name}.png"), dpi=150)
    plt.close()

    f1_scores = f1_score(y_test_enc, y_pred, average=None)
    f1_df = pd.DataFrame({"emotion": label_encoder.classes_, "f1_score": f1_scores}).sort_values("f1_score")
    plt.figure(figsize=(8, 4))
    sns.barplot(data=f1_df, x="f1_score", y="emotion", color="steelblue")
    plt.title(f"F1-Score per Emotion - {name}")
    plt.xlim(0, 1)
    plt.tight_layout()
    plt.savefig(os.path.join(plots_dir, f"f1_per_emotion_{safe_name}.png"), dpi=150)
    plt.close()

    return report


def main(data_dir: str, out_dir: str, epochs: int):
    os.makedirs(out_dir, exist_ok=True)
    plots_dir = os.path.join(out_dir, "plots")

    data = prepare_data(data_dir)
    input_shape = data["X_train"].shape[1:]

    print("\n--- Training CNN baseline ---")
    cnn_model = build_cnn(input_shape, data["num_classes"])
    cnn_history = train_model(cnn_model, data["X_train"], data["y_train_cat"], data["X_test"], data["y_test_cat"], epochs)
    cnn_report = evaluate_and_plot(cnn_model, data["X_test"], data["y_test_enc"], data["label_encoder"], "CNN Baseline", plots_dir)

    print("\n--- Training CNN-BiLSTM + Attention ---")
    final_model = build_cnn_bilstm_attention(input_shape, data["num_classes"])
    final_history = train_model(final_model, data["X_train"], data["y_train_cat"], data["X_test"], data["y_test_cat"], epochs)
    final_report = evaluate_and_plot(final_model, data["X_test"], data["y_test_enc"], data["label_encoder"], "CNN-BiLSTM Attention", plots_dir)

    plt.figure(figsize=(8, 5))
    plt.plot(cnn_history.history["val_accuracy"], label="CNN val accuracy")
    plt.plot(final_history.history["val_accuracy"], label="CNN-BiLSTM+Attention val accuracy")
    plt.xlabel("Epoch")
    plt.ylabel("Validation Accuracy")
    plt.title("Training Comparison")
    plt.legend()
    plt.tight_layout()
    plt.savefig(os.path.join(plots_dir, "training_comparison.png"), dpi=150)
    plt.close()

    print("Saving artifacts...")
    final_model.save(os.path.join(out_dir, "emotion_model.keras"))

    with open(os.path.join(out_dir, "label_encoder.pkl"), "wb") as f:
        pickle.dump(data["label_encoder"], f)

    with open(os.path.join(out_dir, "norm_stats.pkl"), "wb") as f:
        pickle.dump({"mean": data["mean"], "std": data["std"], "n_mfcc": N_MFCC, "max_len": MAX_LEN}, f)

    with open(os.path.join(out_dir, "metrics.json"), "w") as f:
        json.dump({"cnn_baseline": cnn_report, "cnn_bilstm_attention": final_report}, f, indent=2)

    print(f"Done. Artifacts saved to {out_dir}/")


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--data", default="data/ravdess", help="Folder containing the RAVDESS .wav files (any nesting)")
    parser.add_argument("--out", default="models")
    parser.add_argument("--epochs", type=int, default=60)
    args = parser.parse_args()
    main(args.data, args.out, args.epochs)
