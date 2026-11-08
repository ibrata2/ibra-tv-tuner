package tv.ibra.common;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.net.Uri;
import android.os.Build;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;

/**
 * Self-update for IBRA apps from the project's GitHub release.
 * update.json (published by CI next to the APKs) says the newest versionCode, the APK URL, its SHA-256 and the
 * SHA-256 of the signing certificate. The APK is only installed when:
 *   - it comes over HTTPS from github.com (redirects to GitHub's download host are allowed),
 *   - its SHA-256 matches update.json,
 *   - and the update is signed with the same key as the installed app (Android enforces this too).
 * Installation goes through PackageInstaller, so Android shows its normal "update this app?" confirmation.
 */
public final class Updater {
    public static final String MANIFEST = "https://github.com/ibrata2/ibra-tv-tuner/releases/download/v0.1.0/update.json";
    public static final String RELEASES = "https://github.com/ibrata2/ibra-tv-tuner/releases/tag/v0.1.0";
    static final String PREFS = "ibra_update";

    public static final class Info {
        public int versionCode;
        public String versionName = "", url = "", sha256 = "", certSha256 = "", notesAr = "", notesEn = "";
        public long size;
        /** False when the release is signed with another key: then only a manual reinstall works. */
        public boolean sameKey = true;
    }

    public interface Progress { void on(long done, long total); }

    // ------------------------------------------------------------------ check

    /** Newest release info for {@code key} ("launcher" or "installer") if it is newer than this app, else null. */
    public static Info check(Context c, String key) throws Exception {
        JSONObject root = new JSONObject(get(MANIFEST, 512 * 1024));
        JSONObject a = root.getJSONObject("android").getJSONObject(key);
        Info i = new Info();
        i.versionCode = a.getInt("versionCode");
        i.versionName = a.optString("versionName", String.valueOf(i.versionCode));
        i.url = a.getString("url");
        i.sha256 = a.getString("sha256").toLowerCase();
        i.certSha256 = a.optString("certSha256", "").toLowerCase().replace(":", "");
        i.size = a.optLong("size", 0);
        i.notesAr = root.optString("notesAr", "");
        i.notesEn = root.optString("notesEn", "");
        if (!i.url.startsWith("https://github.com/")) throw new Exception("unexpected download host");
        prefs(c).edit().putLong("checked_at", System.currentTimeMillis()).apply();
        if (i.versionCode <= myVersionCode(c)) { prefs(c).edit().remove("available").apply(); return null; }
        String mine = myCertSha256(c);
        i.sameKey = i.certSha256.isEmpty() || mine.isEmpty() || mine.equals(i.certSha256);
        prefs(c).edit().putInt("available", i.versionCode).putString("available_name", i.versionName).apply();
        return i;
    }

    public static boolean due(Context c, long everyMs) {
        return System.currentTimeMillis() - prefs(c).getLong("checked_at", 0) > everyMs;
    }

    /** A newer version was seen at the last check (for a badge). */
    public static boolean available(Context c) { return prefs(c).getInt("available", 0) > myVersionCode(c); }

    public static String availableName(Context c) { return prefs(c).getString("available_name", ""); }

