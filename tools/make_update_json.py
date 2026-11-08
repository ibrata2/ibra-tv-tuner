#!/usr/bin/env python3
"""Writes update.json for the in-app updaters (Android launcher/installer, Windows app).
Usage: make_update_json.py <build_number> <dir with apks and exe> > update.json
Needs `apksigner` (for the signing-certificate digest) and `aapt` (for the versionCode)."""
import hashlib, json, os, re, subprocess, sys, datetime

build, d = int(sys.argv[1]), sys.argv[2]
BASE = "https://github.com/ibrata2/ibra-tv-tuner/releases/download/v0.1.0/"

def sha(p):
    h = hashlib.sha256()
    with open(p, "rb") as f:
        for chunk in iter(lambda: f.read(1 << 20), b""):
            h.update(chunk)
    return h.hexdigest()

def apk(name):
    p = os.path.join(d, name)
    badging = subprocess.run(["aapt", "dump", "badging", p], capture_output=True, text=True).stdout
    m = re.search(r"versionCode='(\d+)' versionName='([^']*)'", badging)
    certs = subprocess.run(["apksigner", "verify", "--print-certs", p], capture_output=True, text=True).stdout
    c = re.search(r"certificate SHA-256 digest: ([0-9a-f]+)", certs)
    return {"versionCode": int(m.group(1)), "versionName": m.group(2), "url": BASE + name,
            "sha256": sha(p), "size": os.path.getsize(p), "certSha256": c.group(1) if c else ""}

exe = os.path.join(d, "IbraTvTuner.exe")
notes = open(os.path.join(os.path.dirname(__file__), "..", "docs", "UPDATE_NOTES.txt"), encoding="utf-8").read().strip().split("\n---\n")
out = {
    "build": build,
    "published": datetime.datetime.now(datetime.timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ"),
    "notesAr": notes[0].strip(),
    "notesEn": notes[1].strip() if len(notes) > 1 else "",
    "android": {"launcher": apk("ibra-tv-launcher.apk"), "installer": apk("ibra-home-installer.apk")},
    "windows": {"version": f"0.1.{build}", "url": BASE + "IbraTvTuner.exe", "sha256": sha(exe), "size": os.path.getsize(exe)},
}
json.dump(out, sys.stdout, ensure_ascii=False, indent=2)
