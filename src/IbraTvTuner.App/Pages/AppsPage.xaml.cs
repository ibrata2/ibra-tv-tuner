using System.ComponentModel;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Input;
using System.Windows.Media;
using IbraTvTuner.Core;
using IbraTvTuner.Core.Adb;
using IbraTvTuner.Core.Changes;
using IbraTvTuner.Core.Packages;
using IbraTvTuner.Core.Platforms;

namespace IbraTvTuner.App.Pages;

public sealed class AppRow : INotifyPropertyChanged
{
    public AppRow(PackageEntry e) { Entry = e; }
    public PackageEntry Entry { get; }
    public string Name => Entry.Name;
    private bool _selected;
    public bool Selected
    {
        get => _selected;
        set { _selected = value; PropertyChanged?.Invoke(this, new PropertyChangedEventArgs(nameof(Selected))); }
    }
    public string KindText => Entry.IsSystem ? Loc.Pick("نظام", "system") : Loc.Pick("مستخدم", "user");
    public string StateText => Entry.State switch
    {
        PackageState.Disabled => Loc.Pick("معطّل", "disabled"),
        PackageState.UninstalledForUser => Loc.Pick("مُزال", "removed"),
        _ => Loc.Pick("مفعّل", "enabled"),
    };
    public string ClassText => Loc.T(Entry.Class.ToString());
    public Brush ClassBrush => (Brush)Application.Current.FindResource(Entry.Class switch
    {
        PackageClass.Protected => "Danger",
        PackageClass.Reviewable => "Ok",
        _ => "Warn",
    });
    public string Reason => Loc.I.IsArabic ? Entry.ReasonAr : Entry.ReasonEn;
    public event PropertyChangedEventHandler? PropertyChanged;
}

public sealed record ModelItem(string? Id, string Display);

public partial class AppsPage : UserControl, IPage
{
    private List<AppRow> _rows = new();
    private bool _loadingModels;

    public AppsPage()
    {
        InitializeComponent();
        AppState.I.CurrentChanged += () => Dispatcher.InvokeAsync(() => { _rows = new(); Apply(); FillModels(null, null); UpdateTarget(); });
        Labels();
        FillModels(null, null);
    }

    public void OnShown() { UpdateTarget(); if (_rows.Count == 0 && AppState.I.Inventory.Count > 0) Build(); }
    public void OnLanguageChanged() { Labels(); Build(); UpdateTarget(); }

    private void Labels()
    {
        ColPackage.Header = Loc.T("ColPackage"); ColKind.Header = Loc.T("ColKind"); ColState.Header = Loc.T("ColState");
        ColClass.Header = Loc.T("ColClass"); ColReason.Header = Loc.T("ColReason");
        var i = Math.Max(0, ClassCombo.SelectedIndex);
        ClassCombo.ItemsSource = new[] { Loc.T("All"), Loc.T("Reviewable"), Loc.T("Unknown"), Loc.T("Protected") };
        ClassCombo.SelectedIndex = i;
    }

    private void UpdateTarget() => TargetText.Text = Loc.T("Target") + " " + AppState.I.TargetName;

    private void Build()
    {
        _rows = AppState.I.Inventory.Select(e => new AppRow(e)).ToList();
        AppCombo.ItemsSource = AppState.I.Inventory.Where(p => p.HasLauncherActivity).Select(p => p.Name).ToList();
        Apply();
    }

    private void Apply()
    {
        IEnumerable<AppRow> q = _rows;
        q = ClassCombo.SelectedIndex switch
        {
            1 => q.Where(r => r.Entry.Class == PackageClass.Reviewable),
            2 => q.Where(r => r.Entry.Class == PackageClass.Unknown),
            3 => q.Where(r => r.Entry.Class == PackageClass.Protected),
            _ => q,
        };
        var s = SearchBox.Text.Trim();
        if (s.Length > 0) q = q.Where(r => r.Name.Contains(s, StringComparison.OrdinalIgnoreCase) || r.Reason.Contains(s, StringComparison.OrdinalIgnoreCase));
        var list = q.OrderBy(r => r.Entry.Class == PackageClass.Reviewable && r.Entry.Recommendation is not null ? 0 : 1).ThenBy(r => r.Name).ToList();
        AppGrid.ItemsSource = list;
        CountText.Text = $"{list.Count} / {_rows.Count}";
    }

    private void Filter_Changed(object sender, RoutedEventArgs e) { if (IsInitialized && AppGrid is not null) Apply(); }

    private void FillModels(DeviceInfo? info, ISet<string>? installed)
    {
        _loadingModels = true;
        var items = new List<ModelItem> { new(null, Loc.T("ModelNone")) };
        var cat = AppState.I.Catalog;
        var cur = AppState.I.Current;
        if (info is not null && installed is not null)
            items.AddRange(cat.Rank(info, installed).Select(r => new ModelItem(r.Model.Id, $"★ {r.Model.Tv} · {r.Model.Os}  ({r.Why})")));
        if (cur?.CatalogModelId is { } saved && items.All(i => i.Id != saved) && cat.Get(saved) is { } m)
            items.Add(new ModelItem(m.Id, $"{m.Tv} · {m.Os}"));
        items.AddRange(cat.Models.Where(m => items.All(i => i.Id != m.Id)).OrderBy(m => m.Tv).Select(m => new ModelItem(m.Id, $"{m.Tv} · {m.Os}")));
        ModelCombo.ItemsSource = items;
        ModelCombo.SelectedItem = items.FirstOrDefault(i => i.Id == cur?.CatalogModelId) ?? items[0];
        _loadingModels = false;
    }

