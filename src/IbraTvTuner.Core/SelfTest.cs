using IbraTvTuner.Core.Adb;
using IbraTvTuner.Core.Changes;
using IbraTvTuner.Core.Packages;

namespace IbraTvTuner.Core;

public sealed record SelfTestStep(string NameAr, string NameEn, bool Ok, string Detail);

/// <summary>
/// The required end-to-end path, on a harmless test app we ship ourselves (tv.ibra.tuner.testapp):
/// read model/system → inventory → install test app → disable → verify → re-enable → verify → remove.
/// </summary>
public sealed class SelfTest(DeviceSession s, Catalog catalog)
{
    public async Task<List<SelfTestStep>> RunAsync(string testApkPath, IProgress<SelfTestStep>? progress = null, CancellationToken ct = default)
    {
        var steps = new List<SelfTestStep>();
        void Add(string ar, string en, bool ok, string d) { var st = new SelfTestStep(ar, en, ok, d); steps.Add(st); progress?.Report(st); }

        var info = await s.ReadInfoAsync(ct);
        Add("قراءة الموديل والنظام", "Read model and system", !string.IsNullOrEmpty(info.Model),
            $"{info.Manufacturer} {info.Model} · Android {info.OsVersion} (SDK {info.Sdk}) · {info.Platform}");

        var inv = await s.InventoryAsync(catalog, ct);
        Add("جرد التطبيقات", "Inventory apps", inv.Count > 0,
            $"{inv.Count} packages · {inv.Count(p => p.Class == PackageClass.Protected)} protected");

        var install = await s.RunAsync(AdbActions.InstallOwnApk(s.Serial, testApkPath), ct);
        var installed = await s.QueryStateAsync(LauncherInfo.TestAppPackage, ct) == PackageState.Enabled;
        Add("تثبيت التطبيق الاختباري", "Install test app", installed, install.Combined.Trim());
        if (!installed) return steps;

        var exec = new ChangeExecutor(s);
        inv = await s.InventoryAsync(catalog, ct);
        var (batch, issues) = exec.Plan(new[] { new PlanRequest { Package = LauncherInfo.TestAppPackage, Action = ChangeAction.Disable, Reason = "self-test" } }, inv);
        batch.Kind = "self-test";
        await exec.ExecuteAsync(batch, null, ct);
        var disabledOk = batch.Items.Count == 1 && batch.Items[0].Status == ChangeStatus.Succeeded && batch.Items[0].After == PackageState.Disabled;
        var detail = batch.Items.FirstOrDefault()?.Error ?? string.Join("; ", issues.Select(i => i.En));
        Add("تعطيل التطبيق الاختباري والتحقق", "Disable test app and verify", disabledOk,
            string.IsNullOrEmpty(detail) ? "pm list packages -d confirms disabled" : detail);

        await exec.RollbackAsync(batch, null, ct);
        var restoredOk = batch.Items.Count == 1 && batch.Items[0].Status == ChangeStatus.RolledBack && batch.Items[0].After == PackageState.Enabled;
        Add("الاسترجاع والتحقق", "Roll back and verify", restoredOk, batch.Items.FirstOrDefault()?.Error ?? "re-enabled, confirmed by the device");

        var un = await s.RunAsync(AdbActions.UninstallOwnApp(s.Serial, LauncherInfo.TestAppPackage), ct);
        var gone = await s.QueryStateAsync(LauncherInfo.TestAppPackage, ct) is null;
        Add("إزالة التطبيق الاختباري", "Remove test app", gone, un.Combined.Trim());
        return steps;
    }
}
