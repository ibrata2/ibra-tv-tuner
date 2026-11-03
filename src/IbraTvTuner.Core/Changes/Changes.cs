using IbraTvTuner.Core.Adb;
using IbraTvTuner.Core.Packages;
using IbraTvTuner.Core.Platforms;
using IbraTvTuner.Core.Storage;

namespace IbraTvTuner.Core.Changes;

public enum ChangeAction { Disable, Enable, UninstallForUser, RestoreForUser }
public enum ChangeStatus { Pending, Succeeded, Failed, Skipped, RolledBack, RollbackFailed }

public sealed class ChangeRecord
{
    public string Package { get; set; } = "";
    public ChangeAction Action { get; set; }
    public string Reason { get; set; } = "";
    public PackageState Before { get; set; }
    public PackageState? After { get; set; }
    public ChangeStatus Status { get; set; } = ChangeStatus.Pending;
    public string? Error { get; set; }
    public DateTimeOffset? At { get; set; }

    public PackageState Expected => Action switch
    {
        ChangeAction.Disable => PackageState.Disabled,
        ChangeAction.UninstallForUser => PackageState.UninstalledForUser,
        _ => PackageState.Enabled,
    };
}

public sealed class ChangeBatch
{
    public string Id { get; set; } = DateTime.Now.ToString("yyyyMMdd-HHmmss") + "-" + Guid.NewGuid().ToString("N")[..4];
    public string DeviceId { get; set; } = "";
    public string DeviceTitle { get; set; } = "";
    public string DeviceSerial { get; set; } = "";
    public string Kind { get; set; } = "apps";
    public DateTimeOffset CreatedAt { get; set; } = DateTimeOffset.Now;
    public List<ChangeRecord> Items { get; set; } = new();
    public bool RolledBack { get; set; }

    public int SucceededCount => Items.Count(i => i.Status == ChangeStatus.Succeeded);
    public int FailedCount => Items.Count(i => i.Status is ChangeStatus.Failed or ChangeStatus.RollbackFailed);
}

public sealed class PlanRequest
{
    public required string Package { get; init; }
    public required ChangeAction Action { get; init; }
    public string Reason { get; init; } = "";
}

public sealed record PlanIssue(string Package, string Ar, string En);

/// <summary>Journal on disk: one JSON file per batch under %LOCALAPPDATA%\IbraTvTuner\journal.</summary>
public static class ChangeJournal
{
    public static string PathOf(ChangeBatch b) => Path.Combine(AppPaths.JournalDir, $"{b.DeviceId}_{b.Id}.json");
    public static void Save(ChangeBatch b) => Json.Save(PathOf(b), b);

    public static List<ChangeBatch> LoadAll(string? deviceId = null) =>
        Directory.EnumerateFiles(AppPaths.JournalDir, "*.json")
            .Where(f => deviceId is null || Path.GetFileName(f).StartsWith(deviceId + "_", StringComparison.Ordinal))
            .Select(f => Json.Load<ChangeBatch>(f))
            .Where(b => b is not null).Cast<ChangeBatch>()
            .OrderByDescending(b => b.CreatedAt).ToList();
}

public sealed class ChangeExecutor(DeviceSession session)
{
    public DeviceSession Session { get; } = session;

    /// <summary>
    /// Builds a batch from requests against a fresh inventory. Protected packages, no-ops and unsupported
    /// actions are refused here, before anything touches the device.
    /// </summary>
    public (ChangeBatch Batch, List<PlanIssue> Issues) Plan(IEnumerable<PlanRequest> requests, IReadOnlyList<PackageEntry> inventory)
    {
        var byName = inventory.ToDictionary(p => p.Name);
        var batch = new ChangeBatch { DeviceId = Session.Profile.Id, DeviceTitle = Session.Profile.Title, DeviceSerial = Session.Serial };
        var issues = new List<PlanIssue>();
        foreach (var r in requests.DistinctBy(r => r.Package))
        {
            if (!Validators.IsPackage(r.Package)) { issues.Add(new(r.Package, "اسم حزمة غير صالح.", "Invalid package name.")); continue; }
            if (!byName.TryGetValue(r.Package, out var p)) { issues.Add(new(r.Package, "الحزمة غير موجودة على الجهاز.", "Package not found on the device.")); continue; }
            if (p.Class == PackageClass.Protected && r.Action is ChangeAction.Disable or ChangeAction.UninstallForUser)
            { issues.Add(new(r.Package, "محمية: " + p.ReasonAr, "Protected: " + p.ReasonEn)); continue; }
            var feature = r.Action is ChangeAction.UninstallForUser or ChangeAction.RestoreForUser ? Feature.UninstallForUser : Feature.DisableApp;
            if (!Session.Module.CanExecute(feature))
            { issues.Add(new(r.Package, "هذا الإجراء غير مدعوم على هذا النظام.", "This action is not supported on this system.")); continue; }
            if (r.Action == ChangeAction.RestoreForUser && Session.Profile.Sdk is > 0 and < 26)
            { issues.Add(new(r.Package, "الإرجاع بعد الإزالة يحتاج Android 8 فأحدث.", "Restoring a removed app needs Android 8+.")); continue; }
            var rec = new ChangeRecord { Package = r.Package, Action = r.Action, Reason = r.Reason, Before = p.State };
            if (rec.Expected == p.State) { issues.Add(new(r.Package, "الحالة مطابقة أصلاً، لا تغيير.", "Already in that state, nothing to do.")); continue; }
            batch.Items.Add(rec);
        }
        return (batch, issues);
    }

