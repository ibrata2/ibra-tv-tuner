using System.IO;
using System.IO.Compression;
using System.Net.Http;
using System.Windows;
using IbraTvTuner.Core;
using IbraTvTuner.Core.Adb;
using IbraTvTuner.Core.Discovery;
using IbraTvTuner.Core.Packages;
using IbraTvTuner.Core.Platforms;
using IbraTvTuner.Core.Storage;

namespace IbraTvTuner.App;

/// <summary>Shared state for all pages: settings, devices, adb and the selected device.</summary>
public sealed class AppState
{
    public static AppState I { get; } = new();

    public AppSettings Settings { get; } = AppSettings.Load();
    public DeviceStore Store { get; } = DeviceStore.Load();
    public Catalog Catalog { get; } = Catalog.LoadEmbedded();
    public List<PackageEntry> Inventory { get; set; } = new();

    public event Action? CurrentChanged;
    public event Action? DevicesChanged;

    private DeviceProfile? _current;
    public DeviceProfile? Current
    {
        get => _current;
        set { _current = value; Inventory = new(); CurrentChanged?.Invoke(); }
    }

    public string? AdbPath => AdbLocator.Find(Settings.AdbPath);
    public IAdbTransport? Adb => AdbPath is { } p ? new ProcessAdbTransport(p) : null;

    public DeviceSession? Session => Current is null || Adb is null ? null : new DeviceSession(Adb, Current);

    public void SaveDevices() { Store.Save(); DevicesChanged?.Invoke(); }

    public string TargetName => Current is null ? Loc.T("NoDevice")
        : $"{Current.Title} ({RoleName(Current.Role)} · {Current.Serial})";

    public static string RoleName(DeviceRole r) => r switch
    {
        DeviceRole.Screen => Loc.T("RoleScreen"),
        DeviceRole.Box => Loc.T("RoleBox"),
        _ => Loc.T("RoleUnknown"),
    };

    public static string StatusName(FeatureStatus s) => Loc.T(s.ToString());

    /// <summary>Writes an APK shipped inside the EXE to local app data and returns its path.</summary>
    public static string? ExtractApk(string name)
    {
        using var s = typeof(AppState).Assembly.GetManifestResourceStream(name);
        if (s is null) return null;
        var path = Path.Combine(AppPaths.Ensure("apk"), name);
        using (var f = File.Create(path)) s.CopyTo(f);
        return path;
    }

    /// <summary>Downloads Google's official Platform-Tools after the user accepted Google's terms.</summary>
    public async Task<string?> DownloadPlatformToolsAsync(IProgress<string>? progress = null)
    {
        var dir = AppPaths.ToolsDir;
        var zip = Path.Combine(dir, "platform-tools-latest-windows.zip");
        using (var http = new HttpClient { Timeout = TimeSpan.FromMinutes(5) })
        {
            progress?.Report("download…");
            var bytes = await http.GetByteArrayAsync(AdbLocator.OfficialWindowsZip);
            await File.WriteAllBytesAsync(zip, bytes);
        }
        progress?.Report("extract…");
        var target = Path.Combine(dir, "platform-tools");
        if (Directory.Exists(target)) Directory.Delete(target, true);
        ZipFile.ExtractToDirectory(zip, dir);
        File.Delete(zip);
        var adb = Path.Combine(target, "adb.exe");
        if (!File.Exists(adb)) return null;
        Settings.AdbPath = adb;
        Settings.Save();
        return adb;
    }
}

public static class Ui
{
    public static void Error(Exception ex)
    {
        var msg = ex switch
        {
            AdbOperationException a => a.Error.Message(Loc.I.IsArabic) + "\n\n" + Short(a.Error.Raw),
            FeatureBlockedException f => Loc.Pick("هذه الميزة غير مدعومة على هذا النظام: ", "Not supported on this system: ") + f.Feature,
            InvalidInputException v => Loc.Pick("قيمة غير صالحة: ", "Invalid value: ") + v.What,
            _ => ex.Message,
        };
        Log.Write("ERROR " + ex.GetType().Name + ": " + ex.Message);
        MessageBox.Show(Application.Current.MainWindow, msg, Loc.T("Error"), MessageBoxButton.OK, MessageBoxImage.Warning);
    }

    public static void Error(AdbError? e, string fallbackAr = "فشل الإجراء.", string fallbackEn = "The action failed.")
    {
        var msg = e is null ? Loc.Pick(fallbackAr, fallbackEn) : e.Message(Loc.I.IsArabic) + "\n\n" + Short(e.Raw);
        MessageBox.Show(Application.Current.MainWindow, msg, Loc.T("Error"), MessageBoxButton.OK, MessageBoxImage.Warning);
    }

    public static void Info(string text) =>
        MessageBox.Show(Application.Current.MainWindow, text, Loc.T("AppTitle"), MessageBoxButton.OK, MessageBoxImage.Information);

    /// <summary>Every change confirmation names the target device first.</summary>
    public static bool ConfirmOnDevice(string what)
    {
        var text = Loc.T("Target") + " " + AppState.I.TargetName + "\n\n" + what;
        return MessageBox.Show(Application.Current.MainWindow, text, Loc.T("ConfirmTitle"),
            MessageBoxButton.YesNo, MessageBoxImage.Question, MessageBoxResult.No) == MessageBoxResult.Yes;
    }

    /// <summary>Asks once more when a feature is implemented but not yet verified on hardware.</summary>
    public static bool AcceptUntested(DeviceSession s, Feature f)
    {
        var st = s.Module.StatusOf(f);
        if (st == FeatureStatus.Unsupported) { Error(new FeatureBlockedException(s.Module.Id, f)); return false; }
        if (st != FeatureStatus.Untested) return true;
        return MessageBox.Show(Application.Current.MainWindow,
            Loc.Pick($"«{f}» منفّذة لكن لم تُختبر بعد على عتاد {s.Module.DisplayName} حقيقي. أكمل؟",
                     $"'{f}' is implemented but not yet verified on real {s.Module.DisplayName} hardware. Continue?"),
            Loc.T("ConfirmTitle"), MessageBoxButton.YesNo, MessageBoxImage.Warning, MessageBoxResult.No) == MessageBoxResult.Yes;
    }

    public static bool NeedSession(out DeviceSession session)
    {
        session = null!;
        if (AppState.I.Adb is null) { Info(Loc.T("AdbNotFound")); return false; }
        var s = AppState.I.Session;
        if (s is null) { Info(Loc.T("NoDevice")); return false; }
        session = s;
        return true;
    }

    private static string Short(string s) => s.Length > 400 ? s[..400] + "…" : s;
}
