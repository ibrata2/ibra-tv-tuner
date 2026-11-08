package tv.ibra.tuner.launcher;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;

/**
 * Entertainment centre data. Only titles, posters and descriptions are shown; "play" opens an app the user
 * has installed (never a streaming link). Sources:
 *   series / tv programmes : TVmaze schedule (no key)
 *   anime                  : Jikan / MyAnimeList current season (no key)
 *   movies / trending      : TMDB when the user saved a key once in settings, otherwise iTunes top movies (no key)
 *   favorites / continue   : stored on this TV
 *   football               : ESPN scoreboards via {@link Feeds}
 */
public final class Content {

    public static final class Item {
        public String id = "", title = "", subtitle = "", overview = "", poster = "", backdrop = "", year = "";
        public double rating;
        public boolean isNew;
        public String kind = "";    // series | movie | anime | tv | match

        JSONObject toJson() {
            JSONObject o = new JSONObject();
            try {
                o.put("id", id); o.put("title", title); o.put("subtitle", subtitle); o.put("overview", overview);
                o.put("poster", poster); o.put("backdrop", backdrop); o.put("year", year); o.put("rating", rating);
                o.put("kind", kind);
            } catch (Exception ignored) { }
            return o;
        }

        static Item fromJson(JSONObject o) {
            Item i = new Item();
            i.id = o.optString("id"); i.title = o.optString("title"); i.subtitle = o.optString("subtitle");
            i.overview = o.optString("overview"); i.poster = o.optString("poster"); i.backdrop = o.optString("backdrop");
            i.year = o.optString("year"); i.rating = o.optDouble("rating", 0); i.kind = o.optString("kind");
            return i;
        }
    }

    static final long TTL = 6 * 60 * 60 * 1000L;
    static final String PREFS = "ibra_content";

    private static final Map<String, List<Item>> memory = new HashMap<String, List<Item>>();

