## IBRA TV Tuner + IBRA HOME v0.1.0 (تجريبي / pre-release)

| الملف | لأي جهاز | شنو هو |
|---|---|---|
| `IbraTvTuner-win-x64-portable.zip` | 💻 الحاسبة (ويندوز 10/11) | برنامج IBRA TV Tuner — فكّ الضغط وشغّل `IbraTvTuner.exe` |
| `ibra-home-installer.apk` | 📱 الموبايل (أندرويد) | مثبّت IBRA HOME على التلفزيون عبر الواي فاي، بدون حاسبة |
| `ibra-tv-launcher.apk` | 📺 التلفزيون / البوكس | واجهة IBRA HOME نفسها (للتثبيت اليدوي) |
| `ibra-tv-testapp.apk` | 🧪 — | تطبيق فحص يستخدمه برنامج الحاسبة فقط |

**Portable:** download `IbraTvTuner-win-x64-portable.zip`, unzip, run `IbraTvTuner.exe` (Windows 10/11 x64, no install, no admin).
The launcher and self-test APKs are inside the EXE; they are attached here too.

### الجديد / What's in it
- إضافة جهاز: IP + منفذ، Wireless debugging (منفذ اقتران منفصل عن منفذ الاتصال)، USB، اكتشاف بالشبكة.
- جرد التطبيقات مع تصنيف محمية / قابلة للمراجعة / غير معروفة + توصيات لـ427 موديل (من CleanYourTV، MIT).
- تعطيل افتراضي قابل للإرجاع، مراجعة قبل التنفيذ، تحقق بعد كل خطوة، سجل محلي واسترجاع.
- واجهة IBRA للتلفزيون (Android 5+) مع معاينة بالبرنامج، و**رجوع للشكل القديم** بزر واحد.
- قياس وقت فتح الواجهة/التطبيق قبل وبعد.
- واجهة عربية RTL وإنجليزية.
- **IBRA HOME** على التلفزيون: بطاقات الطقس ومباريات اليوم والحلقات الجديدة، وصف تطبيقات عراقية.
- **`ibra-home-installer.apk` — مثبّت من الموبايل:** يثبّت IBRA HOME على التلفزيون عبر الواي فاي (ADB منفذ 5555) بدون حاسبة، يحفظ الواجهة الأصلية ويرجّعها بزر.

### Tested / ما تم اختباره
- ✅ 27 automated core tests on Linux and Windows runners (fake in-memory TV).
- ✅ Phone installer end-to-end against a fake TV adbd (RSA prompt once, byte-exact push, set Home, Google-TV-style fallback, restore + remove).
- ✅ APKs build, sign and verify; EXE builds with the icon at 16/32/256 px.
- ❌ **Not yet tested on an emulator or a real TV/box.** All Android TV features show as *Untested* in the app until a hardware run is recorded in `docs/TESTING.md`.

### Limits / القيود
- Samsung Tizen, LG webOS, VIDAA, Roku: not supported (no ADB).
- Phone installer: ADB over network (port 5555) only; Android 11+ *Wireless debugging* pairing (TLS) is not supported yet — use the Windows app for those.
- Fire TV: Home cannot be replaced (Fire OS restriction); inventory/disable only.
- ADB is not bundled (Google licence); the app finds it or downloads Google's official Platform-Tools after you accept.
- The APKs in this draft are signed with a per-build key (the release signing secret could not be set from here). If a later build refuses to update the launcher, use *Restore my old look* with *remove launcher* first, then install again.
- App labels are not available over plain ADB, so package names are shown in the Apps page.
