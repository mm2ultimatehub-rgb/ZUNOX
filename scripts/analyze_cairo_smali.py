#!/usr/bin/env python3
"""Extract focused Cairo fingerprint evidence from APKTool-decoded smali."""
from pathlib import Path
import re
import sys

ROOT = Path(sys.argv[1] if len(sys.argv) > 1 else "cairo-reference-report")
TARGET = "Lcom/google/android/apps/youtube/music/activities/MusicActivity;"
METHOD = re.compile(
    r"(?ms)^\.method[^\n]*\bonCreate\(Landroid/os/Bundle;\)V\s*$"
    r"(.*?)^\.end method\s*$"
)
MARKERS = {
    "sa_e": re.compile(r'"sa_e"'),
    "setContentView": re.compile(r"->setContentView\("),
    "cairo_literal_decimal": re.compile(r"\b45635386\b"),
    "cairo_literal_hex": re.compile(r"0x0*2b8573a\b", re.IGNORECASE),
    "launch_animation_name": re.compile(r"main_activity_launch_animation"),
    "lottie_view": re.compile(r"LottieAnimationView"),
    "app_launch": re.compile(r'"app_launch"'),
}
out = []
method_dump = []
for variant in ("clean",):
    matches = list((ROOT / variant).rglob("MusicActivity.smali"))
    out.append(f"===== {variant} =====")
    method_dump.append(f"===== {variant} =====")
    if not matches:
        out.append("MusicActivity.smali: NOT FOUND")
        continue
    for file in matches:
        text = file.read_text(encoding="utf-8", errors="replace")
        if TARGET not in text:
            out.append(f"{file}: descriptor mismatch")
            continue
        methods = list(METHOD.finditer(text))
        out.append(f"File: {file}")
        method_dump.append(f"File: {file}")
        out.append(f"onCreate(Bundle) matches: {len(methods)}")
        for index, match in enumerate(methods, 1):
            body = match.group(1)
            found = {name: bool(pattern.search(body)) for name, pattern in MARKERS.items()}
            out.append(f"Candidate {index}: {found}")
            method_dump.append(f"--- onCreate candidate {index} ---")
            method_dump.extend(f"{line_no:04d}: {line}" for line_no, line in enumerate(body.splitlines(), 1))
            for line_no, line in enumerate(body.splitlines(), 1):
                if any(pattern.search(line) for pattern in MARKERS.values()):
                    out.append(f"  {line_no}: {line.strip()}")
        if not methods:
            out.append("No exact onCreate(Bundle):V method found.")
out_path = ROOT / "FINGERPRINT_EVIDENCE.txt"
out_path.write_text("\n".join(out) + "\n", encoding="utf-8")
dump_path = ROOT / "METHOD_DUMP.txt"
dump_path.write_text("\n".join(method_dump) + "\n", encoding="utf-8")
print(out_path.read_text(encoding="utf-8"))
print(f"Full method dump written to {dump_path}")
