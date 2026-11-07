using System.Text;
using System.Text.Json;
using IbraTvTuner.Core.Adb;
using IbraTvTuner.Core.Platforms;
using IbraTvTuner.Core.Storage;

namespace IbraTvTuner.Core.Launcher;

/// <summary>Settings the IBRA launcher on the TV understands. Sent as base64 JSON over `am broadcast`.</summary>
public sealed class LauncherConfig
{
    public int Version { get; set; } = 1;
    public string Lang { get; set; } = "ar";
    public string Title { get; set; } = "IBRA HOME";
    public int Columns { get; set; } = 5;
    public string Background1 { get; set; } = "#0B1220";
    public string Background2 { get; set; } = "#14324A";
    public string Accent { get; set; } = "#38BDF8";
    public string TextColor { get; set; } = "#F1F5F9";
    public bool ShowClock { get; set; } = true;
    public bool ShowAllApps { get; set; } = true;
    public bool LargeText { get; set; } = true;
    public List<string> Favorites { get; set; } = new();
    public List<string> Hidden { get; set; } = new();
    /// <summary>Original Home component, so the launcher can show a "original interface" tile.</summary>
    public string? StockHome { get; set; }

    public LauncherConfig Clone() => JsonSerializer.Deserialize<LauncherConfig>(JsonSerializer.Serialize(this, Json.Options), Json.Options)!;

    public static bool IsColor(string s) => s.Length is 7 or 9 && s[0] == '#' && s.Skip(1).All(Uri.IsHexDigit);

    /// <summary>Clamps and filters every field so nothing invalid reaches the device.</summary>
    public LauncherConfig Sanitized()
    {
        var c = Clone();
        c.Lang = c.Lang == "en" ? "en" : "ar";
        c.Title = new string((c.Title ?? "").Where(ch => !char.IsControl(ch)).Take(40).ToArray());
        c.Columns = Math.Clamp(c.Columns, 3, 8);
        foreach (var (get, set, def) in new (Func<string>, Action<string>, string)[]
        {
            (() => c.Background1, v => c.Background1 = v, "#0B1220"),
            (() => c.Background2, v => c.Background2 = v, "#14324A"),
            (() => c.Accent, v => c.Accent = v, "#38BDF8"),
            (() => c.TextColor, v => c.TextColor = v, "#F1F5F9"),
        })
            if (!IsColor(get())) set(def);
        c.Favorites = c.Favorites.Where(Validators.IsPackage).Distinct().Take(40).ToList();
        c.Hidden = c.Hidden.Where(Validators.IsPackage).Distinct().Take(200).ToList();
        if (c.StockHome is not null && !Validators.IsComponent(c.StockHome)) c.StockHome = null;
        return c;
    }

    public string ToBase64() =>
        Convert.ToBase64String(Encoding.UTF8.GetBytes(JsonSerializer.Serialize(Sanitized(), new JsonSerializerOptions { PropertyNamingPolicy = JsonNamingPolicy.CamelCase })));
}

/// <summary>What Home was before IBRA touched it. Written once, never overwritten by our own launcher.</summary>
public sealed class HomeBackup
{
    public string DeviceId { get; set; } = "";
    public DateTimeOffset SavedAt { get; set; } = DateTimeOffset.Now;
    /// <summary>null = the system chooser / no default was set.</summary>
    public string? OriginalHome { get; set; }
    public List<string> HomeCandidates { get; set; } = new();
    public bool LauncherWasInstalledBefore { get; set; }
    public List<LauncherConfigSnapshot> ConfigHistory { get; set; } = new();
    public DateTimeOffset? RestoredAt { get; set; }

    public static string PathFor(string deviceId) => Path.Combine(AppPaths.Ensure("home-backup"), deviceId + ".json");
    public static HomeBackup? Load(string deviceId) => Json.Load<HomeBackup>(PathFor(deviceId));
    public void Save() => Json.Save(PathFor(DeviceId), this);
}

public sealed class LauncherConfigSnapshot
{
    public DateTimeOffset At { get; set; } = DateTimeOffset.Now;
    public LauncherConfig Config { get; set; } = new();
}

public sealed record StepResult(bool Ok, string Ar, string En, string? Detail = null);

public sealed class LauncherManager(DeviceSession session)
{
    /// <summary>Components that answer HOME but are not launchers (setup wizards, settings fallbacks). From CleanYourTV's notes (MIT).</summary>
    public static readonly IReadOnlySet<string> NotLaunchers = new HashSet<string>
    {
        "com.google.android.tungsten.setupwraith", "com.android.tv.settings", "com.android.settings",
        "com.android.provision", "com.apps.tvsettings",
    };

    public static readonly IReadOnlyList<string> KnownStockLaunchers = new[]
    {
        "com.google.android.tvlauncher", "com.google.android.apps.tv.launcherx",
        "com.google.android.leanbacklauncher", "com.mitv.tvhome", "com.amazon.tv.launcher",
    };

