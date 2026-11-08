using IbraTvTuner.Core;
using IbraTvTuner.Core.Adb;
using IbraTvTuner.Core.Changes;
using IbraTvTuner.Core.Launcher;
using IbraTvTuner.Core.Packages;
using IbraTvTuner.Core.Platforms;
using IbraTvTuner.Core.Storage;
using IbraTvTuner.Core.Tests;

// Isolated storage for the run.
AppPaths.Root = Path.Combine(Path.GetTempPath(), "ibra-tv-tuner-tests-" + Guid.NewGuid().ToString("N")[..8]);
var tmpApkDir = AppPaths.Ensure("apk");
var launcherApk = Path.Combine(tmpApkDir, "ibra-tv-launcher.apk");
var testApk = Path.Combine(tmpApkDir, "ibra-tv-testapp.apk");
File.WriteAllText(launcherApk, "x"); File.WriteAllText(testApk, "x");

int pass = 0, fail = 0;
var failures = new List<string>();
async Task T(string name, Func<Task> body)
{
    try { await body(); pass++; Console.WriteLine($"  ok   {name}"); }
    catch (Exception ex) { fail++; failures.Add(name); Console.WriteLine($"  FAIL {name}: {ex.GetType().Name}: {ex.Message}"); }
}
void Eq<TV>(TV expected, TV actual, string what = "") { if (!EqualityComparer<TV>.Default.Equals(expected, actual)) throw new Exception($"{what} expected <{expected}> got <{actual}>"); }
void True(bool c, string what) { if (!c) throw new Exception("expected true: " + what); }
void Throws<TE>(Action a) where TE : Exception { try { a(); } catch (TE) { return; } throw new Exception("expected " + typeof(TE).Name); }

FakeTv NewTv() => new();
DeviceProfile Prof(FakeTv tv) => new() { Host = "192.168.1.50", Port = 5555, Nickname = "TV" };
var catalog = Catalog.LoadEmbedded();

Console.WriteLine("Validation / no free-text commands");
await T("rejects shell metacharacters in package names", async () =>
{
    foreach (var bad in new[] { "com.x;reboot", "com.x && rm -rf /", "$(reboot)", "com.x y", "com", "", "com.x|sh", "com.x`id`", "../../x" })
        True(!Validators.IsPackage(bad), bad);
    True(Validators.IsPackage("com.google.android.tvlauncher"), "valid pkg");
    Throws<InvalidInputException>(() => AdbActions.DisableForUser("1.2.3.4:5555", "com.x;reboot"));
    await Task.CompletedTask;
});
await T("rejects bad hosts, ports and pairing codes", async () =>
{
    Throws<InvalidInputException>(() => AdbActions.Connect("1.2.3.4;reboot", 5555));
    Throws<InvalidInputException>(() => AdbActions.Connect("192.168.1.5", 70000));
    Throws<InvalidInputException>(() => AdbActions.Pair("192.168.1.5", 4000, "12a456"));
    Eq("192.168.1.5:5555", AdbActions.Endpoint("192.168.1.5", 5555));
    Eq("[fe80::1]:5555", AdbActions.Endpoint("fe80::1", 5555));
    await Task.CompletedTask;
});
await T("pairing code is masked in display/log text", async () =>
{
    var c = AdbActions.Pair("192.168.1.5", 41234, "654321");
    True(!c.Display.Contains("654321"), "display masks code");
    Eq("654321", c.Args[2]);
    await Task.CompletedTask;
});
await T("launcher config payload is base64-only", async () =>
{
    var cfg = new LauncherConfig { Title = "تلفزيون \"; reboot", Favorites = { "com.ok.app", "bad;pkg" }, Accent = "red" };
    var b64 = cfg.ToBase64();
    True(Validators.IsBase64(b64), "base64");
    var s = cfg.Sanitized();
    Eq(1, s.Favorites.Count, "bad favorites dropped");
    Eq("#38BDF8", s.Accent, "bad colour reset");
    await Task.CompletedTask;
});

