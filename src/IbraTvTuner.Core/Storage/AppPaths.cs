using System.Text.Json;
using System.Text.Json.Serialization;

namespace IbraTvTuner.Core.Storage;

public static class AppPaths
{
    private static string? _root;

    /// <summary>Overridable for tests. Defaults to %LOCALAPPDATA%\IbraTvTuner (never uploaded anywhere).</summary>
    public static string Root
    {
        get => _root ??= Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData), "IbraTvTuner");
        set => _root = value;
    }

    public static string Ensure(string sub)
    {
        var p = Path.Combine(Root, sub);
        Directory.CreateDirectory(p);
        return p;
    }

    public static string DevicesFile => Path.Combine(Ensure(""), "devices.json");
    public static string SettingsFile => Path.Combine(Ensure(""), "settings.json");
    public static string JournalDir => Ensure("journal");
    public static string LogDir => Ensure("logs");
    public static string ThemesDir => Ensure("themes");
    public static string ToolsDir => Ensure("tools");
}

public static class Json
{
    public static readonly JsonSerializerOptions Options = new()
    {
        WriteIndented = true,
        Converters = { new JsonStringEnumConverter() },
        PropertyNamingPolicy = JsonNamingPolicy.CamelCase,
    };

    public static T? Load<T>(string path) where T : class
    {
        try
        {
            if (!File.Exists(path)) return null;
            return JsonSerializer.Deserialize<T>(File.ReadAllText(path), Options);
        }
        catch { return null; }
    }

    /// <summary>Atomic write: tmp file then replace, so a crash never leaves a half-written journal.</summary>
    public static void Save<T>(string path, T value)
    {
        Directory.CreateDirectory(Path.GetDirectoryName(path)!);
        var tmp = path + ".tmp";
        File.WriteAllText(tmp, JsonSerializer.Serialize(value, Options));
        File.Move(tmp, path, overwrite: true);
    }
}

public sealed class AppSettings
{
    public string? AdbPath { get; set; }
    public string Language { get; set; } = "ar";
    public bool AcceptedPlatformToolsTerms { get; set; }

    public static AppSettings Load() => Json.Load<AppSettings>(AppPaths.SettingsFile) ?? new AppSettings();
    public void Save() => Json.Save(AppPaths.SettingsFile, this);
}

public sealed class DeviceStore
{
    public List<DeviceProfile> Devices { get; set; } = new();
    public static DeviceStore Load() => Json.Load<DeviceStore>(AppPaths.DevicesFile) ?? new DeviceStore();
    public void Save() => Json.Save(AppPaths.DevicesFile, this);
}

/// <summary>Simple local log. Pairing codes never reach it because AdbCommand.Display masks them.</summary>
public static class Log
{
    private static readonly object Gate = new();
    public static event Action<string>? Line;

    public static void Write(string message)
    {
        var line = $"{DateTime.Now:yyyy-MM-dd HH:mm:ss} {message}";
        lock (Gate)
        {
            try { File.AppendAllText(Path.Combine(AppPaths.LogDir, $"{DateTime.Now:yyyy-MM-dd}.log"), line + Environment.NewLine); }
            catch { /* logging must never break the app */ }
        }
        Line?.Invoke(line);
    }
}
