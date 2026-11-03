using IbraTvTuner.Core.Adb;
using IbraTvTuner.Core.Packages;
using IbraTvTuner.Core.Platforms;
using IbraTvTuner.Core.Storage;

namespace IbraTvTuner.Core;

public sealed class AdbOperationException(AdbError error) : Exception(error.English)
{
    public AdbError Error { get; } = error;
}

/// <summary>All operations against one device. Read-only methods never change anything on the device.</summary>
public sealed class DeviceSession(IAdbTransport adb, DeviceProfile profile)
{
    public IAdbTransport Adb { get; } = adb;
    public DeviceProfile Profile { get; } = profile;
    public string Serial => Profile.Serial;
    public IPlatformModule Module => PlatformRegistry.Get(Profile.Platform == PlatformId.Unknown ? PlatformId.AndroidTv : Profile.Platform);

    public async Task<AdbResult> RunAsync(AdbCommand cmd, CancellationToken ct = default)
    {
        var r = await Adb.RunAsync(cmd, ct).ConfigureAwait(false);
        Log.Write($"[{Profile.Title}] {cmd.Display} -> {(r.Ok ? "ok" : "exit " + r.ExitCode)}");
        return r;
    }

    /// <summary>Runs and throws a translated error when adb reports a problem.</summary>
    public async Task<string> RunCheckedAsync(AdbCommand cmd, CancellationToken ct = default)
    {
        var r = await RunAsync(cmd, ct).ConfigureAwait(false);
        // pm prints "Error:" / "Exception" with exit code 0 on old Android, so inspect the text too.
        var err = AdbErrorTranslator.FromResult(r);
        if (err is not null) throw new AdbOperationException(err);
        return r.StdOut;
    }

    public void Require(Feature f)
    {
        if (!Module.CanExecute(f)) throw new FeatureBlockedException(Module.Id, f);
    }

    // ---------------- connection ----------------

    public static async Task<(bool Ok, AdbError? Error)> PairAsync(IAdbTransport adb, string host, int pairingPort, string code, CancellationToken ct = default)
    {
        var cmd = AdbActions.Pair(host, pairingPort, code);
        var r = await adb.RunAsync(cmd, ct).ConfigureAwait(false);
        Log.Write($"{cmd.Display} -> {(r.Ok ? "ok" : "exit " + r.ExitCode)}");
        var ok = r.Ok && r.StdOut.Contains("Successfully paired", StringComparison.OrdinalIgnoreCase);
        if (ok) return (true, null);
        return (false, AdbErrorTranslator.FromResult(r) ?? new AdbError(AdbErrorKind.PairingFailed,
            "فشل الاقتران. تأكد من منفذ الاقتران والرمز.", "Pairing failed. Check the pairing port and code.", r.Combined));
    }

    public async Task<(bool Ok, AdbError? Error)> ConnectAsync(CancellationToken ct = default)
    {
        if (Profile.Connection == ConnectionKind.Network)
        {
            var r = await RunAsync(AdbActions.Connect(Profile.Host, Profile.Port), ct).ConfigureAwait(false);
            var text = r.Combined.ToLowerInvariant();
            // adb connect exits 0 even on failure; the text tells the truth.
            if (!(text.Contains("connected to") && !text.Contains("cannot") && !text.Contains("failed")))
                return (false, AdbErrorTranslator.FromResult(r with { ExitCode = r.ExitCode == 0 ? 1 : r.ExitCode }));
        }
        var st = await RunAsync(AdbActions.GetState(Serial), ct).ConfigureAwait(false);
        if (st.Ok && st.StdOut.Trim() == "device") return (true, null);
        if (st.Combined.Contains("unauthorized", StringComparison.OrdinalIgnoreCase))
            return (false, AdbErrorTranslator.FromResult(st with { ExitCode = 1 }));
        // Some adb builds answer get-state with an error while still authorising: check devices -l.
        var dl = await RunAsync(AdbActions.Devices(), ct).ConfigureAwait(false);
        var line = AdbParsers.ParseDevices(dl.StdOut).FirstOrDefault(d => d.Serial == Serial);
        if (line?.State == "device") return (true, null);
        var fake = new AdbResult(1, "", line?.State ?? st.Combined, false);
        return (false, AdbErrorTranslator.FromResult(fake));
    }

    // ---------------- read-only ----------------

    public async Task<DeviceInfo> ReadInfoAsync(CancellationToken ct = default)
    {
        var props = AdbParsers.ParseGetProp(await RunCheckedAsync(AdbActions.GetAllProps(Serial), ct));
        var feats = DeviceDetector.ParseFeatures(await RunCheckedAsync(AdbActions.ListFeatures(Serial), ct));
        var pkgs = AdbParsers.ParsePackages(await RunCheckedAsync(AdbActions.ListPackages(Serial, AdbActions.PackageFilter.All), ct));
        var info = DeviceDetector.FromProps(props, feats, pkgs);
        Profile.Model = info.Model;
        Profile.Manufacturer = info.Manufacturer;
        Profile.OsVersion = info.OsVersion;
        Profile.Sdk = info.Sdk;
        Profile.Fingerprint = info.Fingerprint;
        Profile.Platform = info.Platform;
        Profile.LastSeen = DateTimeOffset.Now;
        return info;
    }

