using System.Windows;
using System.Windows.Input;
using IbraTvTuner.Core;
using IbraTvTuner.Core.Adb;
using IbraTvTuner.Core.Discovery;

namespace IbraTvTuner.App;

public sealed record UsbItem(AdbDeviceLine Line)
{
    public string Display => $"{Line.Serial}  [{Line.State}]  {string.Join(' ', Line.Info.Select(kv => kv.Key + ":" + kv.Value))}";
}

public sealed record FoundItem(FoundDevice Device)
{
    public string Display => $"{Device.Host}:{Device.Port}  ·  {Device.Source}{(Device.IsPairingService ? "  (pairing)" : "")}  ·  {Device.Name}";
}

public partial class AddDeviceWindow : Window
{
    public DeviceProfile? Result { get; private set; }
    private readonly IAdbTransport _adb;

    public AddDeviceWindow()
    {
        InitializeComponent();
        _adb = AppState.I.Adb!;
    }

    private void Status(string s) => StatusText.Text = s;

    private static bool TryPort(string s, out int port) => int.TryParse(s.Trim(), out port) && Validators.IsPort(port);

    private async Task<bool> TryConnect(DeviceProfile p)
    {
        Status(Loc.T("Busy"));
        var s = new DeviceSession(_adb, p);
        var (ok, err) = await s.ConnectAsync();
        if (ok)
        {
            try { await s.ReadInfoAsync(); } catch (Exception ex) { Ui.Error(ex); }
            Result = p;
            DialogResult = true;
            return true;
        }
        Status(err?.Message(Loc.I.IsArabic) ?? Loc.T("Error"));
        var add = MessageBox.Show(this,
            (err?.Message(Loc.I.IsArabic) ?? "") + "\n\n" + Loc.Pick("تضيفه للقائمة وتحاول الاتصال لاحقاً؟", "Add it to the list and connect later?"),
            Loc.T("Error"), MessageBoxButton.YesNo, MessageBoxImage.Warning, MessageBoxResult.No);
        if (add == MessageBoxResult.Yes) { Result = p; DialogResult = true; return true; }
        return false;
    }

    private async Task Guard(Func<Task> work)
    {
        IsEnabled = false; Mouse.OverrideCursor = Cursors.Wait;
        try { await work(); }
        catch (Exception ex) { Ui.Error(ex); }
        finally { if (IsLoaded) IsEnabled = true; Mouse.OverrideCursor = null; }
    }

    private async void AddNetwork_Click(object sender, RoutedEventArgs e)
    {
        var host = NetHost.Text.Trim();
        if (!Validators.IsHost(host)) { Status(Loc.Pick("IP غير صالح.", "Invalid IP.")); return; }
        if (!TryPort(NetPort.Text, out var port)) { Status(Loc.Pick("منفذ غير صالح.", "Invalid port.")); return; }
        await Guard(() => TryConnect(new DeviceProfile { Host = host, Port = port, Connection = ConnectionKind.Network }));
    }

    private async void PairConnect_Click(object sender, RoutedEventArgs e)
    {
        var host = WHost.Text.Trim();
        var code = WCode.Password.Trim();
        if (!Validators.IsHost(host)) { Status(Loc.Pick("IP غير صالح.", "Invalid IP.")); return; }
        if (!TryPort(WConnPort.Text, out var connPort)) { Status(Loc.Pick("منفذ الاتصال غير صالح.", "Invalid connect port.")); return; }
        var wantsPair = WPairPort.Text.Trim().Length > 0 || code.Length > 0;
        await Guard(async () =>
        {
            if (wantsPair)
            {
                if (!TryPort(WPairPort.Text, out var pairPort)) { Status(Loc.Pick("منفذ الاقتران غير صالح.", "Invalid pairing port.")); return; }
                if (pairPort == connPort) { Status(Loc.Pick("منفذ الاقتران ومنفذ الاتصال مختلفين دائماً. راجع الشاشتين.", "The pairing and connect ports are always different. Check both screens.")); return; }
                if (!Validators.IsPairingCode(code)) { Status(Loc.Pick("الرمز 6 أرقام.", "The code is 6 digits.")); return; }
                Status(Loc.T("Busy"));
                var (ok, err) = await DeviceSession.PairAsync(_adb, host, pairPort, code);
                WCode.Clear(); // never kept
                if (!ok) { Status(err?.Message(Loc.I.IsArabic) ?? ""); Ui.Error(err); return; }
            }
            await TryConnect(new DeviceProfile { Host = host, Port = connPort, Connection = ConnectionKind.Network });
        });
    }

    private async void RefreshUsb_Click(object sender, RoutedEventArgs e)
    {
        await Guard(async () =>
        {
            var r = await _adb.RunAsync(AdbActions.Devices());
            UsbList.ItemsSource = AdbParsers.ParseDevices(r.StdOut)
                .Where(d => !d.Serial.Contains(':') && !d.Serial.Contains("._adb-tls"))
                .Select(d => new UsbItem(d)).ToList();
        });
    }

    private async void UseUsb_Click(object sender, RoutedEventArgs e)
    {
        if (UsbList.SelectedItem is not UsbItem u) return;
        if (!Validators.IsSerial(u.Line.Serial)) return;
        await Guard(() => TryConnect(new DeviceProfile { Connection = ConnectionKind.Usb, UsbSerial = u.Line.Serial, Host = "", Port = 0 }));
    }

    private async void Scan_Click(object sender, RoutedEventArgs e)
    {
        ScanBtn.IsEnabled = false;
        try
        {
            var results = new List<FoundDevice>();
            try { results.AddRange(await LanDiscovery.MdnsAsync(_adb)); } catch { }
            var prog = new Progress<int>(v => ScanProgress.Value = v);
            results.AddRange(await LanDiscovery.ScanPortAsync(5555, 350, prog));
            FoundList.ItemsSource = results.Select(r => new FoundItem(r)).ToList();
            if (results.Count == 0) Status(Loc.Pick("ما ظهر شي. تأكد إن تصحيح ADB مفعّل وإن الجهاز على نفس الشبكة.", "Nothing found. Check ADB debugging is on and the device is on this network."));
        }
        finally { ScanBtn.IsEnabled = true; }
    }

    private void UseFound_Click(object sender, RoutedEventArgs e)
    {
        if (FoundList.SelectedItem is not FoundItem f) return;
        var d = f.Device;
        if (d.Source == "mDNS")
        {
            WHost.Text = d.Host;
            if (d.IsPairingService) WPairPort.Text = d.Port.ToString(); else WConnPort.Text = d.Port.ToString();
            Tabs.SelectedIndex = 1;
        }
        else
        {
            NetHost.Text = d.Host;
            NetPort.Text = d.Port.ToString();
            Tabs.SelectedIndex = 0;
        }
    }
}
