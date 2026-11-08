package tv.ibra.home.installer;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Installs IBRA TV HOME on a TV and makes it the Home app, or brings the original Home back.
 * Mirrors the Windows app (IBRA TV Tuner): the original Home is saved once before anything changes,
 * every step is verified on the device, and restore puts back exactly what was saved.
 * Pure Java (no Android imports) so it is testable on a desktop JVM.
 */
public final class TvInstaller {

    public static final String PKG = "tv.ibra.tuner.launcher";
    public static final String HOME = PKG + "/.HomeActivity";
    static final String RECEIVER = PKG + "/.ConfigReceiver";
    static final String REMOTE_APK = "/data/local/tmp/ibra-home.apk";

    static final List<String> NOT_LAUNCHERS = Arrays.asList(
            "com.google.android.tungsten.setupwraith", "com.android.tv.settings", "com.android.settings",
            "com.android.provision", "com.apps.tvsettings", "android");
    static final List<String> KNOWN_STOCK = Arrays.asList(
            "com.google.android.tvlauncher", "com.google.android.apps.tv.launcherx",
            "com.google.android.leanbacklauncher", "com.mitv.tvhome", "com.xiaomi.mitv.tvlauncher",
            "com.amazon.tv.launcher", "com.android.tv.launcher", "com.android.launcher3", "com.android.launcher",
            "com.droidlogic.launcher.provider", "com.amlogic.launcher", "com.mbx.launcher",
            "com.mstar.tv.tvlauncher", "com.rockchips.android.leanbacklauncher", "com.hisense.homepage");
    static final Pattern COMPONENT = Pattern.compile("^[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+/[A-Za-z0-9_.$]+$");
    static final Pattern PACKAGE = Pattern.compile("^[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+$");

    /** Per-TV memory kept on the phone (original Home + what we disabled). */
    public interface Store {
        String get(String key);
        void put(String key, String value);
    }

    public interface Log {
        void step(boolean ok, String ar);
        void info(String ar);
    }

    private final AdbClient adb;
    private final Store store;
    private final Log log;
    private String deviceKey;
    public int sdk = 0;
    public String model = "";
    public String release = "";
    /** versionName of IBRA TV HOME already on the TV, or "" when it is not installed. */
    public String launcherVersion = "";

    public TvInstaller(AdbClient adb, String deviceKey, Store store, Log log) {
        this.adb = adb;
        this.deviceKey = deviceKey;
        this.store = store;
        this.log = log;
    }

    static String pkgOf(String component) { return component == null ? null : component.split("/")[0]; }

    static boolean isOurs(String component) { return component != null && PKG.equals(pkgOf(component)); }

    /** Reads model and Android version. */
    public void probe() throws IOException {
        model = adb.shell("getprop ro.product.model").trim();
        release = adb.shell("getprop ro.build.version.release").trim();
        try { sdk = Integer.parseInt(adb.shell("getprop ro.build.version.sdk").trim()); } catch (Exception e) { sdk = 0; }
        String serial = adb.shell("getprop ro.serialno").trim();
        if (!serial.isEmpty() && !serial.equalsIgnoreCase("unknown") && serial.length() < 64) deviceKey = serial + "@" + model;
        log.step(true, "اتصلنا بـ " + (model.isEmpty() ? "التلفزيون" : model) + " (أندرويد " + release + ")");
        String d = adb.shell("dumpsys package " + PKG + " | grep versionName");
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("versionName=([0-9A-Za-z._\\-]+)").matcher(d);
        launcherVersion = m.find() ? m.group(1) : "";
        if (!launcherVersion.isEmpty()) log.info("IBRA TV HOME الموجودة على التلفزيون: النسخة " + launcherVersion);
    }

    /** Current default Home component, or null if the system shows a chooser / cannot tell. */
    String currentHome() throws IOException {
        if (sdk < 24) return null;
        String out = adb.shell("cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME");
        String found = null;
        for (String line : out.split("\n")) {
            line = line.trim();
            if (COMPONENT.matcher(line).matches()) found = line;
        }
        if (found != null && NOT_LAUNCHERS.contains(pkgOf(found))) return null;
        return found;
    }

