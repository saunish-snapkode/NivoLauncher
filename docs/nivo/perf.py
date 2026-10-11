#!/usr/bin/env python3
"""Repeatable Nivo performance probe. Usage: perf.py <adb-serial> [label]
Measures cold start, frame jank while exercising pager/drawer/library, memory, and idle CPU.
Numbers from an emulator (software rendering) are only useful for before/after comparison."""
import re, subprocess, sys, time, statistics
serial = sys.argv[1]; label = sys.argv[2] if len(sys.argv) > 2 else "run"
PKG = "com.snapkode.nivolauncher"
ADB = ["/home/saunish/Android/Sdk/platform-tools/adb", "-s", serial]
def sh(*a): return subprocess.run(ADB + ["shell", *a], capture_output=True, text=True).stdout
def tap_swipe(x1, y1, x2, y2, ms): sh("input", "touchscreen", "swipe", str(x1), str(y1), str(x2), str(y2), str(ms))
size = re.search(r"(\d+)x(\d+)", sh("wm", "size")); W, H = int(size.group(1)), int(size.group(2))

# cold start: the home app is relaunched by the system after a force-stop, so read the launch-time log line.
starts = []
for _ in range(3):
    sh("logcat", "-c"); sh("am", "force-stop", PKG); time.sleep(7)
    out = sh("logcat", "-d", "-s", "ActivityTaskManager:I")
    m = re.search(r"Displayed " + re.escape(PKG) + r"/[.\w]+(?: for user \d+)?: \+(?:(\d+)s)?(\d+)ms", out)
    if m: starts.append(int(m.group(1) or 0) * 1000 + int(m.group(2)))

sh("input", "keyevent", "KEYCODE_HOME"); time.sleep(3)
sh("dumpsys", "gfxinfo", PKG, "reset")
for _ in range(4):                                   # drawer open / close
    tap_swipe(W//2, int(H*.88), W//2, int(H*.35), 350); time.sleep(1.2)
    sh("input", "keyevent", "KEYCODE_BACK"); time.sleep(1.2)
for _ in range(3):                                   # scroll the drawer list
    tap_swipe(W//2, int(H*.88), W//2, int(H*.35), 350); time.sleep(1.2)
    for _ in range(3): tap_swipe(W//2, int(H*.8), W//2, int(H*.25), 250); time.sleep(.4)
    sh("input", "keyevent", "KEYCODE_BACK"); time.sleep(1.2)
for _ in range(4):                                   # page swipes Home <-> Library
    tap_swipe(int(W*.85), H//2, int(W*.15), H//2, 300); time.sleep(.9)
    tap_swipe(int(W*.15), H//2, int(W*.85), H//2, 300); time.sleep(.9)
g = sh("dumpsys", "gfxinfo", PKG)
def num(p):
    m = re.search(p, g); return m.group(1) if m else "n/a"
frames = num(r"Total frames rendered:\s*(\d+)"); janky = num(r"Janky frames:\s*(\d+) \(([\d.]+)%\)")
jm = re.search(r"Janky frames:\s*(\d+) \(([\d.]+)%\)", g)
p50, p90, p95, p99 = (num(rf"{p} percentile:\s*(\d+)ms") for p in ("50th", "90th", "95th", "99th"))

sh("input", "keyevent", "KEYCODE_HOME"); time.sleep(2)
pss = re.search(r"TOTAL PSS:\s*(\d+)", sh("dumpsys", "meminfo", PKG)); pss = int(pss.group(1)) // 1024 if pss else -1
time.sleep(30)                                       # idle
top = sh("top", "-b", "-n", "1", "-d", "1")
cpu = "n/a"
for line in top.splitlines():
    if "nivolauncher" in line:
        parts = line.split(); cpu = next((p for p in parts if re.fullmatch(r"\d+(\.\d+)?", p) and "." in p), parts[8] if len(parts) > 8 else "n/a")
print(f"[{label}] cold start ms: {starts} median={statistics.median(starts) if starts else 'n/a'}")
print(f"[{label}] frames={frames} janky={jm.group(1) + ' (' + jm.group(2) + '%)' if jm else 'n/a'} p50={p50}ms p90={p90}ms p95={p95}ms p99={p99}ms")
print(f"[{label}] PSS={pss} MB  idle CPU%={cpu}")
