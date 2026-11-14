#!/usr/bin/env python3
"""Effets sonores de 100 Games Offline, synthétisés."""
from __future__ import annotations

import math
import os
import struct
import wave

RATE = 44100
TAU = 2.0 * math.pi
OUT_ANDROID = os.path.join("app", "src", "main", "res", "raw")
OUT_DESKTOP = os.path.join("desktop", "src", "main", "resources", "sounds")


def write(name: str, samples: list[float], peak: float = 0.8) -> None:
    top = max(1e-6, max(abs(s) for s in samples))
    data = b"".join(
        struct.pack("<h", max(-32767, min(32767, int(s / top * peak * 32767))))
        for s in samples
    )
    for folder in (OUT_ANDROID, OUT_DESKTOP):
        os.makedirs(folder, exist_ok=True)
        with wave.open(os.path.join(folder, name + ".wav"), "wb") as f:
            f.setnchannels(1)
            f.setsampwidth(2)
            f.setframerate(RATE)
            f.writeframes(data)
    print(f"{name}.wav {len(samples)/RATE*1000:.0f} ms")


def render(duration, fn):
    return [fn(i / RATE) for i in range(int(RATE * duration))]


def tone(f, t):
    return math.sin(TAU * f * t)


def main() -> None:
    write("tap", render(0.05, lambda t: tone(760, t) * math.exp(-t * 120)), 0.35)
    write("flip", render(0.06, lambda t: tone(1080, t) * math.exp(-t * 90)), 0.3)
    write("point", render(0.16, lambda t: tone(1318, t) * math.exp(-t * 14) + tone(1975, t) * 0.35 * math.exp(-t * 20)), 0.45)
    write("match", render(0.24, lambda t: sum(
        tone(n, t - k * 0.05) * math.exp(-(t - k * 0.05) * 9) * 0.45
        for k, n in enumerate([660, 880, 1100]) if t >= k * 0.05
    )), 0.55)
    write("win", render(0.55, lambda t: sum(
        tone(n, t - k * 0.08) * math.exp(-(t - k * 0.08) * 5) * 0.4
        for k, n in enumerate([523, 659, 784, 1046]) if t >= k * 0.08
    )), 0.65)
    write("lose", render(0.45, lambda t: (
        tone(330 * (2 ** (-min(1.0, t / 0.4) * 0.8)), t) * math.exp(-t * 4)
    )), 0.55)
    write("ui", render(0.04, lambda t: tone(980, t) * math.exp(-t * 130)), 0.3)


if __name__ == "__main__":
    main()
