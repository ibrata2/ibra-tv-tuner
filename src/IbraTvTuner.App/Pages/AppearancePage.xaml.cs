using System.Windows;
using System.Windows.Controls;
using System.Windows.Input;
using System.Windows.Media;
using IbraTvTuner.Core;
using IbraTvTuner.Core.Launcher;
using IbraTvTuner.Core.Platforms;

namespace IbraTvTuner.App.Pages;

public partial class AppearancePage : UserControl, IPage
{
    private bool _ready;
    private readonly List<string> _favorites = new();
    private readonly List<string> _hidden = new();
    private List<string> _apps = new();

    private static readonly (string Name, string B1, string B2, string Acc, string Txt)[] Presets =
    {
        ("Glass", "#0B1220", "#14324A", "#38BDF8", "#F1F5F9"),
        ("Gold", "#0A0A0A", "#2A2213", "#D4AF37", "#F5EBD0"),
        ("Night", "#000000", "#111111", "#FFFFFF", "#E5E5E5"),
        ("Ocean", "#031B2E", "#0B5563", "#5EEAD4", "#ECFEFF"),
        ("Rose", "#1A0B14", "#4A1530", "#F472B6", "#FDF2F8"),
    };

    public AppearancePage()
    {
        InitializeComponent();
        LangCombo.SelectedIndex = 0;
        foreach (var p in Presets)
        {
            var b = new Button
            {
                Content = p.Name, Padding = new Thickness(10, 4, 10, 4),
                Background = new LinearGradientBrush(Hex(p.B1), Hex(p.B2), 45),
                BorderBrush = new SolidColorBrush(Hex(p.Acc)),
                Foreground = new SolidColorBrush(Hex(p.Txt)),
            };
            b.Click += (_, _) =>
            {
                Bg1Box.Text = p.B1; Bg2Box.Text = p.B2; AccentBox.Text = p.Acc; TextColorBox.Text = p.Txt;
            };
            PresetPanel.Children.Add(b);
        }
        AppState.I.CurrentChanged += () => Dispatcher.InvokeAsync(LoadForDevice);
        _ready = true;
        LoadForDevice();
    }

    public void OnShown() { UpdateTarget(); if (_apps.Count == 0) FillAppsFromInventory(); }
    public void OnLanguageChanged() { UpdateTarget(); Render(); }

    private static Color Hex(string s)
    {
        try { return (Color)ColorConverter.ConvertFromString(s); } catch { return Colors.Gray; }
    }

    private void UpdateTarget()
    {
        TargetText.Text = Loc.T("Target") + " " + AppState.I.TargetName;
        var c = AppState.I.Current;
        var b = c is null ? null : HomeBackup.Load(c.Id);
        BackupText.Text = Loc.T("OriginalHome") + " " + (b?.OriginalHome ?? "—") +
                          (b is null ? "" : $"  ({b.SavedAt:yyyy-MM-dd HH:mm})");
    }

    private void LoadForDevice()
    {
        UpdateTarget();
        var c = AppState.I.Current;
        var last = c is null ? null : HomeBackup.Load(c.Id)?.ConfigHistory.LastOrDefault()?.Config;
        if (last is not null) Apply(last);
        FillAppsFromInventory();
    }

    private void Apply(LauncherConfig c)
    {
        _ready = false;
        LangCombo.SelectedIndex = c.Lang == "en" ? 1 : 0;
        TitleBox.Text = c.Title;
        ColsSlider.Value = c.Columns;
        Bg1Box.Text = c.Background1; Bg2Box.Text = c.Background2;
        AccentBox.Text = c.Accent; TextColorBox.Text = c.TextColor;
        ClockCheck.IsChecked = c.ShowClock; AllAppsCheck.IsChecked = c.ShowAllApps; LargeCheck.IsChecked = c.LargeText;
        _favorites.Clear(); _favorites.AddRange(c.Favorites);
        _hidden.Clear(); _hidden.AddRange(c.Hidden);
        _ready = true;
        RefreshLists();
        Render();
    }

    private LauncherConfig Current() => new LauncherConfig
    {
        Lang = (LangCombo.SelectedItem as ComboBoxItem)?.Tag as string ?? "ar",
        Title = TitleBox.Text,
        Columns = (int)ColsSlider.Value,
        Background1 = Bg1Box.Text.Trim(), Background2 = Bg2Box.Text.Trim(),
        Accent = AccentBox.Text.Trim(), TextColor = TextColorBox.Text.Trim(),
        ShowClock = ClockCheck.IsChecked == true, ShowAllApps = AllAppsCheck.IsChecked == true, LargeText = LargeCheck.IsChecked == true,
        Favorites = _favorites.ToList(), Hidden = _hidden.ToList(),
    }.Sanitized();

    private void Any_Changed(object sender, RoutedEventArgs e)
    {
        if (!_ready) return;
        Render();
    }

    // ------------------------------------------------------------- preview