    static SharedPreferences prefs(Context c) { return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE); }

    @SuppressWarnings("deprecation")
    public static int myVersionCode(Context c) {
        try { return c.getPackageManager().getPackageInfo(c.getPackageName(), 0).versionCode; } catch (Exception e) { return 0; }
    }

    public static String myVersionName(Context c) {
        try { return c.getPackageManager().getPackageInfo(c.getPackageName(), 0).versionName; } catch (Exception e) { return ""; }
    }

    @SuppressWarnings("deprecation")
    public static String myCertSha256(Context c) {
        try {
            PackageInfo pi = c.getPackageManager().getPackageInfo(c.getPackageName(), PackageManager.GET_SIGNATURES);
            Signature[] s = pi.signatures;
            if (s == null || s.length == 0) return "";
            return hex(MessageDigest.getInstance("SHA-256").digest(s[0].toByteArray()));
        } catch (Exception e) {
            return "";
        }
    }

    // ------------------------------------------------------------------ download

    /** Downloads (or reuses) the APK into the cache and verifies its SHA-256. */
    public static File download(Context c, Info i, Progress p) throws Exception {
        File dir = new File(c.getCacheDir(), "update");
        dir.mkdirs();
        File f = new File(dir, "update-" + i.versionCode + ".apk");
        if (f.isFile() && i.sha256.equals(sha256(f))) return f;
        File[] old = dir.listFiles();
        if (old != null) for (File o : old) o.delete();
        HttpURLConnection con = open(i.url);
        try {
            long total = con.getContentLength() > 0 ? con.getContentLength() : i.size;
            if (total > 200L * 1024 * 1024) throw new Exception("too large");
            InputStream in = con.getInputStream();
            File tmp = new File(dir, f.getName() + ".part");
            OutputStream out = new FileOutputStream(tmp);
            try {
                byte[] buf = new byte[32768];
                long done = 0;
                int n;
                while ((n = in.read(buf)) > 0) {
                    out.write(buf, 0, n);
                    done += n;
                    if (done > 200L * 1024 * 1024) throw new Exception("too large");
                    if (p != null) p.on(done, total);
                }
            } finally { out.close(); }
            if (!i.sha256.equals(sha256(tmp))) { tmp.delete(); throw new Exception("checksum mismatch"); }
            if (!tmp.renameTo(f)) throw new Exception("rename failed");
            return f;
        } finally { con.disconnect(); }
    }

    // ------------------------------------------------------------------ install

    /** On Android 8+ the user must allow this app to install apps once. */
    public static boolean canInstall(Context c) {
        if (Build.VERSION.SDK_INT < 26) return true;
        try {
            return (Boolean) PackageManager.class.getMethod("canRequestPackageInstalls").invoke(c.getPackageManager());
        } catch (Exception e) {
            return true;
        }
    }

    /** Opens the "install unknown apps" switch for this app. */
    public static void openInstallPermission(Context c) {
        try {
            c.startActivity(new Intent("android.settings.MANAGE_UNKNOWN_APP_SOURCES", Uri.parse("package:" + c.getPackageName()))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (Exception e) {
            try { c.startActivity(new Intent(android.provider.Settings.ACTION_SECURITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); }
            catch (Exception ignored) { }
        }
    }

    /** Hands the verified APK to PackageInstaller; Android then asks the user to confirm the update. */
    public static void install(Context c, File apk) throws Exception {
        PackageInstaller pi = c.getPackageManager().getPackageInstaller();
        PackageInstaller.SessionParams params = new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);
        params.setAppPackageName(c.getPackageName());
        int id = pi.createSession(params);
        PackageInstaller.Session s = pi.openSession(id);
        try {
            OutputStream out = s.openWrite("base.apk", 0, apk.length());
            InputStream in = new FileInputStream(apk);
            try {
                byte[] buf = new byte[65536];
                int n;
                while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                s.fsync(out);
            } finally { in.close(); out.close(); }
            Intent cb = new Intent(c, UpdateReceiver.class).setAction("tv.ibra.common.UPDATE_STATUS");
            int flags = PendingIntent.FLAG_UPDATE_CURRENT | 0x02000000;   // FLAG_MUTABLE: the system adds the result extras
            s.commit(PendingIntent.getBroadcast(c, 41, cb, flags).getIntentSender());
        } catch (Exception e) {
            s.abandon();
            throw e;
        } finally {
            s.close();
        }
    }

    /** Result of the install session. */
    public static class UpdateReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context c, Intent i) {
            int st = i.getIntExtra(PackageInstaller.EXTRA_STATUS, -999);
            boolean ar = !"en".equals(c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("lang", "ar"));
            if (st == PackageInstaller.STATUS_PENDING_USER_ACTION) {
                Intent confirm = (Intent) i.getParcelableExtra(Intent.EXTRA_INTENT);
                if (confirm != null) {
                    try { c.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); } catch (Exception ignored) { }
                }
                return;
            }
            if (st == PackageInstaller.STATUS_SUCCESS) return;   // the app restarts by itself
            String msg = i.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE);
            String text = st == PackageInstaller.STATUS_FAILURE_CONFLICT || st == PackageInstaller.STATUS_FAILURE_INCOMPATIBLE
                    ? (ar ? "التحديث موقّع بمفتاح مختلف: احذف التطبيق وثبّت النسخة الجديدة من صفحة الإصدارات." : "The update is signed with another key: uninstall and install the new version from the releases page.")
                    : st == PackageInstaller.STATUS_FAILURE_ABORTED ? (ar ? "انلغى التحديث." : "Update cancelled.")
                    : (ar ? "فشل التحديث: " : "Update failed: ") + (msg == null ? st : msg);
            Toast.makeText(c, text, Toast.LENGTH_LONG).show();
        }
    }

    /** Remembers the UI language for messages shown by the receiver. */
    public static void setLanguage(Context c, boolean arabic) {
        prefs(c).edit().putString("lang", arabic ? "ar" : "en").apply();
    }

    // ------------------------------------------------------------------ http + hashing

    static HttpURLConnection open(String url) throws Exception {
        for (int hop = 0; hop < 5; hop++) {
            URL u = new URL(url);
            if (!"https".equals(u.getProtocol())) throw new Exception("https only");
            HttpURLConnection con = (HttpURLConnection) u.openConnection();
            con.setInstanceFollowRedirects(false);
            con.setConnectTimeout(10000);
            con.setReadTimeout(30000);
            con.setRequestProperty("User-Agent", "IBRA-updater/1.0");
            int code = con.getResponseCode();
            if (code >= 300 && code < 400) {
                String next = con.getHeaderField("Location");
                con.disconnect();
                if (next == null) throw new Exception("redirect without location");
                url = new URL(u, next).toString();
                continue;
            }
            if (code != 200) { con.disconnect(); throw new Exception("HTTP " + code); }
            return con;
        }
        throw new Exception("too many redirects");
    }

    static String get(String url, int max) throws Exception {
        HttpURLConnection con = open(url);
        try {
            InputStream in = con.getInputStream();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
                if (out.size() > max) throw new Exception("too large");
            }
            return out.toString("UTF-8");
        } finally { con.disconnect(); }
    }

    public static String sha256(File f) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        InputStream in = new FileInputStream(f);
        try {
            byte[] buf = new byte[65536];
            int n;
            while ((n = in.read(buf)) > 0) md.update(buf, 0, n);
        } finally { in.close(); }
        return hex(md.digest());
    }

    static String hex(byte[] d) {
        StringBuilder sb = new StringBuilder();
        for (byte b : d) sb.append(String.format("%02x", b));
        return sb.toString();
    }
}
