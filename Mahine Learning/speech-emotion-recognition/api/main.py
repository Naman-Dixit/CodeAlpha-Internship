"""
FastAPI service for Real-Time Speech Emotion Recognition.

Run locally:
    uvicorn api.main:app --reload --port 8000

Docs:
    http://localhost:8000/docs
"""

import shutil
import sys
import tempfile
from pathlib import Path

sys.path.append(str(Path(__file__).resolve().parent.parent / "src"))

from fastapi import FastAPI, File, HTTPException, UploadFile
from pydantic import BaseModel

from explain import explain_emotion
from predict import predict_emotion

app = FastAPI(
    title="Speech Emotion Recognition API",
    description="Predicts the emotion expressed in a speech clip (MFCC + CNN-BiLSTM + Attention) and explains which moments in the clip drove the prediction.",
    version="1.0.0",
)


class PredictionResponse(BaseModel):
    emotion: str
    confidence: float
    all_probabilities: dict


class ExplanationResponse(BaseModel):
    emotion: str
    confidence: float
    attention_weights: list


@app.get("/health")
def health():
    return {"status": "ok"}


@app.post("/predict", response_model=PredictionResponse)
async def predict(file: UploadFile = File(...)):
    if not file.filename.lower().endswith((".wav", ".mp3", ".flac", ".m4a", ".ogg")):
        raise HTTPException(status_code=400, detail="Please upload an audio file (.wav, .mp3, .flac, .m4a, .ogg).")

    try:
        with tempfile.NamedTemporaryFile(delete=False, suffix=Path(file.filename).suffix) as tmp:
            shutil.copyfileobj(file.file, tmp)
            tmp_path = tmp.name
        return predict_emotion(tmp_path)
    except FileNotFoundError:
        raise HTTPException(
            status_code=503,
            detail="Model artifacts not found. Run `python src/train.py` first to produce models/emotion_model.keras.",
        )


@app.post("/explain", response_model=ExplanationResponse)
async def explain(file: UploadFile = File(...)):
    if not file.filename.lower().endswith((".wav", ".mp3", ".flac", ".m4a", ".ogg")):
        raise HTTPException(status_code=400, detail="Please upload an audio file (.wav, .mp3, .flac, .m4a, .ogg).")

    try:
        with tempfile.NamedTemporaryFile(delete=False, suffix=Path(file.filename).suffix) as tmp:
            shutil.copyfileobj(file.file, tmp)
            tmp_path = tmp.name
        return explain_emotion(tmp_path)
    except FileNotFoundError:
        raise HTTPException(
            status_code=503,
            detail="Model artifacts not found. Run `python src/train.py` first.",
        )