    private void Render()
    {
        if (!_ready || PvBody is null) return;
        var c = Current();
        ColsText.Text = c.Columns.ToString();
        TvScreen.Background = new LinearGradientBrush(Hex(c.Background1), Hex(c.Background2), 35);
        TvScreen.FlowDirection = c.Lang == "en" ? FlowDirection.LeftToRight : FlowDirection.RightToLeft;
        var text = new SolidColorBrush(Hex(c.TextColor));
        var accent = new SolidColorBrush(Hex(c.Accent));
        PvTitle.Text = c.Title; PvTitle.Foreground = text;
        PvClock.Foreground = text; PvClock.Visibility = c.ShowClock ? Visibility.Visible : Visibility.Collapsed;
        PvTitle.FontSize = c.LargeText ? 30 : 25;

        PvBody.Children.Clear();
        bool ar = c.Lang != "en";
        var rest = _apps.Where(a => !_favorites.Contains(a) && !_hidden.Contains(a)).ToList();
        bool first = true;
        void Section(string title, IEnumerable<string> names, int maxRows)
        {
            var list = names.ToList();
            if (list.Count == 0) return;
            PvBody.Children.Add(new TextBlock { Text = title, Foreground = text, Opacity = 0.85, FontSize = c.LargeText ? 20 : 17, FontWeight = FontWeights.SemiBold, Margin = new Thickness(2, 10, 2, 6) });
            var grid = new System.Windows.Controls.Primitives.UniformGrid { Columns = c.Columns };
            foreach (var n in list.Take(c.Columns * maxRows))
            {
                var tile = new Border
                {
                    Height = 880.0 / c.Columns * 9 / 16 * 0.78, CornerRadius = new CornerRadius(10), Margin = new Thickness(6, 4, 6, 4),
                    Background = new SolidColorBrush(Color.FromArgb(0x33, 255, 255, 255)),
                    BorderBrush = first ? accent : Brushes.Transparent, BorderThickness = new Thickness(3),
                    Child = new TextBlock
                    {
                        Text = Short(n), Foreground = text, FontSize = c.LargeText ? 15 : 13, TextAlignment = TextAlignment.Center,
                        VerticalAlignment = VerticalAlignment.Center, Margin = new Thickness(6), TextWrapping = TextWrapping.Wrap,
                    },
                };
                if (first) tile.RenderTransform = new ScaleTransform(1.06, 1.06, 60, 30);
                first = false;
                grid.Children.Add(tile);
            }
            PvBody.Children.Add(grid);
        }
        var sample = _apps.Count == 0 ? new[] { "YouTube", "Shahid", "Cinemana", "Netflix", "Qi TV", "المنصة", "Kodi", "Browser" }.ToList() : null;
        if (sample is not null) Section(ar ? "كل التطبيقات" : "All apps", sample, 2);
        else
        {
            Section(ar ? "المفضلة" : "Favorites", _favorites, 1);
            if (c.ShowAllApps || _favorites.Count == 0) Section(ar ? "كل التطبيقات" : "All apps", rest, 2);
        }
        Section(ar ? "النظام" : "System", new[] { ar ? "الإعدادات" : "Settings", ar ? "الواجهة الأصلية" : "Original home", ar ? "التطبيقات المخفية" : "Hidden apps" }, 1);
        PreviewNote.Text = Loc.Pick("المعاينة تقريبية: على التلفزيون تظهر صور البانر الحقيقية للتطبيقات.", "Approximate preview: the TV shows each app's real banner.");
    }

    private static string Short(string pkg)
    {
        if (!pkg.Contains('.')) return pkg;
        var parts = pkg.Split('.');
        var last = parts.Last();
        return last is "tv" or "android" or "app" or "leanback" && parts.Length > 1 ? parts[^2] + "." + last : last;
    }

    // ------------------------------------------------------------- favourites

    private void FillAppsFromInventory()
    {
        _apps = AppState.I.Inventory.Where(p => p.HasLauncherActivity && p.State == Core.Packages.PackageState.Enabled)
            .Select(p => p.Name).Where(n => n != Core.Adb.LauncherInfo.Package).OrderBy(n => n).ToList();
        RefreshLists();
        Render();
    }

    private void RefreshLists()
    {
        if (AllAppsList is null) return;
        AllAppsList.ItemsSource = _apps.Where(a => !_favorites.Contains(a) && !_hidden.Contains(a)).ToList();
        FavList.ItemsSource = _favorites.ToList();
        HiddenList.ItemsSource = _hidden.ToList();
    }

    private async void LoadApps_Click(object sender, RoutedEventArgs e)
    {
        if (!Ui.NeedSession(out var s)) return;
        await Busy(async () =>
        {
            AppState.I.Inventory = await s.InventoryAsync(AppState.I.Catalog);
            FillAppsFromInventory();
        });
    }

