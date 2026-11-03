using IbraTvTuner.Core.Adb;

namespace IbraTvTuner.Core.Tests;

/// <summary>
/// An in-memory Android TV that answers the adb commands this app sends, the way a real device does.
/// It records every invocation so tests can assert nothing unexpected was run.
/// </summary>
public sealed class FakeTv : IAdbTransport
{
    public enum S { Enabled, Disabled, Uninstalled }

    public string Serial = "192.168.1.50:5555";
    public bool Authorized = true;
    public bool Reachable = true;
    public bool AllowSetHome = true;
    public bool RefuseDisable;
    public string Model = "GENERAL BRAVIA";
    public int Sdk = 28;
    public string Fingerprint = "GENERAL/CV920/cv920:9/PPR1/1:user/release-keys";
    public string? Home = "com.google.android.tvlauncher/.MainActivity";
    public string? LastBroadcast;
    public readonly Dictionary<string, (S State, bool System)> Pkgs = new()
    {
        ["android"] = (S.Enabled, true),
        ["com.android.systemui"] = (S.Enabled, true),
        ["com.android.tv.settings"] = (S.Enabled, true),
        ["com.google.android.tvlauncher"] = (S.Enabled, true),
        ["com.google.android.inputmethod.latin"] = (S.Enabled, true),
        ["com.google.android.tvrecommendations"] = (S.Enabled, true),
        ["com.google.android.feedback"] = (S.Enabled, true),
        ["com.vendor.mystery"] = (S.Enabled, true),
        ["com.google.android.youtube.tv"] = (S.Enabled, false),
    };
    public readonly List<IReadOnlyList<string>> Calls = new();

    private static AdbResult Ok(string s = "") => new(0, s, "", false);
    private static AdbResult Fail(string err, int code = 1) => new(code, "", err, false);

    public Task<AdbResult> RunAsync(AdbCommand command, CancellationToken ct = default)
    {
        var a = command.Args;
        Calls.Add(a);
        return Task.FromResult(Handle(a));
    }

    private AdbResult Handle(IReadOnlyList<string> a)
    {
        if (a[0] == "connect")
            return Reachable ? Ok($"connected to {a[1]}") : Ok($"cannot connect to {a[1]}: No route to host (10065)");
        if (a[0] == "pair") return a[2] == "123456" ? Ok("Successfully paired to " + a[1]) : Fail("error: protocol fault (couldn't read status message): Wrong password");
        if (a[0] == "devices") return Ok($"List of devices attached\n{Serial}\t{(Authorized ? "device" : "unauthorized")} product:x model:{Model.Replace(' ', '_')} device:cv920\n");
        if (a[0] == "mdns") return Ok("adb-123\t_adb-tls-pairing._tcp\t192.168.1.50:41234\nadb-123\t_adb-tls-connect._tcp\t192.168.1.50:37555\n");
        if (a[0] != "-s" || a[1] != Serial) return Fail($"error: device '{a.ElementAtOrDefault(1)}' not found");
        if (!Authorized) return Fail("error: device unauthorized.\nThis adb server's $ADB_VENDOR_KEYS is not set");
        if (a[2] == "get-state") return Ok("device\n");
        if (a[2] == "install")
        {
            var name = Path.GetFileNameWithoutExtension(a[^1]);
            var pkg = name.Contains("testapp") ? LauncherInfo.TestAppPackage : LauncherInfo.Package;
            Pkgs[pkg] = (S.Enabled, false);
            return Ok("Performing Streamed Install\nSuccess\n");
        }
        if (a[2] == "uninstall") { Pkgs.Remove(a[3]); return Ok("Success\n"); }
        if (a[2] != "shell") return Fail("unknown");
        var sh = a.Skip(3).ToList();
        return Shell(sh);
    }

