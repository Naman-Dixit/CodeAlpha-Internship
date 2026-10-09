# Real-Time Speech Emotion Recognition using MFCC, CNN-BiLSTM & Attention

Classifies the emotion expressed in a speech clip — neutral, calm, happy, sad, angry, fearful,
disgust, or surprised — using MFCC features, a CNN-BiLSTM network with an attention mechanism,
a REST API, and a Streamlit app with live microphone recording.

## 1. Problem

Given a short speech clip, predict which of 8 emotions the speaker is expressing, using audio
alone (no transcript, no video).

## 2. Motivation

Emotion carries a huge amount of communicative meaning that text transcripts miss entirely — tone,
pitch, and rhythm often say more than the words themselves. Speech emotion recognition is used in
call-center analytics, mental-health monitoring tools, voice assistants, and accessibility software.
It's also a genuinely different kind of ML problem from tabular classification: audio is a raw,
variable-length signal, so feature extraction (turning sound into fixed-size numeric features) is
most of the work, and getting there requires real signal-processing understanding, not just
`model.fit()`.

## 3. Dataset

[RAVDESS Emotional Speech Audio (Kaggle)](https://www.kaggle.com/datasets/uwrfkaggler/ravdess-emotional-speech-audio)
(original: [Livingstone & Russo, PLoS ONE](https://zenodo.org/record/1188976), CC BY-NA-SC 4.0).

- 1,440 WAV files, 24 professional actors (12 female, 12 male)
- 8 emotions: neutral, calm, happy, sad, angry, fearful, disgust, surprised
- Each emotion spoken at 2 intensities, 2 lexically-matched statements, 2 repetitions
- Emotion is encoded directly in the filename (e.g. `03-01-06-01-02-01-12.wav`)

Download it, extract it, and place the actor folders (or the whole extracted tree) under
`data/ravdess/` — the loader (`src/dataset.py`) recursively scans for `.wav` files, so any nesting
works.

## 4. Architecture

```
Audio
  ↓
Silence Trimming (librosa.effects.trim)
  ↓
MFCC Extraction (40 coefficients, padded/truncated to a fixed length)
  ↓
Data Augmentation (noise, pitch shift, time stretch — TRAIN split only)
  ↓
Feature Normalization (mean/std fit on TRAIN only)
  ↓
CNN (spatial patterns in the MFCC "image")
  ↓
Bidirectional LSTM ×2 (temporal patterns across the clip)
  ↓
Additive Attention (learns which time frames matter most)
  ↓
Emotion Classification (8-way softmax)
  ↓
Confusion Matrix / F1 per Emotion
  ↓
FastAPI  +  Streamlit (upload or live microphone)
```

```
speech-emotion-recognition/
├── data/                      # place the extracted RAVDESS folder here (gitignored)
├── notebooks/
│   └── Speech_Emotion_Recognition_Complete.ipynb   # exploratory / Colab version
├── src/
│   ├── dataset.py             # parses RAVDESS filenames into a labeled DataFrame
│   ├── features.py            # MFCC extraction + augmentation (shared everywhere)
│   ├── model.py                # CNN baseline + CNN-BiLSTM-Attention architectures
│   ├── train.py                # full training pipeline -> models/
│   ├── predict.py              # predict_emotion() used by API & Streamlit
│   └── explain.py              # attention-weight explainability
├── api/
│   └── main.py                 # FastAPI: /predict, /explain, /health (multipart audio upload)
├── app/
│   └── streamlit_app.py        # Overview / Predict / Explainability, with live mic recording
├── models/                     # emotion_model.keras, label_encoder.pkl, norm_stats.pkl, plots/
├── tests/
│   └── test_features.py
├── requirements.txt
├── .gitignore
└── README.md
```

## 5. EDA

See `notebooks/Speech_Emotion_Recognition_Complete.ipynb` for waveform/Mel-spectrogram examples,
class-balance plots, and an attention-weight visualization overlaid on the MFCC of a real clip.
Running `src/train.py` also regenerates performance plots into `models/plots/`: confusion
matrices and F1-per-emotion bar charts for both models, plus a training-curve comparison.

## 6. Models

| Model | Notes |
|---|---|
| CNN baseline | 2D convolutions over the MFCC treated as an image |
| **CNN-BiLSTM + Attention (final)** | CNN for local spectral patterns → BiLSTM ×2 for temporal structure → additive attention pooling, so the model — and you — can see which moments of the clip mattered most |

Both are trained with early stopping and learning-rate reduction on plateau, and evaluated on a
held-out set of **actors the model never saw during training** (the train/test split is by actor,
not by clip, so the model can't just memorize voices).

## 7. Results

Exact numbers depend on your training run and are written to `models/metrics.json` — a full
`sklearn.classification_report` (precision/recall/F1 per emotion) for both the CNN baseline and
the final CNN-BiLSTM-Attention model.

## 8. Explainability

`src/explain.py` exposes the attention layer's per-time-frame weights for any given clip — a
direct answer to *"why did the model predict this emotion?"* On the Streamlit "Explainability"
page, this renders as a bar chart over time: taller bars = frames the model relied on more
heavily. It's also available as `POST /explain` on the API.

## 9. API

```
POST /predict
Content-Type: multipart/form-data
file: <audio file (.wav/.mp3/.flac/.m4a/.ogg)>
```

Response:

```json
{
  "emotion": "happy",
  "confidence": 0.81,
  "all_probabilities": {
    "angry": 0.02, "calm": 0.03, "disgust": 0.01, "fearful": 0.02,
    "happy": 0.81, "neutral": 0.05, "sad": 0.02, "surprised": 0.04
  }
}
```

`POST /explain` accepts the same upload and returns the per-frame attention weights instead.
Interactive docs are auto-generated at `/docs`.

## 10. Demo

The Streamlit app has three pages:
- **Overview** — a short description plus the latest training run's confusion matrix and F1 chart.
- **Predict Emotion** — record live with your microphone (`st.audio_input`) or upload a file, then
  see the predicted emotion with an emoji, confidence, and a bar chart of all class probabilities.
- **Explainability** — the attention-weight-over-time chart for the most recent prediction.

## 11. Installation

```bash
git clone <your-repo-url>
cd speech-emotion-recognition
pip install -r requirements.txt
```

Download the dataset from
[Kaggle](https://www.kaggle.com/datasets/uwrfkaggler/ravdess-emotional-speech-audio), extract it,
and place it under `data/ravdess/`.

## 12. Run

**1. Train the model** (produces everything in `models/`; takes a while on CPU — a GPU runtime,
e.g. in Colab, is much faster):

```bash
python src/train.py --data data/ravdess --out models --epochs 60
```

**2. Start the API:**

```bash
uvicorn api.main:app --reload --port 8000
```

**3. Start the app** (in a second terminal):

```bash
streamlit run app/streamlit_app.py
```

**4. Run tests:**

```bash
pytest tests/
```

## License

Dataset: CC BY-NA-SC 4.0 (Livingstone & Russo) — non-commercial use, share-alike; the raw dataset
is not included in this repo (see `.gitignore`) and should be downloaded directly from Kaggle.
Code: MIT.