    private void AddFav_Click(object sender, RoutedEventArgs e)
    {
        foreach (var x in AllAppsList.SelectedItems.Cast<string>().ToList()) if (!_favorites.Contains(x)) _favorites.Add(x);
        RefreshLists(); Render();
    }
    private void RemoveFav_Click(object sender, RoutedEventArgs e)
    {
        if (FavList.SelectedItem is string x) _favorites.Remove(x);
        RefreshLists(); Render();
    }
    private void Hide_Click(object sender, RoutedEventArgs e)
    {
        foreach (var x in AllAppsList.SelectedItems.Cast<string>().ToList()) if (!_hidden.Contains(x)) _hidden.Add(x);
        RefreshLists(); Render();
    }
    private void Unhide_Click(object sender, RoutedEventArgs e)
    {
        if (HiddenList.SelectedItem is string x) _hidden.Remove(x);
        RefreshLists(); Render();
    }
    private void Up_Click(object sender, RoutedEventArgs e) => Move(-1);
    private void Down_Click(object sender, RoutedEventArgs e) => Move(1);
    private void Move(int d)
    {
        if (FavList.SelectedItem is not string x) return;
        var i = _favorites.IndexOf(x); var j = i + d;
        if (j < 0 || j >= _favorites.Count) return;
        (_favorites[i], _favorites[j]) = (_favorites[j], _favorites[i]);
        RefreshLists(); FavList.SelectedItem = x; Render();
    }

    // ------------------------------------------------------------- device steps

    private void Show(StepResult r)
    {
        StepStatus.Text = (r.Ok ? "✓ " : "✗ ") + (Loc.I.IsArabic ? r.Ar : r.En);
        StepStatus.Foreground = (Brush)FindResource(r.Ok ? "Ok" : "Danger");
        if (!r.Ok && r.Detail is { Length: > 0 }) Core.Storage.Log.Write("detail: " + r.Detail);
        UpdateTarget();
    }

    private async void Install_Click(object sender, RoutedEventArgs e)
    {
        if (!Ui.NeedSession(out var s) || !Ui.AcceptUntested(s, Feature.InstallLauncher)) return;
        if (!Ui.ConfirmOnDevice(Loc.Pick("تثبيت واجهة IBRA (tv.ibra.tuner.launcher). الواجهة الأصلية تبقى هي الرئيسية لحد الخطوة 4، وتُحفظ قبل أي تغيير.",
                                        "Install the IBRA launcher (tv.ibra.tuner.launcher). The original Home stays the default until step 4 and is saved first."))) return;
        var apk = AppState.ExtractApk("ibra-tv-launcher.apk");
        if (apk is null) { Ui.Info("launcher APK missing from this build"); return; }
        await Busy(async () => Show(await new LauncherManager(s).InstallAsync(apk)));
    }

    private async void Push_Click(object sender, RoutedEventArgs e)
    {
        if (!Ui.NeedSession(out var s) || !Ui.AcceptUntested(s, Feature.PushLauncherTheme)) return;
        var cfg = Current();
        if (!Ui.ConfirmOnDevice(Loc.Pick($"إرسال الشكل: {cfg.Columns} أعمدة، {cfg.Favorites.Count} مفضلة، {cfg.Hidden.Count} مخفية.",
                                        $"Send look: {cfg.Columns} columns, {cfg.Favorites.Count} favorites, {cfg.Hidden.Count} hidden."))) return;
        await Busy(async () => Show(await new LauncherManager(s).PushConfigAsync(cfg)));
    }

    private async void Open_Click(object sender, RoutedEventArgs e)
    {
        if (!Ui.NeedSession(out var s)) return;
        await Busy(async () => Show(await new LauncherManager(s).OpenForTestAsync()));
    }

    private async void SetHome_Click(object sender, RoutedEventArgs e)
    {
        if (!Ui.NeedSession(out var s) || !Ui.AcceptUntested(s, Feature.SetLauncherAsHome)) return;
        if (!Ui.ConfirmOnDevice(Loc.Pick("تعيين واجهة IBRA كواجهة رئيسية (زر Home). الأصلية محفوظة وترجع بزر «رجوع للشكل القديم».",
                                        "Make the IBRA launcher the Home app. The original is saved and comes back with 'Restore my old look'."))) return;
        await Busy(async () => Show(await new LauncherManager(s).SetAsHomeAsync()));
    }

    private async void Revert_Click(object sender, RoutedEventArgs e)
    {
        if (!Ui.NeedSession(out var s)) return;
        if (!Ui.ConfirmOnDevice(Loc.T("RevertTheme") + "?")) return;
        await Busy(async () =>
        {
            var r = await new LauncherManager(s).RevertConfigAsync();
            Show(r);
            if (r.Ok) LoadForDevice();
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
            Show(await new LauncherManager(s).RestoreOriginalAsync(remove));
        });
    }

    private async Task Busy(Func<Task> work)
    {
        IsEnabled = false; Mouse.OverrideCursor = Cursors.Wait;
        StepStatus.Text = Loc.T("Busy"); StepStatus.Foreground = (Brush)FindResource("Muted");
        try { await work(); }
        catch (Exception ex) { Ui.Error(ex); StepStatus.Text = ""; }
        finally { IsEnabled = true; Mouse.OverrideCursor = null; }
    }
}