    public async Task<string?> CurrentHomeAsync(CancellationToken ct = default)
    {
        var r = await RunAsync(AdbActions.ResolveHome(Serial), ct).ConfigureAwait(false);
        return r.Ok ? AdbParsers.ParseSingleComponent(r.StdOut) : null;
    }

    public async Task<List<string>> HomeCandidatesAsync(CancellationToken ct = default)
    {
        var r = await RunAsync(AdbActions.QueryHomeActivities(Serial), ct).ConfigureAwait(false);
        return r.Ok ? AdbParsers.ParseComponents(r.StdOut) : new List<string>();
    }

    public async Task<string?> DefaultImePackageAsync(CancellationToken ct = default)
    {
        var r = await RunAsync(AdbActions.DefaultInputMethod(Serial), ct).ConfigureAwait(false);
        var c = r.StdOut.Trim();
        return c.Contains('/') ? c.Split('/')[0] : null;
    }

    public sealed record PackageSets(HashSet<string> Installed, HashSet<string> System, HashSet<string> Disabled, HashSet<string> WithUninstalled);

    public async Task<PackageSets> ReadPackageSetsAsync(CancellationToken ct = default)
    {
        async Task<HashSet<string>> L(AdbActions.PackageFilter f) =>
            AdbParsers.ParsePackages(await RunCheckedAsync(AdbActions.ListPackages(Serial, f), ct));
        var all = await L(AdbActions.PackageFilter.All);
        var sys = await L(AdbActions.PackageFilter.System);
        var dis = await L(AdbActions.PackageFilter.Disabled);
        HashSet<string> withU;
        try { withU = await L(AdbActions.PackageFilter.IncludingUninstalled); }
        catch (AdbOperationException) { withU = new HashSet<string>(all); }
        return new PackageSets(all, sys, dis, withU);
    }

    public static PackageState StateOf(string pkg, PackageSets s) =>
        !s.Installed.Contains(pkg) ? PackageState.UninstalledForUser
        : s.Disabled.Contains(pkg) ? PackageState.Disabled : PackageState.Enabled;

    /// <summary>Re-queries one package from the device (used to verify every change).</summary>
    public async Task<PackageState?> QueryStateAsync(string pkg, CancellationToken ct = default)
    {
        bool Has(string output) => AdbParsers.ParsePackages(output).Contains(pkg);
        var installed = await RunCheckedAsync(AdbActions.ListPackages(Serial, AdbActions.PackageFilter.All, pkg), ct);
        if (!Has(installed))
        {
            var u = await RunAsync(AdbActions.ListPackages(Serial, AdbActions.PackageFilter.IncludingUninstalled, pkg), ct);
            return Has(u.StdOut) ? PackageState.UninstalledForUser : null;
        }
        var disabled = await RunCheckedAsync(AdbActions.ListPackages(Serial, AdbActions.PackageFilter.Disabled, pkg), ct);
        return Has(disabled) ? PackageState.Disabled : PackageState.Enabled;
    }

    /// <summary>Full inventory with classification. Read only.</summary>
    public async Task<List<PackageEntry>> InventoryAsync(Catalog catalog, CancellationToken ct = default)
    {
        Require(Feature.InventoryApps);
        var sets = await ReadPackageSetsAsync(ct);
        var home = await CurrentHomeAsync(ct);
        var ime = await DefaultImePackageAsync(ct);
        var launchable = new HashSet<string>();
        foreach (var lb in new[] { true, false })
        {
            var r = await RunAsync(AdbActions.QueryLauncherApps(Serial, lb), ct);
            if (r.Ok) foreach (var c in AdbParsers.ParseComponents(r.StdOut)) launchable.Add(AdbParsers.PackageOf(c));
        }
        var model = catalog.Get(Profile.CatalogModelId);
        var ctx = new ProtectionContext(home is null ? null : AdbParsers.PackageOf(home), ime,
            (model?.Keep ?? new List<string>()).ToHashSet());
        var list = new List<PackageEntry>();
        foreach (var name in sets.WithUninstalled.OrderBy(x => x, StringComparer.Ordinal))
        {
            var e = new PackageEntry
            {
                Name = name,
                IsSystem = sets.System.Contains(name) || !sets.Installed.Contains(name),
                State = StateOf(name, sets),
                HasLauncherActivity = launchable.Contains(name),
            };
            e.Class = PackageClassifier.Classify(e, ctx, model, out var ar, out var en);
            e.ReasonAr = ar; e.ReasonEn = en;
            list.Add(e);
        }
        return list;
    }
}
