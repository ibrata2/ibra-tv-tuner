# What has been tested / شنو اختُبر فعلاً

Kept honest on purpose. A feature moves to **Supported** in `PlatformRegistry` only after a real-hardware
run is written down here.

## Automated (every CI run)

| Test | Where | Result |
|---|---|---|
| 27 core tests: input validation (no shell metacharacters reach adb), pairing-code masking, parsers, error messages, classification, plan refuses protected packages, disable → verify → rollback → verify, device refusal recorded as failure, uninstall-for-user + install-existing, launcher install / theme / open / set Home / **restore old look** / remove, theme revert, set-home refused, Fire TV Home blocked, non-ADB platforms blocked, full self-test path, timing | `tests/IbraTvTuner.Core.Tests` against `FakeTv` (an in-memory device that answers adb like Android 9) | ✅ 27/27 (Linux + Windows runners) |
| Launcher + test-app APKs build, sign and verify (`apksigner verify`) | `android/build.sh` | ✅ |
| Windows x64 EXE builds (self-contained, single file) with the icon embedded; icon extracted at 16/32/256 px from the EXE | GitHub Actions `windows-latest` | see the run artifacts |

## Emulator

| What | Result |
|---|---|
| Android TV emulator | ❌ **Not run.** The build machine has no KVM and cannot download Google system images. Next step: run the self-test against an Android TV emulator (API 28 and API 34 / Google TV) on a PC with Android Studio. |

## Real hardware

| Device | OS | What | Result |
|---|---|---|---|
| — | — | — | ❌ **Nothing yet.** No TV or box was connected during this build, and no real TV is touched without the owner's approval. |

### First hardware run (to do, with approval)
1. Devices → Add → connect → *Read model & system*.
2. *Run end-to-end self-test* (installs `tv.ibra.tuner.testapp`, disables, verifies, re-enables, verifies, removes).
3. Appearance → step 1 → step 2 → step 3 (open only; Home unchanged) → check D-pad, OK, long-press OK, BACK.
4. Step 4 (set Home) → press Home on the remote → **Restore my old look** → press Home again.
5. Record model, Android version and results in the table above, then update `PlatformRegistry`.
