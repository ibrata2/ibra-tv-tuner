package tv.ibra.tuner.launcher;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

/**
 * Weather (Open-Meteo, no key), city search (Open-Meteo geocoding) and football (ESPN public scoreboards).
 * Raw responses are cached in their own prefs file so the home screen works offline.
 * Only HTTPS GETs; nothing about the device is sent.
 */
public final class Feeds {
    static final String PREFS = "ibra_feeds";
    static final long WEATHER_TTL = 30 * 60 * 1000L;
    static final long FOOTBALL_TTL = 10 * 60 * 1000L;

    static final String[] LEAGUES = {
            "uefa.champions", "eng.1", "esp.1", "ita.1", "ger.1", "fra.1", "uefa.europa", "ksa.1", "fifa.world"
    };

    // ------------------------------------------------------------------ models

    public static final class Weather {
        public double temp, wind;
        public int code, humidity;
        public boolean day = true;
        public final List<String> days = new ArrayList<String>();
        public final List<Integer> codes = new ArrayList<Integer>();
        public final List<Double> max = new ArrayList<Double>();
        public final List<Double> min = new ArrayList<Double>();
    }

    public static final class Match {
        public String league = "", home = "", away = "", homeScore = "", awayScore = "", state = "pre", detail = "";
        public Date kickoff;
    }

    public static final class Place {
        public String name, region, country, tz;
        public double lat, lon;
    }

    // ------------------------------------------------------------------ cache + http

