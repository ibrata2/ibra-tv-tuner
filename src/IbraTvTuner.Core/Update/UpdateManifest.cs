using System.Security.Cryptography;
using System.Text.Json;

namespace IbraTvTuner.Core.Update;

/// <summary>
/// update.json published by CI next to the release files. The Windows app reads the "windows" entry:
/// a newer version, the EXE URL on github.com and its SHA-256.
/// </summary>
public sealed record WindowsUpdate(Version Version, string Url, string Sha256, long Size, string NotesAr, string NotesEn);

public static class UpdateManifest
{
    public const string Url = "https://github.com/ibrata2/ibra-tv-tuner/releases/download/v0.1.0/update.json";
    public const string ReleasesPage = "https://github.com/ibrata2/ibra-tv-tuner/releases/tag/v0.1.0";

    /// <summary>Parses the manifest; throws on anything malformed or pointing outside github.com.</summary>
    public static WindowsUpdate ParseWindows(string json)
    {
        using var doc = JsonDocument.Parse(json);
        var root = doc.RootElement;
        var w = root.GetProperty("windows");
        var version = Version.Parse(w.GetProperty("version").GetString() ?? throw new FormatException("version"));
        var url = w.GetProperty("url").GetString() ?? throw new FormatException("url");
        if (!url.StartsWith("https://github.com/", StringComparison.Ordinal)) throw new FormatException("unexpected download host");
        var sha = (w.GetProperty("sha256").GetString() ?? "").Trim().ToLowerInvariant();
        if (sha.Length != 64 || !sha.All(Uri.IsHexDigit)) throw new FormatException("sha256");
        long size = w.TryGetProperty("size", out var s) && s.TryGetInt64(out var n) ? n : 0;
        string notes(string k) => root.TryGetProperty(k, out var v) ? v.GetString() ?? "" : "";
        return new WindowsUpdate(version, url, sha, size, notes("notesAr"), notes("notesEn"));
    }

    /// <summary>True when <paramref name="remote"/> is newer than <paramref name="current"/> (major.minor.build).</summary>
    public static bool IsNewer(Version current, Version remote) =>
        new Version(remote.Major, remote.Minor, Math.Max(0, remote.Build)) > new Version(current.Major, current.Minor, Math.Max(0, current.Build));

    public static string Sha256Of(string path)
    {
        using var f = File.OpenRead(path);
        return Convert.ToHexString(SHA256.HashData(f)).ToLowerInvariant();
    }

    /// <summary>
    /// Batch script that waits for this process to exit, swaps the EXE, restarts it and deletes itself.
    /// If the folder is not writable it starts the downloaded copy instead.
    /// </summary>
    public static string SwapScript(int pid, string newExe, string targetExe) =>
        "@echo off\r\n" +
        "chcp 65001 >nul\r\n" +
        ":wait\r\n" +
        $"tasklist /FI \"PID eq {pid}\" 2>nul | find \"{pid}\" >nul && (timeout /t 1 /nobreak >nul & goto wait)\r\n" +
        $"copy /y \"{newExe}\" \"{targetExe}\" >nul 2>&1 || (start \"\" \"{newExe}\" & goto end)\r\n" +
        $"start \"\" \"{targetExe}\"\r\n" +
        $"del \"{newExe}\" >nul 2>&1\r\n" +
        ":end\r\n" +
        "del \"%~f0\" >nul 2>&1\r\n";
}