    static SharedPreferences prefs(Context c) { return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE); }

    public static String label(String cat, boolean ar) {
        if ("series".equals(cat)) return ar ? "المسلسلات الجديدة" : "New series";
        if ("movies".equals(cat)) return ar ? "الأفلام الجديدة" : "New movies";
        if ("trending".equals(cat)) return ar ? "الأكثر مشاهدة" : "Most watched";
        if ("anime".equals(cat)) return ar ? "الأنمي" : "Anime";
        if ("tv".equals(cat)) return ar ? "البرامج التلفزيونية" : "TV shows";
        if ("favorites".equals(cat)) return ar ? "المفضلة" : "Favorites";
        if ("continue".equals(cat)) return ar ? "متابعة المشاهدة" : "Continue watching";
        if ("football".equals(cat)) return ar ? "مباريات اليوم" : "Today's matches";
        return cat;
    }

    public static String icon(String cat) {
        if ("series".equals(cat)) return "i_series";
        if ("movies".equals(cat)) return "i_movie";
        if ("trending".equals(cat)) return "i_fire";
        if ("anime".equals(cat)) return "i_anime";
        if ("tv".equals(cat)) return "i_tv";
        if ("favorites".equals(cat)) return "i_star";
        if ("continue".equals(cat)) return "i_history";
        if ("football".equals(cat)) return "i_ball";
        return "i_movie";
    }

    // ------------------------------------------------------------------ read (UI thread safe: memory or cache)

    public static synchronized List<Item> items(Context c, Config cfg, String cat) {
        if (cfg.previewMode) return preview(cat, cfg.arabic());
        if ("favorites".equals(cat)) return stored(c, "favs");
        if ("continue".equals(cat)) return stored(c, "continue");
        if ("football".equals(cat)) return football(c, cfg);
        List<Item> m = memory.get(cat);
        if (m != null) return m;
        String raw = prefs(c).getString("cat_" + cat, null);
        List<Item> out = new ArrayList<Item>();
        if (raw != null) {
            try {
                JSONArray a = new JSONArray(raw);
                for (int i = 0; i < a.length(); i++) out.add(Item.fromJson(a.getJSONObject(i)));
            } catch (Exception ignored) { }
        }
        memory.put(cat, out);
        return out;
    }

    static synchronized void store(Context c, String cat, List<Item> list) {
        JSONArray a = new JSONArray();
        for (Item i : list) a.put(i.toJson());
        prefs(c).edit().putString("cat_" + cat, a.toString()).putLong("cat_" + cat + "_at", System.currentTimeMillis())
                .putString("cat_" + cat + "_src", sourceKey(c, cat)).apply();
        memory.put(cat, list);
    }

    static String sourceKey(Context c, String cat) {
        Config cfg = Config.load(c);
        return ("movies".equals(cat) || "trending".equals(cat)) ? (cfg.tmdbKey.isEmpty() ? "free" : "tmdb") : "free";
    }

    static boolean stale(Context c, String cat) {
        SharedPreferences p = prefs(c);
        return System.currentTimeMillis() - p.getLong("cat_" + cat + "_at", 0) > TTL
                || !sourceKey(c, cat).equals(p.getString("cat_" + cat + "_src", ""));
    }

    public static synchronized void clearCache(Context c) {
        SharedPreferences p = prefs(c);
        SharedPreferences.Editor e = p.edit();
        for (String k : p.getAll().keySet()) if (k.startsWith("cat_")) e.remove(k);
        e.apply();
        memory.clear();
    }

    // ------------------------------------------------------------------ refresh (background thread)

    /** Refreshes stale network categories that are enabled. Returns the categories that changed. */
    public static List<String> refresh(Context c, Config cfg, boolean force) {
        List<String> changed = new ArrayList<String>();
        if (cfg.previewMode || !cfg.showContent) return changed;
        TimeZone tz = TimeZone.getTimeZone(cfg.tz);
        boolean wantSeries = cfg.contentCats.contains("series"), wantTv = cfg.contentCats.contains("tv");
        boolean wantTrending = cfg.contentCats.contains("trending");
        if ((wantSeries || wantTv || (wantTrending && cfg.tmdbKey.isEmpty()))
                && (force || stale(c, "series") || stale(c, "tv"))) {
            try {
                List<Item>[] r = tvmaze(tz, cfg.arabic());
                if (!r[0].isEmpty()) { store(c, "series", r[0]); changed.add("series"); }
                if (!r[1].isEmpty()) { store(c, "tv", r[1]); changed.add("tv"); }
                if (cfg.tmdbKey.isEmpty() && !r[2].isEmpty()) { store(c, "trending", r[2]); changed.add("trending"); }
            } catch (Exception e) { android.util.Log.w("IBRA", "tvmaze: " + e); }
        }
        if (cfg.contentCats.contains("anime") && (force || stale(c, "anime"))) {
            try {
                List<Item> a;
                try { a = jikan(); }
                catch (Exception e) { android.util.Log.w("IBRA", "jikan failed, trying AniList: " + e); a = anilist(); }
                if (!a.isEmpty()) { store(c, "anime", a); changed.add("anime"); }
            } catch (Exception e) { android.util.Log.w("IBRA", "anime: " + e); }
        }
        if (cfg.contentCats.contains("movies") && (force || stale(c, "movies"))) {
            try {
                List<Item> m = cfg.tmdbKey.isEmpty() ? itunesMovies() : tmdb(cfg.tmdbKey, "/movie/now_playing", cfg.arabic());
                if (!m.isEmpty()) { store(c, "movies", m); changed.add("movies"); }
            } catch (Exception e) { android.util.Log.w("IBRA", "movies: " + e); }
        }
        if (wantTrending && !cfg.tmdbKey.isEmpty() && (force || stale(c, "trending"))) {
            try {
                List<Item> t = tmdb(cfg.tmdbKey, "/trending/all/week", cfg.arabic());
                if (!t.isEmpty()) { store(c, "trending", t); changed.add("trending"); }
            } catch (Exception ignored) { }
        }
        return changed;
    }

    // ------------------------------------------------------------------ TVmaze

    static final Set<String> PROGRAMME_TYPES = new HashSet<String>(java.util.Arrays.asList(
            "Reality", "Talk Show", "Game Show", "Variety", "News", "Documentary", "Sports", "Award Show", "Panel Show"));

    /** {series, tv programmes, most watched (by TVmaze weight)} */
    @SuppressWarnings("unchecked")
    static List<Item>[] tvmaze(TimeZone tz, boolean ar) throws Exception {
        String today = Feeds.today("yyyy-MM-dd", tz);
        java.util.Calendar y = java.util.Calendar.getInstance(tz);
        y.add(java.util.Calendar.DAY_OF_MONTH, -1);
        java.text.SimpleDateFormat f = new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US);
        f.setTimeZone(tz);
        String yesterday = f.format(y.getTime());

        List<JSONObject[]> eps = new ArrayList<JSONObject[]>();   // {episode, show}
        String[] urls = {
                "https://api.tvmaze.com/schedule/web?date=" + today,
                "https://api.tvmaze.com/schedule/web?date=" + yesterday,
                "https://api.tvmaze.com/schedule?country=US&date=" + today,
        };
        boolean any = false;
        for (String u : urls) {
            try {
                JSONArray a = new JSONArray(Feeds.http(u));
                any = true;
                for (int i = 0; i < a.length(); i++) {
                    JSONObject ep = a.getJSONObject(i);
                    JSONObject emb = ep.optJSONObject("_embedded");
                    JSONObject show = emb != null ? emb.optJSONObject("show") : ep.optJSONObject("show");
                    if (show != null) eps.add(new JSONObject[]{ep, show});
                }
            } catch (Exception ignored) { }
        }
        if (!any) throw new Exception("tvmaze unreachable");

        Map<String, Item> series = new LinkedHashMap<String, Item>(), tv = new LinkedHashMap<String, Item>();
        Map<String, Double> weight = new HashMap<String, Double>();
        long now = System.currentTimeMillis();
        for (JSONObject[] pair : eps) {
            JSONObject ep = pair[0], show = pair[1];
            String id = "tvmaze:" + show.optInt("id");
            JSONObject img = show.optJSONObject("image");
            if (img == null) continue;                          // posters only
            String type = show.optString("type", "");
            boolean programme = PROGRAMME_TYPES.contains(type);
            Map<String, Item> target = programme ? tv : series;
            if (target.containsKey(id)) continue;
            Item it = new Item();
            it.id = id;
            it.title = show.optString("name", "");
            it.poster = img.optString("original", img.optString("medium", ""));
            it.backdrop = it.poster;
            it.overview = stripHtml(show.optString("summary", ""));
            String prem = show.optString("premiered", "");
            it.year = prem.length() >= 4 ? prem.substring(0, 4) : "";
            JSONObject r = show.optJSONObject("rating");
            it.rating = r == null ? 0 : r.optDouble("average", 0);
            if (Double.isNaN(it.rating)) it.rating = 0;
            JSONObject ch = show.optJSONObject("webChannel");
            if (ch == null) ch = show.optJSONObject("network");
            String channel = ch == null ? "" : ch.optString("name", "");
            int s = ep.optInt("season", 0), n = ep.optInt("number", 0);
            it.subtitle = (channel.isEmpty() ? "" : channel + " · ") + (ar ? "م" + s + " ح" + n : "S" + s + " E" + n);
            it.isNew = n == 1 || isRecent(prem, now, 45);
            it.kind = programme ? "tv" : "series";
            target.put(id, it);
            weight.put(id, show.optDouble("weight", 0));
        }
        final Map<String, Double> w = weight;
        Comparator<Item> byNewThenWeight = new Comparator<Item>() {
            @Override public int compare(Item a, Item b) {
                if (a.isNew != b.isNew) return a.isNew ? -1 : 1;
                return Double.compare(w.get(b.id), w.get(a.id));
            }
        };
        List<Item> s = new ArrayList<Item>(series.values());
        List<Item> t = new ArrayList<Item>(tv.values());
        Collections.sort(s, byNewThenWeight);
        Collections.sort(t, byNewThenWeight);
        List<Item> top = new ArrayList<Item>(series.values());
        top.addAll(tv.values());
        Collections.sort(top, new Comparator<Item>() {
            @Override public int compare(Item a, Item b) { return Double.compare(w.get(b.id), w.get(a.id)); }
        });
        return new List[]{limit(s, 30), limit(t, 30), limit(top, 30)};
    }

    static boolean isRecent(String ymd, long now, int days) {
        try {
            java.text.SimpleDateFormat f = new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US);
            long t = f.parse(ymd).getTime();
            return now - t < days * 86400000L && now >= t;
        } catch (Exception e) {
            return false;
        }
    }

    // ------------------------------------------------------------------ Jikan (anime)

    static List<Item> jikan() throws Exception {
        JSONArray a;
        try {
            a = new JSONObject(Feeds.http("https://api.jikan.moe/v4/seasons/now?sfw=true&limit=25")).optJSONArray("data");
        } catch (Exception first) {
            android.util.Log.w("IBRA", "jikan seasons/now: " + first);
            Thread.sleep(1500);   // Jikan rate-limits bursts; one retry on a lighter endpoint
            a = new JSONObject(Feeds.http("https://api.jikan.moe/v4/top/anime?filter=airing&sfw=true&limit=25")).optJSONArray("data");
        }
        List<Item> out = new ArrayList<Item>();
        Set<String> seen = new HashSet<String>();
        for (int i = 0; a != null && i < a.length(); i++) {
            JSONObject o = a.getJSONObject(i);
            String id = "mal:" + o.optInt("mal_id");
            if (!seen.add(id)) continue;
            JSONObject jpg = o.optJSONObject("images") == null ? null : o.optJSONObject("images").optJSONObject("jpg");
            if (jpg == null) continue;
            Item it = new Item();
            it.id = id;
            String en = o.optString("title_english", "");
            it.title = en.isEmpty() || "null".equals(en) ? o.optString("title", "") : en;
            it.poster = jpg.optString("large_image_url", jpg.optString("image_url", ""));
            it.backdrop = it.poster;
            it.overview = o.optString("synopsis", "");
            if ("null".equals(it.overview)) it.overview = "";
            it.year = o.isNull("year") ? "" : String.valueOf(o.optInt("year"));
            it.rating = o.optDouble("score", 0);
            if (Double.isNaN(it.rating)) it.rating = 0;
            it.subtitle = o.optString("type", "") + (o.optInt("episodes", 0) > 0 ? " · " + o.optInt("episodes") + " ep" : "");
            it.isNew = true;
            it.kind = "anime";
            out.add(it);
        }
        return out;
    }

    /** Backup anime source: AniList GraphQL (no key), currently airing by popularity. */
    static List<Item> anilist() throws Exception {
        String q = "{\"query\":\"query{Page(perPage:25){media(type:ANIME,status:RELEASING,isAdult:false,sort:POPULARITY_DESC){id title{romaji english} coverImage{extraLarge large} bannerImage description(asHtml:false) averageScore episodes format seasonYear}}}\"}";
        java.net.HttpURLConnection con = (java.net.HttpURLConnection) new java.net.URL("https://graphql.anilist.co").openConnection();
        con.setConnectTimeout(8000);
        con.setReadTimeout(15000);
        con.setRequestMethod("POST");
        con.setDoOutput(true);
        con.setRequestProperty("Content-Type", "application/json");
        con.setRequestProperty("Accept", "application/json");
        con.setRequestProperty("User-Agent", "IBRA-TV-HOME/1.0");
        String body;
        try {
            con.getOutputStream().write(q.getBytes("UTF-8"));
            if (con.getResponseCode() != 200) throw new Exception("HTTP " + con.getResponseCode());
            java.io.InputStream in = con.getInputStream();
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0 && out.size() < 4 * 1024 * 1024) out.write(buf, 0, n);
            body = out.toString("UTF-8");
        } finally { con.disconnect(); }
        JSONArray m = new JSONObject(body).getJSONObject("data").getJSONObject("Page").getJSONArray("media");
        List<Item> outList = new ArrayList<Item>();
        for (int i = 0; i < m.length(); i++) {
            JSONObject o = m.getJSONObject(i);
            JSONObject cover = o.optJSONObject("coverImage");
            if (cover == null) continue;
            Item it = new Item();
            it.id = "anilist:" + o.optInt("id");
            JSONObject t = o.optJSONObject("title");
            String en = t == null ? "" : t.optString("english", "");
            it.title = en.isEmpty() || "null".equals(en) ? (t == null ? "" : t.optString("romaji", "")) : en;
            it.poster = cover.optString("extraLarge", cover.optString("large", ""));
            String banner = o.optString("bannerImage", "");
            it.backdrop = banner.isEmpty() || "null".equals(banner) ? it.poster : banner;
            it.overview = stripHtml(o.optString("description", "")).replace("null", "");
            it.year = o.isNull("seasonYear") ? "" : String.valueOf(o.optInt("seasonYear"));
            it.rating = o.optInt("averageScore", 0) / 10.0;
            it.subtitle = o.optString("format", "") + (o.optInt("episodes", 0) > 0 ? " · " + o.optInt("episodes") + " ep" : "");
            it.isNew = true;
            it.kind = "anime";
            if (!it.title.isEmpty() && !it.poster.isEmpty()) outList.add(it);
        }
        return outList;
    }

    // ------------------------------------------------------------------ TMDB (optional key)

    static List<Item> tmdb(String key, String path, boolean ar) throws Exception {
        String lang = ar ? "ar" : "en-US";
        String url = "https://api.themoviedb.org/3" + path + "?language=" + lang;
        Map<String, String> h = null;
        if (key.length() > 40) {                         // v4 read access token
            h = new HashMap<String, String>();
            h.put("Authorization", "Bearer " + key);
        } else {
            url += "&api_key=" + Feeds.enc(key);
        }
        JSONArray r = new JSONObject(Feeds.http(url, h)).optJSONArray("results");
        List<Item> out = new ArrayList<Item>();
        for (int i = 0; r != null && i < r.length(); i++) {
            JSONObject o = r.getJSONObject(i);
            String poster = o.optString("poster_path", "");
            if (poster.isEmpty() || "null".equals(poster)) continue;
            String media = o.optString("media_type", path.contains("/movie") ? "movie" : "tv");
            if ("person".equals(media)) continue;
            Item it = new Item();
            it.id = "tmdb:" + media + ":" + o.optInt("id");
            it.title = o.optString("title", o.optString("name", ""));
            it.poster = "https://image.tmdb.org/t/p/w342" + poster;
            String bd = o.optString("backdrop_path", "");
            it.backdrop = bd.isEmpty() || "null".equals(bd) ? it.poster : "https://image.tmdb.org/t/p/w780" + bd;
            it.overview = o.optString("overview", "");
            String d = o.optString("release_date", o.optString("first_air_date", ""));
            it.year = d.length() >= 4 ? d.substring(0, 4) : "";
            it.rating = o.optDouble("vote_average", 0);
            it.isNew = isRecent(d, System.currentTimeMillis(), 45);
            it.kind = "movie".equals(media) ? "movie" : "series";
            it.subtitle = "movie".equals(media) ? (ar ? "فيلم" : "Movie") : (ar ? "مسلسل" : "Series");
            out.add(it);
        }
        return out;
    }

    // ------------------------------------------------------------------ iTunes (no key)

    static List<Item> itunesMovies() throws Exception {
        JSONObject feed = new JSONObject(Feeds.http("https://itunes.apple.com/us/rss/topmovies/limit=25/json")).optJSONObject("feed");
        JSONArray e = feed == null ? null : feed.optJSONArray("entry");
        List<Item> out = new ArrayList<Item>();
        for (int i = 0; e != null && i < e.length(); i++) {
            JSONObject o = e.getJSONObject(i);
            Item it = new Item();
            it.title = label(o, "im:name");
            JSONArray imgs = o.optJSONArray("im:image");
            if (imgs == null || imgs.length() == 0) continue;
            String img = imgs.getJSONObject(imgs.length() - 1).optString("label", "");
            it.poster = img.replaceAll("/\\d+x\\d+bb", "/600x900bb");
            it.backdrop = it.poster;
            it.overview = label(o, "summary");
            JSONObject rel = o.optJSONObject("im:releaseDate");
            String d = rel == null ? "" : rel.optString("label", "");
            it.year = d.length() >= 4 ? d.substring(0, 4) : "";
            JSONObject id = o.optJSONObject("id");
            it.id = "itunes:" + (id != null && id.optJSONObject("attributes") != null ? id.getJSONObject("attributes").optString("im:id") : it.title);
            JSONObject cat = o.optJSONObject("category");
            it.subtitle = cat != null && cat.optJSONObject("attributes") != null ? cat.getJSONObject("attributes").optString("label", "") : "";
            it.isNew = isRecent(d.length() >= 10 ? d.substring(0, 10) : "", System.currentTimeMillis(), 120);
            it.kind = "movie";
            if (!it.title.isEmpty()) out.add(it);
        }
        return out;
    }

    static String label(JSONObject o, String key) {
        JSONObject x = o.optJSONObject(key);
        return x == null ? "" : x.optString("label", "");
    }

    // ------------------------------------------------------------------ football

    static List<Item> football(Context c, Config cfg) {
        List<Item> out = new ArrayList<Item>();
        TimeZone tz = TimeZone.getTimeZone(cfg.tz);
        boolean ar = cfg.arabic();
        for (Feeds.Match m : Feeds.matches(c)) {
            Item it = new Item();
            it.kind = "match";
            it.id = "match:" + m.home + "-" + m.away;
            it.title = m.home + "  " + ("pre".equals(m.state) ? "×" : m.homeScore + " - " + m.awayScore) + "  " + m.away;
            String when = "pre".equals(m.state) ? Feeds.time(m.kickoff, tz)
                    : "in".equals(m.state) ? (ar ? "● مباشر " : "● LIVE ") + m.detail : (ar ? "انتهت" : "FT");
            it.subtitle = m.league + " · " + when;
            it.isNew = "in".equals(m.state);
            out.add(it);
        }
        return out;
    }

    // ------------------------------------------------------------------ favorites / continue

    static synchronized List<Item> stored(Context c, String key) {
        List<Item> out = new ArrayList<Item>();
        try {
            JSONArray a = new JSONArray(prefs(c).getString(key, "[]"));
            for (int i = 0; i < a.length(); i++) out.add(Item.fromJson(a.getJSONObject(i)));
        } catch (Exception ignored) { }
        return out;
    }

    static synchronized void saveStored(Context c, String key, List<Item> list) {
        JSONArray a = new JSONArray();
        for (Item i : list) a.put(i.toJson());
        prefs(c).edit().putString(key, a.toString()).apply();
    }

    public static boolean isFavorite(Context c, Item it) {
        for (Item x : stored(c, "favs")) if (x.id.equals(it.id)) return true;
        return false;
    }

    public static void toggleFavorite(Context c, Item it) {
        List<Item> l = stored(c, "favs");
        for (int i = 0; i < l.size(); i++) if (l.get(i).id.equals(it.id)) { l.remove(i); saveStored(c, "favs", l); return; }
        l.add(0, it);
        saveStored(c, "favs", limit(l, 60));
    }

    /** Records an item the user opened from the launcher ("continue watching"). */
    public static void watched(Context c, Item it) {
        if ("match".equals(it.kind)) return;
        List<Item> l = stored(c, "continue");
        for (int i = 0; i < l.size(); i++) if (l.get(i).id.equals(it.id)) { l.remove(i); break; }
        l.add(0, it);
        saveStored(c, "continue", limit(l, 30));
    }

    public static void removeContinue(Context c, Item it) {
        List<Item> l = stored(c, "continue");
        for (int i = 0; i < l.size(); i++) if (l.get(i).id.equals(it.id)) { l.remove(i); break; }
        saveStored(c, "continue", l);
    }

    // ------------------------------------------------------------------ preview mode (sample cards, no network)

    static List<Item> preview(String cat, boolean ar) {
        List<Item> out = new ArrayList<Item>();
        String[] names = ar ? new String[]{"عنوان تجريبي ١", "عنوان تجريبي ٢", "عنوان تجريبي ٣", "عنوان تجريبي ٤", "عنوان تجريبي ٥", "عنوان تجريبي ٦"}
                            : new String[]{"Sample 1", "Sample 2", "Sample 3", "Sample 4", "Sample 5", "Sample 6"};
        for (int i = 0; i < names.length; i++) {
            Item it = new Item();
            it.id = "preview:" + cat + i;
            it.title = names[i];
            it.subtitle = ar ? "وضع المعاينة — بيانات غير حقيقية" : "Preview mode — not real data";
            it.overview = it.subtitle;
            it.isNew = i < 3;
            it.kind = "football".equals(cat) ? "match" : "series";
            out.add(it);
        }
        return out;
    }

    // ------------------------------------------------------------------ helpers

    static List<Item> limit(List<Item> l, int n) { return l.size() > n ? new ArrayList<Item>(l.subList(0, n)) : l; }

    static String stripHtml(String s) {
        return s == null ? "" : s.replaceAll("<[^>]+>", "").replace("&amp;", "&").replace("&quot;", "\"")
                .replace("&#39;", "'").replace("&nbsp;", " ").trim();
    }

    /** All cached titles, for search. */
    public static List<Item> allCached(Context c, Config cfg) {
        List<Item> out = new ArrayList<Item>();
        Set<String> ids = new HashSet<String>();
        for (String cat : Config.ALL_CATS) {
            if ("football".equals(cat)) continue;
            for (Item i : items(c, cfg, cat)) if (ids.add(i.id)) out.add(i);
        }
        return out;
    }
}
