namespace IbraTvTuner.Core.Platforms;

public enum PlatformId { AndroidTv, GoogleTv, FireTv, SamsungTizen, LgWebOs, Vidaa, Roku, Unknown }

public enum FeatureStatus
{
    /// <summary>Implemented and verified on real hardware.</summary>
    Supported,
    /// <summary>Works once the user enables something on the device (developer options, ADB…).</summary>
    NeedsSetup,
    /// <summary>The platform does not allow it, or this app does not implement it. Execution is blocked.</summary>
    Unsupported,
    /// <summary>Implemented but not yet verified on real hardware for this platform.</summary>
    Untested,
}

public enum Feature
{
    ConnectNetwork,
    WirelessDebuggingPairing,
    ConnectUsb,
    LanDiscovery,
    ReadDeviceInfo,
    InventoryApps,
    DisableApp,
    UninstallForUser,
    InstallLauncher,
    SetLauncherAsHome,
    PushLauncherTheme,
    RestoreOriginalHome,
    LaunchTiming,
}

public sealed record FeatureEntry(Feature Feature, FeatureStatus Status, string NoteAr, string NoteEn);

public interface IPlatformModule
{
    PlatformId Id { get; }
    string DisplayName { get; }
    bool UsesAdb { get; }
    IReadOnlyList<FeatureEntry> Features { get; }
    FeatureStatus StatusOf(Feature f) => Features.FirstOrDefault(x => x.Feature == f)?.Status ?? FeatureStatus.Unsupported;
    /// <summary>Executable means: not Unsupported. Untested features run with a warning.</summary>
    bool CanExecute(Feature f) => StatusOf(f) != FeatureStatus.Unsupported;
}

public sealed class PlatformModule(PlatformId id, string name, bool usesAdb, IReadOnlyList<FeatureEntry> features) : IPlatformModule
{
    public PlatformId Id { get; } = id;
    public string DisplayName { get; } = name;
    public bool UsesAdb { get; } = usesAdb;
    public IReadOnlyList<FeatureEntry> Features { get; } = features;
}

public sealed class FeatureBlockedException(PlatformId p, Feature f)
    : InvalidOperationException($"{f} is not supported on {p}")
{
    public PlatformId Platform { get; } = p;
    public Feature Feature { get; } = f;
}

/// <summary>
/// The compatibility matrix. Every status here must match what has really been implemented and checked.
/// Update to Supported only after a hardware test is recorded in docs/TESTING.md.
/// </summary>
public static class PlatformRegistry
{
    private static FeatureEntry F(Feature f, FeatureStatus s, string ar, string en) => new(f, s, ar, en);
    private const FeatureStatus U = FeatureStatus.Untested;
    private const FeatureStatus N = FeatureStatus.NeedsSetup;
    private const FeatureStatus X = FeatureStatus.Unsupported;

    private static List<FeatureEntry> AndroidFeatures(bool google) => new()
    {
        F(Feature.ConnectNetwork, N, "فعّل خيارات المطوّر ثم تصحيح ADB (USB/Network debugging) على الجهاز.", "Enable Developer options and ADB debugging on the device."),
        F(Feature.WirelessDebuggingPairing, N, "Android 11 فأحدث فقط، ومن قائمة Wireless debugging.", "Android 11+ only, from Wireless debugging."),
        F(Feature.ConnectUsb, N, "يحتاج منفذ USB يدعم وضع الجهاز (ليس كل التلفزيونات).", "Needs a USB port that supports device mode (many TVs don't)."),
        F(Feature.LanDiscovery, U, "mDNS لأجهزة Wireless debugging، وفحص المنفذ 5555 على الشبكة المحلية.", "mDNS for wireless debugging, port-5555 scan on the LAN."),
        F(Feature.ReadDeviceInfo, U, "قراءة فقط.", "Read only."),
        F(Feature.InventoryApps, U, "قراءة فقط. اسم التطبيق الظاهر غير متاح من ADB بدون أدوات إضافية؛ نعرض اسم الحزمة.", "Read only. Display labels are not exposed over plain ADB; package names are shown."),
        F(Feature.DisableApp, U, "pm disable-user، قابل للإرجاع.", "pm disable-user, reversible."),
        F(Feature.UninstallForUser, U, "pm uninstall -k --user 0، الإرجاع بـ install-existing (Android 8+).", "pm uninstall -k --user 0, restored with install-existing (Android 8+)."),
        F(Feature.InstallLauncher, U, "يثبّت واجهة IBRA (Android 5+).", "Installs the IBRA launcher (Android 5+)."),
        F(Feature.SetLauncherAsHome, U, google
            ? "Google TV قد يرجّع الرئيسية للواجهة الأصلية؛ يتحقق البرنامج بعد التعيين."
            : "يحتاج cmd package set-home-activity (Android 7+ على أغلب الأجهزة). إذا رفض النظام، شغّل الواجهة يدوياً.",
            google ? "Google TV may take Home back; the app verifies after setting it." : "Needs cmd package set-home-activity (Android 7+ on most devices). If refused, open the launcher manually."),
        F(Feature.PushLauncherTheme, U, "إرسال الإعدادات للواجهة عبر الشبكة.", "Sends settings to the launcher over ADB."),
        F(Feature.RestoreOriginalHome, U, "يرجّع الواجهة الأصلية المحفوظة قبل التغيير.", "Restores the Home app saved before the change."),
        F(Feature.LaunchTiming, U, "قياس وقت الفتح بـ am start -W.", "Measures start time with am start -W."),
    };

