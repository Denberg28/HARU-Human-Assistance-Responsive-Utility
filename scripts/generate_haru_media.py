from __future__ import annotations

import math
import struct
import wave
from pathlib import Path

from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / "assets"
ASSETS.mkdir(parents=True, exist_ok=True)

SIZE = 240
SCALE = 3
W = H = SIZE * SCALE

BG = (247, 251, 255, 255)
WHITE = (250, 251, 252, 255)
SILVER = (211, 218, 226, 255)
DARK = (25, 53, 68, 255)
TEAL = (48, 209, 215, 255)
TEAL_DARK = (20, 148, 158, 255)
EYE = (33, 165, 181, 255)
BLUSH = (255, 190, 197, 155)


def sc(v: float) -> int:
    return int(round(v * SCALE))


def ellipse(draw, box, fill, outline=None, width=1):
    draw.ellipse(tuple(sc(v) for v in box), fill=fill, outline=outline, width=sc(width))


def polygon(draw, pts, fill, outline=None):
    draw.polygon([(sc(x), sc(y)) for x, y in pts], fill=fill, outline=outline)


def line(draw, pts, fill, width=2):
    draw.line([(sc(x), sc(y)) for x, y in pts], fill=fill, width=sc(width), joint="curve")


def rounded(draw, box, radius, fill, outline=None, width=1):
    draw.rounded_rectangle(
        tuple(sc(v) for v in box),
        radius=sc(radius),
        fill=fill,
        outline=outline,
        width=sc(width),
    )


def draw_haru(
    *,
    blink=False,
    wave=0.0,
    bounce=0,
    listen=False,
    think=False,
    speaking=False,
    breathe=0,
):
    image = Image.new("RGBA", (W, H), BG)
    d = ImageDraw.Draw(image, "RGBA")

    # Ground shadow.
    ellipse(d, (53, 200 + bounce, 189, 219 + bounce), (67, 108, 132, 24))

    y = bounce
    body_scale = breathe

    # Tail behind body.
    line(d, [(166, 171 + y), (194, 161 + y), (207, 177 + y), (195, 193 + y)], SILVER, 19)
    line(d, [(194, 163 + y), (205, 176 + y)], TEAL, 7)
    line(d, [(199, 183 + y), (194, 193 + y)], TEAL, 7)

    # Body.
    ellipse(d, (76, 136 + y, 167, 208 + y + body_scale), WHITE, DARK, 2)
    ellipse(d, (92, 160 + y, 151, 209 + y + body_scale), (235, 240, 244, 255))

    # Paws.
    ellipse(d, (64, 182 + y, 103, 215 + y), WHITE, DARK, 2)
    ellipse(d, (140, 182 + y, 179, 215 + y), WHITE, DARK, 2)
    for cx in (83, 159):
        ellipse(d, (cx - 7, 196 + y, cx + 7, 209 + y), (235, 245, 248, 255))
        ellipse(d, (cx - 3, 199 + y, cx + 3, 205 + y), TEAL)

    # Waving arm.
    if wave > 0:
        px = 166 + 18 * wave
        py = 161 - 38 * wave + y
        line(d, [(154, 166 + y), (px, py)], WHITE, 20)
        ellipse(d, (px - 12, py - 12, px + 12, py + 12), WHITE, DARK, 2)
        ellipse(d, (px - 4, py - 4, px + 4, py + 4), TEAL)
        line(d, [(px + 15, py - 7), (px + 23, py - 14)], TEAL_DARK, 2)
        line(d, [(px + 16, py + 1), (px + 26, py)], TEAL_DARK, 2)

    # Ears, perked slightly for listening.
    ear_top = 22 if listen else 30
    polygon(d, [(58, 88 + y), (67, ear_top + y), (103, 62 + y)], WHITE, DARK)
    polygon(d, [(181, 88 + y), (172, ear_top + y), (137, 62 + y)], WHITE, DARK)
    polygon(d, [(68, 72 + y), (72, 43 + y), (93, 65 + y)], TEAL)
    polygon(d, [(171, 72 + y), (168, 43 + y), (147, 65 + y)], TEAL)

    # Head.
    rounded(d, (49, 50 + y, 190, 157 + y + body_scale), 54, WHITE, DARK, 2)
    ellipse(d, (54, 90 + y, 66, 111 + y), SILVER)
    ellipse(d, (173, 90 + y, 185, 111 + y), SILVER)

    # Blush.
    ellipse(d, (66, 119 + y, 91, 132 + y), BLUSH)
    ellipse(d, (149, 119 + y, 174, 132 + y), BLUSH)

    # Eyes.
    if blink:
        line(d, [(76, 103 + y), (98, 103 + y)], DARK, 4)
        line(d, [(141, 103 + y), (163, 103 + y)], DARK, 4)
    else:
        for cx in (87, 152):
            ellipse(d, (cx - 16, 80 + y, cx + 16, 119 + y), DARK)
            ellipse(d, (cx - 11, 86 + y, cx + 11, 116 + y), EYE)
            ellipse(d, (cx - 8, 93 + y, cx + 8, 116 + y), (16, 83, 102, 255))
            ellipse(d, (cx - 7, 84 + y, cx + 1, 94 + y), (255, 255, 255, 245))
            ellipse(d, (cx + 3, 96 + y, cx + 8, 102 + y), (210, 255, 255, 220))

    # Nose + mouth.
    polygon(d, [(117, 116 + y), (123, 116 + y), (120, 120 + y)], (95, 68, 73, 255))
    if speaking:
        ellipse(d, (112, 121 + y, 128, 137 + y), (107, 51, 63, 255), DARK, 1)
        ellipse(d, (116, 129 + y, 124, 134 + y), (255, 155, 166, 255))
    else:
        line(d, [(120, 120 + y), (116, 126 + y), (111, 124 + y)], DARK, 2)
        line(d, [(120, 120 + y), (124, 126 + y), (129, 124 + y)], DARK, 2)

    # Chest assistant badge.
    ellipse(d, (105, 158 + y, 135, 188 + y), DARK)
    ellipse(d, (109, 162 + y, 131, 184 + y), (8, 59, 70, 255), TEAL, 2)
    line(d, [(114, 175 + y), (114, 168 + y), (119, 172 + y), (125, 168 + y), (125, 175 + y)], TEAL, 2)

    # Listening / thinking accents.
    if listen:
        line(d, [(42, 62 + y), (34, 54 + y)], TEAL_DARK, 3)
        line(d, [(198, 62 + y), (206, 54 + y)], TEAL_DARK, 3)
    if think:
        ellipse(d, (151, 145 + y, 173, 166 + y), WHITE, DARK, 2)
        line(d, [(156, 148 + y), (146, 132 + y)], DARK, 7)
        # question mark
        line(d, [(191, 69 + y), (197, 63 + y), (204, 67 + y), (203, 75 + y), (197, 80 + y)], TEAL_DARK, 3)
        ellipse(d, (196, 86 + y, 200, 90 + y), TEAL_DARK)

    # Happy bounce motion marks.
    if bounce < 0:
        line(d, [(42, 156), (31, 149)], TEAL_DARK, 3)
        line(d, [(198, 156), (210, 149)], TEAL_DARK, 3)

    return image.resize((SIZE, SIZE), Image.Resampling.LANCZOS)