    public DeviceSession Session { get; } = session;
    private string Serial => Session.Serial;

    public static bool IsOurs(string? component) => component is not null && AdbParsers.PackageOf(component) == LauncherInfo.Package;

    public async Task<bool> IsInstalledAsync(CancellationToken ct = default) =>
        await Session.QueryStateAsync(LauncherInfo.Package, ct) is not null;

    /// <summary>Step 1: remember the original Home (only the first time).</summary>
    public async Task<HomeBackup> BackupAsync(CancellationToken ct = default)
    {
        var existing = HomeBackup.Load(Session.Profile.Id);
        var current = await Session.CurrentHomeAsync(ct);
        if (existing is not null && (existing.RestoredAt is null || IsOurs(current)))
            return existing;
        var b = new HomeBackup
        {
            DeviceId = Session.Profile.Id,
            OriginalHome = IsOurs(current) || current is not null && NotLaunchers.Contains(AdbParsers.PackageOf(current)) ? null : current,
            HomeCandidates = (await Session.HomeCandidatesAsync(ct)).Where(c => !IsOurs(c)).ToList(),
            LauncherWasInstalledBefore = await IsInstalledAsync(ct),
            ConfigHistory = existing?.ConfigHistory ?? new(),
        };
        if (b.OriginalHome is null)
            b.OriginalHome = b.HomeCandidates.FirstOrDefault(c => KnownStockLaunchers.Contains(AdbParsers.PackageOf(c)));
        b.Save();
        Log.Write($"[{Session.Profile.Title}] home backup: {b.OriginalHome ?? "(chooser)"}");
        return b;
    }

    public async Task<StepResult> InstallAsync(string apkPath, CancellationToken ct = default)
    {
        Session.Require(Feature.InstallLauncher);
        await BackupAsync(ct);
        var r = await Session.RunAsync(AdbActions.InstallOwnApk(Serial, apkPath), ct);
        var err = AdbErrorTranslator.FromResult(r);
        if (err is not null || !r.StdOut.Contains("Success", StringComparison.OrdinalIgnoreCase))
            return new StepResult(false, err?.Arabic ?? "فشل التثبيت.", err?.English ?? "Install failed.", r.Combined);
        var ok = await IsInstalledAsync(ct);
        return ok ? new StepResult(true, "تم تثبيت الواجهة والتحقق منها.", "Launcher installed and verified.")
                  : new StepResult(false, "التثبيت ما ظهر على الجهاز.", "The install did not show up on the device.");
    }

    public async Task<StepResult> PushConfigAsync(LauncherConfig cfg, CancellationToken ct = default)
    {
        Session.Require(Feature.PushLauncherTheme);
        var backup = await BackupAsync(ct);
        var c = cfg.Sanitized();
        c.StockHome ??= backup.OriginalHome;
        var r = await Session.RunAsync(AdbActions.SendLauncherConfig(Serial, c.ToBase64()), ct);
        // am broadcast prints "Broadcast completed: result=1" when our receiver accepted it.
        var ok = r.Ok && r.StdOut.Contains("result=1");
        if (ok)
        {
            backup.ConfigHistory.Add(new LauncherConfigSnapshot { Config = c });
            if (backup.ConfigHistory.Count > 30) backup.ConfigHistory.RemoveAt(0);
            backup.Save();
        }
        return ok ? new StepResult(true, "وصلت الإعدادات للواجهة.", "Settings reached the launcher.")
                  : new StepResult(false, "الواجهة ما استلمت الإعدادات. تأكد إنها مثبّتة.", "The launcher did not accept the settings. Is it installed?", r.Combined);
    }

    /// <summary>Re-sends the previous theme (the one before the latest push).</summary>
    public async Task<StepResult> RevertConfigAsync(CancellationToken ct = default)
    {
        var b = HomeBackup.Load(Session.Profile.Id);
        if (b is null || b.ConfigHistory.Count < 2)
            return new StepResult(false, "لا توجد إعدادات سابقة محفوظة.", "No earlier settings saved.");
        var prev = b.ConfigHistory[^2].Config;
        b.ConfigHistory.RemoveAt(b.ConfigHistory.Count - 1);
        b.ConfigHistory.RemoveAt(b.ConfigHistory.Count - 1);
        b.Save();
        return await PushConfigAsync(prev, ct);
    }

