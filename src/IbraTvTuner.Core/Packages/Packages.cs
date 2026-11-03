using System.IO.Compression;
using System.Text.Json;
using System.Text.Json.Serialization;

namespace IbraTvTuner.Core.Packages;

public enum PackageClass { Protected, Reviewable, Unknown }
public enum PackageState { Enabled, Disabled, UninstalledForUser }

public sealed class PackageEntry
{
    public required string Name { get; init; }
    public bool IsSystem { get; init; }
    public PackageState State { get; set; }
    public PackageClass Class { get; set; }
    public string ReasonAr { get; set; } = "";
    public string ReasonEn { get; set; } = "";
    /// <summary>The recommendation text from the matched model list, if any.</summary>
    public string? Recommendation { get; set; }
    public bool HasLauncherActivity { get; set; }
    public string Kind => IsSystem ? "system" : "user";
}

// ---------- catalog (from CleanYourTV, MIT) ----------

public sealed class CatalogEntry
{
    [JsonPropertyName("p")] public string Package { get; set; } = "";
    [JsonPropertyName("why")] public string Why { get; set; } = "";
}

public sealed class CatalogModel
{
    [JsonPropertyName("id")] public string Id { get; set; } = "";
    [JsonPropertyName("tv")] public string Tv { get; set; } = "";
    [JsonPropertyName("os")] public string Os { get; set; } = "";
    [JsonPropertyName("brand")] public string Brand { get; set; } = "";
    [JsonPropertyName("fingerprint")] public string Fingerprint { get; set; } = "";
    [JsonPropertyName("disable")] public List<CatalogEntry> Disable { get; set; } = new();
    [JsonPropertyName("keep")] public List<string> Keep { get; set; } = new();
}

public sealed class Catalog
{
    [JsonPropertyName("source")] public string Source { get; set; } = "";
    [JsonPropertyName("url")] public string Url { get; set; } = "";
    [JsonPropertyName("commit")] public string Commit { get; set; } = "";
    [JsonPropertyName("models")] public List<CatalogModel> Models { get; set; } = new();

    public static Catalog Empty { get; } = new();

    public static Catalog LoadEmbedded()
    {
        var asm = typeof(Catalog).Assembly;
        using var s = asm.GetManifestResourceStream("IbraTvTuner.catalog.json.gz");
        if (s is null) return Empty;
        using var gz = new GZipStream(s, CompressionMode.Decompress);
        return JsonSerializer.Deserialize<Catalog>(gz) ?? Empty;
    }

    public CatalogModel? Get(string? id) => id is null ? null : Models.FirstOrDefault(m => m.Id == id);

    /// <summary>
    /// Ranks catalog models for a device: exact fingerprint first, then overlap of the device's own
    /// package set with each list, then brand / model words. The user always confirms the choice.
    /// </summary>
    public List<(CatalogModel Model, int Score, string Why)> Rank(DeviceInfo d, ISet<string> installed, int take = 8)
    {
        var res = new List<(CatalogModel, int, string)>();
        var words = $"{d.Model} {d.Device} {d.Brand} {d.Manufacturer}".ToLowerInvariant()
            .Split(new[] { ' ', '-', '_', '/' }, StringSplitOptions.RemoveEmptyEntries).Where(w => w.Length > 2).ToHashSet();
        foreach (var m in Models)
        {
            int score = 0; var why = new List<string>();
            if (!string.IsNullOrEmpty(m.Fingerprint) && m.Fingerprint == d.Fingerprint) { score += 1000; why.Add("fingerprint"); }
            if (m.Disable.Count > 0)
            {
                var hit = m.Disable.Count(e => installed.Contains(e.Package));
                var pct = hit * 100 / m.Disable.Count;
                score += pct; if (pct >= 60) why.Add($"{pct}% packages");
            }
            if (!string.IsNullOrEmpty(m.Brand) && (d.Brand.Equals(m.Brand, StringComparison.OrdinalIgnoreCase) || d.Manufacturer.Equals(m.Brand, StringComparison.OrdinalIgnoreCase)))
            { score += 40; why.Add("brand"); }
            var mw = $"{m.Id} {m.Tv}".ToLowerInvariant().Split(new[] { ' ', '-', '_', '/', '(', ')', ',' }, StringSplitOptions.RemoveEmptyEntries);
            var common = mw.Count(words.Contains);
            if (common > 0) { score += common * 15; why.Add("name"); }
            var osSdkMatch = m.Os.Contains(d.OsVersion.Split('.')[0] + "", StringComparison.Ordinal) && d.OsVersion.Length > 0;
            if (osSdkMatch) score += 10;
            if (score > 30) res.Add((m, score, string.Join(", ", why)));
        }
        return res.OrderByDescending(x => x.Item2).Take(take).ToList();
    }
}