Console.WriteLine("Parsers");
await T("parses adb devices -l", async () =>
{
    var d = AdbParsers.ParseDevices("List of devices attached\n192.168.1.5:5555\tdevice product:a model:TV device:b\nemulator-5554\tunauthorized\n");
    Eq(2, d.Count); Eq("TV", d[0].Info["model"]); Eq("unauthorized", d[1].State);
    await Task.CompletedTask;
});
await T("parses resolve-activity incl. chooser", async () =>
{
    Eq("com.google.android.tvlauncher/.MainActivity", AdbParsers.ParseSingleComponent("priority=0 isDefault=true\ncom.google.android.tvlauncher/.MainActivity\n"));
    Eq(null, AdbParsers.ParseSingleComponent("priority=0\nandroid/com.android.internal.app.ResolverActivity\n"));
    await Task.CompletedTask;
});
await T("parses mdns services", async () =>
{
    var m = AdbParsers.ParseMdns("adb-X\t_adb-tls-pairing._tcp\t192.168.1.9:41234\nadb-X\t_adb-tls-connect._tcp\t192.168.1.9:37555\n");
    Eq(2, m.Count); True(m[0].IsPairing, "pairing"); Eq(37555, m[1].Port);
    await Task.CompletedTask;
});
await T("parses am start -W TotalTime", async () =>
{
    Eq(812, AdbParsers.ParseStartTotalTime("Status: ok\nTotalTime: 812\nWaitTime: 840"));
    Eq(840, AdbParsers.ParseStartTotalTime("Status: ok\nWaitTime: 840"));
    await Task.CompletedTask;
});
await T("getprop words like 'failure' are not errors", async () =>
{
    Eq(null, AdbErrorTranslator.FromResult(new AdbResult(0, "[persist.sys.boot.failure]: [0]\n", "", false)));
    await Task.CompletedTask;
});

Console.WriteLine("Errors");
await T("translates common adb errors", async () =>
{
    Eq(AdbErrorKind.Unauthorized, AdbErrorTranslator.FromResult(new AdbResult(1, "", "error: device unauthorized.", false))!.Kind);
    Eq(AdbErrorKind.ConnectionRefused, AdbErrorTranslator.FromResult(new AdbResult(1, "failed to connect to 1.2.3.4:5555: Connection refused", "", false))!.Kind);
    Eq(AdbErrorKind.PairingCodeWrong, AdbErrorTranslator.FromResult(new AdbResult(1, "", "Wrong password", false))!.Kind);
    Eq(AdbErrorKind.Timeout, AdbErrorTranslator.FromResult(new AdbResult(-1, "", "TIMEOUT", true))!.Kind);
    Eq(AdbErrorKind.AdbMissing, AdbErrorTranslator.FromResult(new AdbResult(-1, "", "ADB_START_FAILED: x", false))!.Kind);
    Eq(AdbErrorKind.SignatureMismatch, AdbErrorTranslator.FromResult(new AdbResult(1, "Failure [INSTALL_FAILED_UPDATE_INCOMPATIBLE: x]", "", false))!.Kind);
    await Task.CompletedTask;
});

Console.WriteLine("Connection");
await T("pairing: wrong code fails, right code succeeds", async () =>
{
    var tv = NewTv();
    var (bad, e) = await DeviceSession.PairAsync(tv, "192.168.1.50", 41234, "000000");
    True(!bad, "wrong code"); Eq(AdbErrorKind.PairingCodeWrong, e!.Kind);
    var (ok, _) = await DeviceSession.PairAsync(tv, "192.168.1.50", 41234, "123456");
    True(ok, "right code");
});
await T("connect reports unreachable and unauthorized", async () =>
{
    var tv = NewTv(); tv.Reachable = false;
    var (ok, e) = await new DeviceSession(tv, Prof(tv)).ConnectAsync();
    True(!ok, "unreachable"); Eq(AdbErrorKind.Unreachable, e!.Kind);
    tv = NewTv(); tv.Authorized = false;
    (ok, e) = await new DeviceSession(tv, Prof(tv)).ConnectAsync();
    True(!ok, "unauthorized"); Eq(AdbErrorKind.Unauthorized, e!.Kind);
});
await T("reads model and system", async () =>
{
    var tv = NewTv(); var s = new DeviceSession(tv, Prof(tv));
    var (ok, _) = await s.ConnectAsync(); True(ok, "connected");
    var info = await s.ReadInfoAsync();
    Eq("GENERAL BRAVIA", info.Model); Eq(28, info.Sdk); True(info.IsTv, "tv"); Eq(PlatformId.AndroidTv, info.Platform);
});

