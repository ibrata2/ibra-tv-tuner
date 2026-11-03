using IbraTvTuner.Core.Adb;
using IbraTvTuner.Core.Storage;

namespace IbraTvTuner.Core;

public sealed class Measurement
{
    public DateTimeOffset At { get; set; } = DateTimeOffset.Now;
    public string DeviceId { get; set; } = "";
    public string What { get; set; } = "";
    public string Method { get; set; } = "";
    public List<int> RunsMs { get; set; } = new();
    public int? MedianMs => RunsMs.Count == 0 ? null : RunsMs.OrderBy(x => x).ElementAt(RunsMs.Count / 2);
    public string Label { get; set; } = "";
}

/// <summary>
/// Practical before/after timing using `am start -W` (cold start after force-stop).
/// Reports what was measured and how; no speed-up is ever claimed.
/// </summary>
public sealed class Benchmark(DeviceSession s)
{
    public async Task<Measurement> HomeColdStartAsync(int runs = 3, string label = "", CancellationToken ct = default)
    {
        var m = new Measurement { DeviceId = s.Profile.Id, What = "Home", Label = label,
            Method = "am force-stop <home pkg>; am start -W -c HOME → TotalTime" };
        for (int i = 0; i < runs; i++)
        {
            var home = await s.CurrentHomeAsync(ct);
            if (home is not null) await s.RunAsync(AdbActions.ForceStop(s.Serial, AdbParsers.PackageOf(home)), ct);
            var r = await s.RunAsync(AdbActions.StartHome(s.Serial), ct);
            var ms = AdbParsers.ParseStartTotalTime(r.StdOut);
            if (ms is not null) m.RunsMs.Add(ms.Value);
        }
        Save(m);
        return m;
    }

    public async Task<Measurement> AppColdStartAsync(string component, int runs = 3, string label = "", CancellationToken ct = default)
    {
        var pkg = AdbParsers.PackageOf(Validators.RequireComponent(component));
        var m = new Measurement { DeviceId = s.Profile.Id, What = component, Label = label,
            Method = "am force-stop <pkg>; am start -W -n <component> → TotalTime" };
        for (int i = 0; i < runs; i++)
        {
            await s.RunAsync(AdbActions.ForceStop(s.Serial, pkg), ct);
            var r = await s.RunAsync(AdbActions.StartActivity(s.Serial, component), ct);
            var ms = AdbParsers.ParseStartTotalTime(r.StdOut);
            if (ms is not null) m.RunsMs.Add(ms.Value);
        }
        await s.RunAsync(AdbActions.KeyEvent(s.Serial, AdbActions.Key.Home), ct);
        Save(m);
        return m;
    }

    private static string FileFor(string deviceId) => Path.Combine(AppPaths.Ensure("measurements"), deviceId + ".json");

    private static void Save(Measurement m)
    {
        var all = Load(m.DeviceId);
        all.Add(m);
        Json.Save(FileFor(m.DeviceId), all);
    }

    public static List<Measurement> Load(string deviceId) => Json.Load<List<Measurement>>(FileFor(deviceId)) ?? new();
}