    List<String> homeCandidates() throws IOException {
        List<String> out = new ArrayList<String>();
        if (sdk < 24) return out;
        String s = adb.shell("cmd package query-activities --brief -a android.intent.action.MAIN -c android.intent.category.HOME");
        for (String line : s.split("\n")) {
            line = line.trim();
            if (COMPONENT.matcher(line).matches() && !isOurs(line) && !NOT_LAUNCHERS.contains(pkgOf(line))) out.add(line);
        }
        return out;
    }

    boolean installed() throws IOException {
        return adb.shell("pm list packages " + PKG).contains("package:" + PKG);
    }

    private String key(String k) { return deviceKey + "|" + k; }

    /** Saves the original Home the first time only (never overwrites it with ours). */
    String backupHome() throws IOException {
        String saved = store.get(key("home"));
        String now = currentHome();
        if (saved != null && !saved.isEmpty()) return saved;
        String orig = isOurs(now) ? null : now;
        if (orig == null) {
            for (String c : homeCandidates()) if (KNOWN_STOCK.contains(pkgOf(c))) { orig = c; break; }
        }
        if (orig == null) {
            List<String> c = homeCandidates();
            if (!c.isEmpty()) orig = c.get(0);
        }
        store.put(key("home"), orig == null ? "" : orig);
        log.info(orig == null ? "ما لگينا واجهة أصلية محددة (الجهاز يسأل كل مرة)." : "حفظنا الواجهة الأصلية: " + orig);
        return orig;
    }

    // ------------------------------------------------------------------ install

    public boolean install(InputStream apk, long size, AdbClient.Progress p) throws IOException {
        boolean had = installed();
        String orig = backupHome();

        adb.push(apk, size, REMOTE_APK, 0100644, p);
        log.step(true, "انرفعت IBRA TV HOME للتلفزيون");

        String r = adb.shell("pm install -r -d " + REMOTE_APK);
        if (r.contains("INSTALL_FAILED_UPDATE_INCOMPATIBLE") || r.contains("signatures do not match")) {
            // the copy on the TV was signed with an older key: replace it (its look settings start fresh)
            log.info("النسخة القديمة على التلفزيون موقّعة بمفتاح مختلف؛ نحذفها ونثبّت الجديدة.");
            adb.shell("pm uninstall " + PKG);
            r = adb.shell("pm install -r " + REMOTE_APK);
            had = false;
        }
        adb.shell("rm -f " + REMOTE_APK);
        if (!r.contains("Success") || !installed()) {
            log.step(false, "التثبيت فشل: " + shortErr(r));
            return false;
        }
        log.step(true, "تثبّتت IBRA TV HOME");
        // Let the launcher update itself later (Android 8+ asks per app; this is that switch).
        if (sdk >= 26) adb.shell("appops set " + PKG + " REQUEST_INSTALL_PACKAGES allow");

        // Tell the launcher where the original Home is, so it shows an "original home" tile.
        // Only on a fresh install: a config broadcast replaces the launcher's saved settings.
        if (!had && orig != null && COMPONENT.matcher(orig).matches()) {
            String json = "{\"stockHome\":\"" + orig + "\"}";
            String b64 = AdbClient.Base64.encode(json.getBytes("UTF-8"));
            adb.shell("am broadcast -n " + RECEIVER + " -a tv.ibra.tuner.APPLY_CONFIG --es cfg " + b64);
        }
        return true;
    }

