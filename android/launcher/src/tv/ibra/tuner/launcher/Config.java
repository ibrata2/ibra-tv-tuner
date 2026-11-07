package tv.ibra.tuner.launcher;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/** Launcher settings. Written by ConfigReceiver (from the Windows app) and by on-TV edits. */
public final class Config {
    static final String PREFS = "ibra";
    static final String KEY_JSON = "cfg";
    private static final Pattern PKG = Pattern.compile("^[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+$");
    private static final Pattern COMPONENT = Pattern.compile("^[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+/[A-Za-z0-9_.$]+$");

    public String lang = "ar";
    public String title = "IBRA HOME";
    public int columns = 5;
    // IBRA black & gold
    public int bg1 = Color.parseColor("#0A0A0A");
    public int bg2 = Color.parseColor("#1C1608");
    public int accent = Color.parseColor("#C9A84C");
    public int text = Color.parseColor("#F5F0E1");
    public boolean showClock = true;
    public boolean showAllApps = true;
    public boolean largeText = true;
    public final List<String> favorites = new ArrayList<String>();
    public final List<String> hidden = new ArrayList<String>();
    public String stockHome = null;

    // IBRA HOME sections
    public boolean showWeather = true;
    public boolean showFootball = true;
    public boolean showShows = true;
    public boolean showIraqiApps = true;
    public String city = "بغداد";
    public double lat = 33.3152;
    public double lon = 44.3661;
    /** Extra packages added to the Iraqi apps row (on top of the built-in list). */
    public final List<String> extraApps = new ArrayList<String>();

    public boolean arabic() { return !"en".equals(lang); }

    static int color(JSONObject o, String key, int def) {
        try {
            String s = o.optString(key, "");
            if (s.matches("^#([0-9A-Fa-f]{6}|[0-9A-Fa-f]{8})$")) return Color.parseColor(s);
        } catch (Exception ignored) { }
        return def;
    }

    static void list(JSONObject o, String key, List<String> out) {
        out.clear();
        JSONArray a = o.optJSONArray(key);
        if (a == null) return;
        for (int i = 0; i < a.length() && i < 200; i++) {
            String p = a.optString(i, "");
            if (PKG.matcher(p).matches() && !out.contains(p)) out.add(p);
        }
    }

    /** Parses and validates. Returns null for anything that is not a well-formed config. */
    public static Config parse(String json) {
        try {
            JSONObject o = new JSONObject(json);
            Config c = new Config();
            c.lang = "en".equals(o.optString("lang", "ar")) ? "en" : "ar";
            String t = o.optString("title", c.title);
            c.title = t.length() > 40 ? t.substring(0, 40) : t;
            c.columns = Math.max(3, Math.min(8, o.optInt("columns", 5)));
            c.bg1 = color(o, "background1", c.bg1);
            c.bg2 = color(o, "background2", c.bg2);
            c.accent = color(o, "accent", c.accent);
            c.text = color(o, "textColor", c.text);
            c.showClock = o.optBoolean("showClock", true);
            c.showAllApps = o.optBoolean("showAllApps", true);
            c.largeText = o.optBoolean("largeText", true);
            list(o, "favorites", c.favorites);
            list(o, "hidden", c.hidden);
            String sh = o.optString("stockHome", "");
            c.stockHome = COMPONENT.matcher(sh).matches() ? sh : null;
            c.showWeather = o.optBoolean("showWeather", true);
            c.showFootball = o.optBoolean("showFootball", true);
            c.showShows = o.optBoolean("showShows", true);
            c.showIraqiApps = o.optBoolean("showIraqiApps", true);
            String city = o.optString("city", c.city).trim();
            if (!city.isEmpty()) c.city = city.length() > 30 ? city.substring(0, 30) : city;
            double la = o.optDouble("lat", c.lat), lo = o.optDouble("lon", c.lon);
            if (la >= -90 && la <= 90 && lo >= -180 && lo <= 180) { c.lat = la; c.lon = lo; }
            list(o, "extraApps", c.extraApps);
            return c;
        } catch (Exception e) {
            return null;
        }
    }

    public String toJson() {
        try {
            JSONObject o = new JSONObject();
            o.put("lang", lang);
            o.put("title", title);
            o.put("columns", columns);
            o.put("background1", hex(bg1));
            o.put("background2", hex(bg2));
            o.put("accent", hex(accent));
            o.put("textColor", hex(text));
            o.put("showClock", showClock);
            o.put("showAllApps", showAllApps);
            o.put("largeText", largeText);
            o.put("favorites", new JSONArray(favorites));
            o.put("hidden", new JSONArray(hidden));
            if (stockHome != null) o.put("stockHome", stockHome);
            o.put("showWeather", showWeather);
            o.put("showFootball", showFootball);
            o.put("showShows", showShows);
            o.put("showIraqiApps", showIraqiApps);
            o.put("city", city);
            o.put("lat", lat);
            o.put("lon", lon);
            o.put("extraApps", new JSONArray(extraApps));
            return o.toString();
        } catch (Exception e) {
            return "{}";
        }
    }

    static String hex(int c) { return String.format("#%08X", c); }

    public static Config load(Context ctx) {
        SharedPreferences p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        Config c = parse(p.getString(KEY_JSON, "{}"));
        return c != null ? c : new Config();
    }

    public void save(Context ctx) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_JSON, toJson()).apply();
    }
}
