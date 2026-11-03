using System.Diagnostics;
using System.Text;

namespace IbraTvTuner.Core.Adb;

public sealed record AdbResult(int ExitCode, string StdOut, string StdErr, bool TimedOut)
{
    public bool Ok => ExitCode == 0 && !TimedOut;
    public string Combined => (StdOut + "\n" + StdErr).Trim();
}

/// <summary>Runs one adb invocation. Arguments are always a list, never a shell string.</summary>
public interface IAdbTransport
{
    Task<AdbResult> RunAsync(AdbCommand command, CancellationToken ct = default);
}

/// <summary>
/// A built, validated adb invocation. Only <see cref="AdbActions"/> can create one.
/// <see cref="Display"/> is what is safe to show and log (secrets masked).
/// </summary>
public sealed class AdbCommand
{
    internal AdbCommand(IReadOnlyList<string> args, string display, TimeSpan timeout)
    {
        Args = args;
        Display = display;
        Timeout = timeout;
    }

    public IReadOnlyList<string> Args { get; }
    public string Display { get; }
    public TimeSpan Timeout { get; }
    public override string ToString() => Display;
}

public sealed class ProcessAdbTransport(string adbPath) : IAdbTransport
{
    public string AdbPath { get; } = adbPath;

    public async Task<AdbResult> RunAsync(AdbCommand command, CancellationToken ct = default)
    {
        var psi = new ProcessStartInfo(AdbPath)
        {
            RedirectStandardOutput = true,
            RedirectStandardError = true,
            UseShellExecute = false,
            CreateNoWindow = true,
            StandardOutputEncoding = Encoding.UTF8,
            StandardErrorEncoding = Encoding.UTF8,
        };
        foreach (var a in command.Args) psi.ArgumentList.Add(a);

        using var proc = new Process { StartInfo = psi };
        try
        {
            proc.Start();
        }
        catch (Exception ex)
        {
            return new AdbResult(-1, "", "ADB_START_FAILED: " + ex.Message, false);
        }

        var outTask = proc.StandardOutput.ReadToEndAsync(ct);
        var errTask = proc.StandardError.ReadToEndAsync(ct);
        using var timeoutCts = CancellationTokenSource.CreateLinkedTokenSource(ct);
        timeoutCts.CancelAfter(command.Timeout);
        try
        {
            await proc.WaitForExitAsync(timeoutCts.Token).ConfigureAwait(false);
        }
        catch (OperationCanceledException)
        {
            try { proc.Kill(entireProcessTree: true); } catch { /* already gone */ }
            ct.ThrowIfCancellationRequested();
            return new AdbResult(-1, "", "TIMEOUT", true);
        }

        string so = "", se = "";
        try { so = await outTask.ConfigureAwait(false); } catch { }
        try { se = await errTask.ConfigureAwait(false); } catch { }
        return new AdbResult(proc.ExitCode, so, se, false);
    }
}
