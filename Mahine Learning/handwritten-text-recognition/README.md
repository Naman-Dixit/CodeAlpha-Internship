# Handwritten Text Recognition using CRNN, BiLSTM & CTC

An end-to-end handwritten text recognition system using CNN, BiLSTM and CTC.

## Demo

[Live Demo](#) — add your Streamlit Community Cloud URL here once deployed.

## Features

- Handwritten text recognition
- CNN visual feature extraction
- BiLSTM sequence modeling
- CTC-based decoding
- Character Error Rate (CER) evaluation
- Word Error Rate (WER) evaluation
- Image upload
- Interactive handwriting recognition (drawing canvas)

## Architecture

```
Image
  ↓
CNN
  ↓
BiLSTM
  ↓
CTC
  ↓
Text
```

The CNN extracts visual features from the input image; the BiLSTM reads those features as a
left-to-right sequence; CTC (Connectionist Temporal Classification) decodes that sequence into
text without needing the image pre-segmented into individual characters.

The project progresses in two stages:
1. **Character recognition baseline** on EMNIST — establishes that the visual features are
   learnable before committing to the harder sequence-modeling problem.
2. **Full CRNN word recognition** on the **IAM Handwriting Database** — real handwritten word
   images with human transcriptions (not synthesized text).

## Tech Stack

- Python
- PyTorch
- OpenCV / Pillow
- Streamlit

## Dataset

**[IAM Handwriting Database](https://fki.tic.heia-fr.ch/databases/iam-handwriting-database)**

This dataset requires free registration directly with the FKI institute (it is not an anonymous
one-click download). The training notebook downloads **EMNIST automatically** (via
`torchvision.datasets.EMNIST`) for the character-level baseline, but IAM must be obtained
manually:

1. Register for a free account at the link above.
2. Download `words.tgz` (word-level images) and `ascii/words.txt` (transcriptions).
3. Upload both into the Colab session when the notebook prompts you (or place them in Google
   Drive and mount it — the notebook has a commented-out cell for that path).

The full training dataset is **not included in this repository** — only the trained model
artifacts are (see Project Structure below).

## Project Structure

```
handwritten-text-recognition/
│
├── README.md
│
├── notebooks/
│   └── Handwritten_Text_Recognition.ipynb   # complete training/research pipeline
│
├── app/
│   ├── app.py              # Streamlit app (Home, Recognition, Drawing Canvas, Model Info, Examples)
│   ├── model.py             # CRNN architecture definition + load_model()
│   ├── preprocessing.py     # image -> model-ready tensor (aspect-ratio preserving)
│   └── decoder.py           # CTC greedy decoding
│
├── models/
│   ├── crnn_model.pth       # trained weights (produced by the notebook)
│   ├── vocab.json            # char_to_idx / idx_to_char mappings
│   ├── config.json           # image size, num_classes, blank_index, rnn_hidden - must match training
│   ├── metrics.json           # CER, WER, character accuracy from the last training run
│   └── plots/                 # training curves, confusion matrix, sample predictions
│
├── tests/
│   └── test_app_modules.py   # unit tests for preprocessing + decoder (no model file needed)
│
├── requirements.txt
└── .gitignore
```

The app is deliberately split into small, single-purpose modules rather than one large file —
`app.py` only orchestrates the UI; all model logic, preprocessing, and decoding live in their
own files, matching how you'd structure real production inference code.

**The app never retrains.** All training happens in the notebook; the app only loads
`crnn_model.pth` + `vocab.json` + `config.json` and runs inference.

## Results

Exact numbers depend on your training run and are written to `models/metrics.json` after
running the notebook:

| Metric | Result |
|---|---:|
| Character Accuracy | see `models/metrics.json` |
| CER | see `models/metrics.json` |
| WER | see `models/metrics.json` |

(Use your actual measured values here once you've trained on real IAM data — the notebook
computes all three directly, nothing is hardcoded.)

## How to Run

**1. Install dependencies:**

```bash
pip install -r requirements.txt
```

**2. Train the model** — open `notebooks/Handwritten_Text_Recognition.ipynb` in Google Colab,
follow the IAM download instructions in Part B, and run all cells. This produces
`crnn_model.pth`, `vocab.json`, `config.json`, and `metrics.json`.

**3. Copy the trained artifacts into the project:**

```bash
cp crnn_model.pth vocab.json config.json metrics.json models/
```

**4. Run the app:**

```bash
streamlit run app/app.py
```

**5. Run tests:**

```bash
pytest tests/
```

## Deployment

```
GitHub → Streamlit Community Cloud → Public URL
```

Push this repository to GitHub (the `.gitignore` already keeps datasets out, and the small
trained-model files are committed so the deployed app works without retraining), then connect
the repo at [share.streamlit.io](https://share.streamlit.io), pointing the main file at
`app/app.py`. Put the resulting public URL at the top of this README.

## What this project deliberately avoids

- No Docker — for this project, a clean GitHub repo + reproducible Colab notebook + a deployed
  Streamlit app is a more useful portfolio artifact than container orchestration.
- No retraining inside the app — training lives entirely in the notebook.
- No full dataset committed to GitHub — only the trained model, vocab, and config.
- No giant single-file app — `app.py` is UI only; model/preprocessing/decoding are separate modules.

## License

Code: MIT. IAM Handwriting Database: available for research purposes under its own license —
see the [official database page](https://fki.tic.heia-fr.ch/databases/iam-handwriting-database)
for terms. EMNIST: derived from NIST Special Database 19, released for unrestricted use.
