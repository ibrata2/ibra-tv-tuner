## IBRA TV Tuner v0.1.0 (draft)

**Portable:** download `IbraTvTuner-win-x64-portable.zip`, unzip, run `IbraTvTuner.exe` (Windows 10/11 x64, no install, no admin).
The launcher and self-test APKs are inside the EXE; they are attached here too.

### الجديد / What's in it
- إضافة جهاز: IP + منفذ، Wireless debugging (منفذ اقتران منفصل عن منفذ الاتصال)، USB، اكتشاف بالشبكة.
- جرد التطبيقات مع تصنيف محمية / قابلة للمراجعة / غير معروفة + توصيات لـ427 موديل (من CleanYourTV، MIT).
- تعطيل افتراضي قابل للإرجاع، مراجعة قبل التنفيذ، تحقق بعد كل خطوة، سجل محلي واسترجاع.
- واجهة IBRA للتلفزيون (Android 5+) مع معاينة بالبرنامج، و**رجوع للشكل القديم** بزر واحد.
- قياس وقت فتح الواجهة/التطبيق قبل وبعد.
- واجهة عربية RTL وإنجليزية.

### Tested / ما تم اختباره
- ✅ 27 automated core tests on Linux and Windows runners (fake in-memory TV).
- ✅ APKs build, sign and verify; EXE builds with the icon at 16/32/256 px.
- ❌ **Not yet tested on an emulator or a real TV/box.** All Android TV features show as *Untested* in the app until a hardware run is recorded in `docs/TESTING.md`.

### Limits / القيود
- Samsung Tizen, LG webOS, VIDAA, Roku: not supported (no ADB).
- Fire TV: Home cannot be replaced (Fire OS restriction); inventory/disable only.
- ADB is not bundled (Google licence); the app finds it or downloads Google's official Platform-Tools after you accept.
- App labels are not available over plain ADB, so package names are shown in the Apps page.
