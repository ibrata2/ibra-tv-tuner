using System.Diagnostics;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Input;
using System.Windows.Media;
using IbraTvTuner.Core;
using IbraTvTuner.Core.Adb;
using IbraTvTuner.Core.Storage;
using Microsoft.Win32;

namespace IbraTvTuner.App.Pages;

public sealed record DeviceRow(DeviceProfile Profile)
{
    public string Sub => $"{Profile.Serial}  {Profile.Manufacturer} {Profile.Model}".Trim();
}

public sealed record TestLine(string Mark, Brush Brush, string Text);

public partial class DevicesPage : UserControl, IPage
{
    public DevicesPage()
    {
        InitializeComponent();
        Log.Line += line => Dispatcher.InvokeAsync(() =>
        {
            LogBox.AppendText(line + Environment.NewLine);
            LogBox.ScrollToEnd();
        });
        AppState.I.DevicesChanged += Reload;
        FillRoles();
        Reload();
    }

    public void OnShown() { UpdateAdb(); Reload(); }
    public void OnLanguageChanged() { FillRoles(); Reload(); UpdateAdb(); }

    private void FillRoles()
    {
        var sel = RoleCombo.SelectedIndex;
        RoleCombo.ItemsSource = new[] { Loc.T("RoleUnknown"), Loc.T("RoleScreen"), Loc.T("RoleBox") };
        RoleCombo.SelectedIndex = sel;
    }

    private void UpdateAdb()
    {
        var p = AppState.I.AdbPath;
        AdbPathBox.Text = p ?? "";
        AdbStatus.Text = p is null ? Loc.T("AdbNotFound")
            : Loc.Pick("ADB من Android SDK Platform-Tools (Google). لا يُرفق داخل البرنامج بسبب شروط إعادة التوزيع.",
                       "ADB from Google's Android SDK Platform-Tools. Not bundled because of its redistribution terms.");
    }

    private void Reload()
    {
        var rows = AppState.I.Store.Devices.Select(d => new DeviceRow(d)).ToList();
        DeviceList.ItemsSource = rows;
        DeviceList.SelectedItem = rows.FirstOrDefault(r => r.Profile == AppState.I.Current);
    }

    private DeviceProfile? Selected => (DeviceList.SelectedItem as DeviceRow)?.Profile;

    private void DeviceList_SelectionChanged(object sender, SelectionChangedEventArgs e)
    {
        var p = Selected;
        Details.IsEnabled = p is not null;
        if (p is null) return;
        if (AppState.I.Current != p) AppState.I.Current = p;
        NickBox.Text = p.Nickname;
        RoleCombo.SelectedIndex = p.Role switch { DeviceRole.Screen => 1, DeviceRole.Box => 2, _ => 0 };
        InfoText.Text = Describe(p);
    }

    private static string Describe(DeviceProfile p) =>
        $"{p.Serial}\n{p.Manufacturer} {p.Model}\nAndroid {p.OsVersion} (SDK {p.Sdk}) · {p.Platform}\n{p.Fingerprint}\n" +
        (p.LastSeen is { } t ? $"last seen {t:yyyy-MM-dd HH:mm}" : "");

    private void SaveDevice_Click(object sender, RoutedEventArgs e)
    {
        if (Selected is not { } p) return;
        p.Nickname = NickBox.Text.Trim();
        p.Role = RoleCombo.SelectedIndex switch { 1 => DeviceRole.Screen, 2 => DeviceRole.Box, _ => DeviceRole.Unknown };
        AppState.I.SaveDevices();
    }

    private void Add_Click(object sender, RoutedEventArgs e)
    {
        if (AppState.I.Adb is null) { Ui.Info(Loc.T("AdbNotFound")); return; }
        var w = new AddDeviceWindow { Owner = Window.GetWindow(this) };
        if (w.ShowDialog() == true && w.Result is { } p)
        {
            AppState.I.Store.Devices.Add(p);
            AppState.I.SaveDevices();
            AppState.I.Current = p;
            Reload();
        }
    }

    private async void Connect_Click(object sender, RoutedEventArgs e)
    {
        if (!Ui.NeedSession(out var s)) return;
        await Busy(async () =>
        {
            var (ok, err) = await s.ConnectAsync();
            if (!ok) { Ui.Error(err); return; }
            await s.ReadInfoAsync();
            AppState.I.SaveDevices();
            InfoText.Text = Describe(s.Profile);
        });
    }

