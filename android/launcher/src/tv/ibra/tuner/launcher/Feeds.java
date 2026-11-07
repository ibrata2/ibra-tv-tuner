package tv.ibra.tuner.launcher;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TimeZone;

/**
 * Live info for the home screen: weather (Open-Meteo), football (ESPN public scoreboards) and
 * today's episodes (TVmaze). Raw responses are cached in their own prefs file so the home screen
 * works offline and the launcher settings listener is not triggered by refreshes.
 * Only HTTPS GETs to these three public APIs; nothing about the device is sent.
 */
public final class Feeds {
    static final String PREFS = "ibra_feeds";

    static final long WEATHER_TTL = 30 * 60 * 1000L;
    static final long FOOTBALL_TTL = 10 * 60 * 1000L;
    static final long SHOWS_TTL = 6 * 60 * 60 * 1000L;

    /** ESPN league slugs shown in the football card, in display order. */
    static final String[] LEAGUES = {
            "uefa.champions", "eng.1", "esp.1", "ita.1", "ger.1", "fra.1", "uefa.europa", "ksa.1", "fifa.world"
    };

    static final TimeZone BAGHDAD = TimeZone.getTimeZone("Asia/Baghdad");

    // ------------------------------------------------------------------ models

    public static final class Weather {
        public double temp;
        public int code;
        public boolean day = true;
        public final List<String> days = new ArrayList<String>();   // yyyy-MM-dd
        public final List<Integer> codes = new ArrayList<Integer>();
        public final List<Double> max = new ArrayList<Double>();
        public final List<Double> min = new ArrayList<Double>();
    }

    public static final class Match {
        public String league = "";
        public String home = "";
        public String away = "";
        public String homeScore = "";
        public String awayScore = "";
        public String state = "pre";   // pre | in | post
        public String detail = "";
        public Date kickoff;
    }

    public static final class Episode {
        public String show = "";
        public String channel = "";
        public int season;
        public int number;
        public double weight;
    }

    // ------------------------------------------------------------------ cache

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    static boolean stale(Context c, String key, long ttl) {
        return System.currentTimeMillis() - prefs(c).getLong(key + "_at", 0) > ttl;
    }

    static boolean has(Context c, String key) {
        return prefs(c).contains(key);
    }

    private static void put(Context c, String key, String body) {
        prefs(c).edit().putString(key, body).putLong(key + "_at", System.currentTimeMillis()).apply();
    }

    private static String get(Context c, String key) {
        return prefs(c).getString(key, null);
    }

    // ------------------------------------------------------------------ network

    static String http(String url) throws Exception {
        HttpURLConnection con = (HttpURLConnection) new URL(url).openConnection();
        con.setConnectTimeout(8000);
        con.setReadTimeout(12000);
        con.setRequestProperty("User-Agent", "IBRA-HOME/1.0 (Android TV launcher)");
        con.setRequestProperty("Accept", "application/json");
        try {
            int code = con.getResponseCode();
            if (code != 200) throw new Exception("HTTP " + code);
            InputStream in = con.getInputStream();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            long total = 0;
            while ((n = in.read(buf)) > 0) {
                total += n;
                if (total > 4L * 1024 * 1024) throw new Exception("response too large");
                out.write(buf, 0, n);
            }
            return out.toString("UTF-8");
        } finally {
            con.disconnect();
        }
    }

    static String today(String pattern) {
        SimpleDateFormat f = new SimpleDateFormat(pattern, Locale.US);
        f.setTimeZone(BAGHDAD);
        return f.format(new Date());
    }