    public static readonly IPlatformModule AndroidTv = new PlatformModule(PlatformId.AndroidTv, "Android TV / TV Box", true, AndroidFeatures(false));
    public static readonly IPlatformModule GoogleTv = new PlatformModule(PlatformId.GoogleTv, "Google TV", true, AndroidFeatures(true));

    public static readonly IPlatformModule FireTv = new PlatformModule(PlatformId.FireTv, "Amazon Fire TV", true, new List<FeatureEntry>
    {
        F(Feature.ConnectNetwork, N, "Settings → My Fire TV → Developer options → ADB debugging.", "Settings → My Fire TV → Developer options → ADB debugging."),
        F(Feature.WirelessDebuggingPairing, X, "غير متاح على Fire OS.", "Not available on Fire OS."),
        F(Feature.ConnectUsb, N, "حسب الجهاز.", "Depends on the device."),
        F(Feature.LanDiscovery, U, "فحص المنفذ 5555.", "Port-5555 scan."),
        F(Feature.ReadDeviceInfo, U, "قراءة فقط.", "Read only."),
        F(Feature.InventoryApps, U, "قراءة فقط.", "Read only."),
        F(Feature.DisableApp, U, "بعض إصدارات Fire OS ترفض pm تماماً (مثل Fire OS 5).", "Some Fire OS versions refuse pm entirely (e.g. Fire OS 5)."),
        F(Feature.UninstallForUser, X, "معطّل في النسخة الأولى على Fire TV.", "Disabled in v1 on Fire TV."),
        F(Feature.InstallLauncher, U, "يمكن تثبيت الواجهة وتشغيلها يدوياً.", "The launcher can be installed and opened manually."),
        F(Feature.SetLauncherAsHome, X, "Fire OS لا يسلّم زر Home لواجهة خارجية.", "Fire OS will not hand Home to a third-party launcher."),
        F(Feature.PushLauncherTheme, U, "", ""),
        F(Feature.RestoreOriginalHome, X, "لا يوجد تغيير على Home لإرجاعه.", "Home is never changed, so nothing to restore."),
        F(Feature.LaunchTiming, U, "", ""),
    });

    private static IPlatformModule NoAdb(PlatformId id, string name, string whyAr, string whyEn) =>
        new PlatformModule(id, name, false, Enum.GetValues<Feature>().Select(f => F(f, X, whyAr, whyEn)).ToList());

    public static readonly IPlatformModule Tizen = NoAdb(PlatformId.SamsungTizen, "Samsung Tizen",
        "لا يوجد ADB ولا pm disable. المرجع: jellyfin-tizen (تطبيقات Tizen تحتاج Tizen Studio وشهادة مطوّر). مرحلة لاحقة.",
        "No ADB or pm disable. Reference: jellyfin-tizen (Tizen apps need Tizen Studio and a developer certificate). Later phase.");
    public static readonly IPlatformModule WebOs = NoAdb(PlatformId.LgWebOs, "LG webOS",
        "لا يوجد ADB. المرجع: webOS Homebrew Channel (يحتاج Developer Mode أو root ولا يغيّر الواجهة الرئيسية). مرحلة لاحقة.",
        "No ADB. Reference: webOS Homebrew Channel (needs Developer Mode or root, does not replace Home). Later phase.");
    public static readonly IPlatformModule Vidaa = NoAdb(PlatformId.Vidaa, "Hisense VIDAA",
        "نظام مغلق بدون ADB أو تثبيت تطبيقات خارجية. غير مدعوم.", "Closed system, no ADB or sideloading. Not supported.");
    public static readonly IPlatformModule Roku = NoAdb(PlatformId.Roku, "Roku",
        "لا يوجد ADB. واجهة ECP المحلية (منفذ 8060) للقراءة والتحكم فقط وغير منفّذة بعد.",
        "No ADB. The local ECP API (port 8060) is control/read only and not implemented yet.");

    public static IReadOnlyList<IPlatformModule> All { get; } = new[] { AndroidTv, GoogleTv, FireTv, Tizen, WebOs, Vidaa, Roku };

    public static IPlatformModule Get(PlatformId id) => All.FirstOrDefault(m => m.Id == id) ?? AndroidTv;

    /// <summary>Box systems (CoreELEC / LibreELEC) — shown in the compatibility page as reference only.</summary>
    public static readonly (string Name, string NoteAr, string NoteEn)[] ReferenceOnly =
    {
        ("CoreELEC / LibreELEC", "أنظمة Kodi مستقلة تُقلع بدل Android؛ لا ADB ولا pm. تخصيصها يتم من Kodi نفسه. مرجع فقط.",
         "Standalone Kodi systems that boot instead of Android; no ADB or pm. Customised from Kodi itself. Reference only."),
    };
}