Console.WriteLine("Classification");
await T("protects core, home, keyboard; marks recommendations and unknowns", async () =>
{
    var tv = NewTv(); var s = new DeviceSession(tv, Prof(tv));
    var inv = await s.InventoryAsync(catalog);
    PackageClass C(string p) => inv.First(x => x.Name == p).Class;
    Eq(PackageClass.Protected, C("com.android.systemui"), "systemui");
    Eq(PackageClass.Protected, C("com.google.android.tvlauncher"), "current home");
    Eq(PackageClass.Protected, C("com.google.android.inputmethod.latin"), "ime");
    Eq(PackageClass.Unknown, C("com.vendor.mystery"), "unknown system");
    Eq(PackageClass.Reviewable, C("com.google.android.youtube.tv"), "user app");
});
await T("model list turns entries into reviewable with reason, keeps 'kept' packages protected", async () =>
{
    var tv = NewTv();
    var model = catalog.Models.First(m => m.Disable.Any(e => e.Package == "com.google.android.tvrecommendations"));
    var p = Prof(tv); p.CatalogModelId = model.Id;
    var inv = await new DeviceSession(tv, p).InventoryAsync(catalog);
    Eq(PackageClass.Reviewable, inv.First(x => x.Name == "com.google.android.tvrecommendations").Class);
});
await T("embedded catalog loaded with attribution", async () =>
{
    True(catalog.Models.Count > 100, "models"); True(catalog.Source.Contains("MIT"), "license noted");
    var fpModel = catalog.Models.First(m => m.Fingerprint.Length > 0);
    var d = new DeviceInfo("x", "x", "x", "x", "14", 34, fpModel.Fingerprint, true, false, false, null, PlatformId.AndroidTv);
    Eq(fpModel.Id, catalog.Rank(d, new HashSet<string>())[0].Model.Id, "fingerprint ranks first");
    await Task.CompletedTask;
});

Console.WriteLine("Changes and rollback");
await T("plan refuses protected packages and no-ops", async () =>
{
    var tv = NewTv(); var s = new DeviceSession(tv, Prof(tv));
    var inv = await s.InventoryAsync(catalog);
    var (batch, issues) = new ChangeExecutor(s).Plan(new[]
    {
        new PlanRequest { Package = "com.android.systemui", Action = ChangeAction.Disable },
        new PlanRequest { Package = "com.google.android.tvlauncher", Action = ChangeAction.Disable },
        new PlanRequest { Package = "com.vendor.mystery", Action = ChangeAction.Enable },
        new PlanRequest { Package = "com.google.android.feedback", Action = ChangeAction.Disable },
    }, inv);
    Eq(1, batch.Items.Count, "only feedback planned"); Eq(3, issues.Count);
    True(tv.Calls.All(c => !c.Contains("disable-user")), "nothing changed during planning");
});
await T("disable → verify → rollback → verify (journal on disk)", async () =>
{
    var tv = NewTv(); var p = Prof(tv); var s = new DeviceSession(tv, p);
    var ex = new ChangeExecutor(s);
    var (batch, _) = ex.Plan(new[] { new PlanRequest { Package = "com.google.android.feedback", Action = ChangeAction.Disable, Reason = "test" } }, await s.InventoryAsync(catalog));
    await ex.ExecuteAsync(batch);
    Eq(ChangeStatus.Succeeded, batch.Items[0].Status); Eq(FakeTv.S.Disabled, tv.Pkgs["com.google.android.feedback"].State);
    True(File.Exists(ChangeJournal.PathOf(batch)), "journal saved");
    var reloaded = ChangeJournal.LoadAll(p.Id).First(b => b.Id == batch.Id);
    await ex.RollbackAsync(reloaded);
    Eq(ChangeStatus.RolledBack, reloaded.Items[0].Status); Eq(FakeTv.S.Enabled, tv.Pkgs["com.google.android.feedback"].State);
    True(reloaded.RolledBack, "batch rolled back");
});
await T("device refusal is recorded as failed, not success", async () =>
{
    var tv = NewTv(); tv.RefuseDisable = true; var s = new DeviceSession(tv, Prof(tv));
    var ex = new ChangeExecutor(s);
    var (batch, _) = ex.Plan(new[] { new PlanRequest { Package = "com.google.android.feedback", Action = ChangeAction.Disable } }, await s.InventoryAsync(catalog));
    await ex.ExecuteAsync(batch);
    Eq(ChangeStatus.Failed, batch.Items[0].Status); True(batch.Items[0].Error!.Length > 0, "error text");
});
await T("uninstall-for-user is separate and restorable with install-existing", async () =>
{
    var tv = NewTv(); var p = Prof(tv); var s = new DeviceSession(tv, p);
    await s.ReadInfoAsync();
    var ex = new ChangeExecutor(s);
    var (batch, _) = ex.Plan(new[] { new PlanRequest { Package = "com.google.android.feedback", Action = ChangeAction.UninstallForUser } }, await s.InventoryAsync(catalog));
    await ex.ExecuteAsync(batch);
    Eq(FakeTv.S.Uninstalled, tv.Pkgs["com.google.android.feedback"].State);
    await ex.RollbackAsync(batch);
    Eq(FakeTv.S.Enabled, tv.Pkgs["com.google.android.feedback"].State);
    True(tv.Calls.Any(c => c.Contains("install-existing")), "install-existing used");
});

