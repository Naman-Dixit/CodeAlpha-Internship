"""
Turns a raw image (upload or drawing canvas) into exactly the tensor shape
and normalization the CRNN was trained on.

Must mirror the notebook's preprocessing pipeline exactly:
    Raw image -> Grayscale -> Resize (aspect-ratio preserved) -> Padding -> Normalization
Stretching handwritten text into a fixed square distorts letterforms and hurts
accuracy, so we resize by height only and pad the width instead.
"""

from __future__ import annotations

import numpy as np
from PIL import Image


def to_grayscale_ink(image: np.ndarray) -> np.ndarray:
    """Normalizes arbitrary input (RGB/RGBA/grayscale, light-on-dark or
    dark-on-light) to: single channel, bright ink on a dark background,
    matching how the training images were prepared."""
    if image.ndim == 3:
        if image.shape[-1] == 4:  # RGBA, e.g. from a drawing canvas - alpha IS the ink mask
            alpha = image[..., 3]
            if alpha.max() > 0:
                return alpha.astype(np.uint8)
            image = image[..., :3]
        gray = image.mean(axis=-1)
    else:
        gray = image

    gray = gray.astype(np.float32)
    if gray.mean() > 127:  # background looks light (typical for uploads/scans) - invert
        gray = 255 - gray
    return gray.astype(np.uint8)


def _crop_to_ink(img: np.ndarray, pad: int = 6) -> np.ndarray:
    ys, xs = np.where(img > 20)
    if len(ys) == 0:
        return img
    y0, y1 = max(ys.min() - pad, 0), min(ys.max() + pad, img.shape[0])
    x0, x1 = max(xs.min() - pad, 0), min(xs.max() + pad, img.shape[1])
    return img[y0:y1, x0:x1]


def preprocess_image(raw_image: np.ndarray, img_height: int = 32, img_width: int = 256) -> np.ndarray:
    """Raw image -> (1, 1, img_height, img_width) float32 array in [0, 1],
    ready to feed to the CRNN (batch dimension already included)."""
    ink = to_grayscale_ink(raw_image)
    cropped = _crop_to_ink(ink)
    if cropped.size == 0:
        cropped = ink

    h, w = cropped.shape
    scale = img_height / h
    new_w = min(int(w * scale), img_width)
    new_w = max(new_w, 1)

    pil_img = Image.fromarray(cropped).resize((new_w, img_height), Image.BILINEAR)
    resized = np.array(pil_img)

    canvas = np.zeros((img_height, img_width), dtype=np.uint8)
    canvas[:, :resized.shape[1]] = resized[:, :img_width]

    normed = canvas.astype("float32") / 255.0
    return normed[np.newaxis, np.newaxis, ...]  # (1, 1, H, W)