    private AdbResult Shell(List<string> sh)
    {
        string J(int i) => string.Join(' ', sh.Skip(i));
        switch (sh[0])
        {
            case "getprop" when sh.Count == 1:
                return Ok($"[ro.product.model]: [{Model}]\n[ro.product.manufacturer]: [GENERAL]\n[ro.product.brand]: [GENERAL]\n" +
                          $"[ro.product.device]: [cv920]\n[ro.build.version.release]: [9]\n[ro.build.version.sdk]: [{Sdk}]\n" +
                          $"[ro.build.fingerprint]: [{Fingerprint}]\n[ro.build.characteristics]: [tv]\n");
            case "pm" when sh[1] == "list" && sh[2] == "features":
                return Ok("feature:android.software.leanback\nfeature:android.hardware.wifi\n");
            case "pm" when sh[1] == "list" && sh[2] == "packages":
            {
                var flags = sh.Skip(3).Where(x => x.StartsWith('-')).ToList();
                var filter = sh.Skip(3).FirstOrDefault(x => !x.StartsWith('-'));
                IEnumerable<KeyValuePair<string, (S State, bool System)>> q = Pkgs;
                if (!flags.Contains("-u")) q = q.Where(p => p.Value.State != S.Uninstalled);
                if (flags.Contains("-s")) q = q.Where(p => p.Value.System);
                if (flags.Contains("-3")) q = q.Where(p => !p.Value.System);
                if (flags.Contains("-d")) q = q.Where(p => p.Value.State == S.Disabled);
                if (flags.Contains("-e")) q = q.Where(p => p.Value.State == S.Enabled);
                if (filter is not null) q = q.Where(p => p.Key.Contains(filter));
                return Ok(string.Concat(q.Select(p => "package:" + p.Key + "\n")));
            }
            case "pm" when sh[1] == "disable-user":
            {
                var pkg = sh[^1];
                if (!Pkgs.ContainsKey(pkg)) return Ok($"Error: Unknown package: {pkg}");
                if (RefuseDisable) return Ok("Error: java.lang.SecurityException: Cannot disable a protected package: " + pkg);
                Pkgs[pkg] = (S.Disabled, Pkgs[pkg].System);
                return Ok($"Package {pkg} new state: disabled-user");
            }
            case "pm" when sh[1] == "enable":
            {
                var pkg = sh[^1];
                Pkgs[pkg] = (S.Enabled, Pkgs[pkg].System);
                return Ok($"Package {pkg} new state: enabled");
            }
            case "pm" when sh[1] == "uninstall":
            {
                var pkg = sh[^1];
                Pkgs[pkg] = (S.Uninstalled, Pkgs[pkg].System);
                return Ok("Success");
            }
            case "cmd" when sh[1] == "package" && sh[2] == "install-existing":
            {
                var pkg = sh[^1];
                Pkgs[pkg] = (S.Enabled, Pkgs[pkg].System);
                return Ok($"Package {pkg} installed for user: 0");
            }
            case "cmd" when sh[2] == "resolve-activity":
                return Ok(Home is null ? "priority=0 preferredOrder=0\nandroid/com.android.internal.app.ResolverActivity\n"
                                       : "priority=0 preferredOrder=0 match=0x108000 isDefault=true\n" + Home + "\n");
            case "cmd" when sh[2] == "query-activities" && J(3).Contains("category.HOME"):
            {
                var list = new List<string>();
                if (Pkgs.TryGetValue("com.google.android.tvlauncher", out var t) && t.State == S.Enabled) list.Add("com.google.android.tvlauncher/.MainActivity");
                if (Pkgs.TryGetValue("com.android.tv.settings", out var st) && st.State == S.Enabled) list.Add("com.android.tv.settings/.FallbackHome");
                if (Pkgs.TryGetValue(LauncherInfo.Package, out var o) && o.State == S.Enabled) list.Add(LauncherInfo.MainActivity);
                return Ok(string.Join("\n", list.Select(c => "priority=0\n" + c)));
            }
            case "cmd" when sh[2] == "query-activities":
                return Ok("priority=0\ncom.google.android.youtube.tv/com.google.android.apps.youtube.tv.activity.ShellActivity\n");
            case "cmd" when sh[2] == "set-home-activity":
            {
                if (!AllowSetHome) return Ok("Error: unknown command 'set-home-activity'");
                var comp = sh[^1];
                if (!Pkgs.TryGetValue(comp.Split('/')[0], out var p) || p.State != S.Enabled) return Ok("Error: component not found");
                Home = comp;
                return Ok("Success");
            }
            case "settings": return Ok("com.google.android.inputmethod.latin/com.android.inputmethod.latin.LatinIME\n");
            case "am" when sh[1] == "broadcast":
                if (!Pkgs.ContainsKey(LauncherInfo.Package)) return Ok("Broadcasting: Intent\nBroadcast completed: result=0");
                LastBroadcast = sh[^1];
                return Ok("Broadcasting: Intent { act=tv.ibra.tuner.APPLY_CONFIG }\nBroadcast completed: result=1");
            case "am" when sh[1] == "start":
                return Ok("Starting: Intent\nStatus: ok\nActivity: x\nThisTime: 812\nTotalTime: 812\nWaitTime: 840\nComplete\n");
            case "am" when sh[1] == "force-stop": return Ok();
            case "input": return Ok();
            case "dumpsys": return Ok("    versionName=1.0\n");
        }
        return Fail("/system/bin/sh: unknown");
    }
}
