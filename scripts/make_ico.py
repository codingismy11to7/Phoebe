#!/usr/bin/env python3
"""Generate a multi-size Windows .ico by packing PNG entries (Vista+ format).

Run from the repo root. No external dependencies required (uses /usr/bin/sips for resizing).
"""
from __future__ import annotations

import os
import struct
import subprocess
import sys
import tempfile

SRC = os.path.join(os.path.dirname(__file__), os.pardir, "branding", "icon-source.png")
OUT = os.path.join(
    os.path.dirname(__file__),
    os.pardir,
    "composeApp",
    "src",
    "desktopMain",
    "resources",
    "icons",
    "icon.ico",
)
SIZES = [16, 24, 32, 48, 64, 128, 256]


def main() -> int:
    src = os.path.abspath(SRC)
    out = os.path.abspath(OUT)
    os.makedirs(os.path.dirname(out), exist_ok=True)
    if not os.path.exists(src):
        print(f"source not found: {src}", file=sys.stderr)
        return 1

    pngs: list[tuple[int, bytes]] = []
    with tempfile.TemporaryDirectory() as tmpdir:
        for size in SIZES:
            scaled = os.path.join(tmpdir, f"{size}.png")
            subprocess.check_call(["cp", src, scaled])
            subprocess.check_call(
                ["sips", "-z", str(size), str(size), scaled, "--setProperty", "format", "png"],
                stdout=subprocess.DEVNULL,
                stderr=subprocess.DEVNULL,
            )
            with open(scaled, "rb") as f:
                pngs.append((size, f.read()))

    with open(out, "wb") as f:
        # ICONDIR (reserved=0, type=1 icon, count)
        f.write(struct.pack("<HHH", 0, 1, len(pngs)))
        header_size = 6 + 16 * len(pngs)
        offset = header_size
        for size, data in pngs:
            byte = 0 if size >= 256 else size
            # width, height, color_count, reserved, planes, bit_count, bytes_in_res, image_offset
            f.write(struct.pack("<BBBBHHII", byte, byte, 0, 0, 1, 32, len(data), offset))
            offset += len(data)
        for _, data in pngs:
            f.write(data)

    print(f"wrote {out} ({os.path.getsize(out)} bytes, {len(pngs)} entries)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