    /** Refreshes whatever is stale. Call off the UI thread. Returns true if anything changed. */
    static boolean refresh(Context c, double lat, double lon, boolean force) {
        boolean changed = false;
        if (force || stale(c, "weather", WEATHER_TTL)) {
            try {
                String url = String.format(Locale.US,
                        "https://api.open-meteo.com/v1/forecast?latitude=%.4f&longitude=%.4f"
                                + "&current=temperature_2m,weather_code,is_day"
                                + "&daily=weather_code,temperature_2m_max,temperature_2m_min"
                                + "&timezone=auto&forecast_days=5", lat, lon);
                String body = http(url);
                if (parseWeather(body) != null) { put(c, "weather", body); changed = true; }
            } catch (Exception ignored) { }
        }
        if (force || stale(c, "football", FOOTBALL_TTL)) {
            JSONArray all = new JSONArray();
            String date = today("yyyyMMdd");
            for (String lg : LEAGUES) {
                try {
                    JSONObject o = new JSONObject(http("https://site.api.espn.com/apis/site/v2/sports/soccer/"
                            + lg + "/scoreboard?dates=" + date));
                    all.put(o);
                } catch (Exception ignored) { }
            }
            if (all.length() > 0) { put(c, "football", all.toString()); changed = true; }
        }
        if (force || stale(c, "shows", SHOWS_TTL)) {
            try {
                String body = http("https://api.tvmaze.com/schedule/web?date=" + today("yyyy-MM-dd"));
                if (!parseShows(body).isEmpty()) { put(c, "shows", body); changed = true; }
            } catch (Exception ignored) { }
        }
        return changed;
    }

    // ------------------------------------------------------------------ parsing

    static Weather weather(Context c) {
        String b = get(c, "weather");
        return b == null ? null : parseWeather(b);
    }

    static Weather parseWeather(String body) {
        try {
            JSONObject o = new JSONObject(body);
            JSONObject cur = o.getJSONObject("current");
            Weather w = new Weather();
            w.temp = cur.getDouble("temperature_2m");
            w.code = cur.optInt("weather_code", 0);
            w.day = cur.optInt("is_day", 1) == 1;
            JSONObject d = o.optJSONObject("daily");
            if (d != null) {
                JSONArray t = d.getJSONArray("time"), wc = d.getJSONArray("weather_code"),
                        mx = d.getJSONArray("temperature_2m_max"), mn = d.getJSONArray("temperature_2m_min");
                for (int i = 0; i < t.length(); i++) {
                    w.days.add(t.getString(i));
                    w.codes.add(wc.optInt(i, 0));
                    w.max.add(mx.optDouble(i, Double.NaN));
                    w.min.add(mn.optDouble(i, Double.NaN));
                }
            }
            return w;
        } catch (Exception e) {
            return null;
        }
    }

    static List<Match> matches(Context c) {
        List<Match> out = new ArrayList<Match>();
        String b = get(c, "football");
        if (b == null) return out;
        try {
            JSONArray boards = new JSONArray(b);
            for (int i = 0; i < boards.length(); i++) out.addAll(parseScoreboard(boards.getJSONObject(i)));
        } catch (Exception ignored) { }
        // live first, then upcoming by time, then finished
        Collections.sort(out, new Comparator<Match>() {
            int rank(Match m) { return "in".equals(m.state) ? 0 : "pre".equals(m.state) ? 1 : 2; }
            @Override public int compare(Match x, Match y) {
                int r = rank(x) - rank(y);
                if (r != 0) return r;
                long a = x.kickoff == null ? 0 : x.kickoff.getTime(), z = y.kickoff == null ? 0 : y.kickoff.getTime();
                return a < z ? -1 : a > z ? 1 : 0;
            }
        });
        return out;
    }

    static List<Match> parseScoreboard(JSONObject o) {
        List<Match> out = new ArrayList<Match>();
        String league = "";
        JSONArray lgs = o.optJSONArray("leagues");
        if (lgs != null && lgs.length() > 0) league = lgs.optJSONObject(0).optString("name", "");
        JSONArray ev = o.optJSONArray("events");
        if (ev == null) return out;
        SimpleDateFormat iso = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm'Z'", Locale.US);
        iso.setTimeZone(TimeZone.getTimeZone("UTC"));
        for (int i = 0; i < ev.length(); i++) {
            try {
                JSONObject e = ev.getJSONObject(i);
                JSONObject comp = e.getJSONArray("competitions").getJSONObject(0);
                Match m = new Match();
                m.league = league;
                try { m.kickoff = iso.parse(e.optString("date", "")); } catch (Exception ignored) { }
                JSONObject st = e.optJSONObject("status");
                if (st != null && st.optJSONObject("type") != null) {
                    m.state = st.getJSONObject("type").optString("state", "pre");
                    m.detail = st.getJSONObject("type").optString("shortDetail", "");
                }
                JSONArray cs = comp.getJSONArray("competitors");
                for (int k = 0; k < cs.length(); k++) {
                    JSONObject t = cs.getJSONObject(k);
                    JSONObject team = t.optJSONObject("team");
                    String name = team == null ? "" : team.optString("shortDisplayName", team.optString("displayName", ""));
                    if ("home".equals(t.optString("homeAway"))) { m.home = name; m.homeScore = t.optString("score", ""); }
                    else { m.away = name; m.awayScore = t.optString("score", ""); }
                }
                out.add(m);
            } catch (Exception ignored) { }
        }
        return out;
    }

