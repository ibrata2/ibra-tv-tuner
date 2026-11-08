package tv.ibra.tuner.launcher;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.provider.Settings;
import android.widget.Toast;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Installed launchable apps (TV and phone apps), launch history and the default favourites. */
public final class Apps {

    public static final class App {
        public String pkg, label;
        public ComponentName component;
        public Intent intent;
        public Drawable icon, banner;
        public boolean tv;          // has a Leanback (TV) launcher entry
    }

    private static List<App> cache;

    public static synchronized void invalidate() { cache = null; }

    public static synchronized List<App> all(Context c) {
        if (cache != null) return cache;
        PackageManager pm = c.getPackageManager();
        Map<String, App> map = new LinkedHashMap<String, App>();
        Intent lb = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
        Intent la = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        for (int pass = 0; pass < 2; pass++) {
            List<ResolveInfo> list = pm.queryIntentActivities(pass == 0 ? lb : la, 0);
            for (ResolveInfo ri : list) {
                ActivityInfo ai = ri.activityInfo;
                if (ai == null || c.getPackageName().equals(ai.packageName) || map.containsKey(ai.packageName)) continue;
                App a = new App();
                a.pkg = ai.packageName;
                a.tv = pass == 0;
                a.component = new ComponentName(ai.packageName, ai.name);
                CharSequence l = ri.loadLabel(pm);
                a.label = l == null ? ai.packageName : l.toString();
                try { a.banner = ai.loadBanner(pm); } catch (Exception ignored) { }
                if (a.banner == null) try { a.banner = pm.getApplicationBanner(ai.packageName); } catch (Exception ignored) { }
                try { a.icon = ri.loadIcon(pm); } catch (Exception ignored) { }
                Intent i = a.tv ? pm.getLeanbackLaunchIntentForPackage(a.pkg) : null;
                if (i == null) i = new Intent(Intent.ACTION_MAIN)
                        .addCategory(a.tv ? Intent.CATEGORY_LEANBACK_LAUNCHER : Intent.CATEGORY_LAUNCHER).setComponent(a.component);
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
                a.intent = i;
                map.put(a.pkg, a);
            }
        }
        List<App> apps = new ArrayList<App>(map.values());
        final Collator col = Collator.getInstance();
        Collections.sort(apps, new Comparator<App>() {
            @Override public int compare(App x, App y) { return col.compare(x.label, y.label); }
        });
        cache = apps;
        return apps;
    }

    public static Map<String, App> byPkg(Context c) {
        Map<String, App> m = new LinkedHashMap<String, App>();
        for (App a : all(c)) m.put(a.pkg, a);
        return m;
    }

    // ------------------------------------------------------------------ usage

    static SharedPreferences usage(Context c) { return c.getSharedPreferences("ibra_usage", Context.MODE_PRIVATE); }

    public static long lastUsed(Context c, String pkg) { return usage(c).getLong(pkg, 0); }

    public static List<App> sortedByRecent(Context c, List<App> in) {
        final SharedPreferences u = usage(c);
        List<App> out = new ArrayList<App>(in);
        Collections.sort(out, new Comparator<App>() {
            @Override public int compare(App a, App b) { return Long.compare(u.getLong(b.pkg, 0), u.getLong(a.pkg, 0)); }
        });
        return out;
    }

    public static boolean launch(Activity a, App app) {
        try {
            a.startActivity(app.intent);
            usage(a).edit().putLong(app.pkg, System.currentTimeMillis()).apply();
            return true;
        } catch (Exception e) {
            Toast.makeText(a, Config.load(a).arabic() ? "ما گدرت أفتح التطبيق" : "Could not open the app", Toast.LENGTH_SHORT).show();
            return false;
        }
    }

    public static void info(Activity a, String pkg) {
        try {
            a.startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + pkg))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (Exception ignored) { }
    }

    public static void openStore(Activity a, String pkg) {
        Intent[] tries = {
                new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + pkg)),
                new Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=" + pkg)),
        };
        for (Intent i : tries) {
            try { a.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); return; } catch (Exception ignored) { }
        }
        Toast.makeText(a, Config.load(a).arabic() ? "ما لگيت متجر تطبيقات على الجهاز" : "No app store on this device", Toast.LENGTH_LONG).show();
    }

    // ------------------------------------------------------------------ defaults

    /** Preferred first-run favourites, in order; only installed ones are used. */
    static final String[] PREFERRED = {
            "com.google.android.youtube.tv", "com.google.android.youtube", "com.netflix.ninja", "com.netflix.mediaclient",
            "net.mbc.shahidTV", "net.mbc.shahid", "com.shabakaty.cbox.cinemana", "com.shabakaty.cbox.cinemanb",
            "com.shabakaty.cbox.cinemanx", "com.amazon.amazonvideo.livingroom", "com.disney.disneyplus",
            "com.spotify.tv.android", "com.spotify.music", "org.videolan.vlc", "org.xbmc.kodi",
            "com.android.chrome", "com.android.vending",
    };

    /** Suggested apps offered in "add app" when not installed: {label, package}. */
    static final String[][] SUGGESTED = {
            {"YouTube", "com.google.android.youtube.tv"}, {"Netflix", "com.netflix.ninja"},
            {"شاهد Shahid", "net.mbc.shahidTV"}, {"سينمانا", "com.shabakaty.cbox.cinemana"},
            {"Prime Video", "com.amazon.amazonvideo.livingroom"}, {"Disney+", "com.disney.disneyplus"},
            {"Spotify", "com.spotify.tv.android"}, {"VLC", "org.videolan.vlc"},
    };

    /** Streaming apps offered in "play with…", with an optional search deep link (%s = encoded title). */
    static final String[][] PLAYERS = {
            {"com.netflix.ninja", "https://www.netflix.com/search?q=%s"},
            {"com.netflix.mediaclient", "https://www.netflix.com/search?q=%s"},
            {"com.google.android.youtube.tv", "https://www.youtube.com/results?search_query=%s"},
            {"com.google.android.youtube", "https://www.youtube.com/results?search_query=%s"},
            {"net.mbc.shahidTV", null}, {"net.mbc.shahid", null},
            {"com.shabakaty.cbox.cinemana", null}, {"com.shabakaty.cbox.cinemanb", null}, {"com.shabakaty.cbox.cinemanx", null},
            {"com.amazon.amazonvideo.livingroom", null}, {"com.disney.disneyplus", null},
            {"org.xbmc.kodi", null}, {"com.plexapp.android", null}, {"org.videolan.vlc", null},
    };

    public static void initFavorites(Context c, Config cfg) {
        if (cfg.favoritesInit) return;
        Map<String, App> m = byPkg(c);
        cfg.favorites.clear();
        java.util.Set<String> labels = new java.util.HashSet<String>();
        for (String p : PREFERRED) {
            App a = m.get(p);
            if (a != null && labels.add(a.label.toLowerCase()) && cfg.favorites.size() < 8) cfg.favorites.add(p);
        }
        if (cfg.favorites.isEmpty()) for (App a : m.values()) { if (cfg.favorites.size() >= 6) break; cfg.favorites.add(a.pkg); }
        cfg.favoritesInit = true;
        cfg.save(c);
    }
}