def make_gif():
    states = [
        dict(breathe=1),
        dict(blink=True),
        dict(breathe=2),
        dict(listen=True),
        dict(think=True),
        dict(wave=1.0),
        dict(bounce=-12),
        dict(speaking=True),
        dict(speaking=False),
        dict(),
    ]
    durations = [850, 170, 700, 600, 700, 700, 500, 300, 300, 900]

    frames = [draw_haru(**state).convert("P", palette=Image.Palette.ADAPTIVE, colors=96) for state in states]
    output = ASSETS / "haru_animation.gif"
    frames[0].save(
        output,
        save_all=True,
        append_images=frames[1:],
        duration=durations,
        loop=0,
        optimize=True,
        disposal=2,
    )
    print(f"Generated {output}")


def make_theme():
    sample_rate = 11025
    bpm = 132
    beat = 60.0 / bpm
    notes = {
        "C4": 261.63,
        "E4": 329.63,
        "G4": 392.00,
        "A4": 440.00,
        "C5": 523.25,
        "D5": 587.33,
        "E5": 659.25,
        "G5": 783.99,
        "R": 0.0,
    }
    melody = [
        ("C5", 1), ("E5", 1), ("G5", 2),
        ("E5", 1), ("D5", 1), ("C5", 2),
        ("E5", 1), ("G5", 1), ("A4", 2),
        ("G5", 1), ("E5", 1), ("D5", 2),
        ("C5", 1), ("D5", 1), ("E5", 1),
        ("G5", 1), ("E5", 1), ("C5", 2), ("R", 1),
    ]

    pcm = bytearray()
    phase = 0.0
    for name, beats in melody:
        freq = notes[name]
        count = int(sample_rate * beat * beats)
        for i in range(count):
            if freq == 0:
                value = 0.0
            else:
                t = i / sample_rate
                attack = min(1.0, i / max(1, int(count * 0.08)))
                release = min(1.0, (count - i) / max(1, int(count * 0.20)))
                env = attack * release
                lead = math.sin(2 * math.pi * freq * t + phase)
                sparkle = 0.24 * math.sin(2 * math.pi * freq * 2 * t + 0.4)
                value = (lead + sparkle) * env * 0.30
            pcm.extend(struct.pack("<h", int(max(-1.0, min(1.0, value)) * 32767)))
        if freq:
            phase = (phase + 0.25) % (2 * math.pi)

    output = ASSETS / "haru_cute_theme.wav"
    with wave.open(str(output), "wb") as wf:
        wf.setnchannels(1)
        wf.setsampwidth(2)
        wf.setframerate(sample_rate)
        wf.writeframes(bytes(pcm))
    print(f"Generated {output}")


if __name__ == "__main__":
    make_gif()
    make_theme()