    static List<Episode> shows(Context c) {
        String b = get(c, "shows");
        return b == null ? new ArrayList<Episode>() : parseShows(b);
    }

    static List<Episode> parseShows(String body) {
        List<Episode> out = new ArrayList<Episode>();
        try {
            JSONArray a = new JSONArray(body);
            Set<String> seen = new HashSet<String>();
            for (int i = 0; i < a.length(); i++) {
                JSONObject ep = a.getJSONObject(i);
                JSONObject emb = ep.optJSONObject("_embedded");
                JSONObject show = emb == null ? ep.optJSONObject("show") : emb.optJSONObject("show");
                if (show == null) continue;
                String name = show.optString("name", "");
                if (name.isEmpty() || !seen.add(name)) continue;
                Episode e = new Episode();
                e.show = name;
                e.season = ep.optInt("season", 0);
                e.number = ep.optInt("number", 0);
                e.weight = show.optDouble("weight", 0);
                JSONObject ch = show.optJSONObject("webChannel");
                if (ch == null) ch = show.optJSONObject("network");
                e.channel = ch == null ? "" : ch.optString("name", "");
                out.add(e);
            }
        } catch (Exception ignored) { }
        Collections.sort(out, new Comparator<Episode>() {
            @Override public int compare(Episode x, Episode y) { return Double.compare(y.weight, x.weight); }
        });
        return out.size() > 20 ? new ArrayList<Episode>(out.subList(0, 20)) : out;
    }

    // ------------------------------------------------------------------ labels

    /** WMO weather code → short label. */
    static String weatherText(int code, boolean ar) {
        if (code == 0) return ar ? "صحو" : "Clear";
        if (code <= 2) return ar ? "غائم جزئياً" : "Partly cloudy";
        if (code == 3) return ar ? "غائم" : "Cloudy";
        if (code == 45 || code == 48) return ar ? "ضباب" : "Fog";
        if (code >= 51 && code <= 57) return ar ? "رذاذ" : "Drizzle";
        if (code >= 61 && code <= 67) return ar ? "مطر" : "Rain";
        if (code >= 71 && code <= 77) return ar ? "ثلج" : "Snow";
        if (code >= 80 && code <= 82) return ar ? "زخات مطر" : "Showers";
        if (code >= 85 && code <= 86) return ar ? "زخات ثلج" : "Snow showers";
        if (code >= 95) return ar ? "عاصفة رعدية" : "Thunderstorm";
        return ar ? "—" : "—";
    }

    static String weatherSymbol(int code, boolean day) {
        if (code == 0) return day ? "☀" : "☾";
        if (code <= 2) return "⛅";
        if (code == 3 || code == 45 || code == 48) return "☁";
        if (code >= 71 && code <= 77 || code == 85 || code == 86) return "❄";
        if (code >= 95) return "⚡";
        return "☂";
    }

    static String time(Date d) {
        if (d == null) return "";
        SimpleDateFormat f = new SimpleDateFormat("HH:mm", Locale.US);
        f.setTimeZone(BAGHDAD);
        return f.format(d);
    }

    static String dayName(String ymd, boolean ar) {
        try {
            SimpleDateFormat in = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
            in.setTimeZone(BAGHDAD);
            Date d = in.parse(ymd);
            SimpleDateFormat out = new SimpleDateFormat("EEE", ar ? new Locale("ar") : Locale.US);
            out.setTimeZone(BAGHDAD);
            return out.format(d);
        } catch (Exception e) {
            return ymd;
        }
    }
}
