using System.ComponentModel;
using System.Windows;

namespace IbraTvTuner.App;

/// <summary>Arabic / English strings. XAML binds with {Binding [Key], Source={x:Static local:Loc.I}}.</summary>
public sealed class Loc : INotifyPropertyChanged
{
    public static Loc I { get; } = new();
    public event PropertyChangedEventHandler? PropertyChanged;

    private string _lang = "ar";
    public string Lang
    {
        get => _lang;
        set
        {
            _lang = value == "en" ? "en" : "ar";
            PropertyChanged?.Invoke(this, new PropertyChangedEventArgs("Item[]"));
            PropertyChanged?.Invoke(this, new PropertyChangedEventArgs(nameof(Flow)));
            PropertyChanged?.Invoke(this, new PropertyChangedEventArgs(nameof(IsArabic)));
        }
    }

    public bool IsArabic => _lang == "ar";
    public FlowDirection Flow => IsArabic ? FlowDirection.RightToLeft : FlowDirection.LeftToRight;

    public string this[string key] => T(key);

    public static string T(string key) =>
        Strings.TryGetValue(key, out var v) ? (I.IsArabic ? v.Ar : v.En) : key;

    public static string Pick(string ar, string en) => I.IsArabic ? ar : en;

    private static readonly Dictionary<string, (string Ar, string En)> Strings = new()
    {
        ["AppTitle"] = ("IBRA TV Tuner", "IBRA TV Tuner"),
        ["CheckUpdates"] = ("تحقق من التحديثات", "Check for updates"),
        ["AutoUpdate"] = ("تحديث تلقائي", "Automatic updates"),
        ["Tagline"] = ("تخصيص التلفزيون والبوكس من الحاسبة", "Customise your TV and box from the PC"),
        ["NavDevices"] = ("الأجهزة", "Devices"),
        ["NavAppearance"] = ("المظهر", "Appearance"),
        ["NavApps"] = ("التطبيقات", "Apps"),
        ["NavChanges"] = ("التغييرات والاسترجاع", "Changes & restore"),
        ["NavCompat"] = ("التوافق", "Compatibility"),
        ["Language"] = ("English", "العربية"),
        ["Target"] = ("الجهاز المستهدف:", "Target device:"),
        ["NoDevice"] = ("لا يوجد جهاز محدد", "No device selected"),
        ["Credit"] = ("تصميم وتطوير إبراهيم الخزرجي", "Designed & developed by Ibrahim Al-Khazraji"),

        // devices
        ["AddDevice"] = ("إضافة جهاز", "Add device"),
        ["Connect"] = ("اتصال", "Connect"),
        ["ReadInfo"] = ("قراءة الموديل والنظام", "Read model & system"),
        ["Remove"] = ("حذف من القائمة", "Remove from list"),
        ["Nickname"] = ("اسم مختصر", "Nickname"),
        ["Role"] = ("النوع", "Role"),
        ["RoleScreen"] = ("شاشة", "Screen"),
        ["RoleBox"] = ("بوكس", "Box"),
        ["RoleUnknown"] = ("غير محدد", "Unspecified"),
        ["RoleHint"] = ("الشاشة والبوكس المتصل بيها يُضافان كجهازين مستقلين، وكل تعديل يبيّن اسم الجهاز.", "A screen and the box plugged into it are two separate devices; every change names its target."),
        ["Save"] = ("حفظ", "Save"),
        ["AdbPath"] = ("مسار ADB", "ADB path"),
        ["Browse"] = ("اختيار…", "Browse…"),
        ["DownloadAdb"] = ("تحميل Platform-Tools الرسمية", "Download official Platform-Tools"),
        ["AdbNotFound"] = ("ADB غير موجود. اختَر adb.exe أو حمّل Platform-Tools الرسمية من Google.", "ADB not found. Choose adb.exe or download Google's official Platform-Tools."),
        ["SelfTest"] = ("اختبار المسار الكامل", "Run end-to-end self-test"),
        ["SelfTestHint"] = ("يثبّت تطبيقاً اختبارياً صغيراً، يعطّله، يتحقق، يرجّعه، يتحقق، ثم يحذفه. لا يلمس تطبيقاتك.", "Installs a tiny test app, disables it, verifies, re-enables, verifies, then removes it. Your apps are untouched."),
        ["Log"] = ("السجل", "Log"),
        ["Status"] = ("الحالة", "Status"),

        // add device
        ["TabNetwork"] = ("ADB عبر الشبكة", "ADB over network"),
        ["TabWireless"] = ("Wireless debugging (Android 11+)", "Wireless debugging (Android 11+)"),
        ["TabUsb"] = ("USB", "USB"),
        ["TabDiscover"] = ("اكتشاف", "Discover"),
        ["Host"] = ("IP الجهاز", "Device IP"),
        ["Port"] = ("المنفذ", "Port"),
        ["PairPort"] = ("منفذ الاقتران (Pairing port)", "Pairing port"),
        ["PairCode"] = ("رمز الاقتران (6 أرقام)", "Pairing code (6 digits)"),
        ["ConnectPort"] = ("منفذ الاتصال (Connect port)", "Connect port"),
        ["PairAndConnect"] = ("اقتران ثم اتصال", "Pair, then connect"),
        ["AddAndConnect"] = ("إضافة واتصال", "Add & connect"),
        ["Scan"] = ("بحث في الشبكة", "Scan network"),
        ["RefreshUsb"] = ("تحديث أجهزة USB", "Refresh USB devices"),
        ["UseThis"] = ("استخدم هذا", "Use this"),
        ["Cancel"] = ("إلغاء", "Cancel"),
        ["StepsNetwork"] = (
            "على التلفزيون:\n1) الإعدادات ← تفضيلات الجهاز ← حول ← اضغط OK على «الإصدار/Build» 7 مرات.\n2) ارجع ← خيارات المطوّر ← فعّل «تصحيح USB» و/أو «تصحيح الشبكة/ADB debugging».\n3) الإعدادات ← الشبكة: اكتب IP التلفزيون.\nعلى الحاسبة: اكتب الـIP والمنفذ 5555 واضغط إضافة واتصال، ثم اضغط «سماح» على التلفزيون.\n\nالحاسبة تقدر تكون على كيبل Ethernet والتلفزيون على Wi-Fi، بشرط يكونون على نفس الراوتر/الشبكة المحلية وما في عزل بين الأجهزة (Client/AP isolation).",
            "On the TV:\n1) Settings → Device Preferences → About → press OK on Build 7 times.\n2) Back → Developer options → turn on USB debugging and/or Network/ADB debugging.\n3) Settings → Network: note the TV's IP.\nOn the PC: enter the IP and port 5555, press Add & connect, then press Allow on the TV.\n\nThe PC can be on Ethernet and the TV on Wi-Fi, as long as both are on the same router/local network without client (AP) isolation."),
        ["StepsWireless"] = (
            "Android 11 فأحدث:\n1) خيارات المطوّر ← Wireless debugging ← تشغيل.\n2) «Pair device with pairing code»: تظهر IP ومنفذ اقتران ورمز من 6 أرقام. هذا المنفذ للاقتران فقط.\n3) شاشة Wireless debugging الرئيسية تعرض IP ومنفذ ثاني: هذا منفذ الاتصال.\nاكتب الاثنين هنا. رمز الاقتران لا يُحفظ ولا يُكتب في السجل.",
            "Android 11+:\n1) Developer options → Wireless debugging → On.\n2) 'Pair device with pairing code' shows IP, a PAIRING port and a 6-digit code. That port is for pairing only.\n3) The main Wireless debugging screen shows the IP and a different CONNECT port.\nEnter both here. The pairing code is never saved or logged."),
        ["StepsUsb"] = (
            "USB يشتغل فقط إذا الجهاز ومنفذه يدعمان وضع الجهاز (كثير من التلفزيونات ما تدعم). البوكسات اللي فيها منفذ OTG أو USB-C غالباً تدعم. فعّل تصحيح USB ثم وصّل الكيبل واضغط تحديث.",
            "USB only works when the device and that port support device mode (many TVs don't). Boxes with an OTG or USB-C port usually do. Turn on USB debugging, plug in, then refresh."),
        ["StepsDiscover"] = (
            "الاكتشاف للقراءة فقط: يقرأ أجهزة Wireless debugging المعلنة (mDNS)، ويفحص المنفذ 5555 على شبكتك المحلية بدون أي تعديل.",
            "Discovery is read-only: it lists devices announcing Wireless debugging (mDNS) and probes port 5555 on your local network without changing anything."),

        // appearance
        ["Preview"] = ("معاينة", "Preview"),
        ["LauncherLang"] = ("لغة الواجهة", "Launcher language"),
        ["LauncherTitle"] = ("العنوان", "Title"),
        ["Columns"] = ("عدد الأعمدة", "Columns"),
        ["Background"] = ("الخلفية (لونين)", "Background (two colours)"),
        ["Accent"] = ("لون التحديد", "Focus colour"),
        ["TextColor"] = ("لون النص", "Text colour"),
        ["ShowClock"] = ("إظهار الساعة", "Show clock"),
        ["ShowAllApps"] = ("إظهار كل التطبيقات", "Show all apps"),
        ["LargeText"] = ("خط كبير مقروء من بعيد", "Large text for viewing distance"),
        ["Favorites"] = ("المفضلة وترتيب البلاطات", "Favorites & tile order"),
        ["Hidden"] = ("مخفية من الواجهة", "Hidden from home"),
        ["LoadApps"] = ("تحميل تطبيقات الجهاز", "Load device apps"),
        ["Up"] = ("▲", "▲"),
        ["Down"] = ("▼", "▼"),
        ["Step1"] = ("1. تثبيت واجهة IBRA", "1. Install IBRA launcher"),
        ["Step2"] = ("2. إرسال الشكل", "2. Send the look"),
        ["Step3"] = ("3. فتحها للتجربة", "3. Open it to try"),
        ["Step4"] = ("4. تعيينها كواجهة رئيسية", "4. Make it Home"),
        ["RevertTheme"] = ("رجوع للإعدادات السابقة", "Back to previous settings"),
        ["RestoreOld"] = ("رجوع للشكل القديم", "Restore my old look"),
        ["RestoreOldHint"] = ("يرجّع الواجهة الأصلية المحفوظة كواجهة رئيسية ويتحقق منها.", "Puts the saved original Home back and verifies it."),
        ["RemoveLauncherToo"] = ("واحذف واجهة IBRA من الجهاز", "and remove the IBRA launcher"),
        ["OriginalHome"] = ("الواجهة الأصلية المحفوظة:", "Saved original Home:"),
        ["Presets"] = ("ألوان جاهزة", "Presets"),

        // apps
        ["Refresh"] = ("جرد التطبيقات", "Inventory apps"),
        ["ModelList"] = ("قائمة توصيات الموديل", "Model recommendation list"),
        ["ModelNone"] = ("(بدون قائمة)", "(no list)"),
        ["Search"] = ("بحث", "Search"),
        ["ShowClass"] = ("عرض", "Show"),
        ["All"] = ("الكل", "All"),
        ["Protected"] = ("محمية", "Protected"),
        ["Reviewable"] = ("قابلة للمراجعة", "Reviewable"),
        ["Unknown"] = ("غير معروفة", "Unknown"),
        ["ColPackage"] = ("الحزمة", "Package"),
        ["ColKind"] = ("النوع", "Type"),
        ["ColState"] = ("الحالة", "State"),
        ["ColClass"] = ("التصنيف", "Class"),
        ["ColReason"] = ("السبب", "Reason"),
        ["DisableSelected"] = ("تعطيل المحدد (مستحسن)", "Disable selected (recommended)"),
        ["EnableSelected"] = ("تفعيل المحدد", "Enable selected"),
        ["UninstallSelected"] = ("إزالة للمستخدم (متقدم)", "Remove for user (advanced)"),
        ["UninstallWarn"] = ("الإزالة قد تمسح بيانات التطبيق. التعطيل أسلم ويرجع بضغطة. متأكد؟", "Removing may delete the app's data. Disabling is safer and reverts in one click. Continue?"),
        ["Measure"] = ("قياس الأداء", "Measure"),
        ["MeasureHome"] = ("قياس فتح الواجهة", "Measure Home start"),
        ["MeasureApp"] = ("قياس فتح تطبيق", "Measure app start"),
        ["MeasureNote"] = ("قياس عملي بـ am start -W بعد إيقاف التطبيق (بارد). قارن قبل وبعد؛ لا يوجد تسريع مضمون.", "Practical cold-start timing via am start -W after force-stop. Compare before/after; no speed-up is promised."),

        // changes
        ["Batches"] = ("دفعات التغييرات", "Change batches"),
        ["Rollback"] = ("استرجاع هذه الدفعة", "Roll back this batch"),
        ["RefreshList"] = ("تحديث", "Refresh"),
        ["Plan"] = ("مراجعة التغييرات قبل التنفيذ", "Review changes before applying"),
        ["Apply"] = ("تنفيذ", "Apply"),
        ["Change"] = ("التغيير", "Change"),
        ["Before"] = ("قبل", "Before"),
        ["After"] = ("بعد", "After"),
        ["Result"] = ("النتيجة", "Result"),
        ["Skipped"] = ("لن يُنفَّذ", "Will not run"),
        ["HomeBackup"] = ("نسخة الواجهة الأصلية", "Original Home backup"),

        // compat
        ["CompatHint"] = ("مدعومة = مختبرة على عتاد حقيقي · تحتاج إعداداً = تشتغل بعد تفعيل شيء على الجهاز · غير مدعومة = ممنوعة · غير مختبرة = منفّذة وتنتظر اختباراً على عتاد حقيقي (تشتغل مع تحذير).",
                        "Supported = verified on real hardware · Needs setup = works after enabling something on the device · Unsupported = blocked · Untested = implemented, awaiting a hardware test (runs with a warning)."),
        ["Feature"] = ("الميزة", "Feature"),
        ["Supported"] = ("مدعومة", "Supported"),
        ["NeedsSetup"] = ("تحتاج إعداداً", "Needs setup"),
        ["Unsupported"] = ("غير مدعومة", "Unsupported"),
        ["Untested"] = ("غير مختبرة", "Untested"),

        ["Busy"] = ("جاري التنفيذ…", "Working…"),
        ["Done"] = ("تم", "Done"),
        ["Error"] = ("خطأ", "Error"),
        ["ConfirmTitle"] = ("تأكيد", "Confirm"),
    };
}
