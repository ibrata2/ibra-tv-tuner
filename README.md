<p align="center"><img src="assets/icon/ibra-tv-tuner-1024.png" width="128" alt="IBRA TV Tuner icon"></p>

<h1 align="center">IBRA TV Tuner</h1>

<p align="center">تخصيص واجهة التلفزيون الذكي والـTV Box وإدارة التطبيقات الزائدة من الحاسبة عبر الشبكة المحلية.<br>
Customise smart-TV and TV-box home screens and manage bloat from your Windows PC over the local network.</p>

> **الحالة / Status:** v0.1.0 — first build. Core logic is covered by automated tests; **nothing has been run on a real TV yet** (see [docs/TESTING.md](docs/TESTING.md)).

---

## بالعربي

### شنو يسوي
- **الأجهزة:** إضافة جهاز بالـIP والمنفذ، أو Wireless debugging (منفذ اقتران + رمز، ومنفذ اتصال منفصل)، أو USB، أو اكتشاف بالشبكة. الشاشة والبوكس جهازين مستقلين، وكل تعديل يبيّن اسم الجهاز المستهدف.
- **المظهر:** معاينة داخل البرنامج (خلفية، مفضلة، ترتيب البلاطات، أعمدة، ألوان)، ثم: تثبيت واجهة IBRA ← إرسال الشكل ← فتحها للتجربة ← تعيينها رئيسية.
- **واجهة IBRA TV HOME على التلفزيون (1920×1080، تتكيف مع 720p و4K):** تصميم زجاجي (Glassmorphism) عربي RTL أو إنجليزي، شريط جانبي (الرئيسية، بحث، تلفزيون، كل التطبيقات، إعدادات)، ثلاث بطاقات علوية:
  - **الساعة والطقس:** ساعة كبيرة، التاريخ الميلادي (بأسماء الأشهر العراقية) والهجري (أم القرى + تصحيح يدوي)، الحرارة والحالة، المدينة (قائمة مدن عراقية، بحث عن أي مدينة، أو موقع الجهاز)، توقعات 7 أيام.
  - **مركز الترفيه:** قائمة منسدلة — المسلسلات الجديدة، الأفلام الجديدة، الأكثر مشاهدة، الأنمي، البرامج التلفزيونية، المفضلة، متابعة المشاهدة، مباريات اليوم. بوستر كبير + شريط بوسترات، تفاصيل وزر «شغّل عبر» يفتح تطبيق مثبّت عندك فقط (لا روابط غير مرخصة).
  - **مواقيت الصلاة الجعفرية:** تُحسب على التلفزيون بدون إنترنت (جعفري – ليوة قم، أو جيوفيزياء طهران؛ المغرب بزاوية 4° مو الغروب)، الصلاة القادمة والوقت المتبقي، عرض الظهرين والعشاءين، ضبط الدقائق، تنبيه أو أذان (ملف adhan.mp3 تضيفه أنت).
  - **تطبيقاتي المفضلة:** ترتيب بالريموت، مجلدات، حجم الأيقونات، ضغطة مطوّلة = خيارات، و«كل التطبيقات» بشبكة مع بحث وفرز أبجدي/آخر استخدام.
  - **الإعدادات:** 5 ثيمات (أسود زجاجي، أبيض زجاجي، أحمر وأسود، بنفسجي سينمائي، ذهبي IBRA)، خلفية ثابتة أو متحركة، شفافية المربعات، إظهار/إخفاء وترتيب الأقسام، نسخ احتياطي واستعادة، إعادة الضبط، وضع معاينة.
  - **الأداء:** تحميل كسول للبوسترات مع ذاكرة مؤقتة، كل شي يشتغل بدون إنترنت من آخر نسخة محفوظة، المؤثرات الثقيلة تتوقف تلقائياً على الأجهزة الضعيفة (≤1.5GB RAM).
