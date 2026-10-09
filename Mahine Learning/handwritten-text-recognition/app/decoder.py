"""
CTC greedy decoding: argmax per time step -> remove repeated characters ->
remove blank tokens -> join into text.

Example: "H H _ E E _ L L _ L O O" -> "HELLO"
(where "_" is the CTC blank token)
"""

from __future__ import annotations

import numpy as np


def greedy_decode(log_probs: np.ndarray, idx_to_char: dict, blank_idx: int) -> str:
    """log_probs: (time_steps, num_classes) - a single sample's output
    (already squeezed out of the batch dimension)."""
    best_path = np.argmax(log_probs, axis=-1)  # (time_steps,)

    chars = []
    previous = None
    for idx in best_path:
        idx = int(idx)
        if idx != previous and idx != blank_idx:
            chars.append(idx_to_char.get(str(idx), idx_to_char.get(idx, "")))
        previous = idx

    return "".join(chars)


def batch_greedy_decode(log_probs_batch: np.ndarray, idx_to_char: dict, blank_idx: int) -> list[str]:
    """log_probs_batch: (batch, time_steps, num_classes)."""
    return [greedy_decode(sample, idx_to_char, blank_idx) for sample in log_probs_batch]