    private async void ModelCombo_SelectionChanged(object sender, SelectionChangedEventArgs e)
    {
        if (_loadingModels || AppState.I.Current is not { } cur || ModelCombo.SelectedItem is not ModelItem mi) return;
        cur.CatalogModelId = mi.Id;
        AppState.I.SaveDevices();
        if (AppState.I.Inventory.Count > 0) await RefreshInventory(false);
    }

    private async void Refresh_Click(object sender, RoutedEventArgs e) => await RefreshInventory(true);

    private async Task RefreshInventory(bool rankModels)
    {
        if (!Ui.NeedSession(out var s)) return;
        await Busy(async () =>
        {
            var (ok, err) = await s.ConnectAsync();
            if (!ok) { Ui.Error(err); return; }
            if (rankModels)
            {
                var info = await s.ReadInfoAsync();
                var sets = await s.ReadPackageSetsAsync();
                var ranked = AppState.I.Catalog.Rank(info, sets.WithUninstalled);
                if (s.Profile.CatalogModelId is null && ranked.FirstOrDefault() is { Score: >= 1000 } exact)
                    s.Profile.CatalogModelId = exact.Model.Id; // only an exact fingerprint match is auto-selected
                AppState.I.SaveDevices();
                FillModels(info, sets.WithUninstalled);
            }
            AppState.I.Inventory = await s.InventoryAsync(AppState.I.Catalog);
            Build();
        });
    }

    private List<PackageEntry> Chosen()
    {
        var chosen = _rows.Where(r => r.Selected).Select(r => r.Entry).ToList();
        if (chosen.Count == 0) chosen = AppGrid.SelectedItems.OfType<AppRow>().Select(r => r.Entry).ToList();
        return chosen;
    }

    private async Task Change(ChangeAction action, Feature feature)
    {
        if (!Ui.NeedSession(out var s) || !Ui.AcceptUntested(s, feature)) return;
        var chosen = Chosen();
        if (chosen.Count == 0) { Ui.Info(Loc.Pick("حدّد تطبيقاً واحداً على الأقل.", "Select at least one app.")); return; }
        var exec = new ChangeExecutor(s);
        var reqs = chosen.Select(p => new PlanRequest
        {
            Package = p.Name, Action = action,
            Reason = p.Recommendation is { Length: > 0 } r ? r : (Loc.I.IsArabic ? p.ReasonAr : p.ReasonEn),
        });
        var (batch, issues) = exec.Plan(reqs, AppState.I.Inventory);
        var w = new PlanWindow(batch, issues) { Owner = Window.GetWindow(this) };
        if (w.ShowDialog() != true || batch.Items.Count == 0) return;
        await Busy(async () =>
        {
            var (ok, err) = await s.ConnectAsync();
            if (!ok) { Ui.Error(err); return; }
            await exec.ExecuteAsync(batch);
            Ui.Info(Loc.Pick($"تم: {batch.SucceededCount} نجحت وتم التحقق منها، {batch.FailedCount} فشلت. التفاصيل والاسترجاع في «التغييرات والاسترجاع».",
                             $"Done: {batch.SucceededCount} verified, {batch.FailedCount} failed. Details and rollback in 'Changes & restore'."));
            AppState.I.Inventory = await s.InventoryAsync(AppState.I.Catalog);
            Build();
        });
    }

    private async void Disable_Click(object sender, RoutedEventArgs e) => await Change(ChangeAction.Disable, Feature.DisableApp);
    private async void Enable_Click(object sender, RoutedEventArgs e) => await Change(ChangeAction.Enable, Feature.DisableApp);
    private async void Uninstall_Click(object sender, RoutedEventArgs e)
    {
        if (MessageBox.Show(Window.GetWindow(this), Loc.T("UninstallWarn"), Loc.T("ConfirmTitle"), MessageBoxButton.YesNo, MessageBoxImage.Warning, MessageBoxResult.No) != MessageBoxResult.Yes) return;
        await Change(ChangeAction.UninstallForUser, Feature.UninstallForUser);
    }

    private async void MeasureHome_Click(object sender, RoutedEventArgs e)
    {
        if (!Ui.NeedSession(out var s) || !Ui.AcceptUntested(s, Feature.LaunchTiming)) return;
        await Busy(async () =>
        {
            var m = await new Benchmark(s).HomeColdStartAsync(3, MeasureLabel.Text.Trim());
            Report(m);
        });
    }

    private async void MeasureApp_Click(object sender, RoutedEventArgs e)
    {
        if (!Ui.NeedSession(out var s) || AppCombo.SelectedItem is not string pkg) return;
        if (!Ui.AcceptUntested(s, Feature.LaunchTiming)) return;
        await Busy(async () =>
        {
            string? comp = null;
            foreach (var lb in new[] { true, false })
            {
                var r = await s.RunAsync(AdbActions.QueryLauncherApps(s.Serial, lb));
                comp ??= AdbParsers.ParseComponents(r.StdOut).FirstOrDefault(c => AdbParsers.PackageOf(c) == pkg);
            }
            if (comp is null) { Ui.Info("no launchable activity"); return; }
            Report(await new Benchmark(s).AppColdStartAsync(comp, 3, MeasureLabel.Text.Trim()));
        });
    }

    private void Report(Measurement m)
    {
        MeasureOut.AppendText($"{m.At:HH:mm:ss} [{m.Label}] {m.What}: median {m.MedianMs?.ToString() ?? "-"} ms  runs=[{string.Join(", ", m.RunsMs)}]  method: {m.Method}{Environment.NewLine}");
        MeasureOut.ScrollToEnd();
    }

    private async Task Busy(Func<Task> work)
    {
        IsEnabled = false; Mouse.OverrideCursor = Cursors.Wait;
        try { await work(); }
        catch (Exception ex) { Ui.Error(ex); }
        finally { IsEnabled = true; Mouse.OverrideCursor = null; }
    }
}