- **رجوع للشكل القديم:** البرنامج يحفظ الواجهة الأصلية قبل أي تغيير، وزر واحد يرجّعها كواجهة رئيسية ويتحقق، مع خيار حذف واجهة IBRA. ويوجد كذلك «رجوع للإعدادات السابقة» للشكل، وبلاطة «الواجهة الأصلية» داخل واجهة IBRA على التلفزيون نفسه.
- **التطبيقات:** جرد كامل (الحزمة، النوع، الحالة، نظام أو مستخدم) مع تصنيف: محمية / قابلة للمراجعة / غير معروفة، وقوائم توصيات مربوطة بالموديل. الافتراضي **تعطيل** قابل للإرجاع؛ الإزالة خيار منفصل متقدم.
- **التغييرات والاسترجاع:** كل دفعة تُحفظ قبل التنفيذ، كل خطوة يُعاد التحقق منها من الجهاز، والاسترجاع يرجّع الحالة المسجّلة قبل التغيير.
- **التوافق:** مصفوفة تُرسم من الكود نفسه: مدعومة / تحتاج إعداداً / غير مدعومة / غير مختبرة.
- **قياس:** وقت فتح الواجهة أو تطبيق (am start -W) قبل وبعد، بدون ادعاء تسريع.

### مثبّت IBRA HOME من الموبايل (`ibra-home-installer.apk`)
بدون حاسبة: نصّب التطبيق على موبايل أندرويد، وهو يحمل واجهة IBRA HOME بداخله.
1. على التلفزيون فعّل خيارات المطوّر و«تصحيح USB» / «ADB عبر الشبكة» (الخطوات تحت، وموجودة داخل التطبيق).
2. الموبايل والتلفزيون على نفس الواي فاي ← «ابحث بالشبكة» أو اكتب الـ IP (المنفذ 5555).
3. «ثبّت IBRA HOME» ← اضغط **سماح** على التلفزيون (مرة وحدة) ← يثبّت، يحفظ الواجهة الأصلية، ويخلي IBRA HOME رئيسية ويتحقق.
4. «رجّع الواجهة الأصلية» أو «رجّع الأصلية واحذف IBRA HOME» يرجّعك مثل ما كنت.

خيار للأجهزة اللي ترجع للواجهة القديمة بنفسها (مثل Google TV): تعطيل الواجهة الأصلية مؤقتاً، وزر الاسترجاع يفعّلها من جديد.
حالياً يدعم «ADB عبر الشبكة» على المنفذ 5555؛ منفذ «تصحيح الأخطاء اللاسلكي» (اقتران برمز، أندرويد 11+) غير مدعوم بعد — استعمل برنامج الحاسبة لهذا النوع.
الاتصال محلي فقط (الموبايل ← التلفزيون)؛ ما يتصل بأي سيرفر، ومفتاح ADB يتولّد ويبقى داخل الموبايل.

### ربط التلفزيون أو البوكس عبر Wi-Fi
1. على التلفزيون: الإعدادات ← تفضيلات الجهاز ← حول ← اضغط OK على **الإصدار/Build** 7 مرات.
2. خيارات المطوّر ← فعّل **تصحيح USB** و/أو **ADB عبر الشبكة**.
3. اعرف IP التلفزيون من إعدادات الشبكة.
4. بالبرنامج: الأجهزة ← إضافة جهاز ← اكتب الـIP والمنفذ `5555` ← إضافة واتصال ← اضغط **سماح** على التلفزيون.

**Android 11+ (Wireless debugging):** شاشة «Pair device with pairing code» تعطي **منفذ اقتران** ورمز 6 أرقام؛ الشاشة الرئيسية تعطي **منفذ اتصال** مختلف. اكتب الاثنين. الرمز لا يُحفظ ولا يُسجَّل.

الحاسبة تقدر تكون على **Ethernet** والتلفزيون على **Wi-Fi** إذا كانوا على نفس الراوتر وما في عزل بين الأجهزة.

