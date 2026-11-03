#!/usr/bin/env python3
"""Builds src/IbraTvTuner.Core/Data/catalog.json.gz from a CleanYourTV checkout.

CleanYourTV (https://github.com/Gathin23/cleanyourtv) is MIT licensed. Its per-model lists in
examples/<model>/debloat.txt and tv.json are converted, with attribution, into the recommendation
catalog the app shows. Packages named in a list's "Kept on purpose" comment block become
per-model protected packages.

usage: python3 -I tools/build_catalog.py <path-to-cleanyourtv> <commit-sha>
"""
import gzip
import json
import re
import sys
from pathlib import Path

PKG = re.compile(r"\b([A-Za-z][A-Za-z0-9_]*(?:\.[A-Za-z0-9_]+)+)\b")
VALID = re.compile(r"^[A-Za-z][A-Za-z0-9_]*(\.[A-Za-z0-9_]+)+$")


def parse_list(text: str):
    disable, keep, header = [], set(), []
    in_keep = False
    fingerprint = ""
    for raw in text.splitlines():
        line = raw.rstrip()
        if line.lstrip().startswith("#"):
            body = line.lstrip()[1:].strip()
            header.append(body)
            m = re.search(r"Fingerprint:?\s+(\S+)", body)
            if m and not fingerprint:
                fingerprint = m.group(1).rstrip(".")
            low = body.lower()
            if low.startswith("kept on purpose") or low.startswith("keep:") or low.startswith("kept:"):
                in_keep = True
                continue
            if in_keep:
                if body.startswith("-") or body.startswith("  ") or raw.startswith("#   "):
                    for p in PKG.findall(body):
                        if p.count(".") >= 1 and not p.endswith((".md", ".txt", ".sh", ".py", ".ps1")) and "/" not in p:
                            keep.add(p)
                    continue
                if body == "" or body.startswith("Quirk") or body.endswith(":"):
                    in_keep = False
            continue
        if not line.strip():
            in_keep = False
            continue
        pkg, _, why = line.partition("#")
        pkg = pkg.strip()
        if VALID.match(pkg):
            disable.append({"p": pkg, "why": why.strip()})
    return disable, sorted(keep), fingerprint


def main():
    root = Path(sys.argv[1])
    sha = sys.argv[2] if len(sys.argv) > 2 else "unknown"
    models = []
    for d in sorted((root / "examples").iterdir()):
        lst = d / "debloat.txt"
        if not lst.is_file():
            continue
        meta = {}
        tvj = d / "tv.json"
        if tvj.is_file():
            try:
                meta = json.loads(tvj.read_text(encoding="utf-8"))
            except Exception:
                meta = {}
        disable, keep, fp = parse_list(lst.read_text(encoding="utf-8", errors="replace"))
        disabled_names = {x["p"] for x in disable}
        models.append({
            "id": d.name,
            "tv": meta.get("tv", d.name),
            "os": meta.get("os", ""),
            "brand": meta.get("brand", ""),
            "fingerprint": fp,
            "disable": disable,
            "keep": [k for k in keep if k not in disabled_names],
        })
    out = {
        "source": "CleanYourTV examples (MIT License, Copyright (c) 2026 Gathin)",
        "url": "https://github.com/Gathin23/cleanyourtv",
        "commit": sha,
        "models": models,
    }
    dest = Path(__file__).resolve().parent.parent / "src/IbraTvTuner.Core/Data/catalog.json.gz"
    dest.parent.mkdir(parents=True, exist_ok=True)
    with gzip.open(dest, "wt", encoding="utf-8") as f:
        json.dump(out, f, ensure_ascii=False, separators=(",", ":"))
    print(f"{len(models)} models, {sum(len(m['disable']) for m in models)} entries -> {dest}")


if __name__ == "__main__":
    main()
