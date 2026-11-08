package tv.ibra.tuner.launcher;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Launcher settings, stored as one JSON object. Written by on-TV settings and by ConfigReceiver
 * (Windows app / phone installer). {@link #apply} only changes the keys present in the input, so a
 * partial update from the PC never wipes prayer offsets, city, folders etc.
 */
public final class Config {
    static final String PREFS = "ibra";
    static final String KEY_JSON = "cfg";
    static final Pattern PKG = Pattern.compile("^[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+$");
    static final Pattern COMPONENT = Pattern.compile("^[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+/[A-Za-z0-9_.$]+$");
    static final Pattern FOLDER_REF = Pattern.compile("^folder:[a-z0-9]{1,12}$");

    // ---- look
    public String lang = "ar";
    public String title = "IBRA TV HOME";
    public String theme = "glass_black";       // glass_black | glass_white | red_black | purple_cinema | gold | custom
    public String background = "horizon";     // gradient | horizon | animated
    public int cardAlpha = 38;                 // glass fill opacity, 10..90 %
    public int iconSize = 1;                   // 0 small, 1 medium, 2 large
    public int columns = 6;                    // all-apps grid columns (4..8); same key the Windows app sends
    public String effects = "auto";            // auto | on | off
    public boolean largeText = true;
    // ---- style (shapes and layout)
    public String layout = "classic";          // classic | cinema | minimal
    public String corners = "round";           // round | soft | sharp | pill
    public String surface = "glass";           // glass | solid | outline | clear
    public String focusStyle = "zoom";         // zoom | glow | ring | lift
    public String iconStyle = "banner";        // banner | circle | square | colorful
    public String font = "system";             // system | cairo | tajawal | almarai | reemkufi
    public String preset = "ibra";             // last preset applied (display only)
    // ---- updates
    public String updateMode = "auto";         // auto | notify | off
    // custom colours (theme "custom", or sent by the Windows app)
    public int bg1 = Color.parseColor("#07080F");
    public int bg2 = Color.parseColor("#121A33");
    public int accent = Color.parseColor("#8B5CF6");
    public int text = Color.parseColor("#F1F5F9");

    // ---- home layout
    public boolean showClock = true;
    public boolean showWeather = true;
    public boolean showContent = true;
    public boolean showPrayer = true;
    public boolean showFavorites = true;
    public boolean showAllApps = true;
    public final List<String> cardsOrder = new ArrayList<String>(Arrays.asList("weather", "content", "prayer"));
    public final List<String> sectionsOrder = new ArrayList<String>(Arrays.asList("cards", "favorites"));

    // ---- apps
    public final List<String> favorites = new ArrayList<String>();
    public boolean favoritesInit = false;
    public final List<String> hidden = new ArrayList<String>();
    public final Map<String, Folder> folders = new LinkedHashMap<String, Folder>();
    public boolean sortRecent = false;
    public String stockHome = null;

    // ---- place
    public String city = "بغداد";
    public double lat = 33.3152;
    public double lon = 44.3661;
    public String tz = "Asia/Baghdad";

    // ---- prayer
    public int prayerMethod = Prayer.JAFARI;
    public boolean prayerCombined = false;
    public final int[] prayerOffsets = new int[Prayer.COUNT];
    public String prayerAlert = "off";         // off | tone | adhan
    public int hijriOffset = 0;

    // ---- content centre
    public static final String[] ALL_CATS = {"series", "movies", "trending", "anime", "tv", "favorites", "continue", "football"};
    public final List<String> contentCats = new ArrayList<String>(Arrays.asList(ALL_CATS));
    public String contentCat = "series";
    public String tmdbKey = "";
    public boolean previewMode = false;

    public static final class Folder {
        public String name = "";
        public final List<String> apps = new ArrayList<String>();
    }

    public boolean arabic() { return !"en".equals(lang); }

    // ------------------------------------------------------------------ theme colours

    public static final String[] THEMES = {"glass_black", "glass_white", "red_black", "purple_cinema", "gold",
            "ocean", "emerald", "rose", "sunset", "amoled", "slate", "cream"};
    public static final String[] LAYOUTS = {"classic", "cinema", "minimal"};
    public static final String[] CORNERS = {"round", "soft", "sharp", "pill"};
    public static final String[] SURFACES = {"glass", "solid", "outline", "clear"};
    public static final String[] FOCUS = {"zoom", "glow", "ring", "lift"};
    public static final String[] ICONS = {"banner", "circle", "square", "colorful"};
    public static final String[] FONTS = {"system", "cairo", "tajawal", "almarai", "reemkufi"};

    /**
     * Ready-made looks: {id, layout, corners, surface, focus, icons, font, theme, background, iconSize}.
     * Applying one changes only the look, never favourites, city or prayer settings.
     */
    public static final String[][] PRESETS = {
            {"ibra", "classic", "round", "glass", "zoom", "banner", "system", "glass_black", "horizon", "1"},
            {"cinema", "cinema", "soft", "glass", "glow", "banner", "cairo", "purple_cinema", "gradient", "1"},
            {"luxury", "classic", "soft", "outline", "ring", "circle", "reemkufi", "gold", "horizon", "1"},
            {"simple", "minimal", "round", "solid", "zoom", "square", "tajawal", "slate", "gradient", "2"},
            {"metro", "classic", "sharp", "solid", "lift", "colorful", "cairo", "amoled", "gradient", "1"},
            {"neon", "classic", "sharp", "outline", "glow", "banner", "almarai", "red_black", "animated", "1"},
            {"calm", "classic", "round", "glass", "zoom", "circle", "tajawal", "glass_white", "gradient", "1"},
            {"ocean", "cinema", "pill", "glass", "zoom", "banner", "almarai", "ocean", "animated", "1"},
            {"emerald", "minimal", "soft", "glass", "glow", "circle", "cairo", "emerald", "horizon", "2"},
            {"sunset", "classic", "pill", "solid", "glow", "colorful", "reemkufi", "sunset", "gradient", "1"},
            {"rose", "cinema", "round", "glass", "glow", "circle", "tajawal", "rose", "animated", "1"},
            {"paper", "minimal", "soft", "solid", "ring", "square", "almarai", "cream", "gradient", "2"},
    };

    public void applyPreset(String[] p) {
        preset = p[0]; layout = p[1]; corners = p[2]; surface = p[3]; focusStyle = p[4]; iconStyle = p[5];
        font = p[6]; theme = p[7]; background = p[8]; iconSize = Integer.parseInt(p[9]);
    }

    /** {bg1, bg2, accent, text, glow} for the active theme. */
    public int[] palette() {
        if ("glass_white".equals(theme)) return new int[]{0xFFE8ECF4, 0xFFC4CEE3, 0xFF6D28D9, 0xFF0F172A, 0xFFA78BFA};
        if ("red_black".equals(theme)) return new int[]{0xFF050505, 0xFF2A070C, 0xFFE11D2E, 0xFFF8F1F1, 0xFFFF4D5E};
        if ("purple_cinema".equals(theme)) return new int[]{0xFF0A0620, 0xFF2B1259, 0xFFA78BFA, 0xFFF5F3FF, 0xFFC084FC};
        if ("gold".equals(theme)) return new int[]{0xFF0A0A0A, 0xFF1C1608, 0xFFC9A84C, 0xFFF5F0E1, 0xFFE8C766};
        if ("ocean".equals(theme)) return new int[]{0xFF02111F, 0xFF0B4F6C, 0xFF38BDF8, 0xFFEFF9FF, 0xFF22D3EE};
        if ("emerald".equals(theme)) return new int[]{0xFF03100C, 0xFF0B3B2E, 0xFF34D399, 0xFFECFDF5, 0xFF10B981};
        if ("rose".equals(theme)) return new int[]{0xFF14050C, 0xFF4A1530, 0xFFF472B6, 0xFFFDF2F8, 0xFFEC4899};
        if ("sunset".equals(theme)) return new int[]{0xFF140803, 0xFF4A1D0C, 0xFFFB923C, 0xFFFFF7ED, 0xFFF97316};
        if ("amoled".equals(theme)) return new int[]{0xFF000000, 0xFF000000, 0xFF0EA5E9, 0xFFF5F5F5, 0xFF0369A1};
        if ("slate".equals(theme)) return new int[]{0xFF0B1220, 0xFF1E293B, 0xFF94A3B8, 0xFFF1F5F9, 0xFF64748B};
        if ("cream".equals(theme)) return new int[]{0xFFF7F1E8, 0xFFE6D6BF, 0xFFB45309, 0xFF1C1917, 0xFFF59E0B};
        if ("custom".equals(theme)) return new int[]{bg1, bg2, accent, text, accent};
        return new int[]{0xFF06070D, 0xFF111A36, 0xFF8B5CF6, 0xFFF1F5F9, 0xFF7C3AED};   // glass_black (default)
    }

    public boolean light() { return "glass_white".equals(theme) || "cream".equals(theme); }

    // ------------------------------------------------------------------ parsing

    static int color(String s, int def) {
        try {
            if (s != null && s.matches("^#([0-9A-Fa-f]{6}|[0-9A-Fa-f]{8})$")) return Color.parseColor(s);
        } catch (Exception ignored) { }
        return def;
    }

    static void pkgList(JSONArray a, List<String> out, boolean allowFolders, int max) {
        out.clear();
        if (a == null) return;
        for (int i = 0; i < a.length() && out.size() < max; i++) {
            String p = a.optString(i, "");
            boolean ok = PKG.matcher(p).matches() || (allowFolders && FOLDER_REF.matcher(p).matches());
            if (ok && !out.contains(p)) out.add(p);
        }
    }

    static void keyList(JSONArray a, List<String> out, String[] allowed) {
        if (a == null) return;
        List<String> tmp = new ArrayList<String>();
        List<String> ok = Arrays.asList(allowed);
        for (int i = 0; i < a.length(); i++) {
            String k = a.optString(i, "");
            if (ok.contains(k) && !tmp.contains(k)) tmp.add(k);
        }
        out.clear();
        out.addAll(tmp);
    }

    static String pick(String v, String[] allowed, String def) { return Arrays.asList(allowed).contains(v) ? v : def; }

    static int clamp(int v, int lo, int hi) { return Math.max(lo, Math.min(hi, v)); }

    static String clip(String s, int max) {
        s = s == null ? "" : s.replaceAll("[\\p{Cntrl}]", "").trim();
        return s.length() > max ? s.substring(0, max) : s;
    }

    /** Applies only the keys present in {@code o}. Every value is validated and clamped. */
    public Config apply(JSONObject o) {
        if (o.has("lang")) lang = "en".equals(o.optString("lang")) ? "en" : "ar";
        if (o.has("title")) { String t = clip(o.optString("title"), 40); if (!t.isEmpty()) title = t; }
        if (o.has("theme")) {
            String t = o.optString("theme");
            if (Arrays.asList(THEMES).contains(t) || "custom".equals(t)) theme = t;
        }
        if (o.has("background")) {
            String b = o.optString("background");
            if (b.equals("gradient") || b.equals("horizon") || b.equals("animated")) background = b;
        }
        if (o.has("cardAlpha")) cardAlpha = clamp(o.optInt("cardAlpha", cardAlpha), 10, 90);
        if (o.has("iconSize")) iconSize = clamp(o.optInt("iconSize", iconSize), 0, 2);
        if (o.has("columns")) columns = clamp(o.optInt("columns", columns), 4, 8);
        if (o.has("effects")) { String e = o.optString("effects"); if (e.equals("on") || e.equals("off") || e.equals("auto")) effects = e; }
        if (o.has("largeText")) largeText = o.optBoolean("largeText", largeText);
        if (o.has("layout")) layout = pick(o.optString("layout"), LAYOUTS, layout);
        if (o.has("corners")) corners = pick(o.optString("corners"), CORNERS, corners);
        if (o.has("surface")) surface = pick(o.optString("surface"), SURFACES, surface);
        if (o.has("focusStyle")) focusStyle = pick(o.optString("focusStyle"), FOCUS, focusStyle);
        if (o.has("iconStyle")) iconStyle = pick(o.optString("iconStyle"), ICONS, iconStyle);
        if (o.has("font")) font = pick(o.optString("font"), FONTS, font);
        if (o.has("preset")) preset = clip(o.optString("preset"), 20);
        if (o.has("updateMode")) updateMode = pick(o.optString("updateMode"), new String[]{"auto", "notify", "off"}, updateMode);
        if (o.has("applyPreset")) {
            for (String[] p : PRESETS) if (p[0].equals(o.optString("applyPreset"))) applyPreset(p);
        }

        boolean colours = false;
        if (o.has("background1")) { bg1 = color(o.optString("background1"), bg1); colours = true; }
        if (o.has("background2")) { bg2 = color(o.optString("background2"), bg2); colours = true; }
        if (o.has("accent")) { accent = color(o.optString("accent"), accent); colours = true; }
        if (o.has("textColor")) { text = color(o.optString("textColor"), text); colours = true; }
        if (colours && !o.has("theme")) theme = "custom";   // the Windows app sends explicit colours

        if (o.has("showClock")) showClock = o.optBoolean("showClock", true);
        if (o.has("showWeather")) showWeather = o.optBoolean("showWeather", true);
        if (o.has("showContent")) showContent = o.optBoolean("showContent", true);
        if (o.has("showPrayer")) showPrayer = o.optBoolean("showPrayer", true);
        if (o.has("showFavorites")) showFavorites = o.optBoolean("showFavorites", true);
        if (o.has("showAllApps")) showAllApps = o.optBoolean("showAllApps", true);
        if (o.has("cardsOrder")) keyList(o.optJSONArray("cardsOrder"), cardsOrder, new String[]{"weather", "content", "prayer"});
        if (o.has("sectionsOrder")) keyList(o.optJSONArray("sectionsOrder"), sectionsOrder, new String[]{"cards", "favorites"});
        for (String k : new String[]{"weather", "content", "prayer"}) if (!cardsOrder.contains(k)) cardsOrder.add(k);
        for (String k : new String[]{"cards", "favorites"}) if (!sectionsOrder.contains(k)) sectionsOrder.add(k);

        if (o.has("favorites")) { pkgList(o.optJSONArray("favorites"), favorites, true, 60); favoritesInit = true; }
        if (o.has("favoritesInit")) favoritesInit = o.optBoolean("favoritesInit", favoritesInit);
        if (o.has("hidden")) pkgList(o.optJSONArray("hidden"), hidden, false, 300);
        if (o.has("sortRecent")) sortRecent = o.optBoolean("sortRecent", false);
        if (o.has("stockHome")) {
            String sh = o.optString("stockHome", "");
            stockHome = COMPONENT.matcher(sh).matches() ? sh : null;
        }
        if (o.has("folders")) {
            folders.clear();
            JSONObject f = o.optJSONObject("folders");
            if (f != null) {
                Iterator<String> it = f.keys();
                while (it.hasNext() && folders.size() < 30) {
                    String id = it.next();
                    JSONObject fo = f.optJSONObject(id);
                    if (fo == null || !id.matches("^[a-z0-9]{1,12}$")) continue;
                    Folder fd = new Folder();
                    fd.name = clip(fo.optString("name", ""), 30);
                    pkgList(fo.optJSONArray("apps"), fd.apps, false, 60);
                    folders.put(id, fd);
                }
            }
        }

        if (o.has("city")) { String c = clip(o.optString("city"), 40); if (!c.isEmpty()) city = c; }
        if (o.has("lat") && o.has("lon")) {
            double la = o.optDouble("lat", lat), lo = o.optDouble("lon", lon);
            if (la >= -66 && la <= 66 && lo >= -180 && lo <= 180) { lat = la; lon = lo; }
        }
        if (o.has("tz")) {
            String z = o.optString("tz", tz);
            if (z.matches("^[A-Za-z_]+(/[A-Za-z_+\\-0-9]+){0,2}$")) tz = z;
        }

        if (o.has("prayerMethod")) prayerMethod = o.optInt("prayerMethod", 0) == Prayer.TEHRAN ? Prayer.TEHRAN : Prayer.JAFARI;
        if (o.has("prayerCombined")) prayerCombined = o.optBoolean("prayerCombined", false);
        if (o.has("prayerOffsets")) {
            JSONArray a = o.optJSONArray("prayerOffsets");
            for (int i = 0; a != null && i < prayerOffsets.length && i < a.length(); i++) prayerOffsets[i] = clamp(a.optInt(i, 0), -30, 30);
        }
        if (o.has("prayerAlert")) { String p = o.optString("prayerAlert"); if (p.equals("off") || p.equals("tone") || p.equals("adhan")) prayerAlert = p; }
        if (o.has("hijriOffset")) hijriOffset = clamp(o.optInt("hijriOffset", 0), -3, 3);

        if (o.has("contentCats")) keyList(o.optJSONArray("contentCats"), contentCats, ALL_CATS);
        if (o.has("contentCat")) { String c = o.optString("contentCat"); if (Arrays.asList(ALL_CATS).contains(c)) contentCat = c; }
        if (o.has("tmdbKey")) { String k = o.optString("tmdbKey", "").trim(); tmdbKey = k.matches("^[A-Za-z0-9._\\-]{0,400}$") ? k : ""; }
        if (o.has("previewMode")) previewMode = o.optBoolean("previewMode", false);
        if (!contentCats.contains(contentCat) && !contentCats.isEmpty()) contentCat = contentCats.get(0);
        return this;
    }

    public static Config parse(String json) {
        try {
            return new Config().apply(new JSONObject(json));
        } catch (Exception e) {
            return null;
        }
    }

    public JSONObject toJsonObject() {
        try {
            JSONObject o = new JSONObject();
            o.put("lang", lang);
            o.put("title", title);
            o.put("theme", theme);
            o.put("background", background);
            o.put("cardAlpha", cardAlpha);
            o.put("iconSize", iconSize);
            o.put("columns", columns);
            o.put("effects", effects);
            o.put("largeText", largeText);
            o.put("layout", layout);
            o.put("corners", corners);
            o.put("surface", surface);
            o.put("focusStyle", focusStyle);
            o.put("iconStyle", iconStyle);
            o.put("font", font);
            o.put("preset", preset);
            o.put("updateMode", updateMode);
            o.put("background1", hex(bg1));
            o.put("background2", hex(bg2));
            o.put("accent", hex(accent));
            o.put("textColor", hex(text));
            o.put("showClock", showClock);
            o.put("showWeather", showWeather);
            o.put("showContent", showContent);
            o.put("showPrayer", showPrayer);
            o.put("showFavorites", showFavorites);
            o.put("showAllApps", showAllApps);
            o.put("cardsOrder", new JSONArray(cardsOrder));
            o.put("sectionsOrder", new JSONArray(sectionsOrder));
            o.put("favorites", new JSONArray(favorites));
            o.put("favoritesInit", favoritesInit);
            o.put("hidden", new JSONArray(hidden));
            o.put("sortRecent", sortRecent);
            if (stockHome != null) o.put("stockHome", stockHome);
            JSONObject f = new JSONObject();
            for (Map.Entry<String, Folder> e : folders.entrySet()) {
                JSONObject fo = new JSONObject();
                fo.put("name", e.getValue().name);
                fo.put("apps", new JSONArray(e.getValue().apps));
                f.put(e.getKey(), fo);
            }
            o.put("folders", f);
            o.put("city", city);
            o.put("lat", lat);
            o.put("lon", lon);
            o.put("tz", tz);
            o.put("prayerMethod", prayerMethod);
            o.put("prayerCombined", prayerCombined);
            JSONArray off = new JSONArray();
            for (int v : prayerOffsets) off.put(v);
            o.put("prayerOffsets", off);
            o.put("prayerAlert", prayerAlert);
            o.put("hijriOffset", hijriOffset);
            o.put("contentCats", new JSONArray(contentCats));
            o.put("contentCat", contentCat);
            o.put("tmdbKey", tmdbKey);
            o.put("previewMode", previewMode);
            return o;
        } catch (Exception e) {
            return new JSONObject();
        }
    }

    public String toJson() { return toJsonObject().toString(); }

    /** Backup without the API key (the key stays on this TV only). */
    public String toBackupJson() {
        JSONObject o = toJsonObject();
        o.remove("tmdbKey");
        try { o.put("ibraBackup", 1); } catch (Exception ignored) { }
        return o.toString();
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

    /** New folder id, unique in this config. */
    public String newFolderId() {
        for (int i = 1; i < 1000; i++) {
            String id = "f" + i;
            if (!folders.containsKey(id)) return id;
        }
        return "f" + System.currentTimeMillis() % 100000;
    }
}
