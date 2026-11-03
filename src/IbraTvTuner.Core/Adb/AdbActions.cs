namespace IbraTvTuner.Core.Adb;

/// <summary>
/// The complete list of things this app can ask adb to do. Each method validates its inputs
/// and returns a ready <see cref="AdbCommand"/>. There is deliberately no "run arbitrary shell" entry.
/// </summary>
public static class AdbActions
{
    private static readonly TimeSpan Short = TimeSpan.FromSeconds(15);
    private static readonly TimeSpan Medium = TimeSpan.FromSeconds(45);
    private static readonly TimeSpan Long = TimeSpan.FromMinutes(4);

    private static AdbCommand Make(TimeSpan t, params string[] args) => new(args, "adb " + string.Join(' ', args), t);
    private static AdbCommand Shell(string serial, TimeSpan t, params string[] shellArgs)
    {
        var all = new List<string> { "-s", Validators.RequireSerial(serial), "shell" };
        all.AddRange(shellArgs);
        return new AdbCommand(all, "adb " + string.Join(' ', all), t);
    }

    // ---------- server / connection ----------
    public static AdbCommand Version() => Make(Short, "version");
    public static AdbCommand StartServer() => Make(Medium, "start-server");
    public static AdbCommand Devices() => Make(Short, "devices", "-l");
    public static AdbCommand MdnsCheck() => Make(Short, "mdns", "check");
    public static AdbCommand MdnsServices() => Make(Short, "mdns", "services");

    public static AdbCommand Connect(string host, int port)
    {
        var target = Endpoint(host, port);
        return Make(Medium, "connect", target);
    }

    public static AdbCommand Disconnect(string host, int port) => Make(Short, "disconnect", Endpoint(host, port));

    /// <summary>Wireless debugging pairing. The code is masked in <see cref="AdbCommand.Display"/>.</summary>
    public static AdbCommand Pair(string host, int pairingPort, string code)
    {
        var target = Endpoint(host, pairingPort);
        Validators.RequirePairingCode(code);
        return new AdbCommand(new[] { "pair", target, code }, $"adb pair {target} ******", Medium);
    }

    public static string Endpoint(string host, int port)
    {
        host = Validators.RequireHost(host);
        Validators.RequirePort(port);
        return host.Contains(':') ? $"[{host}]:{port}" : $"{host}:{port}";
    }

    // ---------- read-only queries ----------
    public static AdbCommand GetState(string serial) => Make(Short, "-s", Validators.RequireSerial(serial), "get-state");
    public static AdbCommand GetProp(string serial, string prop) => Shell(serial, Short, "getprop", Validators.RequireProp(prop));
    public static AdbCommand GetAllProps(string serial) => Shell(serial, Short, "getprop");
    public static AdbCommand ListFeatures(string serial) => Shell(serial, Short, "pm", "list", "features");

    public enum PackageFilter { All, System, ThirdParty, Disabled, Enabled, IncludingUninstalled }

    public static AdbCommand ListPackages(string serial, PackageFilter f, string? onlyPackage = null)
    {
        var args = new List<string> { "pm", "list", "packages" };
        switch (f)
        {
            case PackageFilter.System: args.Add("-s"); break;
            case PackageFilter.ThirdParty: args.Add("-3"); break;
            case PackageFilter.Disabled: args.Add("-d"); break;
            case PackageFilter.Enabled: args.Add("-e"); break;
            case PackageFilter.IncludingUninstalled: args.Add("-u"); break;
        }
        if (onlyPackage is not null) args.Add(Validators.RequirePackage(onlyPackage));
        return Shell(serial, Medium, args.ToArray());
    }

    /// <summary>Which activity answers HOME right now (Android 7+).</summary>
    public static AdbCommand ResolveHome(string serial) =>
        Shell(serial, Short, "cmd", "package", "resolve-activity", "--brief", "-a", "android.intent.action.MAIN", "-c", "android.intent.category.HOME");

    public static AdbCommand QueryHomeActivities(string serial) =>
        Shell(serial, Short, "cmd", "package", "query-activities", "--brief", "-a", "android.intent.action.MAIN", "-c", "android.intent.category.HOME");

    public static AdbCommand QueryLauncherApps(string serial, bool leanback) =>
        Shell(serial, Short, "cmd", "package", "query-activities", "--brief", "-a", "android.intent.action.MAIN", "-c",
            leanback ? "android.intent.category.LEANBACK_LAUNCHER" : "android.intent.category.LAUNCHER");

    public static AdbCommand DefaultInputMethod(string serial) =>
        Shell(serial, Short, "settings", "get", "secure", "default_input_method");

