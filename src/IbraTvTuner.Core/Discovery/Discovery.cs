using System.Net;
using System.Net.NetworkInformation;
using System.Net.Sockets;
using IbraTvTuner.Core.Adb;

namespace IbraTvTuner.Core.Discovery;

public sealed record FoundDevice(string Host, int Port, string Source, bool IsPairingService, string Name);

/// <summary>Read-only LAN discovery: adb's mDNS list plus a TCP probe of port 5555 on local /24 subnets.</summary>
public static class LanDiscovery
{
    public static async Task<List<FoundDevice>> MdnsAsync(IAdbTransport adb, CancellationToken ct = default)
    {
        var r = await adb.RunAsync(AdbActions.MdnsServices(), ct);
        if (!r.Ok) return new();
        return AdbParsers.ParseMdns(r.StdOut)
            .Select(s => new FoundDevice(s.Host, s.Port, "mDNS", s.IsPairing, s.Name)).ToList();
    }

    /// <summary>Local IPv4 /24 networks of up, non-loopback interfaces (Ethernet and Wi-Fi alike).</summary>
    public static List<(IPAddress Local, string Prefix)> LocalSubnets()
    {
        var list = new List<(IPAddress, string)>();
        foreach (var ni in NetworkInterface.GetAllNetworkInterfaces())
        {
            if (ni.OperationalStatus != OperationalStatus.Up || ni.NetworkInterfaceType == NetworkInterfaceType.Loopback) continue;
            foreach (var ua in ni.GetIPProperties().UnicastAddresses)
            {
                if (ua.Address.AddressFamily != AddressFamily.InterNetwork) continue;
                var b = ua.Address.GetAddressBytes();
                if (b[0] == 169 && b[1] == 254) continue; // link-local
                if (!IsPrivate(b)) continue;
                list.Add((ua.Address, $"{b[0]}.{b[1]}.{b[2]}."));
            }
        }
        return list.DistinctBy(x => x.Item2).ToList();
    }

    public static bool IsPrivate(byte[] b) =>
        b[0] == 10 || (b[0] == 172 && b[1] >= 16 && b[1] <= 31) || (b[0] == 192 && b[1] == 168);

    public static async Task<List<FoundDevice>> ScanPortAsync(int port = 5555, int timeoutMs = 350, IProgress<int>? progress = null, CancellationToken ct = default)
    {
        var found = new System.Collections.Concurrent.ConcurrentBag<FoundDevice>();
        var subnets = LocalSubnets();
        var targets = subnets.SelectMany(s => Enumerable.Range(1, 254).Select(i => s.Prefix + i))
            .Where(h => subnets.All(s => s.Local.ToString() != h)).ToList();
        int done = 0;
        using var gate = new SemaphoreSlim(64);
        var tasks = targets.Select(async host =>
        {
            await gate.WaitAsync(ct);
            try
            {
                using var c = new TcpClient();
                using var cts = CancellationTokenSource.CreateLinkedTokenSource(ct);
                cts.CancelAfter(timeoutMs);
                try
                {
                    await c.ConnectAsync(host, port, cts.Token);
                    found.Add(new FoundDevice(host, port, $"TCP {port}", false, host));
                }
                catch { /* closed or filtered */ }
            }
            finally
            {
                gate.Release();
                progress?.Report(Interlocked.Increment(ref done) * 100 / Math.Max(1, targets.Count));
            }
        });
        await Task.WhenAll(tasks);
        return found.OrderBy(f => Version.TryParse(f.Host, out var v) ? v : new Version()).ToList();
    }
}

public static class AdbLocator
{
    /// <summary>Common places adb.exe lives on Windows. Nothing is downloaded by this method.</summary>
    public static IEnumerable<string> Candidates()
    {
        var local = Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData);
        var exe = OperatingSystem.IsWindows() ? "adb.exe" : "adb";
        yield return Path.Combine(Storage.AppPaths.ToolsDir, "platform-tools", exe);
        yield return Path.Combine(AppContext.BaseDirectory, "platform-tools", exe);
        yield return Path.Combine(local, "Android", "Sdk", "platform-tools", exe);
        var sdk = Environment.GetEnvironmentVariable("ANDROID_HOME") ?? Environment.GetEnvironmentVariable("ANDROID_SDK_ROOT");
        if (!string.IsNullOrEmpty(sdk)) yield return Path.Combine(sdk, "platform-tools", exe);
        foreach (var dir in (Environment.GetEnvironmentVariable("PATH") ?? "").Split(Path.PathSeparator))
            if (!string.IsNullOrWhiteSpace(dir)) yield return Path.Combine(dir.Trim('"'), exe);
        yield return @"C:\platform-tools\adb.exe";
        yield return @"C:\adb\adb.exe";
    }

    public static string? Find(string? configured)
    {
        if (!string.IsNullOrEmpty(configured) && File.Exists(configured)) return configured;
        return Candidates().FirstOrDefault(File.Exists);
    }

    /// <summary>Google's official Platform-Tools for Windows. Only fetched after the user accepts Google's terms.</summary>
    public const string OfficialWindowsZip = "https://dl.google.com/android/repository/platform-tools-latest-windows.zip";
    public const string OfficialPage = "https://developer.android.com/tools/releases/platform-tools";
}