    private async void ReadInfo_Click(object sender, RoutedEventArgs e)
    {
        if (!Ui.NeedSession(out var s)) return;
        await Busy(async () =>
        {
            await s.ReadInfoAsync();
            AppState.I.SaveDevices();
            InfoText.Text = Describe(s.Profile);
        });
    }

    private async void Remove_Click(object sender, RoutedEventArgs e)
    {
        if (Selected is not { } p) return;
        if (MessageBox.Show(Window.GetWindow(this), Loc.Pick("حذف الجهاز من القائمة فقط (لا يغيّر شي على الجهاز)؟", "Remove from the list only (nothing changes on the device)?"),
                Loc.T("ConfirmTitle"), MessageBoxButton.YesNo) != MessageBoxResult.Yes) return;
        if (AppState.I.Adb is { } adb && p.Connection == ConnectionKind.Network)
        {
            try { await adb.RunAsync(AdbActions.Disconnect(p.Host, p.Port)); } catch { }
        }
        AppState.I.Store.Devices.Remove(p);
        AppState.I.Current = null;
        AppState.I.SaveDevices();
        Reload();
    }

    private void BrowseAdb_Click(object sender, RoutedEventArgs e)
    {
        var dlg = new OpenFileDialog { Filter = "adb.exe|adb.exe|*.exe|*.exe", Title = "adb.exe" };
        if (dlg.ShowDialog() != true) return;
        AppState.I.Settings.AdbPath = dlg.FileName;
        AppState.I.Settings.Save();
        UpdateAdb();
    }

    private async void DownloadAdb_Click(object sender, RoutedEventArgs e)
    {
        var text = Loc.Pick(
            "سيتم تحميل Android SDK Platform-Tools من موقع Google الرسمي:\n" + AdbLocator.OfficialWindowsZip +
            "\n\nالتحميل يعني موافقتك على شروط Google الخاصة بـ Android SDK. تقدر تقرأها بالصفحة الرسمية قبل الموافقة.\nموافق؟",
            "Android SDK Platform-Tools will be downloaded from Google's official site:\n" + AdbLocator.OfficialWindowsZip +
            "\n\nDownloading means you accept Google's Android SDK terms. You can read them on the official page first.\nAccept?");
        var r = MessageBox.Show(Window.GetWindow(this), text, "Platform-Tools", MessageBoxButton.YesNoCancel, MessageBoxImage.Question, MessageBoxResult.Cancel);
        if (r == MessageBoxResult.No)
        {
            try { Process.Start(new ProcessStartInfo(AdbLocator.OfficialPage) { UseShellExecute = true }); } catch { }
            return;
        }
        if (r != MessageBoxResult.Yes) return;
        await Busy(async () =>
        {
            var path = await AppState.I.DownloadPlatformToolsAsync();
            AppState.I.Settings.AcceptedPlatformToolsTerms = true;
            AppState.I.Settings.Save();
            UpdateAdb();
            if (path is null) Ui.Info(Loc.Pick("ما لگيت adb.exe بعد الفك.", "adb.exe not found after extraction."));
        });
    }

    private async void SelfTest_Click(object sender, RoutedEventArgs e)
    {
        if (!Ui.NeedSession(out var s)) return;
        var apk = AppState.ExtractApk("ibra-tv-testapp.apk");
        if (apk is null) { Ui.Info("test APK missing from this build"); return; }
        if (!Ui.ConfirmOnDevice(Loc.T("SelfTestHint"))) return;
        var lines = new List<TestLine>();
        SelfTestList.ItemsSource = null;
        await Busy(async () =>
        {
            var (ok, err) = await s.ConnectAsync();
            if (!ok) { Ui.Error(err); return; }
            var progress = new Progress<SelfTestStep>(st =>
            {
                lines.Add(new TestLine(st.Ok ? "✓" : "✗", st.Ok ? (Brush)FindResource("Ok") : (Brush)FindResource("Danger"),
                    (Loc.I.IsArabic ? st.NameAr : st.NameEn) + " — " + st.Detail.Replace("\n", " ").Trim()));
                SelfTestList.ItemsSource = null;
                SelfTestList.ItemsSource = lines;
            });
            await new SelfTest(s, AppState.I.Catalog).RunAsync(apk, progress);
            AppState.I.SaveDevices();
        });
    }

    private async Task Busy(Func<Task> work)
    {
        IsEnabled = false;
        Mouse.OverrideCursor = Cursors.Wait;
        try { await work(); }
        catch (Exception ex) { Ui.Error(ex); }
        finally { IsEnabled = true; Mouse.OverrideCursor = null; }
    }
}