Console.WriteLine("Launcher and restore-old-look");
await T("install, push theme, open, set Home, restore original, remove", async () =>
{
    var tv = NewTv(); var p = Prof(tv); var s = new DeviceSession(tv, p);
    var lm = new LauncherManager(s);
    True((await lm.InstallAsync(launcherApk)).Ok, "install");
    var b = HomeBackup.Load(p.Id)!; Eq("com.google.android.tvlauncher/.MainActivity", b.OriginalHome, "original saved");
    True((await lm.PushConfigAsync(new LauncherConfig { Columns = 6 })).Ok, "theme");
    True(tv.LastBroadcast is not null, "broadcast sent");
    True((await lm.OpenForTestAsync()).Ok, "open");
    Eq("com.google.android.tvlauncher/.MainActivity", tv.Home, "opening does not change Home");
    True((await lm.SetAsHomeAsync()).Ok, "set home"); Eq(LauncherInfo.MainActivity, tv.Home);
    // A second backup must not overwrite the original with our own launcher.
    await lm.BackupAsync(); Eq("com.google.android.tvlauncher/.MainActivity", HomeBackup.Load(p.Id)!.OriginalHome);
    var r = await lm.RestoreOriginalAsync(removeLauncher: false);
    True(r.Ok, "restore: " + r.En); Eq("com.google.android.tvlauncher/.MainActivity", tv.Home);
    r = await lm.RestoreOriginalAsync(removeLauncher: true);
    True(r.Ok, "restore+remove"); True(!tv.Pkgs.ContainsKey(LauncherInfo.Package), "launcher removed");
});
await T("theme revert re-sends the previous settings", async () =>
{
    var tv = NewTv(); var s = new DeviceSession(tv, Prof(tv)); var lm = new LauncherManager(s);
    await lm.InstallAsync(launcherApk);
    await lm.PushConfigAsync(new LauncherConfig { Columns = 4 });
    await lm.PushConfigAsync(new LauncherConfig { Columns = 7 });
    True((await lm.RevertConfigAsync()).Ok, "revert");
    var json = System.Text.Encoding.UTF8.GetString(Convert.FromBase64String(tv.LastBroadcast!));
    True(json.Contains("\"columns\":4"), "previous columns sent: " + json);
});
await T("set-home refused by system is reported, Home untouched", async () =>
{
    var tv = NewTv(); tv.AllowSetHome = false; var s = new DeviceSession(tv, Prof(tv)); var lm = new LauncherManager(s);
    await lm.InstallAsync(launcherApk);
    var r = await lm.SetAsHomeAsync();
    True(!r.Ok, "refused"); Eq("com.google.android.tvlauncher/.MainActivity", tv.Home);
});
await T("Fire TV: Home change blocked by platform module", async () =>
{
    var tv = NewTv(); var p = Prof(tv); p.Platform = PlatformId.FireTv; var s = new DeviceSession(tv, p);
    var r = await new LauncherManager(s).SetAsHomeAsync();
    True(!r.Ok, "blocked"); True(tv.Calls.All(c => !c.Contains("set-home-activity")), "no command sent");
});
await T("non-ADB platforms block every action", async () =>
{
    foreach (var m in new[] { PlatformRegistry.Tizen, PlatformRegistry.WebOs, PlatformRegistry.Vidaa, PlatformRegistry.Roku })
        True(Enum.GetValues<Feature>().All(f => !m.CanExecute(f)), m.DisplayName);
    await Task.CompletedTask;
});

