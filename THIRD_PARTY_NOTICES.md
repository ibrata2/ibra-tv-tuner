# Third-party notices / التراخيص والمصادر

IBRA TV Tuner is MIT licensed. This file lists every project it was checked against, what (if anything)
was reused, and under which licence. Reviewed 2026-11-04.

## Reused material

### CleanYourTV — MIT
- Repo: https://github.com/Gathin23/cleanyourtv (commit `8672e68`)
- Copyright (c) 2026 Gathin — MIT License (full text below).
- **Reused:** the per-model lists in `examples/<model>/debloat.txt` and `tv.json` (427 models).
  `tools/build_catalog.py` converts them into `src/IbraTvTuner.Core/Data/catalog.json.gz`, keeping each
  entry's comment as the recommendation reason, and the packages named in a list's "Kept on purpose"
  block as protected for that model. The app shows them as *suggestions to review*, never as automatic actions.
- **Ideas reused (no code copied):** the Home-guard rules — never leave the device without a Home app,
  do not treat setup wizards / settings fallbacks (`setupwraith`, `com.android.tv.settings`, …) as launchers,
  record Home before changing it, and the list of known stock launchers.
  These are in `PackageClassifier.CoreProtected` and `LauncherManager.NotLaunchers / KnownStockLaunchers`.

```
MIT License

Copyright (c) 2026 Gathin

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```

## Reviewed, nothing reused

| Project | Licence | Why nothing was copied |
|---|---|---|
| [tv-debloat](https://github.com/AryamanSi17/tv-debloat) (`cd006ed`) | MIT | Same approach as CleanYourTV (`pm disable-user`, backup, restore); used as a cross-check only. |
| [LtvLauncher](https://github.com/leanbitlab-org/LtvLauncher) (`0c6614e`) | GPL-3.0 | Flutter launcher. Its code or APK would make this app GPL; the IBRA launcher was written from scratch instead. |
| [ArcLauncher](https://github.com/meddouribadis/arclauncher) (`378098e`) | GPL-3.0 | Same reason as above. |
| [Universal Android Debloater NG](https://github.com/Universal-Debloater-Alliance/universal-android-debloater-next-generation) (`e1fff1e`) | GPL-3.0 | Its package list (`uad_lists.json`) is GPL-3.0, so it is **not** bundled. |
| [CoreELEC](https://github.com/CoreELEC/CoreELEC) (`eb55c8d`) | GPL-2.0 | Reference only: a full Kodi OS for boxes, not reachable over ADB. |
| [LibreELEC.tv](https://github.com/LibreELEC/LibreELEC.tv) (`2bb0c52`) | GPL-2.0 | Reference only, same as CoreELEC. |
| [webOS Homebrew Channel](https://github.com/webosbrew/webos-homebrew-channel) (`6e928d0`) | MIT | Reference for what LG webOS allows (Developer Mode / root, no Home replacement). Not implemented yet. |
| [jellyfin-tizen](https://github.com/jellyfin/jellyfin-tizen) (`31408c1`) | MPL-2.0 | Reference for Tizen app packaging (Tizen Studio + certificate). Not implemented yet. |

## Not bundled

- **Android SDK Platform-Tools (adb)** — © Google, distributed under the Android Software Development Kit
  License Agreement, which does not grant redistribution. The app finds an existing `adb.exe`, lets the user
  pick one, or downloads the official zip from `dl.google.com` only after the user accepts Google's terms.
- **.NET runtime** — included in the self-contained EXE under the MIT License (© .NET Foundation and Contributors).