// ---------- classification ----------

public sealed record ProtectionContext(
    string? CurrentHomePackage,
    string? DefaultImePackage,
    IReadOnlySet<string> ModelKeep);

public static class PackageClassifier
{
    /// <summary>
    /// Never offered for bulk changes: the device stops booting, loses its remote, its network or Home.
    /// Partly informed by CleanYourTV's NOT_LAUNCHERS / setupwraith notes (MIT).
    /// </summary>
    public static readonly IReadOnlySet<string> CoreProtected = new HashSet<string>(StringComparer.Ordinal)
    {
        "android", "com.android.systemui", "com.android.settings", "com.android.tv.settings",
        "com.android.providers.settings", "com.android.providers.media", "com.android.providers.downloads",
        "com.android.providers.tv", "com.android.providers.contacts", "com.android.providers.telephony",
        "com.android.packageinstaller", "com.google.android.packageinstaller", "com.android.permissioncontroller",
        "com.google.android.permissioncontroller", "com.android.shell", "com.android.externalstorage",
        "com.android.inputdevices", "com.android.bluetooth", "com.android.networkstack", "com.google.android.networkstack",
        "com.android.wifi.resources", "com.android.server.telecom", "com.android.phone", "com.android.location.fused",
        "com.android.keychain", "com.android.certinstaller", "com.android.webview", "com.google.android.webview",
        "com.android.provision", "com.google.android.tungsten.setupwraith", "com.android.tv.frameworkpackagestubs",
        "com.android.localtransport", "com.android.sharedstoragebackup", "com.android.defcontainer",
        "com.android.dynsystem", "com.android.se", "com.android.cts.priv.ctsshim",
        "com.google.android.gms", "com.google.android.gsf", "com.android.vending",
        "com.google.android.tv.remote.service", "com.google.android.inputmethod.latin",
        "com.google.android.leanbacklauncher.partnercustomizer",
        Adb.LauncherInfo.Package,
    };

    public static PackageClass Classify(PackageEntry p, ProtectionContext ctx, CatalogModel? model, out string ar, out string en)
    {
        if (CoreProtected.Contains(p.Name) || p.Name.StartsWith("com.android.providers.", StringComparison.Ordinal)
            || p.Name.StartsWith("android.", StringComparison.Ordinal) && p.Name.Contains("overlay"))
        {
            ar = "مكوّن أساسي لتشغيل الجهاز."; en = "Core component the device needs to run.";
            return PackageClass.Protected;
        }
        if (p.Name == ctx.CurrentHomePackage)
        {
            ar = "الواجهة الرئيسية الحالية. لا يمكن تعطيلها قبل تعيين واجهة أخرى."; en = "Current Home app. Cannot be disabled before another Home is set.";
            return PackageClass.Protected;
        }
        if (p.Name == ctx.DefaultImePackage)
        {
            ar = "لوحة المفاتيح الحالية."; en = "Current keyboard.";
            return PackageClass.Protected;
        }
        if (ctx.ModelKeep.Contains(p.Name))
        {
            ar = "قائمة الموديل تقول اتركه (ريموت، HDMI، موالف، تحديثات…)."; en = "The model list says keep it (remote, HDMI, tuner, updates…).";
            return PackageClass.Protected;
        }
        var rec = model?.Disable.FirstOrDefault(e => e.Package == p.Name);
        if (rec is not null)
        {
            p.Recommendation = rec.Why;
            ar = "مقترح للمراجعة حسب قائمة الموديل" + (string.IsNullOrEmpty(rec.Why) ? "." : ": " + rec.Why);
            en = "Suggested for review by the model list" + (string.IsNullOrEmpty(rec.Why) ? "." : ": " + rec.Why);
            return PackageClass.Reviewable;
        }
        if (!p.IsSystem)
        {
            ar = "تطبيق مثبّت من المستخدم أو المصنع كتحديث."; en = "User-installed app.";
            return PackageClass.Reviewable;
        }
        ar = "غير معروف: لا توجد معلومات عنه لهذا الموديل. اتركه إلا إذا تعرف وظيفته."; en = "Unknown for this model. Leave it unless you know what it does.";
        return PackageClass.Unknown;
    }
}