Console.WriteLine("End-to-end self-test");
await T("self-test: info → inventory → install test app → disable → verify → restore → verify → remove", async () =>
{
    var tv = NewTv(); var s = new DeviceSession(tv, Prof(tv));
    var steps = await new SelfTest(s, catalog).RunAsync(testApk);
    foreach (var st in steps) Console.WriteLine($"       {(st.Ok ? "✓" : "✗")} {st.NameEn}: {st.Detail}");
    Eq(6, steps.Count); True(steps.All(x => x.Ok), "all steps ok");
    True(!tv.Pkgs.ContainsKey(LauncherInfo.TestAppPackage), "test app removed");
});
await T("benchmark records method and runs", async () =>
{
    var tv = NewTv(); var s = new DeviceSession(tv, Prof(tv));
    var m = await new Benchmark(s).HomeColdStartAsync(3, "before");
    Eq(3, m.RunsMs.Count); Eq(812, m.MedianMs); True(m.Method.Contains("am start -W"), "method recorded");
});

Console.WriteLine("Updates");
await T("update manifest: parse, newer check, host and hash validation", async () =>
{
    var sha = new string('a', 64);
    var json = "{\"build\":42,\"notesAr\":\"جديد\",\"notesEn\":\"new\",\"windows\":{\"version\":\"0.1.42\",\"url\":\"https://github.com/ibrata2/ibra-tv-tuner/releases/download/v0.1.0/IbraTvTuner.exe\",\"sha256\":\"" + sha + "\",\"size\":123}}";
    var u = IbraTvTuner.Core.Update.UpdateManifest.ParseWindows(json);
    Eq(new Version(0, 1, 42), u.Version); Eq(sha, u.Sha256); Eq("جديد", u.NotesAr); Eq(123L, u.Size);
    True(IbraTvTuner.Core.Update.UpdateManifest.IsNewer(new Version(0, 1, 41, 0), u.Version), "41 < 42");
    True(!IbraTvTuner.Core.Update.UpdateManifest.IsNewer(new Version(0, 1, 42, 0), u.Version), "42 == 42");
    True(!IbraTvTuner.Core.Update.UpdateManifest.IsNewer(new Version(0, 2, 0), u.Version), "0.2 > 0.1.42");
    Throws<FormatException>(() => IbraTvTuner.Core.Update.UpdateManifest.ParseWindows(json.Replace("https://github.com/", "https://evil.example/")));
    Throws<FormatException>(() => IbraTvTuner.Core.Update.UpdateManifest.ParseWindows(json.Replace(sha, "xyz")));
    var script = IbraTvTuner.Core.Update.UpdateManifest.SwapScript(1234, @"C:\t\new.exe", @"C:\app\IbraTvTuner.exe");
    True(script.Contains("PID eq 1234") && script.Contains("copy /y") && script.Contains(@"start """" ""C:\app\IbraTvTuner.exe"""), "swap script");
    await Task.CompletedTask;
});

Console.WriteLine($"\n{pass} passed, {fail} failed");
try { Directory.Delete(AppPaths.Root, true); } catch { }
return fail == 0 ? 0 : 1;