    public static AdbCommand PackageVersion(string serial, string pkg) =>
        Shell(serial, Short, "dumpsys", "package", Validators.RequirePackage(pkg));

    // ---------- changes (each one is reversible and recorded by ChangeExecutor) ----------
    public static AdbCommand DisableForUser(string serial, string pkg, int user = 0) =>
        Shell(serial, Short, "pm", "disable-user", "--user", user.ToString(), Validators.RequirePackage(pkg));

    public static AdbCommand Enable(string serial, string pkg, int user = 0) =>
        Shell(serial, Short, "pm", "enable", "--user", user.ToString(), Validators.RequirePackage(pkg));

    /// <summary>Removes an app for user 0 only; system apps stay on the device and can be brought back.</summary>
    public static AdbCommand UninstallForUser(string serial, string pkg, int user = 0) =>
        Shell(serial, Short, "pm", "uninstall", "-k", "--user", user.ToString(), Validators.RequirePackage(pkg));

    /// <summary>Brings back a system app removed with <see cref="UninstallForUser"/> (Android 8+).</summary>
    public static AdbCommand InstallExisting(string serial, string pkg, int user = 0) =>
        Shell(serial, Short, "cmd", "package", "install-existing", "--user", user.ToString(), Validators.RequirePackage(pkg));

    public static AdbCommand SetHomeActivity(string serial, string component, int user = 0) =>
        Shell(serial, Short, "cmd", "package", "set-home-activity", "--user", user.ToString(), Validators.RequireComponent(component));

    // ---------- our own APKs ----------
    public static AdbCommand InstallOwnApk(string serial, string localApkPath)
    {
        if (!File.Exists(localApkPath) || !localApkPath.EndsWith(".apk", StringComparison.OrdinalIgnoreCase))
            throw new InvalidInputException("apk", localApkPath);
        return Make(Long, "-s", Validators.RequireSerial(serial), "install", "-r", localApkPath);
    }

    /// <summary>Only our own packages may be fully uninstalled by this app.</summary>
    public static AdbCommand UninstallOwnApp(string serial, string pkg)
    {
        if (!OwnPackages.Contains(pkg)) throw new InvalidInputException("own package", pkg);
        return Make(Medium, "-s", Validators.RequireSerial(serial), "uninstall", pkg);
    }

    public static readonly IReadOnlySet<string> OwnPackages = new HashSet<string>
    {
        LauncherInfo.Package, LauncherInfo.TestAppPackage,
    };

    // ---------- launcher config / UI ----------
    public static AdbCommand SendLauncherConfig(string serial, string base64Json) =>
        Shell(serial, Short, "am", "broadcast", "-n", LauncherInfo.ConfigReceiver, "-a", LauncherInfo.ConfigAction,
            "--es", "cfg", Validators.RequireBase64(base64Json));

    public static AdbCommand StartActivity(string serial, string component) =>
        Shell(serial, Medium, "am", "start", "-W", "-n", Validators.RequireComponent(component));

    public static AdbCommand StartHome(string serial) =>
        Shell(serial, Medium, "am", "start", "-W", "-a", "android.intent.action.MAIN", "-c", "android.intent.category.HOME");

    public static AdbCommand ForceStop(string serial, string pkg) =>
        Shell(serial, Short, "am", "force-stop", Validators.RequirePackage(pkg));

    public enum Key { Home, Back, Up, Down, Left, Right, Center }

    public static AdbCommand KeyEvent(string serial, Key k) => Shell(serial, Short, "input", "keyevent", k switch
    {
        Key.Home => "KEYCODE_HOME",
        Key.Back => "KEYCODE_BACK",
        Key.Up => "KEYCODE_DPAD_UP",
        Key.Down => "KEYCODE_DPAD_DOWN",
        Key.Left => "KEYCODE_DPAD_LEFT",
        Key.Right => "KEYCODE_DPAD_RIGHT",
        _ => "KEYCODE_DPAD_CENTER",
    });
}

public static class LauncherInfo
{
    public const string Package = "tv.ibra.tuner.launcher";
    public const string MainActivity = "tv.ibra.tuner.launcher/.HomeActivity";
    public const string ConfigReceiver = "tv.ibra.tuner.launcher/.ConfigReceiver";
    public const string ConfigAction = "tv.ibra.tuner.APPLY_CONFIG";
    public const string TestAppPackage = "tv.ibra.tuner.testapp";
    public const string TestAppActivity = "tv.ibra.tuner.testapp/.MainActivity";
}