    private static SharedPreferences prefs(Context c) { return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE); }

    static boolean stale(Context c, String key, long ttl) {
        return System.currentTimeMillis() - prefs(c).getLong(key + "_at", 0) > ttl;
    }

    static boolean has(Context c, String key) { return prefs(c).contains(key); }

    static void put(Context c, String key, String body) {
        prefs(c).edit().putString(key, body).putLong(key + "_at", System.currentTimeMillis()).apply();
    }

    static String get(Context c, String key) { return prefs(c).getString(key, null); }

    static void clear(Context c) { prefs(c).edit().clear().apply(); }

    static String http(String url) throws Exception { return http(url, null); }

    static String http(String url, Map<String, String> headers) throws Exception {
        HttpURLConnection con = (HttpURLConnection) new URL(url).openConnection();
        con.setConnectTimeout(8000);
        con.setReadTimeout(15000);
        con.setRequestProperty("User-Agent", "IBRA-TV-HOME/1.0 (Android TV launcher)");
        con.setRequestProperty("Accept", "application/json");
        if (headers != null) for (Map.Entry<String, String> e : headers.entrySet()) con.setRequestProperty(e.getKey(), e.getValue());
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
                if (total > 6L * 1024 * 1024) throw new Exception("response too large");
                out.write(buf, 0, n);
            }
            return out.toString("UTF-8");
        } finally {
            con.disconnect();
        }
    }

    static String enc(String s) {
        try { return URLEncoder.encode(s, "UTF-8"); } catch (Exception e) { return s; }
    }

    static String today(String pattern, TimeZone tz) {
        SimpleDateFormat f = new SimpleDateFormat(pattern, Locale.US);
        f.setTimeZone(tz);
        return f.format(new Date());
    }

    // ------------------------------------------------------------------ refresh

    /** Refreshes stale weather/football. Off the UI thread. Returns true if anything changed. */
    static boolean refresh(Context c, Config cfg, boolean force) {
        boolean changed = false;
        String place = String.format(Locale.US, "%.3f,%.3f", cfg.lat, cfg.lon);
        boolean moved = !place.equals(prefs(c).getString("weather_place", ""));
        if (cfg.showWeather && (force || moved || stale(c, "weather", WEATHER_TTL))) {
            try {
                String url = String.format(Locale.US,
                        "https://api.open-meteo.com/v1/forecast?latitude=%.4f&longitude=%.4f"
                                + "&current=temperature_2m,weather_code,is_day,relative_humidity_2m,wind_speed_10m"
                                + "&daily=weather_code,temperature_2m_max,temperature_2m_min"
                                + "&timezone=%s&forecast_days=7", cfg.lat, cfg.lon, enc(cfg.tz));
                String body = http(url);
                if (parseWeather(body) != null) {
                    put(c, "weather", body);
                    prefs(c).edit().putString("weather_place", place).apply();
                    changed = true;
                }
            } catch (Exception ignored) { }
        }
        boolean wantFootball = cfg.showContent && cfg.contentCats.contains("football");
        if (wantFootball && (force || stale(c, "football", FOOTBALL_TTL))) {
            JSONArray all = new JSONArray();
            String date = today("yyyyMMdd", TimeZone.getTimeZone(cfg.tz));
            for (String lg : LEAGUES) {
                try {
                    all.put(new JSONObject(http("https://site.api.espn.com/apis/site/v2/sports/soccer/" + lg + "/scoreboard?dates=" + date)));
                } catch (Exception ignored) { }
            }
            if (all.length() > 0) { put(c, "football", all.toString()); changed = true; }
        }
        return changed;
    }

    // ------------------------------------------------------------------ weather

    static Weather weather(Context c, Config cfg) {
        String place = String.format(Locale.US, "%.3f,%.3f", cfg.lat, cfg.lon);
        if (!place.equals(prefs(c).getString("weather_place", ""))) return null;   // cached data is for another city
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
            w.humidity = cur.optInt("relative_humidity_2m", -1);
            w.wind = cur.optDouble("wind_speed_10m", -1);
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

    /** City search for the settings screen. Off the UI thread. */
    static List<Place> searchCity(String q, boolean ar) throws Exception {
        String body = http("https://geocoding-api.open-meteo.com/v1/search?count=10&format=json&language="
                + (ar ? "ar" : "en") + "&name=" + enc(q));
        List<Place> out = new ArrayList<Place>();
        JSONArray r = new JSONObject(body).optJSONArray("results");
        if (r == null) return out;
        for (int i = 0; i < r.length(); i++) {
            JSONObject o = r.getJSONObject(i);
            Place p = new Place();
            p.name = o.optString("name", "");
            p.region = o.optString("admin1", "");
            p.country = o.optString("country", "");
            p.tz = o.optString("timezone", "UTC");
            p.lat = o.optDouble("latitude", 0);
            p.lon = o.optDouble("longitude", 0);
            if (!p.name.isEmpty()) out.add(p);
        }
        return out;
    }

    /** Iraqi cities offered without internet. {name_ar, name_en, lat, lon} */
    static final Object[][] IRAQ = {
            {"بغداد", "Baghdad", 33.3152, 44.3661}, {"البصرة", "Basra", 30.5085, 47.7804},
            {"النجف", "Najaf", 32.0259, 44.3462}, {"كربلاء", "Karbala", 32.6160, 44.0249},
            {"الموصل", "Mosul", 36.3350, 43.1189}, {"أربيل", "Erbil", 36.1911, 44.0092},
            {"السليمانية", "Sulaymaniyah", 35.5613, 45.4374}, {"كركوك", "Kirkuk", 35.4681, 44.3922},
            {"الحلة", "Hillah", 32.4637, 44.4199}, {"الناصرية", "Nasiriyah", 31.0439, 46.2575},
            {"العمارة", "Amarah", 31.8356, 47.1446}, {"الكوت", "Kut", 32.5128, 45.8182},
            {"الديوانية", "Diwaniyah", 31.9929, 44.9255}, {"السماوة", "Samawah", 31.3099, 45.2806},
            {"بعقوبة", "Baqubah", 33.7466, 44.6437}, {"الرمادي", "Ramadi", 33.4206, 43.3078},
            {"تكريت", "Tikrit", 34.6070, 43.6782}, {"دهوك", "Duhok", 36.8679, 42.9883},
            {"سامراء", "Samarra", 34.1959, 43.8857}, {"الفلوجة", "Fallujah", 33.3533, 43.7829},
    };

    // ------------------------------------------------------------------ labels

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
        return "—";
    }

    /** Icon resource name for a WMO code. */
    static String weatherIcon(int code, boolean day) {
        if (code == 0) return day ? "w_sun" : "w_moon";
        if (code <= 2) return day ? "w_cloud_sun" : "w_cloud_moon";
        if (code == 3) return "w_cloud";
        if (code == 45 || code == 48) return "w_fog";
        if (code >= 71 && code <= 77 || code == 85 || code == 86) return "w_snow";
        if (code >= 95) return "w_storm";
        return "w_rain";
    }

    // ------------------------------------------------------------------ football

    static List<Match> matches(Context c) {
        List<Match> out = new ArrayList<Match>();
        String b = get(c, "football");
        if (b == null) return out;
        try {
            JSONArray boards = new JSONArray(b);
            for (int i = 0; i < boards.length(); i++) out.addAll(parseScoreboard(boards.getJSONObject(i)));
        } catch (Exception ignored) { }
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

    static String time(Date d, TimeZone tz) {
        if (d == null) return "";
        SimpleDateFormat f = new SimpleDateFormat("HH:mm", Locale.US);
        f.setTimeZone(tz);
        return f.format(d);
    }

    static String dayName(String ymd, boolean ar, TimeZone tz) {
        try {
            SimpleDateFormat in = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
            in.setTimeZone(tz);
            java.util.Calendar c = java.util.Calendar.getInstance(tz);
            c.setTime(in.parse(ymd));
            int dow = c.get(java.util.Calendar.DAY_OF_WEEK) - 1;
            return ar ? Prayer.DAYS_AR[dow] : Prayer.DAYS_EN[dow].substring(0, 3);
        } catch (Exception e) {
            return ymd;
        }
    }
}
