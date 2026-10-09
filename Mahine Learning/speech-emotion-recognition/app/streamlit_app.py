"""
Streamlit front end for Real-Time Speech Emotion Recognition.

Run:
    streamlit run app/streamlit_app.py

Uses st.audio_input (Streamlit >= 1.36) for real-time microphone recording -
no extra webrtc setup needed.
"""

import sys
import tempfile
from pathlib import Path

import numpy as np
import pandas as pd
import streamlit as st

sys.path.append(str(Path(__file__).resolve().parent.parent / "src"))

from explain import explain_emotion
from predict import predict_emotion

st.set_page_config(page_title="Speech Emotion AI", page_icon="\U0001F399\ufe0f", layout="wide")

EMOJI = {
    "neutral": "\U0001F610", "calm": "\U0001F60C", "happy": "\U0001F604", "sad": "\U0001F622",
    "angry": "\U0001F620", "fearful": "\U0001F628", "disgust": "\U0001F922", "surprised": "\U0001F632",
}

page = st.sidebar.radio("Navigate", ["Overview", "Predict Emotion", "Explainability"])
st.sidebar.markdown("---")
st.sidebar.caption("Real-Time Speech Emotion Recognition — MFCC + CNN-BiLSTM + Attention")


def save_temp_audio(uploaded_or_recorded) -> str:
    with tempfile.NamedTemporaryFile(delete=False, suffix=".wav") as tmp:
        tmp.write(uploaded_or_recorded.getvalue())
        return tmp.name


if page == "Overview":
    st.title("Speech Emotion Recognition — Overview")
    st.write(
        "This system classifies the emotion expressed in a speech clip — "
        "**neutral, calm, happy, sad, angry, fearful, disgust, or surprised** — "
        "from audio alone, using MFCC features and a CNN-BiLSTM network with attention."
    )

    plots_dir = Path(__file__).resolve().parent.parent / "models" / "plots"
    if plots_dir.exists():
        st.subheader("Model performance (from the last training run)")
        cols = st.columns(2)
        cm_path = plots_dir / "confusion_matrix_cnn-bilstm_attention.png"
        f1_path = plots_dir / "f1_per_emotion_cnn-bilstm_attention.png"
        if cm_path.exists():
            cols[0].image(str(cm_path), caption="Confusion Matrix", use_container_width=True)
        if f1_path.exists():
            cols[1].image(str(f1_path), caption="F1-score per emotion", use_container_width=True)
    else:
        st.info("Run `python src/train.py` to populate this page with real performance plots.")


elif page == "Predict Emotion":
    st.title("Predict Emotion from Speech")
    st.caption("Upload an audio clip, or record yourself live with the microphone.")

    tab1, tab2 = st.tabs(["\U0001F3A4 Record with microphone", "\U0001F4C1 Upload a file"])

    audio_source = None
    with tab1:
        recorded = st.audio_input("Speak a sentence, then stop recording")
        if recorded is not None:
            audio_source = recorded

    with tab2:
        uploaded = st.file_uploader("Upload a WAV/MP3 file", type=["wav", "mp3", "flac", "m4a", "ogg"])
        if uploaded is not None:
            audio_source = uploaded

    if audio_source is not None:
        st.audio(audio_source)
        if st.button("Predict Emotion", type="primary"):
            try:
                tmp_path = save_temp_audio(audio_source)
                result = predict_emotion(tmp_path)
                st.session_state["last_audio_path"] = tmp_path
                st.session_state["last_result"] = result

                emoji = EMOJI.get(result["emotion"], "")
                st.markdown(f"## {emoji} Predicted emotion: **{result['emotion'].capitalize()}**")
                st.metric("Confidence", f"{result['confidence']*100:.1f}%")

                probs_df = pd.Series(result["all_probabilities"]).sort_values(ascending=True)
                st.bar_chart(probs_df)
                st.info("Go to the **Explainability** tab to see which moments in the clip drove this prediction.")
            except FileNotFoundError:
                st.error("Model artifacts not found. Run `python src/train.py` first to produce models/emotion_model.keras.")


elif page == "Explainability":
    st.title("Why was this emotion predicted?")

    if "last_audio_path" not in st.session_state:
        st.info("Predict an emotion on the **Predict Emotion** tab first, then come back here.")
    else:
        try:
            result = explain_emotion(st.session_state["last_audio_path"])
            emoji = EMOJI.get(result["emotion"], "")
            st.write(f"Prediction: {emoji} **{result['emotion'].capitalize()}** "
                      f"(confidence: {result['confidence']*100:.1f}%)")

            st.subheader("Attention over time")
            st.caption(
                "The model learns to focus on certain moments in the clip more than others. "
                "Higher bars = frames the model weighted more heavily when deciding the emotion."
            )
            weights = np.array(result["attention_weights"])
            st.bar_chart(pd.Series(weights, name="attention weight"))
        except FileNotFoundError:
            st.error("Model artifacts not found. Run `python src/train.py` first.")
