namespace IbraTvTuner.Core.Adb;

public sealed record AdbDeviceLine(string Serial, string State, IReadOnlyDictionary<string, string> Info);
public sealed record MdnsService(string Name, string Type, string Host, int Port)
{
    public bool IsPairing => Type.Contains("pairing", StringComparison.OrdinalIgnoreCase);
}

public static class AdbParsers
{
    /// <summary>Parses `adb devices -l`.</summary>
    public static List<AdbDeviceLine> ParseDevices(string output)
    {
        var list = new List<AdbDeviceLine>();
        foreach (var raw in Lines(output))
        {
            if (raw.StartsWith("List of devices") || raw.StartsWith('*')) continue;
            var parts = raw.Split((char[]?)null, StringSplitOptions.RemoveEmptyEntries);
            if (parts.Length < 2) continue;
            var info = new Dictionary<string, string>();
            foreach (var p in parts.Skip(2))
            {
                var i = p.IndexOf(':');
                if (i > 0) info[p[..i]] = p[(i + 1)..];
            }
            list.Add(new AdbDeviceLine(parts[0], parts[1], info));
        }
        return list;
    }

    /// <summary>Parses `pm list packages` → package names.</summary>
    public static HashSet<string> ParsePackages(string output)
    {
        var set = new HashSet<string>(StringComparer.Ordinal);
        foreach (var l in Lines(output))
        {
            if (!l.StartsWith("package:")) continue;
            var name = l["package:".Length..].Trim();
            // `-f` form is package:/path/base.apk=name
            var eq = name.LastIndexOf('=');
            if (eq >= 0) name = name[(eq + 1)..];
            if (Validators.IsPackage(name)) set.Add(name);
        }
        return set;
    }

    /// <summary>Parses `getprop` (all) → dictionary.</summary>
    public static Dictionary<string, string> ParseGetProp(string output)
    {
        var d = new Dictionary<string, string>(StringComparer.Ordinal);
        foreach (var l in Lines(output))
        {
            // [ro.product.model]: [Foo]
            if (!l.StartsWith('[')) continue;
            var close = l.IndexOf("]: [", StringComparison.Ordinal);
            if (close < 0 || !l.EndsWith(']')) continue;
            d[l[1..close]] = l[(close + 4)..^1];
        }
        return d;
    }

    /// <summary>Parses the `--brief` output of resolve-activity / query-activities → components.</summary>
    public static List<string> ParseComponents(string output)
    {
        var list = new List<string>();
        foreach (var l in Lines(output))
        {
            var t = l.Trim();
            if (t.StartsWith("priority=") || t.StartsWith("No activity")) continue;
            if (!t.Contains('/')) continue;
            // Expand ".Cls" shorthand to full form for validation stability
            if (Validators.IsComponent(t)) list.Add(t);
        }
        return list;
    }

    public static string? ParseSingleComponent(string output) => ParseComponents(output).FirstOrDefault();

    public static string PackageOf(string component) => component.Split('/')[0];

    /// <summary>Parses `adb mdns services` lines: name \t _adb-tls-connect._tcp \t 192.168.1.5:37000</summary>
    public static List<MdnsService> ParseMdns(string output)
    {
        var list = new List<MdnsService>();
        foreach (var l in Lines(output))
        {
            var parts = l.Split(new[] { '\t', ' ' }, StringSplitOptions.RemoveEmptyEntries);
            if (parts.Length < 3) continue;
            var type = parts.FirstOrDefault(p => p.StartsWith("_adb", StringComparison.Ordinal));
            var ep = parts.Last();
            if (type is null) continue;
            var colon = ep.LastIndexOf(':');
            if (colon <= 0 || !int.TryParse(ep[(colon + 1)..], out var port)) continue;
            var host = ep[..colon].Trim('[', ']');
            if (!Validators.IsHost(host) || !Validators.IsPort(port)) continue;
            list.Add(new MdnsService(parts[0], type, host, port));
        }
        return list;
    }

    /// <summary>`am start -W` → TotalTime in ms (or null).</summary>
    public static int? ParseStartTotalTime(string output)
    {
        foreach (var l in Lines(output))
        {
            var t = l.Trim();
            if (t.StartsWith("TotalTime:") && int.TryParse(t["TotalTime:".Length..].Trim(), out var ms)) return ms;
        }
        foreach (var l in Lines(output))
        {
            var t = l.Trim();
            if (t.StartsWith("WaitTime:") && int.TryParse(t["WaitTime:".Length..].Trim(), out var ms)) return ms;
        }
        return null;
    }

    /// <summary>From `dumpsys package X`: versionName.</summary>
    public static string? ParseVersionName(string output)
    {
        foreach (var l in Lines(output))
        {
            var t = l.Trim();
            if (t.StartsWith("versionName=")) return t["versionName=".Length..];
        }
        return null;
    }

    private static IEnumerable<string> Lines(string s) =>
        s.Replace("\r", "").Split('\n', StringSplitOptions.RemoveEmptyEntries).Select(x => x.TrimEnd());
}
