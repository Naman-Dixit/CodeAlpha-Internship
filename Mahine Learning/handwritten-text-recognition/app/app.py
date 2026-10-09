"""
Handwritten Text Recognition - Streamlit app.

This app ONLY performs inference. It loads the model trained in the
companion Colab notebook and never retrains - training lives entirely in
notebooks/Handwritten_Text_Recognition.ipynb.

Run:
    streamlit run app/app.py
"""

import json
import os
from pathlib import Path

import numpy as np
import streamlit as st
import torch
from streamlit_drawable_canvas import st_canvas

from decoder import greedy_decode
from model import load_model
from preprocessing import preprocess_image

MODELS_DIR = Path(__file__).resolve().parent.parent / "models"
GITHUB_URL = "https://github.com/<your-username>/handwritten-text-recognition"  # update after you push

st.set_page_config(page_title="Handwritten Text AI", page_icon="\u270D\ufe0f", layout="wide")


@st.cache_resource
def get_model():
    model, vocab, config = load_model(str(MODELS_DIR), device="cpu")
    return model, vocab, config


def run_inference(raw_image: np.ndarray) -> str:
    model, vocab, config = get_model()
    x = preprocess_image(raw_image, img_height=config["image_height"], img_width=config["image_width"])
    with torch.no_grad():
        log_probs = model(torch.from_numpy(x).float())[0].numpy()  # (time, num_classes)
    idx_to_char = vocab["idx_to_char"]
    return greedy_decode(log_probs, idx_to_char, blank_idx=config["blank_index"])


def models_available() -> bool:
    return all(
        (MODELS_DIR / f).exists() for f in ["crnn_model.pth", "vocab.json", "config.json"]
    )


page = st.sidebar.radio(
    "Navigate", ["Home", "Recognition", "Drawing Canvas", "Model Information", "Example Predictions"]
)
st.sidebar.markdown("---")
st.sidebar.caption("CRNN + BiLSTM + CTC — Handwritten Text Recognition")
st.sidebar.markdown(f"[View on GitHub]({GITHUB_URL})")


if page == "Home":
    st.title("\u270D\ufe0f Handwritten Text AI")
    st.subheader("Convert handwritten text into digital text using CRNN + BiLSTM + CTC.")

    st.markdown(
        """
**Project description**

This is an end-to-end handwritten text recognition system: a CNN extracts visual features from
an image, a bidirectional LSTM models the sequence those features form, and CTC (Connectionist
Temporal Classification) decodes that sequence into text — without ever needing the image
pre-segmented into individual characters.

**Model architecture**
```
Image → CNN Feature Extractor → BiLSTM → CTC → Text
```

**Technology stack**
- PyTorch (model + training)
- OpenCV / Pillow (image preprocessing)
- Streamlit (this app)

**Dataset**
- [IAM Handwriting Database](https://fki.tic.heia-fr.ch/databases/iam-handwriting-database)
  (word/line-level handwritten text images with transcriptions)
- EMNIST used as a character-level baseline before building the full sequence model

**GitHub**
"""
    )
    st.markdown(f"[{GITHUB_URL}]({GITHUB_URL})")

    if not models_available():
        st.warning(
            "No trained model found in `models/`. Run the Colab notebook, then copy "
            "`crnn_model.pth`, `vocab.json`, and `config.json` into the `models/` folder."
        )


elif page == "Recognition":
    st.title("Recognize Handwritten Text")
    st.caption("Upload an image of handwritten text (a word or short line).")

    if not models_available():
        st.error("Model artifacts not found in `models/`. See the Home page for setup instructions.")
    else:
        uploaded = st.file_uploader("Upload handwritten image", type=["png", "jpg", "jpeg", "bmp"])
        if uploaded is not None:
            from PIL import Image

            image = Image.open(uploaded)
            st.image(image, caption="Uploaded Image", use_container_width=True)

            if st.button("Recognize Text", type="primary"):
                text = run_inference(np.array(image))
                st.markdown("### Predicted Text:")
                st.success(f'"{text}"' if text else "(nothing recognized)")


elif page == "Drawing Canvas":
    st.title("Write by Hand")
    st.caption("Draw a word or short phrase below, then click Recognize.")

    if not models_available():
        st.error("Model artifacts not found in `models/`. See the Home page for setup instructions.")
    else:
        canvas_result = st_canvas(
            fill_color="black",
            stroke_width=10,
            stroke_color="white",
            background_color="black",
            height=150,
            width=700,
            drawing_mode="freedraw",
            key="htr_canvas",
        )

        if st.button("Recognize", type="primary"):
            if canvas_result.image_data is None or canvas_result.image_data[..., 3].max() == 0:
                st.warning("Please write something first.")
            else:
                text = run_inference(canvas_result.image_data)
                st.markdown("### Prediction:")
                st.success(text.upper() if text else "(nothing recognized)")


elif page == "Model Information":
    st.title("Model Information")

    config_path = MODELS_DIR / "config.json"
    if config_path.exists():
        with open(config_path) as f:
            config = json.load(f)
    else:
        config = {}

    col1, col2 = st.columns(2)
    with col1:
        st.markdown(
            """
**Model**
CRNN (CNN + BiLSTM + CTC)

**CNN**
Visual feature extraction from the input image

**BiLSTM**
Sequence modeling over the CNN's feature map, read left to right

**CTC**
Alignment-free decoding — turns the per-time-step character probabilities
into text without needing the image pre-segmented into characters
"""
        )
    with col2:
        st.markdown("**Dataset**\n\nIAM Handwriting Database")
        if config:
            st.markdown("**Configuration (from `models/config.json`)**")
            st.json(config)

    metrics_path = MODELS_DIR / "metrics.json"
    if metrics_path.exists():
        with open(metrics_path) as f:
            metrics = json.load(f)
        st.markdown("**Measured performance (from the last training run)**")
        cols = st.columns(len(metrics))
        for col, (k, v) in zip(cols, metrics.items()):
            col.metric(k, f"{v*100:.1f}%" if isinstance(v, float) and v <= 1 else v)

    plots_dir = MODELS_DIR / "plots"
    if plots_dir.exists():
        for img_name, caption in [
            ("cnn_training_curves.png", "CNN baseline training curves"),
            ("crnn_training_curves.png", "CRNN training curves"),
            ("confusion_matrix.png", "CNN baseline confusion matrix"),
        ]:
            p = plots_dir / img_name
            if p.exists():
                st.image(str(p), caption=caption, use_container_width=True)


elif page == "Example Predictions":
    st.title("Example Predictions")
    st.caption("A handful of real test-set predictions from the last training run, proving this isn't just a static interface.")

    examples_path = MODELS_DIR / "plots" / "sample_predictions.json"
    if examples_path.exists():
        with open(examples_path) as f:
            examples = json.load(f)
        for ex in examples:
            cols = st.columns([1, 2])
            if ex.get("image_path") and os.path.exists(ex["image_path"]):
                cols[0].image(ex["image_path"], use_container_width=True)
            else:
                cols[0].write("(image not bundled)")
            cols[1].write(f"**Ground truth:** {ex['ground_truth']}")
            marker = "\u2705" if ex["ground_truth"] == ex["prediction"] else "\u274c"
            cols[1].write(f"**Prediction:** {ex['prediction']} {marker}")
            st.divider()
    else:
        st.info(
            "Run the Colab notebook's evaluation cells to generate "
            "`models/plots/sample_predictions.json`, then this page will show real examples."
        )