    public async Task<StepResult> OpenForTestAsync(CancellationToken ct = default)
    {
        var r = await Session.RunAsync(AdbActions.StartActivity(Serial, LauncherInfo.MainActivity), ct);
        var bad = r.Combined.Contains("Error", StringComparison.Ordinal) || r.Combined.Contains("Exception", StringComparison.Ordinal);
        return r.Ok && !bad
            ? new StepResult(true, "انفتحت الواجهة على التلفزيون للتجربة. الواجهة الأصلية ما زالت هي الرئيسية.", "The launcher is open on the TV for testing. The original Home is still the default.")
            : new StepResult(false, "ما گدرت أفتح الواجهة.", "Could not open the launcher.", r.Combined);
    }

    /// <summary>Makes IBRA the Home app, then checks the device really answers HOME with it.</summary>
    public async Task<StepResult> SetAsHomeAsync(CancellationToken ct = default)
    {
        if (!Session.Module.CanExecute(Feature.SetLauncherAsHome))
            return new StepResult(false, "هذا النظام لا يسمح بتغيير زر Home. افتح الواجهة يدوياً من قائمة التطبيقات.",
                "This system does not allow changing Home. Open the launcher manually from the apps list.");
        await BackupAsync(ct);
        var r = await Session.RunAsync(AdbActions.SetHomeActivity(Serial, LauncherInfo.MainActivity), ct);
        var err = AdbErrorTranslator.FromResult(r);
        var now = await Session.CurrentHomeAsync(ct);
        if (IsOurs(now))
        {
            await Session.RunAsync(AdbActions.KeyEvent(Serial, AdbActions.Key.Home), ct);
            return new StepResult(true, "صارت IBRA هي الواجهة الرئيسية وتم التحقق من زر Home.", "IBRA is now Home, verified.");
        }
        return new StepResult(false,
            "النظام ما قبل تغيير الواجهة الرئيسية" + (err is null ? "." : ": " + err.Arabic) + " تقدر تفتحها يدوياً، أو تضغط Home وتختارها إذا ظهرت قائمة الاختيار.",
            "The system did not accept the Home change" + (err is null ? "." : ": " + err.English) + " Open it manually, or press Home and pick it if a chooser appears.",
            $"resolve-activity now: {now ?? "(chooser)"}; {r.Combined}");
    }

    /// <summary>
    /// The "bring my old interface back" service: Home goes back to the saved original, verified.
    /// With <paramref name="removeLauncher"/> our launcher is uninstalled too.
    /// </summary>
    public async Task<StepResult> RestoreOriginalAsync(bool removeLauncher, CancellationToken ct = default)
    {
        var b = HomeBackup.Load(Session.Profile.Id);
        var msgs = new List<string>();
        var target = b?.OriginalHome;
        if (target is null)
        {
            // No saved default: pick a stock launcher that is still on the device.
            var cands = await Session.HomeCandidatesAsync(ct);
            target = cands.FirstOrDefault(c => !IsOurs(c) && KnownStockLaunchers.Contains(AdbParsers.PackageOf(c)))
                     ?? cands.FirstOrDefault(c => !IsOurs(c) && !NotLaunchers.Contains(AdbParsers.PackageOf(c)));
        }
        if (target is not null)
        {
            var pkg = AdbParsers.PackageOf(target);
            var st = await Session.QueryStateAsync(pkg, ct);
            if (st == Packages.PackageState.Disabled) await Session.RunAsync(AdbActions.Enable(Serial, pkg), ct);
            if (st == Packages.PackageState.UninstalledForUser) await Session.RunAsync(AdbActions.InstallExisting(Serial, pkg), ct);
            await Session.RunAsync(AdbActions.SetHomeActivity(Serial, target), ct);
        }
        if (removeLauncher && await IsInstalledAsync(ct))
        {
            var u = await Session.RunAsync(AdbActions.UninstallOwnApp(Serial, LauncherInfo.Package), ct);
            msgs.Add(u.Combined);
        }
        var now = await Session.CurrentHomeAsync(ct);
        var ok = !IsOurs(now) || (now is null && !await IsInstalledAsync(ct));
        if (target is not null && now is not null) ok = AdbParsers.PackageOf(now) == AdbParsers.PackageOf(target);
        if (ok)
        {
            await Session.RunAsync(AdbActions.KeyEvent(Serial, AdbActions.Key.Home), ct);
            if (b is not null) { b.RestoredAt = DateTimeOffset.Now; b.Save(); }
            return new StepResult(true, $"رجعت الواجهة الأصلية ({now ?? target ?? "اختيار النظام"}) وتم التحقق.",
                $"Original Home is back ({now ?? target ?? "system chooser"}), verified.");
        }
        return new StepResult(false,
            "ما تم التحقق من رجوع الواجهة الأصلية. جرّب «إزالة واجهة IBRA بالكامل» أو اضغط Home على الريموت واختر الأصلية.",
            "Could not verify the original Home. Try 'remove IBRA launcher completely', or press Home and choose the original.",
            $"now: {now ?? "(chooser)"}; target: {target ?? "-"}; {string.Join(" | ", msgs)}");
    }
}