    private AdbCommand CommandFor(ChangeAction a, string pkg) => a switch
    {
        ChangeAction.Disable => AdbActions.DisableForUser(Session.Serial, pkg),
        ChangeAction.Enable => AdbActions.Enable(Session.Serial, pkg),
        ChangeAction.UninstallForUser => AdbActions.UninstallForUser(Session.Serial, pkg),
        ChangeAction.RestoreForUser => AdbActions.InstallExisting(Session.Serial, pkg),
        _ => throw new ArgumentOutOfRangeException(nameof(a)),
    };

    /// <summary>Moves a package from its current state to <paramref name="target"/>, then re-reads it.</summary>
    private async Task<(bool Ok, PackageState? Now, string? Error)> MoveAsync(string pkg, PackageState current, PackageState target, CancellationToken ct)
    {
        var steps = new List<ChangeAction>();
        if (current == PackageState.UninstalledForUser && target != PackageState.UninstalledForUser) steps.Add(ChangeAction.RestoreForUser);
        if (target == PackageState.UninstalledForUser) steps.Add(ChangeAction.UninstallForUser);
        if (target == PackageState.Disabled) steps.Add(ChangeAction.Disable);
        if (target == PackageState.Enabled && current == PackageState.Disabled) steps.Add(ChangeAction.Enable);
        string? error = null;
        foreach (var s in steps)
        {
            var r = await Session.RunAsync(CommandFor(s, pkg), ct);
            var err = AdbErrorTranslator.FromResult(r);
            if (err is not null) { error = err.English + " | " + Trim(err.Raw); break; }
        }
        var now = await Session.QueryStateAsync(pkg, ct);
        return (now == target, now, error);
    }

    public async Task ExecuteAsync(ChangeBatch batch, IProgress<ChangeRecord>? progress = null, CancellationToken ct = default)
    {
        // Snapshot first: the journal exists before the first command runs.
        ChangeJournal.Save(batch);
        foreach (var item in batch.Items.Where(i => i.Status == ChangeStatus.Pending))
        {
            ct.ThrowIfCancellationRequested();
            try
            {
                // Re-read the real state now: the plan may be minutes old.
                var live = await Session.QueryStateAsync(item.Package, ct);
                if (live is null) { item.Status = ChangeStatus.Skipped; item.Error = "package vanished"; }
                else
                {
                    item.Before = live.Value;
                    if (live == item.Expected) { item.Status = ChangeStatus.Skipped; item.After = live; item.Error = "already in target state"; }
                    else
                    {
                        var (ok, now, err) = await MoveAsync(item.Package, live.Value, item.Expected, ct);
                        item.After = now;
                        item.Status = ok ? ChangeStatus.Succeeded : ChangeStatus.Failed;
                        item.Error = ok ? null : err ?? $"verification: device reports {now?.ToString() ?? "missing"}";
                    }
                }
            }
            catch (AdbOperationException ex) { item.Status = ChangeStatus.Failed; item.Error = ex.Error.English; }
            catch (InvalidInputException ex) { item.Status = ChangeStatus.Failed; item.Error = ex.Message; }
            item.At = DateTimeOffset.Now;
            ChangeJournal.Save(batch);
            progress?.Report(item);
        }
    }

    /// <summary>Puts every verified change back to the state recorded before it, newest first, and verifies.</summary>
    public async Task RollbackAsync(ChangeBatch batch, IProgress<ChangeRecord>? progress = null, CancellationToken ct = default)
    {
        foreach (var item in Enumerable.Reverse(batch.Items).Where(i => i.Status is ChangeStatus.Succeeded or ChangeStatus.RollbackFailed))
        {
            ct.ThrowIfCancellationRequested();
            try
            {
                var live = await Session.QueryStateAsync(item.Package, ct);
                if (live is null) { item.Status = ChangeStatus.RollbackFailed; item.Error = "package missing on device"; }
                else if (live == item.Before) { item.Status = ChangeStatus.RolledBack; item.After = live; }
                else
                {
                    var (ok, now, err) = await MoveAsync(item.Package, live.Value, item.Before, ct);
                    item.After = now;
                    item.Status = ok ? ChangeStatus.RolledBack : ChangeStatus.RollbackFailed;
                    item.Error = ok ? null : err ?? $"verification: device reports {now?.ToString() ?? "missing"}";
                }
            }
            catch (AdbOperationException ex) { item.Status = ChangeStatus.RollbackFailed; item.Error = ex.Error.English; }
            item.At = DateTimeOffset.Now;
            ChangeJournal.Save(batch);
            progress?.Report(item);
        }
        batch.RolledBack = batch.Items.All(i => i.Status is not ChangeStatus.Succeeded and not ChangeStatus.RollbackFailed);
        ChangeJournal.Save(batch);
    }

    private static string Trim(string s) => s.Length > 300 ? s[..300] : s;
}