    /** Makes IBRA TV HOME the default Home and verifies it. Returns true when verified. */
    public boolean setAsHome(boolean disableStockIfNeeded) throws IOException {
        if (sdk > 0 && sdk < 24) {
            adb.shell("am start -n " + HOME);
            log.step(true, "انفتحت IBRA TV HOME. هذا الأندرويد قديم: اضغط زر Home على الريموت واختار IBRA TV HOME ثم «دائماً».");
            return false;
        }
        adb.shell("cmd package set-home-activity --user 0 " + HOME);
        if (!isOurs(currentHome()) && sdk >= 29) {
            adb.shell("cmd role add-role-holder --user 0 android.app.role.HOME " + PKG);
        }
        if (!isOurs(currentHome()) && disableStockIfNeeded) {
            List<String> disabled = new ArrayList<String>();
            for (String c : homeCandidates()) {
                String p = pkgOf(c);
                if (KNOWN_STOCK.contains(p) && !disabled.contains(p)) {
                    String r = adb.shell("pm disable-user --user 0 " + p);
                    if (r.contains("new state")) disabled.add(p);
                }
            }
            if (!disabled.isEmpty()) {
                String prev = store.get(key("disabled"));
                StringBuilder sb = new StringBuilder(prev == null ? "" : prev);
                for (String p : disabled) if (!(("," + sb + ",").contains("," + p + ","))) sb.append(sb.length() == 0 ? "" : ",").append(p);
                store.put(key("disabled"), sb.toString());
                log.info("عطّلنا الواجهة الأصلية مؤقتاً: " + disabled + " (ترجع بزر الاسترجاع)");
                adb.shell("cmd package set-home-activity --user 0 " + HOME);
            }
        }
        boolean ok = isOurs(currentHome());
        adb.shell("input keyevent KEYCODE_HOME");
        if (ok) log.step(true, "صارت IBRA TV HOME هي الواجهة الرئيسية ✓");
        else log.step(false, "الجهاز ما قبل يغيّر الواجهة الرئيسية. اضغط Home على الريموت واختار IBRA TV HOME، "
                + "أو فعّل «تعطيل الواجهة الأصلية» وأعد المحاولة.");
        return ok;
    }

    // ------------------------------------------------------------------ restore

    public boolean restore(boolean removeIbra) throws IOException {
        String disabled = store.get(key("disabled"));
        if (disabled != null && !disabled.isEmpty()) {
            for (String p : disabled.split(",")) {
                if (PACKAGE.matcher(p).matches()) adb.shell("pm enable " + p);
            }
            store.put(key("disabled"), "");
            log.info("رجّعنا تفعيل: " + disabled);
        }
        String target = store.get(key("home"));
        if (target == null || target.isEmpty()) {
            for (String c : homeCandidates()) if (KNOWN_STOCK.contains(pkgOf(c))) { target = c; break; }
            if (target == null || target.isEmpty()) {
                List<String> c = homeCandidates();
                target = c.isEmpty() ? null : c.get(0);
            }
        }
        if (target != null && COMPONENT.matcher(target).matches() && sdk >= 24) {
            adb.shell("cmd package set-home-activity --user 0 " + target);
            if (sdk >= 29 && !target.equals(currentHome())) {
                adb.shell("cmd role add-role-holder --user 0 android.app.role.HOME " + pkgOf(target));
            }
        }
        if (removeIbra && installed()) {
            String r = adb.shell("pm uninstall " + PKG);
            log.step(r.contains("Success"), r.contains("Success") ? "انحذفت IBRA TV HOME" : "ما انحذفت: " + shortErr(r));
        }
        String now = currentHome();
        boolean ok = sdk < 24 || (target != null ? target.equals(now) || pkgOf(target).equals(pkgOf(now)) : !isOurs(now));
        adb.shell("input keyevent KEYCODE_HOME");
        if (ok) {
            store.put(key("home"), "");   // next install saves a fresh backup
            log.step(true, "رجعت الواجهة الأصلية" + (target == null ? "" : " (" + target + ")") + " ✓");
        } else {
            log.step(false, "ما تأكدنا من رجوع الواجهة الأصلية. اضغط Home على الريموت واختارها.");
        }
        return ok;
    }

    static String shortErr(String s) {
        s = s == null ? "" : s.trim();
        int i = s.indexOf("Failure");
        if (i >= 0) s = s.substring(i);
        return s.length() > 160 ? s.substring(0, 160) : s;
    }
}
