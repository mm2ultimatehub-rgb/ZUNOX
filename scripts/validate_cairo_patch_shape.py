#!/usr/bin/env python3
"""Validate the exact register/view sequence used by the ZUNOX Cairo patch."""
from pathlib import Path
import re
import sys

apk_root = Path(sys.argv[1] if len(sys.argv) > 1 else "cairo-reference-report/clean")
target = apk_root / "smali/com/google/android/apps/youtube/music/activities/MusicActivity.smali"
if not target.exists():
    candidates = list(apk_root.rglob("MusicActivity.smali"))
    if not candidates:
        raise SystemExit("FAIL: MusicActivity.smali not found in clean APK decode")
    target = candidates[0]

lines = target.read_text(encoding="utf-8", errors="replace").splitlines()
start = next((i for i, line in enumerate(lines) if re.search(r"^\.method .*onCreate\(Landroid/os/Bundle;\)V", line)), None)
if start is None:
    raise SystemExit("FAIL: MusicActivity.onCreate(Bundle):void not found")
end = next((i for i in range(start + 1, len(lines)) if lines[i].strip() == ".end method"), None)
if end is None:
    raise SystemExit("FAIL: onCreate method end not found")
body = lines[start + 1:end]

def fail(message):
    raise SystemExit("FAIL: " + message)

def is_instruction(line):
    value = line.strip()
    return bool(value) and not value.startswith((".", ":", "#"))

def previous_instruction(index):
    return next((i for i in range(index - 1, -1, -1) if is_instruction(body[i])), None)

def next_instruction(index):
    return next((i for i in range(index + 1, len(body)) if is_instruction(body[i])), None)

def regs(text):
    return [int(value) for value in re.findall(r"\bv(\d+)\b", text)]

lottie_index = next((i for i, line in enumerate(body) if "check-cast" in line and "Lcom/airbnb/lottie/LottieAnimationView;" in line), None)
if lottie_index is None:
    fail("LottieAnimationView cast not found")
lottie_match = re.search(r"check-cast v(\d+),", body[lottie_index])
if not lottie_match:
    fail("Could not read Lottie view register")
lottie_reg = int(lottie_match.group(1))

find_index = next((i for i in range(lottie_index - 1, -1, -1) if "->findViewById(I)Landroid/view/View;" in body[i]), None)
if find_index is None:
    fail("Cairo overlay findViewById call not found")
find_match = re.search(r"invoke-virtual \{([^}]+)\}", body[find_index])
if not find_match:
    fail("Could not read findViewById argument registers")
find_regs = regs(find_match.group(1))
if len(find_regs) != 2:
    fail("findViewById does not have the expected receiver and ID registers")
root_reg = find_regs[0]

lottie_result_index = next_instruction(find_index)
if lottie_result_index is None or "move-result-object" not in body[lottie_result_index]:
    fail("findViewById is not followed by move-result-object")
if regs(body[lottie_result_index]) != [lottie_reg]:
    fail("Lottie lookup result register differs from its cast register")
if next_instruction(lottie_result_index) != lottie_index:
    fail("Lottie result is not immediately followed by its expected check-cast")

root_result_index = next((i for i in range(find_index - 1, -1, -1)
                          if re.search(rf"\bmove-result-object v{root_reg}\b", body[i])), None)
if root_result_index is None:
    fail("Overlay root move-result-object not found")
inflate_index = previous_instruction(root_result_index)
if inflate_index is None or "Landroid/view/LayoutInflater;->inflate(ILandroid/view/ViewGroup;)Landroid/view/View;" not in body[inflate_index]:
    fail("Overlay root is not returned by the expected LayoutInflater.inflate overload")

inflate_match = re.search(r"invoke-virtual \{([^}]+)\}", body[inflate_index])
if not inflate_match:
    fail("Could not read LayoutInflater.inflate registers")
inflate_regs = regs(inflate_match.group(1))
if len(inflate_regs) != 3 or inflate_regs[0] != root_reg:
    fail("Unexpected LayoutInflater.inflate receiver/result register sequence")
temp_reg = inflate_regs[2]
if temp_reg != 15:
    fail(f"Expected verified YouTube Music 9.47.51 temporary register v15, found v{temp_reg}")
if temp_reg > 15 or root_reg > 15:
    fail("Registers exceed the 4-bit range required by the injected instructions")
if temp_reg == lottie_reg or root_reg == lottie_reg:
    fail("Unexpected register alias between overlay root, null argument, and Lottie child")

null_init_index = next((i for i in range(inflate_index - 1, -1, -1)
                        if re.search(rf"^\s*const/4 v{temp_reg},\s*0x0\b", body[i])), None)
if null_init_index is None:
    fail("Inflate ViewGroup argument has no preceding null initialization")
for i in range(null_init_index + 1, inflate_index):
    if re.match(rf"^\s*[a-z0-9/-]+\s+v{temp_reg}(?:,|\s|$)", body[i]):
        fail(f"Temporary null register v{temp_reg} is overwritten before Cairo inflate at method line {i + 1}")

safe_overwrite_index = None
write_pattern = re.compile(
    r"^\s*(?:move-result(?:-wide|-object)?|move-exception|const(?:/(?:4|16|high16))?|"
    r"const-wide(?:/(?:16|32|high16))?|const-string(?:/jumbo)?|const-class|"
    r"new-instance|new-array|sget(?:-wide|-object|-boolean|-byte|-char|-short)?)\s+v(\d+)\b"
)
for i in range(root_result_index + 1, len(body)):
    line = body[i]
    write = write_pattern.search(line)
    if write and int(write.group(1)) == temp_reg:
        safe_overwrite_index = i
        break
    if re.search(rf"\bv{temp_reg}\b", line):
        fail(f"Temporary register v{temp_reg} is read before its first overwrite at method line {i + 1}")
if safe_overwrite_index is None:
    fail(f"No later instruction safely overwrites v{temp_reg}")

report = [
    "PASS: clean YouTube Music 9.47.51 Cairo patch shape verified",
    f"Activity method: {target}",
    f"Overlay root register: v{root_reg}",
    f"Lottie child register: v{lottie_reg}",
    f"Reusable null-argument register: v{temp_reg}",
    f"Overlay root result line in method: {root_result_index + 1}",
    f"First safe overwrite line in method: {safe_overwrite_index + 1}",
    "Injection: const/4 vTEMP, 0x8; invoke-virtual {vROOT, vTEMP}, View.setVisibility(I)",
    "The overlay is hidden before it is attached; Activity startup and Lottie callbacks remain intact.",
]
out = Path("cairo-reference-report/PATCH_SHAPE_VALIDATION.txt")
out.parent.mkdir(parents=True, exist_ok=True)
out.write_text("\n".join(report) + "\n", encoding="utf-8")
print(out.read_text(encoding="utf-8"))
