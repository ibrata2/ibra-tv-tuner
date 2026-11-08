using System.Diagnostics;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Navigation;
using IbraTvTuner.App.Pages;
using IbraTvTuner.Core;
using IbraTvTuner.Core.Platforms;

namespace IbraTvTuner.App;

public sealed record DeviceItem(DeviceProfile Profile)
{
    public string Display => $"{Profile.Title} · {AppState.RoleName(Profile.Role)} · {Profile.Serial}";
}

public partial class MainWindow : Window
{
    private readonly Dictionary<string, UserControl> _pages = new();

    public MainWindow()
    {
        InitializeComponent();
        VersionText.Text = "v" + (typeof(MainWindow).Assembly.GetName().Version?.ToString(3) ?? "0.1.0");
        AppState.I.DevicesChanged += ReloadDevices;
        AppState.I.CurrentChanged += UpdatePlatform;
        Loc.I.PropertyChanged += (_, _) => { ReloadDevices(); UpdatePlatform(); };
        ReloadDevices();
        Show("devices");
        AutoUpdateBox.IsChecked = AppState.I.Settings.AutoUpdate;
        Loaded += async (_, _) =>
        {
            await Task.Delay(4000);
            await Updater.AutoCheckAsync(t => VersionText.Text = t);
        };
    }

    private async void Update_Click(object sender, RoutedEventArgs e) =>
        await Updater.CheckAsync(interactive: true, t => VersionText.Text = t);

    private void AutoUpdate_Changed(object sender, RoutedEventArgs e)
    {
        if (!IsInitialized) return;
        AppState.I.Settings.AutoUpdate = AutoUpdateBox.IsChecked == true;
        AppState.I.Settings.Save();
    }

    private UserControl Page(string key)
    {
        if (_pages.TryGetValue(key, out var p)) return p;
        p = key switch
        {
            "appearance" => new AppearancePage(),
            "apps" => new AppsPage(),
            "changes" => new ChangesPage(),
            "compat" => new CompatibilityPage(),
            _ => new DevicesPage(),
        };
        _pages[key] = p;
        return p;
    }

    private void Show(string key)
    {
        var p = Page(key);
        PageHost.Content = p;
        if (p is IPage ip) ip.OnShown();
    }

    private void Nav_Checked(object sender, RoutedEventArgs e)
    {
        if (!IsInitialized || PageHost is null) return;
        if (sender is RadioButton rb && rb.Tag is string key) Show(key);
    }

    private bool _reloading;
    private void ReloadDevices()
    {
        _reloading = true;
        var current = AppState.I.Current;
        DeviceCombo.ItemsSource = AppState.I.Store.Devices.Select(d => new DeviceItem(d)).ToList();
        DeviceCombo.SelectedItem = (DeviceCombo.ItemsSource as List<DeviceItem>)?.FirstOrDefault(d => d.Profile == current);
        _reloading = false;
    }

    private void DeviceCombo_SelectionChanged(object sender, SelectionChangedEventArgs e)
    {
        if (_reloading) return;
        if (DeviceCombo.SelectedItem is DeviceItem d && d.Profile != AppState.I.Current) AppState.I.Current = d.Profile;
    }

    private void UpdatePlatform()
    {
        var c = AppState.I.Current;
        PlatformText.Text = c is null ? "" :
            $"{PlatformRegistry.Get(c.Platform == PlatformId.Unknown ? PlatformId.AndroidTv : c.Platform).DisplayName}" +
            (string.IsNullOrEmpty(c.OsVersion) ? "" : $" · Android {c.OsVersion}");
        if (!_reloading && (DeviceCombo.SelectedItem as DeviceItem)?.Profile != c)
            ReloadDevices();
    }

    private void Lang_Click(object sender, RoutedEventArgs e)
    {
        Loc.I.Lang = Loc.I.IsArabic ? "en" : "ar";
        AppState.I.Settings.Language = Loc.I.Lang;
        AppState.I.Settings.Save();
        foreach (var p in _pages.Values.OfType<IPage>()) p.OnLanguageChanged();
    }

    private void Link_Navigate(object sender, RequestNavigateEventArgs e)
    {
        try { Process.Start(new ProcessStartInfo(e.Uri.AbsoluteUri) { UseShellExecute = true }); } catch { }
        e.Handled = true;
    }
}

public interface IPage
{
    void OnShown();
    void OnLanguageChanged();
}
