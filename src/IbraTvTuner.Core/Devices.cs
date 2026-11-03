using System.Text.Json.Serialization;
using IbraTvTuner.Core.Adb;
using IbraTvTuner.Core.Platforms;

namespace IbraTvTuner.Core;

public enum DeviceRole { Screen, Box, Unknown }
public enum ConnectionKind { Network, Usb }

/// <summary>A device the user added. A TV panel and the box plugged into it are two separate profiles.</summary>
public sealed class DeviceProfile
{
    public string Id { get; set; } = Guid.NewGuid().ToString("N")[..12];
    public string Nickname { get; set; } = "";
    public DeviceRole Role { get; set; } = DeviceRole.Unknown;
    public ConnectionKind Connection { get; set; } = ConnectionKind.Network;
    public string Host { get; set; } = "";
    public int Port { get; set; } = 5555;
    public string UsbSerial { get; set; } = "";
    public PlatformId Platform { get; set; } = PlatformId.Unknown;
    public string Model { get; set; } = "";
    public string Manufacturer { get; set; } = "";
    public string OsVersion { get; set; } = "";
    public int Sdk { get; set; }
    public string Fingerprint { get; set; } = "";
    public string? CatalogModelId { get; set; }
    public DateTimeOffset? LastSeen { get; set; }

    [JsonIgnore]
    public string Serial => Connection == ConnectionKind.Usb ? UsbSerial : AdbActions.Endpoint(Host, Port);

    [JsonIgnore]
    public string Title => string.IsNullOrWhiteSpace(Nickname) ? (string.IsNullOrEmpty(Model) ? Serial : Model) : Nickname;
}

public sealed record DeviceInfo(
    string Model, string Manufacturer, string Brand, string Device, string OsVersion, int Sdk,
    string Fingerprint, bool IsTv, bool IsGoogleTv, bool IsFireTv, string? FireOsVersion, PlatformId Platform);

public static class DeviceDetector
{
    public static DeviceInfo FromProps(IReadOnlyDictionary<string, string> p, ISet<string> features, ISet<string> packages)
    {
        string G(string k) => p.TryGetValue(k, out var v) ? v : "";
        int.TryParse(G("ro.build.version.sdk"), out var sdk);
        var manu = G("ro.product.manufacturer");
        var isFire = manu.Equals("Amazon", StringComparison.OrdinalIgnoreCase) || packages.Contains("com.amazon.tv.launcher");
        var isGoogleTv = packages.Contains("com.google.android.apps.tv.launcherx");
        var isTv = features.Contains("android.software.leanback") || features.Contains("android.hardware.type.television")
                   || G("ro.build.characteristics").Contains("tv");
        var fireOs = G("ro.build.mktg.fireos");
        var platform = isFire ? PlatformId.FireTv : isGoogleTv ? PlatformId.GoogleTv : PlatformId.AndroidTv;
        return new DeviceInfo(G("ro.product.model"), manu, G("ro.product.brand"), G("ro.product.device"),
            G("ro.build.version.release"), sdk, G("ro.build.fingerprint"), isTv, isGoogleTv, isFire,
            string.IsNullOrEmpty(fireOs) ? null : fireOs, platform);
    }

    public static HashSet<string> ParseFeatures(string output) =>
        output.Replace("\r", "").Split('\n')
            .Select(l => l.Trim())
            .Where(l => l.StartsWith("feature:"))
            .Select(l => l["feature:".Length..].Split('=')[0])
            .ToHashSet();
}