### ADB
ما يُرفق ADB داخل البرنامج لأن ترخيص Google لا يسمح بإعادة التوزيع. البرنامج يلگى `adb.exe` الموجود، أو تختاره، أو يحمّل Platform-Tools الرسمية من Google بعد موافقتك.

### الأمان
بدون root، بدون فلاش، بدون فتح bootloader. ما تُرسل أي أوامر نصية حرة لـADB؛ كل إجراء من قائمة ثابتة ويتحقق من مدخلاته. المكونات الأساسية (System UI، الإعدادات، الواجهة الحالية، لوحة المفاتيح، خدمات الشبكة…) محمية. لا يُرفع أي شيء لأي خدمة خارجية؛ البيانات في `%LOCALAPPDATA%\IbraTvTuner`. البرنامج يشتغل بصلاحيات المستخدم العادي.

---

## English

### Build
```bash
# Android launcher + self-test APK (no Gradle needed)
sudo apt-get install android-sdk-platform-23 aapt dalvik-exchange zipalign apksigner openjdk-17-jdk-headless
android/build.sh                       # → android/out/*.apk

# Core tests (any OS with the .NET 10 SDK)
dotnet run --project tests/IbraTvTuner.Core.Tests -c Release

# Windows app (on Windows, .NET 10 SDK)
dotnet publish src/IbraTvTuner.App -c Release -r win-x64 --self-contained true ^
  -p:PublishSingleFile=true -p:IncludeNativeLibrariesForSelfExtract=true -o publish
```
CI (`.github/workflows/build.yml`) does all three, uploads the portable EXE and the APKs, and keeps a draft release up to date.

### Layout
| Path | What |
|---|---|
| `src/IbraTvTuner.Core` | ADB actions (fixed list, validated), device session, package classifier, change journal + rollback, launcher manager + Home backup/restore, discovery, timing. Platform modules decide what each OS may do. |
| `src/IbraTvTuner.App` | WPF UI (Arabic RTL / English): Devices, Appearance, Apps, Changes & restore, Compatibility. |
| `android/launcher` | IBRA TV HOME launcher (Java, Android 5+, no Gradle): glass UI scaled from a 1920×1080 design, sidebar, clock/weather, entertainment centre (TVmaze, Jikan, TMDB optional, iTunes, ESPN), offline Ja'fari prayer times + Umm al-Qura Hijri, favourites with folders, all-apps grid, search, settings, backup. `test/run.sh` checks prayer times against PrayTimes.org and Hijri against Umm al-Qura; `test/screenshots.sh` runs on an Android TV emulator in CI. |
| `android/installer` | IBRA HOME phone installer (Java, Android 5+): pure-Java ADB client (RSA auth, shell, sync push) over port 5555, LAN scan, install + set Home + verified restore; carries the launcher APK in its assets. `test/run.sh` runs it end-to-end against a fake TV. |
| `android/testapp` | Harmless app used only by the self-test. |
| `tests/` | Dependency-free test runner with an in-memory fake TV. |
| `tools/build_catalog.py` | Regenerates the model recommendation catalog from CleanYourTV (MIT). |

### Compatibility (what is really implemented)
| Platform | Status in v0.1.0 |
|---|---|
| Android TV / Android TV Box | Implemented, **untested on hardware** |
| Google TV | Implemented, **untested on hardware**; Google TV may take Home back, so the app verifies after setting it |
| Fire TV | Inventory/disable implemented (untested); **Home replacement blocked** (Fire OS does not allow it) |
| Samsung Tizen, LG webOS, Hisense VIDAA, Roku | Listed, **not supported** (no ADB) — later phases |
| CoreELEC / LibreELEC | Reference only |

### Licence
MIT — see [LICENSE](LICENSE). Reused material and reviewed projects: [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).

Designed & developed by **إبراهيم الخزرجي** · [t.me/ibraprogram](https://t.me/ibraprogram) · TikTok [@ibraprogram](https://www.tiktok.com/@ibraprogram)
