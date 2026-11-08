using System.Diagnostics;
using System.IO;
using System.Net.Http;
using System.Windows;
using IbraTvTuner.Core.Storage;
using IbraTvTuner.Core.Update;

namespace IbraTvTuner.App;

/// <summary>
/// Checks update.json on the GitHub release, downloads the new EXE, verifies its SHA-256, then a small script
/// swaps the file after this window closes and starts the new version.
/// </summary>
public static class Updater
{
    private static bool _busy;

    public static Version Current => typeof(Updater).Assembly.GetName().Version ?? new Version(0, 1, 0);

    /// <summary>Automatic check at start-up (at most every 12 h, silent when nothing is new).</summary>
    public static async Task AutoCheckAsync(Action<string> status)
    {
        var s = AppState.I.Settings;
        if (!s.AutoUpdate || DateTimeOffset.Now - s.LastUpdateCheck < TimeSpan.FromHours(12)) return;
        await CheckAsync(interactive: false, status);
    }

    public static async Task CheckAsync(bool interactive, Action<string> status)
    {
        if (_busy) return;
        _busy = true;
        try
        {
            var exe = Environment.ProcessPath;
            if (exe is null || !exe.EndsWith(".exe", StringComparison.OrdinalIgnoreCase)) return;   // dev run (dotnet ...)
            using var http = new HttpClient { Timeout = TimeSpan.FromSeconds(30) };
            http.DefaultRequestHeaders.UserAgent.ParseAdd("IbraTvTuner-updater/1.0");
            WindowsUpdate u;
            try { u = UpdateManifest.ParseWindows(await http.GetStringAsync(UpdateManifest.Url)); }
            catch (Exception ex)
            {
                Log.Write("update check failed: " + ex.Message);
                if (interactive) Ui.Info(Loc.Pick("ما گدرت أوصل لسيرفر التحديث: ", "Could not reach the update server: ") + ex.Message);
                return;
            }
            var settings = AppState.I.Settings;
            settings.LastUpdateCheck = DateTimeOffset.Now;
            settings.Save();
            if (!UpdateManifest.IsNewer(Current, u.Version))
            {
                if (interactive) Ui.Info(Loc.Pick("عندك آخر نسخة ✓ ", "You have the latest version ✓ ") + "v" + Current.ToString(3));
                return;
            }
            if (!interactive && settings.SkippedVersion == u.Version.ToString(3)) return;
            var notes = Loc.I.IsArabic ? u.NotesAr : u.NotesEn;
            var answer = MessageBox.Show(Application.Current.MainWindow,
                Loc.Pick($"يتوفر إصدار جديد v{u.Version.ToString(3)} (عندك v{Current.ToString(3)}).\n\n{notes}\n\nتحديث الآن؟ البرنامج يتسكر ويرجع يفتح بالنسخة الجديدة.\n(لا = تخطّي هذه النسخة)",
                         $"Version v{u.Version.ToString(3)} is available (you have v{Current.ToString(3)}).\n\n{notes}\n\nUpdate now? The app closes and reopens on the new version.\n(No = skip this version)"),
                Loc.T("AppTitle"), MessageBoxButton.YesNoCancel, MessageBoxImage.Information, MessageBoxResult.Yes);
            if (answer == MessageBoxResult.No) { settings.SkippedVersion = u.Version.ToString(3); settings.Save(); return; }
            if (answer != MessageBoxResult.Yes) return;

            var dir = AppPaths.Ensure("update");
            var tmp = Path.Combine(dir, "IbraTvTuner-" + u.Version.ToString(3) + ".exe");
            using (var resp = await http.GetAsync(u.Url, HttpCompletionOption.ResponseHeadersRead))
            {
                resp.EnsureSuccessStatusCode();
                var total = resp.Content.Headers.ContentLength ?? u.Size;
                await using var src = await resp.Content.ReadAsStreamAsync();
                await using var dst = File.Create(tmp);
                var buf = new byte[81920];
                long done = 0;
                int n, last = -1;
                while ((n = await src.ReadAsync(buf)) > 0)
                {
                    await dst.WriteAsync(buf.AsMemory(0, n));
                    done += n;
                    var pct = total > 0 ? (int)(done * 100 / total) : 0;
                    if (pct != last) { last = pct; status(Loc.Pick($"ينزل التحديث {pct}٪", $"Downloading update {pct}%")); }
                }
            }
            if (UpdateManifest.Sha256Of(tmp) != u.Sha256)
            {
                File.Delete(tmp);
                Ui.Info(Loc.Pick("ملف التحديث ما طابق البصمة، انلغى التحديث.", "The update did not match its checksum; cancelled."));
                return;
            }
            var script = Path.Combine(dir, "apply-update.cmd");
            File.WriteAllText(script, UpdateManifest.SwapScript(Environment.ProcessId, tmp, exe));
            Log.Write($"update {Current.ToString(3)} -> {u.Version.ToString(3)} downloaded, restarting");
            Process.Start(new ProcessStartInfo("cmd.exe", $"/c \"{script}\"") { CreateNoWindow = true, UseShellExecute = false, WindowStyle = ProcessWindowStyle.Hidden });
            Application.Current.Shutdown();
        }
        catch (Exception ex)
        {
            Log.Write("update failed: " + ex);
            if (interactive) Ui.Info(Loc.Pick("فشل التحديث: ", "Update failed: ") + ex.Message);
        }
        finally
        {
            _busy = false;
            status("v" + Current.ToString(3));
        }
    }
}
