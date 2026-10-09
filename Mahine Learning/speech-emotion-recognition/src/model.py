"""Model architectures: CNN baseline and CNN-BiLSTM with an additive attention layer."""

from __future__ import annotations

import tensorflow as tf
from tensorflow.keras import layers, models


def build_cnn(input_shape: tuple, num_classes: int) -> tf.keras.Model:
    """Simple 2D-CNN baseline treating the MFCC array as an image."""
    model = models.Sequential(
        [
            layers.Input(shape=input_shape),
            layers.Conv2D(32, (3, 3), activation="relu", padding="same"),
            layers.BatchNormalization(),
            layers.MaxPooling2D((2, 2)),
            layers.Dropout(0.25),

            layers.Conv2D(64, (3, 3), activation="relu", padding="same"),
            layers.BatchNormalization(),
            layers.MaxPooling2D((2, 2)),
            layers.Dropout(0.25),

            layers.Conv2D(128, (3, 3), activation="relu", padding="same"),
            layers.BatchNormalization(),
            layers.MaxPooling2D((2, 2)),
            layers.Dropout(0.3),

            layers.Flatten(),
            layers.Dense(128, activation="relu"),
            layers.Dropout(0.4),
            layers.Dense(num_classes, activation="softmax"),
        ]
    )
    return model


class AdditiveAttention(layers.Layer):
    """A simple additive (Bahdanau-style) attention pooling layer.

    Takes a (batch, time_steps, features) sequence and returns a single
    (batch, features) context vector, weighted by a learned relevance score
    per time step. Also stores the last computed per-step weights on
    `self.last_attention_weights` so they can be inspected for explainability
    (see src/explain.py) - this only reflects the most recent eager call.
    """

    def __init__(self, units: int = 64, **kwargs):
        super().__init__(**kwargs)
        self.units = units
        self.last_attention_weights = None

    def build(self, input_shape):
        feature_dim = input_shape[-1]
        self.W = self.add_weight(shape=(feature_dim, self.units), initializer="glorot_uniform", trainable=True, name="att_W")
        self.b = self.add_weight(shape=(self.units,), initializer="zeros", trainable=True, name="att_b")
        self.u = self.add_weight(shape=(self.units, 1), initializer="glorot_uniform", trainable=True, name="att_u")
        super().build(input_shape)

    def call(self, inputs):
        # inputs: (batch, time, features)
        score = tf.tanh(tf.tensordot(inputs, self.W, axes=1) + self.b)  # (batch, time, units)
        score = tf.tensordot(score, self.u, axes=1)                      # (batch, time, 1)
        weights = tf.nn.softmax(score, axis=1)                           # (batch, time, 1)
        self.last_attention_weights = weights
        context = tf.reduce_sum(inputs * weights, axis=1)                # (batch, features)
        return context

    def get_config(self):
        config = super().get_config()
        config.update({"units": self.units})
        return config


def build_cnn_bilstm_attention(input_shape: tuple, num_classes: int) -> tf.keras.Model:
    """MFCC -> CNN feature extraction -> BiLSTM sequence modeling -> attention pooling -> classification."""
    inputs = layers.Input(shape=input_shape)

    x = layers.Conv2D(32, (3, 3), activation="relu", padding="same")(inputs)
    x = layers.BatchNormalization()(x)
    x = layers.MaxPooling2D((2, 2))(x)
    x = layers.Dropout(0.25)(x)

    x = layers.Conv2D(64, (3, 3), activation="relu", padding="same")(x)
    x = layers.BatchNormalization()(x)
    x = layers.MaxPooling2D((2, 2))(x)
    x = layers.Dropout(0.25)(x)

    # (freq, time, channels) -> (time, freq*channels) so the LSTM reads across time
    shape = x.shape
    x = layers.Permute((2, 1, 3))(x)
    x = layers.Reshape((shape[2], shape[1] * shape[3]))(x)

    x = layers.Bidirectional(layers.LSTM(128, return_sequences=True))(x)
    x = layers.Bidirectional(layers.LSTM(64, return_sequences=True))(x)

    x = AdditiveAttention(units=64, name="attention")(x)

    x = layers.Dense(128, activation="relu")(x)
    x = layers.Dropout(0.4)(x)
    outputs = layers.Dense(num_classes, activation="softmax")(x)

    return models.Model(inputs, outputs, name="cnn_bilstm_attention")
