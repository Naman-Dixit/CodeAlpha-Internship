"""
CRNN model definition (CNN feature extractor + BiLSTM + CTC output).

This class definition must stay IDENTICAL to the one used for training in the
Colab notebook - the saved .pth file only stores weights, not architecture,
so loading will silently produce garbage (or error out) if the two drift
apart. Treat this file as the single source of truth and copy it back into
the notebook if you ever change it.
"""

from __future__ import annotations

import json
import os

import torch
import torch.nn as nn


class CRNN(nn.Module):
    def __init__(self, img_height: int, num_classes: int, rnn_hidden: int = 256):
        super().__init__()

        self.cnn = nn.Sequential(
            nn.Conv2d(1, 32, 3, padding=1), nn.BatchNorm2d(32), nn.ReLU(),
            nn.MaxPool2d(2, 2),  # H/2, W/2

            nn.Conv2d(32, 64, 3, padding=1), nn.BatchNorm2d(64), nn.ReLU(),
            nn.MaxPool2d(2, 2),  # H/4, W/4

            nn.Conv2d(64, 128, 3, padding=1), nn.BatchNorm2d(128), nn.ReLU(),
            nn.MaxPool2d((2, 1), (2, 1)),  # H/8, W/4 - pool height faster than width

            nn.Conv2d(128, 128, 3, padding=1), nn.BatchNorm2d(128), nn.ReLU(),
        )

        cnn_out_height = img_height // 8
        self.feature_dim = 128 * cnn_out_height

        self.rnn = nn.LSTM(
            input_size=self.feature_dim,
            hidden_size=rnn_hidden,
            num_layers=2,
            bidirectional=True,
            batch_first=True,
        )

        self.fc = nn.Linear(rnn_hidden * 2, num_classes)  # num_classes includes the CTC blank

    def forward(self, x: torch.Tensor) -> torch.Tensor:
        # x: (batch, 1, H, W)
        features = self.cnn(x)                       # (batch, channels, H', W')
        batch, channels, h, w = features.shape
        features = features.permute(0, 3, 1, 2)       # (batch, W', channels, H')
        features = features.reshape(batch, w, channels * h)  # (batch, time=W', features)

        rnn_out, _ = self.rnn(features)               # (batch, time, 2*hidden)
        logits = self.fc(rnn_out)                      # (batch, time, num_classes)
        log_probs = logits.log_softmax(dim=-1)
        return log_probs  # (batch, time, num_classes) - ready for CTC loss / decoding


def load_model(models_dir: str, device: str = "cpu") -> tuple[CRNN, dict, dict]:
    """Loads the trained CRNN plus its vocab and config, from the same folder
    the notebook's final 'Save model' cell writes to."""
    with open(os.path.join(models_dir, "config.json")) as f:
        config = json.load(f)
    with open(os.path.join(models_dir, "vocab.json")) as f:
        vocab = json.load(f)

    model = CRNN(
        img_height=config["image_height"],
        num_classes=config["num_classes"],
        rnn_hidden=config.get("rnn_hidden", 256),
    )
    state_dict = torch.load(
        os.path.join(models_dir, "crnn_model.pth"), map_location=device
    )
    model.load_state_dict(state_dict)
    model.to(device)
    model.eval()

    return model, vocab, config
