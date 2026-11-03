using System.Windows;
using System.Windows.Controls;
using System.Windows.Input;
using IbraTvTuner.Core.Changes;
using IbraTvTuner.Core.Launcher;

namespace IbraTvTuner.App.Pages;

public sealed record BatchItem(ChangeBatch Batch)
{
    public string Display =>
        $"{Batch.CreatedAt:yyyy-MM-dd HH:mm} · {Batch.Kind} · {Batch.Items.Count}" +
        (Batch.RolledBack ? Loc.Pick("  ↺ مسترجعة", "  ↺ rolled back") : "") +
        (Batch.FailedCount > 0 ? $"  ⚠ {Batch.FailedCount}" : "");
}

public sealed record ItemRow(string Package, string Change, string Before, string After, string Result, string Error);

public partial class ChangesPage : UserControl, IPage
{
    public ChangesPage()
    {
        InitializeComponent();
        AppState.I.CurrentChanged += () => Dispatcher.InvokeAsync(Reload);
        Headers();
    }

    public void OnShown() => Reload();
    public void OnLanguageChanged() { Headers(); Reload(); }

    private void Headers()
    {
        CPkg.Header = Loc.T("ColPackage"); CChange.Header = Loc.T("Change"); CBefore.Header = Loc.T("Before");
        CAfter.Header = Loc.T("After"); CResult.Header = Loc.T("Result"); CError.Header = Loc.T("Log");
    }

    private void Reload()
    {
        var c = AppState.I.Current;
        var b = c is null ? null : HomeBackup.Load(c.Id);
        BackupText.Text = c is null ? Loc.T("NoDevice")
            : b is null ? Loc.Pick("لم تُحفظ نسخة بعد (تُحفظ تلقائياً قبل أول تغيير على الواجهة).", "No backup yet (saved automatically before the first launcher change).")
            : $"{AppState.I.TargetName}\n{Loc.T("OriginalHome")} {b.OriginalHome ?? "(chooser)"} · {b.SavedAt:yyyy-MM-dd HH:mm}" +
              (b.RestoredAt is { } r ? $" · restored {r:yyyy-MM-dd HH:mm}" : "") + $" · themes saved: {b.ConfigHistory.Count}";
        BatchList.ItemsSource = c is null ? null : ChangeJournal.LoadAll(c.Id).Select(x => new BatchItem(x)).ToList();
        ItemsGrid.ItemsSource = null;
        RollbackBtn.IsEnabled = false;
        SummaryText.Text = "";
    }

    private void BatchList_SelectionChanged(object sender, SelectionChangedEventArgs e) => ShowBatch();

    private void ShowBatch()
    {
        if (BatchList.SelectedItem is not BatchItem bi) return;
        var b = bi.Batch;
        ItemsGrid.ItemsSource = b.Items.Select(i => new ItemRow(i.Package, PlanWindow.ActionText(i.Action), PlanWindow.StateText(i.Before),
            i.After is { } a ? PlanWindow.StateText(a) : "—", Status(i.Status), i.Error ?? "")).ToList();
        RollbackBtn.IsEnabled = b.Items.Any(i => i.Status is ChangeStatus.Succeeded or ChangeStatus.RollbackFailed);
        SummaryText.Text = $"{b.DeviceTitle} · {b.DeviceSerial}";
    }

    private static string Status(ChangeStatus s) => s switch
    {
        ChangeStatus.Succeeded => Loc.Pick("✓ تم التحقق", "✓ verified"),
        ChangeStatus.Failed => Loc.Pick("✗ فشل", "✗ failed"),
        ChangeStatus.Skipped => Loc.Pick("تخطّي", "skipped"),
        ChangeStatus.RolledBack => Loc.Pick("↺ مسترجع", "↺ rolled back"),
        ChangeStatus.RollbackFailed => Loc.Pick("✗ فشل الاسترجاع", "✗ rollback failed"),
        _ => Loc.Pick("بانتظار", "pending"),
    };

    private void Refresh_Click(object sender, RoutedEventArgs e) => Reload();

    private async void Rollback_Click(object sender, RoutedEventArgs e)
    {
        if (BatchList.SelectedItem is not BatchItem bi || !Ui.NeedSession(out var s)) return;
        if (!Ui.ConfirmOnDevice(Loc.Pick($"استرجاع {bi.Batch.SucceededCount} تغيير للحالة المحفوظة قبلها، مع التحقق من كل واحد.",
                                        $"Return {bi.Batch.SucceededCount} change(s) to their saved earlier state, verifying each."))) return;
        await Busy(async () =>
        {
            var (ok, err) = await s.ConnectAsync();
            if (!ok) { Ui.Error(err); return; }
            await new ChangeExecutor(s).RollbackAsync(bi.Batch);
            AppState.I.Inventory = new();
            ShowBatch();
        });
    }

    private async void RestoreOld_Click(object sender, RoutedEventArgs e)
    {
        if (!Ui.NeedSession(out var s)) return;
        var remove = RemoveLauncherCheck.IsChecked == true;
        if (!Ui.ConfirmOnDevice(Loc.T("RestoreOldHint") + (remove ? "\n" + Loc.T("RemoveLauncherToo") : ""))) return;
        await Busy(async () =>
        {
            var (ok, err) = await s.ConnectAsync();
            if (!ok) { Ui.Error(err); return; }
            var r = await new LauncherManager(s).RestoreOriginalAsync(remove);
            if (r.Ok) Ui.Info((Loc.I.IsArabic ? r.Ar : r.En));
            else Ui.Error(new Exception((Loc.I.IsArabic ? r.Ar : r.En) + "\n\n" + r.Detail));
            Reload();
        });
    }

    private async Task Busy(Func<Task> work)
    {
        IsEnabled = false; Mouse.OverrideCursor = Cursors.Wait;
        try { await work(); }
        catch (Exception ex) { Ui.Error(ex); }
        finally { IsEnabled = true; Mouse.OverrideCursor = null; }
    }
}
