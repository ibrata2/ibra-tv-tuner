package tv.ibra.tuner.launcher;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

/** Every setting of the spec, usable with the remote. Changes are saved immediately. */
public class SettingsActivity extends Activity {
    private Config cfg;
    private boolean ar;
    private LinearLayout list;
    private ScrollView scroll;
    private final Handler h = new Handler();
    static final String BACKUP_NAME = "ibra-tv-home-backup.json";

    private String s(String a, String e) { return ar ? a : e; }
    private int u(float v) { return Ui.u(this, v); }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        build();
        final String open = getIntent().getStringExtra("open");
        if (open != null) h.postDelayed(new Runnable() {
            @Override public void run() {
                if ("city".equals(open)) city();
                else if ("prayer".equals(open)) focusRow("prayer");
                else if ("content".equals(open)) focusRow("content");
            }
        }, 200);
    }

    private void save() { cfg.save(this); }

    private void changed() {
        save();
        View f = getCurrentFocus();
        String tag = f != null && f.getTag() instanceof String ? (String) f.getTag() : null;
        int y = scroll == null ? 0 : scroll.getScrollY();
        build();
        if (tag != null) { View v = list.findViewWithTag(tag); if (v != null) v.requestFocus(); }
        final int yy = y;
        scroll.post(new Runnable() { @Override public void run() { scroll.scrollTo(0, yy); } });
    }

    private void focusRow(String tag) {
        View v = list.findViewWithTag(tag);
        if (v != null) { v.requestFocus(); scroll.smoothScrollTo(0, v.getTop()); }
    }

    // ------------------------------------------------------------------ layout

    private void build() {
        cfg = Config.load(this);
        ar = cfg.arabic();
        FrameLayout root = new FrameLayout(this);
        root.setLayoutDirection(ar ? View.LAYOUT_DIRECTION_RTL : View.LAYOUT_DIRECTION_LTR);
        root.addView(new Ui.Background(this, cfg), Dlg.match());
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(u(300), u(40), u(300), 0);
        root.addView(col, Dlg.match());
        LinearLayout head = Ui.iconText(this, cfg, "i_settings", s("الإعدادات", "Settings"), 42, 46, Ui.text(cfg));
        head.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        col.addView(head);
        scroll = new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setPadding(0, u(16), 0, u(40));
        scroll.setClipToPadding(false);
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(list);
        col.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        String[] themes = themeNames();
        int ti = Arrays.asList(Config.THEMES).indexOf(cfg.theme);

        section(s("المظهر", "Look"));
        row("theme", "i_edit", s("الثيم", "Theme"), ti >= 0 ? themes[ti] : s("ألوان من الحاسبة", "Colours from PC"), new Runnable() { public void run() { theme(); } });
        row("bg", "i_tv", s("الخلفية", "Background"), bgName(cfg.background), new Runnable() { public void run() { background(); } });
        row("alpha", "i_apps", s("شفافية المربعات", "Card transparency"), cfg.cardAlpha + "%", new Runnable() { public void run() { alpha(); } });
        row("icons", "i_apps", s("حجم أيقونات المفضلة", "Favourite tile size"), sizeName(cfg.iconSize), new Runnable() { public void run() { iconSize(); } });
        row("cols", "i_apps", s("عدد التطبيقات بالصف (كل التطبيقات)", "Apps per row (all apps)"), String.valueOf(cfg.columns), new Runnable() { public void run() { columns(); } });
        row("lang", "i_info", s("اللغة", "Language"), ar ? "العربية" : "English", new Runnable() { public void run() { language(); } });
        row("fx", "i_fire", s("المؤثرات والحركة", "Effects & motion"), fxName(), new Runnable() { public void run() { effects(); } });
        row("title", "i_edit", s("عنوان الواجهة", "Launcher title"), cfg.title, new Runnable() { public void run() { title(); } });

        section(s("الشاشة الرئيسية", "Home screen"));
        row("show", "i_home", s("إظهار / إخفاء", "Show / hide"), shownSummary(), new Runnable() { public void run() { show(); } });
        row("cardsOrder", "i_sort", s("ترتيب البطاقات العلوية", "Order of top cards"), cardsSummary(), new Runnable() { public void run() { cardsOrder(); } });
        row("sectionsOrder", "i_sort", s("ترتيب الأقسام", "Order of sections"), sectionsSummary(), new Runnable() { public void run() { sectionsOrder(); } });

        section(s("المدينة والطقس", "City & weather"));
        row("city", "i_pin", s("المدينة", "City"), cfg.city, new Runnable() { public void run() { city(); } });

        section(s("الصلاة", "Prayer"));
        row("prayer", "i_mosque", s("طريقة الحساب", "Calculation"), methodName(cfg.prayerMethod), new Runnable() { public void run() { method(); } });
        row("pdisplay", "i_mosque", s("طريقة العرض", "Display"), cfg.prayerCombined ? s("الظهرين والعشاءين", "Combined") : s("خمسة أوقات", "Five times"),
                new Runnable() { public void run() { cfg.prayerCombined = !cfg.prayerCombined; changed(); PrayerAlarm.schedule(SettingsActivity.this); } });
        row("poffsets", "i_edit", s("ضبط الدقائق يدوياً", "Manual minute offsets"), offsetsSummary(), new Runnable() { public void run() { offsets(); } });
        row("palert", "i_history", s("التنبيه والأذان", "Alert & adhan"), alertName(cfg.prayerAlert), new Runnable() { public void run() { alert(); } });
        row("hijri", "i_history", s("تصحيح التاريخ الهجري", "Hijri date correction"), signed(cfg.hijriOffset) + s(" يوم", " day"), new Runnable() { public void run() { hijri(); } });

        section(s("مركز الترفيه", "Entertainment"));
        row("content", "i_movie", s("الأقسام الظاهرة", "Visible categories"), cfg.contentCats.size() + "/" + Config.ALL_CATS.length, new Runnable() { public void run() { cats(); } });
        row("catsOrder", "i_sort", s("ترتيب الأقسام", "Category order"), cfg.contentCats.isEmpty() ? "—" : Content.label(cfg.contentCats.get(0), ar) + "…", new Runnable() { public void run() { catsOrder(); } });
        row("tmdb", "i_movie", s("مفتاح TMDB (اختياري) للأفلام والأكثر مشاهدة", "TMDB key (optional) for movies & trending"),
                cfg.tmdbKey.isEmpty() ? s("غير مضاف — مصدر مجاني", "not set — free source") : s("محفوظ ✓", "saved ✓"), new Runnable() { public void run() { tmdb(); } });
        row("sources", "i_info", s("مصادر المحتوى", "Content sources"), "", new Runnable() { public void run() { sources(); } });
        row("preview", "i_tv", s("وضع المعاينة (بيانات تجريبية)", "Preview mode (sample data)"), cfg.previewMode ? s("مفعّل", "on") : s("متوقف", "off"),
                new Runnable() { public void run() { cfg.previewMode = !cfg.previewMode; changed(); } });
        row("cache", "i_delete", s("مسح الصور والمحتوى المؤقت", "Clear cached posters & content"), "", new Runnable() { public void run() { clearCache(); } });

        section(s("النظام", "System"));
        row("home", "i_home", s("تعيين كواجهة رئيسية", "Set as Home app"), "", new Runnable() { public void run() { homeGuide(); } });
        row("backup", "i_down", s("حفظ نسخة احتياطية", "Save a backup"), "", new Runnable() { public void run() { backup(); } });
        row("restore", "i_up", s("استعادة النسخة الاحتياطية", "Restore backup"), "", new Runnable() { public void run() { restore(); } });
        row("reset", "i_refresh", s("إعادة التصميم الافتراضي", "Reset design to default"), "", new Runnable() { public void run() { reset(); } });
        row("about", "i_info", s("حول", "About"), "IBRA TV HOME " + version(), new Runnable() { public void run() { about(); } });

        setContentView(root);
        if (list.getChildCount() > 1) list.getChildAt(1).requestFocus();
    }

    private void section(String name) {
        TextView t = Ui.tv(this, cfg, name, 26, true);
        t.setTextColor(Ui.accent(cfg));
        t.setPadding(u(10), u(26), u(10), u(10));
        list.addView(t);
    }

    private void row(String tag, String icon, String label, String value, final Runnable r) {
        LinearLayout row = Dlg.row(this, cfg, icon, label, value, false);
        row.setTag(tag);
        row.setBackground(Ui.glass(this, cfg, 18));
        final android.graphics.drawable.GradientDrawable ring = (android.graphics.drawable.GradientDrawable)
                ((android.graphics.drawable.LayerDrawable) row.getBackground()).getDrawable(0);
        Ui.focusable(this, cfg, row, ring, 1.015f, null);
        row.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { r.run(); } });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = u(10);
        list.addView(row, lp);
    }

    // ------------------------------------------------------------------ names

    private String[] themeNames() {
        return new String[]{s("أسود زجاجي", "Black glass"), s("أبيض زجاجي", "White glass"), s("أحمر وأسود", "Red & black"),
                s("بنفسجي سينمائي", "Cinema purple"), s("ذهبي IBRA", "IBRA gold")};
    }

    private String bgName(String b) {
        if ("gradient".equals(b)) return s("ثابتة — تدرّج", "Static — gradient");
        if ("animated".equals(b)) return s("متحركة — أضواء ناعمة", "Animated — soft lights");
        return s("ثابتة — أفق ليلي", "Static — night horizon");
    }

    private String sizeName(int i) { return i == 0 ? s("صغير", "Small") : i == 2 ? s("كبير", "Large") : s("متوسط", "Medium"); }

    private String fxName() {
        String v = "on".equals(cfg.effects) ? s("تشغيل", "On") : "off".equals(cfg.effects) ? s("إيقاف", "Off") : s("تلقائي", "Auto");
        if ("auto".equals(cfg.effects) && Ui.lowEnd(this)) v += s(" (جهاز ضعيف: متوقفة)", " (low-end: off)");
        return v;
    }

    private String methodName(int m) {
        return m == Prayer.TEHRAN ? s("معهد الجيوفيزياء – طهران", "Geophysics Institute, Tehran")
                : s("جعفري – معهد ليوة، قم", "Ja'fari – Leva Institute, Qum");
    }

    private String alertName(String a) {
        return "tone".equals(a) ? s("تنبيه بصوت", "Tone") : "adhan".equals(a) ? s("أذان", "Adhan") : s("إيقاف", "Off");
    }

    static String signed(int v) { return v > 0 ? "+" + v : String.valueOf(v); }

    private String keyName(String k) {
        if ("weather".equals(k)) return s("الساعة والطقس", "Clock & weather");
        if ("content".equals(k)) return s("مركز الترفيه", "Entertainment");
        if ("prayer".equals(k)) return s("مواقيت الصلاة", "Prayer times");
        if ("cards".equals(k)) return s("البطاقات العلوية", "Top cards");
        if ("favorites".equals(k)) return s("التطبيقات المفضلة", "Favourite apps");
        return k;
    }

    private String cardsSummary() {
        StringBuilder sb = new StringBuilder();
        for (String k : cfg.cardsOrder) sb.append(sb.length() == 0 ? "" : " · ").append(keyName(k));
        return sb.toString();
    }

    private String sectionsSummary() {
        StringBuilder sb = new StringBuilder();
        for (String k : cfg.sectionsOrder) sb.append(sb.length() == 0 ? "" : " · ").append(keyName(k));
        return sb.toString();
    }

    private String shownSummary() {
        int n = 0;
        for (boolean b : new boolean[]{cfg.showClock, cfg.showWeather, cfg.showContent, cfg.showPrayer, cfg.showFavorites, cfg.showAllApps}) if (b) n++;
        return n + "/6";
    }

    private String offsetsSummary() {
        for (int v : cfg.prayerOffsets) if (v != 0) return s("مخصّص", "custom");
        return s("بدون", "none");
    }

    private String version() {
        try { return getPackageManager().getPackageInfo(getPackageName(), 0).versionName; } catch (Exception e) { return ""; }
    }

    // ------------------------------------------------------------------ look

    private void theme() {
        int ti = Arrays.asList(Config.THEMES).indexOf(cfg.theme);
        Dlg.choose(this, cfg, s("الثيم", "Theme"), themeNames(), null, null, ti, new Dlg.Pick() {
            public void on(int i) { cfg.theme = Config.THEMES[i]; changed(); }
        });
    }

    private void background() {
        final String[] keys = {"horizon", "gradient", "animated"};
        String[] names = {bgName("horizon"), bgName("gradient"), bgName("animated")};
        Dlg.choose(this, cfg, s("الخلفية", "Background"), names, null,
                new String[]{null, null, Ui.lowEnd(this) && !"on".equals(cfg.effects) ? s("تتوقف على الأجهزة الضعيفة", "paused on low-end") : null},
                Arrays.asList(keys).indexOf(cfg.background), new Dlg.Pick() {
                    public void on(int i) { cfg.background = keys[i]; changed(); }
                });
    }

    private void alpha() {
        final int[] vals = {10, 20, 30, 38, 50, 60, 70, 80, 90};
        String[] names = new String[vals.length];
        int sel = -1;
        for (int i = 0; i < vals.length; i++) { names[i] = vals[i] + "%"; if (vals[i] == cfg.cardAlpha) sel = i; }
        Dlg.choose(this, cfg, s("شفافية المربعات (أقل = أشفّ)", "Card opacity (lower = clearer)"), names, null, null, sel, new Dlg.Pick() {
            public void on(int i) { cfg.cardAlpha = vals[i]; changed(); }
        });
    }

    private void iconSize() {
        Dlg.choose(this, cfg, s("حجم الأيقونات", "Tile size"), new String[]{sizeName(0), sizeName(1), sizeName(2)}, null, null, cfg.iconSize,
                new Dlg.Pick() { public void on(int i) { cfg.iconSize = i; changed(); } });
    }

    private void columns() {
        final String[] n = {"4", "5", "6", "7", "8"};
        Dlg.choose(this, cfg, s("عدد التطبيقات بالصف", "Apps per row"), n, null, null, cfg.columns - 4,
                new Dlg.Pick() { public void on(int i) { cfg.columns = i + 4; changed(); } });
    }

    private void language() {
        Dlg.choose(this, cfg, s("اللغة", "Language"), new String[]{"العربية", "English"}, null, null, ar ? 0 : 1,
                new Dlg.Pick() { public void on(int i) { cfg.lang = i == 0 ? "ar" : "en"; changed(); } });
    }

    private void effects() {
        final String[] keys = {"auto", "on", "off"};
        Dlg.choose(this, cfg, s("المؤثرات والحركة", "Effects & motion"),
                new String[]{s("تلقائي (يوقف الثقيل على الأجهزة الضعيفة)", "Auto (off on low-end devices)"), s("تشغيل دائماً", "Always on"), s("إيقاف", "Off")},
                null, null, Arrays.asList(keys).indexOf(cfg.effects), new Dlg.Pick() {
                    public void on(int i) { cfg.effects = keys[i]; changed(); }
                });
    }

    private void title() {
        Dlg.input(this, cfg, s("عنوان الواجهة", "Launcher title"), "IBRA TV HOME", cfg.title, false, new Dlg.Text() {
            public void on(String v) { if (!v.isEmpty()) { cfg.title = Config.clip(v, 40); changed(); } }
        });
    }

    // ------------------------------------------------------------------ home layout

    private void show() {
        String[] names = {s("الساعة", "Clock"), s("الطقس", "Weather"), s("مركز الترفيه", "Entertainment"), s("مواقيت الصلاة", "Prayer times"),
                s("التطبيقات المفضلة", "Favourite apps"), s("زر كل التطبيقات", "All-apps button")};
        boolean[] v = {cfg.showClock, cfg.showWeather, cfg.showContent, cfg.showPrayer, cfg.showFavorites, cfg.showAllApps};
        Dlg.multi(this, cfg, s("إظهار / إخفاء", "Show / hide"), names, v, new Dlg.Multi() {
            public void on(boolean[] c) {
                cfg.showClock = c[0]; cfg.showWeather = c[1]; cfg.showContent = c[2]; cfg.showPrayer = c[3]; cfg.showFavorites = c[4]; cfg.showAllApps = c[5];
                changed();
                PrayerAlarm.schedule(SettingsActivity.this);
            }
        });
    }

    private void cardsOrder() {
        final List<String> keys = new ArrayList<String>(cfg.cardsOrder);
        List<String> labels = new ArrayList<String>();
        for (String k : keys) labels.add(keyName(k));
        Dlg.reorder(this, cfg, s("ترتيب البطاقات (الأول = ", "Card order (first = ") + (ar ? "اليمين)" : "left)"), keys, labels, new Runnable() {
            public void run() { cfg.cardsOrder.clear(); cfg.cardsOrder.addAll(keys); changed(); }
        });
    }

    private void sectionsOrder() {
        final List<String> keys = new ArrayList<String>(cfg.sectionsOrder);
        List<String> labels = new ArrayList<String>();
        for (String k : keys) labels.add(keyName(k));
        Dlg.reorder(this, cfg, s("ترتيب الأقسام", "Section order"), keys, labels, new Runnable() {
            public void run() { cfg.sectionsOrder.clear(); cfg.sectionsOrder.addAll(keys); changed(); }
        });
    }

    // ------------------------------------------------------------------ city

    void city() {
        final List<String> labels = new ArrayList<String>();
        List<String> icons = new ArrayList<String>();
        labels.add(s("حسب موقع الجهاز", "Use device location")); icons.add("i_pin");
        labels.add(s("ابحث عن مدينة (أي دولة)", "Search a city (any country)")); icons.add("i_search");
        int sel = -1;
        for (int i = 0; i < Feeds.IRAQ.length; i++) {
            String n = (String) Feeds.IRAQ[i][ar ? 0 : 1];
            labels.add(n); icons.add(null);
            if (Math.abs((Double) Feeds.IRAQ[i][2] - cfg.lat) < 0.01 && Math.abs((Double) Feeds.IRAQ[i][3] - cfg.lon) < 0.01) sel = i + 2;
        }
        Dlg.choose(this, cfg, s("المدينة (للطقس والصلاة)", "City (weather & prayer)"), labels.toArray(new String[0]), icons.toArray(new String[0]), null, sel,
                new Dlg.Pick() {
                    public void on(int i) {
                        if (i == 0) { locate(); return; }
                        if (i == 1) { searchCity(); return; }
                        Object[] c = Feeds.IRAQ[i - 2];
                        setCity((String) c[ar ? 0 : 1], (Double) c[2], (Double) c[3], "Asia/Baghdad");
                    }
                });
    }

    private void setCity(String name, double lat, double lon, String tz) {
        cfg.city = Config.clip(name, 40);
        cfg.lat = lat;
        cfg.lon = lon;
        cfg.tz = tz;
        changed();
        PrayerAlarm.schedule(this);
        Toast.makeText(this, s("صارت المدينة: ", "City set: ") + cfg.city, Toast.LENGTH_SHORT).show();
    }

    private void searchCity() {
        Dlg.input(this, cfg, s("ابحث عن مدينة", "Search a city"), s("مثلاً: الكوفة أو Istanbul", "e.g. Kufa or Istanbul"), "", false, new Dlg.Text() {
            public void on(final String q) {
                if (q.isEmpty()) return;
                new Thread(new Runnable() {
                    public void run() {
                        List<Feeds.Place> r = null;
                        try { r = Feeds.searchCity(q, ar); } catch (Exception ignored) { }
                        final List<Feeds.Place> res = r;
                        h.post(new Runnable() { public void run() { cityResults(res); } });
                    }
                }).start();
            }
        });
    }

    private void cityResults(final List<Feeds.Place> res) {
        if (res == null) { Toast.makeText(this, s("ما گدرت أبحث، تأكد من الإنترنت", "Search failed — check the internet"), Toast.LENGTH_LONG).show(); return; }
        if (res.isEmpty()) { Toast.makeText(this, s("ما لگيت مدينة بهذا الاسم", "No city with that name"), Toast.LENGTH_LONG).show(); return; }
        List<String> labels = new ArrayList<String>(), trail = new ArrayList<String>();
        for (Feeds.Place p : res) { labels.add(p.name); trail.add((p.region.isEmpty() ? "" : p.region + "، ") + p.country); }
        Dlg.choose(this, cfg, s("اختار المدينة", "Pick the city"), labels.toArray(new String[0]), null, trail.toArray(new String[0]), -1, new Dlg.Pick() {
            public void on(int i) { Feeds.Place p = res.get(i); setCity(p.name, p.lat, p.lon, p.tz); }
        });
    }

    private void locate() {
        if (Build.VERSION.SDK_INT >= 23 && checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, 5);
            return;
        }
        Location best = null;
        try {
            LocationManager lm = (LocationManager) getSystemService(LOCATION_SERVICE);
            for (String p : lm.getProviders(true)) {
                Location l = lm.getLastKnownLocation(p);
                if (l != null && (best == null || l.getTime() > best.getTime())) best = l;
            }
        } catch (SecurityException ignored) {
        } catch (Exception ignored) { }
        if (best == null) {
            Dlg.info(this, cfg, s("الموقع", "Location"), s("التلفزيون ما يعرف موقعه (أغلب التلفزيونات ما بيها GPS).\nاختار مدينتك من القائمة أو ابحث عنها.",
                    "This TV does not know its location (most TVs have no GPS).\nPick your city from the list or search it."));
            return;
        }
        setCity(s("موقعي", "My location") + String.format(Locale.US, " (%.2f, %.2f)", best.getLatitude(), best.getLongitude()),
                best.getLatitude(), best.getLongitude(), TimeZone.getDefault().getID());
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] res) {
        if (code == 5 && res.length > 0 && res[0] == PackageManager.PERMISSION_GRANTED) locate();
        else if (code == 5) Toast.makeText(this, s("بدون إذن الموقع اختار المدينة يدوياً", "Without location permission, pick the city manually"), Toast.LENGTH_LONG).show();
    }

    // ------------------------------------------------------------------ prayer

    private void method() {
        Dlg.choose(this, cfg, s("طريقة الحساب (جعفري)", "Calculation (Ja'fari)"),
                new String[]{methodName(Prayer.JAFARI), methodName(Prayer.TEHRAN)}, null,
                new String[]{s("فجر 16° · مغرب 4° · عشاء 14°", "Fajr 16° · Maghrib 4° · Isha 14°"), s("فجر 17.7° · مغرب 4.5° · عشاء 14°", "Fajr 17.7° · Maghrib 4.5° · Isha 14°")},
                cfg.prayerMethod, new Dlg.Pick() {
                    public void on(int i) { cfg.prayerMethod = i; changed(); PrayerAlarm.schedule(SettingsActivity.this); }
                });
    }

    private void offsets() {
        final int[] idx = {Prayer.FAJR, Prayer.SUNRISE, Prayer.DHUHR, Prayer.ASR, Prayer.MAGHRIB, Prayer.ISHA};
        String[] names = new String[idx.length + 1];
        String[] trail = new String[idx.length + 1];
        for (int i = 0; i < idx.length; i++) { names[i] = Prayer.name(idx[i], ar, false); trail[i] = signed(cfg.prayerOffsets[idx[i]]) + s(" د", " min"); }
        names[idx.length] = s("تصفير الكل", "Reset all");
        trail[idx.length] = null;
        Dlg.choose(this, cfg, s("ضبط الدقائق (حسب مرجعك أو تقويم مدينتك)", "Minute offsets (to match your local calendar)"), names, null, trail, -1, new Dlg.Pick() {
            public void on(int i) {
                if (i == idx.length) { Arrays.fill(cfg.prayerOffsets, 0); changed(); PrayerAlarm.schedule(SettingsActivity.this); return; }
                final int p = idx[i];
                final String[] vals = new String[21];
                for (int k = 0; k < 21; k++) vals[k] = signed(k - 10) + s(" دقيقة", " min");
                Dlg.choose(SettingsActivity.this, cfg, Prayer.name(p, ar, false), vals, null, null, cfg.prayerOffsets[p] + 10, new Dlg.Pick() {
                    public void on(int k) { cfg.prayerOffsets[p] = k - 10; changed(); PrayerAlarm.schedule(SettingsActivity.this); offsets(); }
                });
            }
        });
    }

    private void alert() {
        final String[] keys = {"off", "tone", "adhan"};
        File dir = getExternalFilesDir(null);
        String path = dir == null ? "Android/data/" + getPackageName() + "/files/adhan.mp3" : new File(dir, "adhan.mp3").getPath();
        boolean has = dir != null && new File(dir, "adhan.mp3").isFile();
        Dlg.choose(this, cfg, s("التنبيه عند وقت الصلاة", "Alert at prayer time"),
                new String[]{alertName("off"), alertName("tone"), alertName("adhan")}, null,
                new String[]{null, s("صوت إشعار قصير", "short notification tone"), has ? s("ملف الأذان موجود ✓", "adhan file found ✓") : s("يحتاج ملف adhan.mp3", "needs adhan.mp3")},
                Arrays.asList(keys).indexOf(cfg.prayerAlert), new Dlg.Pick() {
                    public void on(int i) {
                        cfg.prayerAlert = keys[i];
                        changed();
                        PrayerAlarm.schedule(SettingsActivity.this);
                    }
                });
        if (!has) Toast.makeText(this, s("للأذان: حط ملف صوت باسم adhan.mp3 بهذا المسار: ", "For adhan, put an audio file named adhan.mp3 at: ") + path, Toast.LENGTH_LONG).show();
    }

    private void hijri() {
        final String[] vals = {"-3", "-2", "-1", "0", "+1", "+2", "+3"};
        Dlg.choose(this, cfg, s("تصحيح التاريخ الهجري (حسب رؤية الهلال عندك)", "Hijri correction (local moon sighting)"), vals, null, null, cfg.hijriOffset + 3,
                new Dlg.Pick() { public void on(int i) { cfg.hijriOffset = i - 3; changed(); } });
    }

    // ------------------------------------------------------------------ content

    private void cats() {
        String[] names = new String[Config.ALL_CATS.length];
        boolean[] on = new boolean[Config.ALL_CATS.length];
        for (int i = 0; i < names.length; i++) { names[i] = Content.label(Config.ALL_CATS[i], ar); on[i] = cfg.contentCats.contains(Config.ALL_CATS[i]); }
        Dlg.multi(this, cfg, s("الأقسام الظاهرة بالقائمة", "Categories in the menu"), names, on, new Dlg.Multi() {
            public void on(boolean[] c) {
                List<String> keep = new ArrayList<String>();
                for (String k : cfg.contentCats) { int i = Arrays.asList(Config.ALL_CATS).indexOf(k); if (i >= 0 && c[i]) keep.add(k); }
                for (int i = 0; i < c.length; i++) if (c[i] && !keep.contains(Config.ALL_CATS[i])) keep.add(Config.ALL_CATS[i]);
                cfg.contentCats.clear();
                cfg.contentCats.addAll(keep);
                if (!keep.contains(cfg.contentCat) && !keep.isEmpty()) cfg.contentCat = keep.get(0);
                changed();
            }
        });
    }

    private void catsOrder() {
        final List<String> keys = new ArrayList<String>(cfg.contentCats);
        List<String> labels = new ArrayList<String>();
        for (String k : keys) labels.add(Content.label(k, ar));
        Dlg.reorder(this, cfg, s("ترتيب أقسام الترفيه", "Category order"), keys, labels, new Runnable() {
            public void run() { cfg.contentCats.clear(); cfg.contentCats.addAll(keys); changed(); }
        });
    }

    private void tmdb() {
        Dlg.input(this, cfg, s("مفتاح TMDB (يتحفظ على هذا التلفزيون فقط)", "TMDB key (stored on this TV only)"),
                s("API Key أو Read Access Token — اتركه فارغ للحذف", "API key or read access token — empty to remove"), cfg.tmdbKey, true, new Dlg.Text() {
                    public void on(String v) {
                        cfg.tmdbKey = v.matches("^[A-Za-z0-9._\\-]{0,400}$") ? v : "";
                        changed();
                        Content.clearCache(SettingsActivity.this);
                    }
                });
    }

    private void sources() {
        Dlg.info(this, cfg, s("مصادر المحتوى", "Content sources"), s(
                "• المسلسلات والبرامج: TVmaze (بدون مفتاح)\n• الأنمي: Jikan / MyAnimeList (بدون مفتاح)\n• الأفلام والأكثر مشاهدة: TMDB إذا ضفت مفتاح (بالعربي)، وإلا قائمة iTunes للأفلام\n• المباريات: ESPN\n• الطقس: Open-Meteo\n\nالواجهة تعرض العناوين والصور والوصف فقط. زر التشغيل يفتح تطبيق مثبّت عندك (نتفلكس، شاهد، سينمانا…) ولا يعرض روابط تشغيل غير مرخصة.\nالمحتوى يتحدث تلقائياً كل 6 ساعات ويبقى محفوظ بدون إنترنت.",
                "• Series & TV shows: TVmaze (no key)\n• Anime: Jikan / MyAnimeList (no key)\n• Movies & trending: TMDB with your key, otherwise iTunes top movies\n• Football: ESPN\n• Weather: Open-Meteo\n\nOnly titles, posters and descriptions are shown. Play opens an app you have installed; no unlicensed streams.\nContent refreshes every 6 hours and stays available offline."));
    }

    private void clearCache() {
        Content.clearCache(this);
        Feeds.clear(this);
        ImageLoader.get(this).clear();
        Toast.makeText(this, s("انمسحت الذاكرة المؤقتة", "Cache cleared"), Toast.LENGTH_SHORT).show();
    }

    // ------------------------------------------------------------------ system

    private void homeGuide() {
        boolean can = getPackageManager().resolveActivity(new Intent(Settings.ACTION_HOME_SETTINGS), 0) != null;
        String msg = s("حتى يصير زر Home يفتح IBRA TV HOME:\n\n"
                        + "١. الأسهل: من الموبايل استخدم «مثبّت IBRA TV HOME» أو من الحاسبة برنامج IBRA TV Tuner، يسويها بضغطة ويرجّع القديمة بزر.\n"
                        + "٢. أو اضغط زر Home بالريموت؛ إذا طلعت قائمة اختار IBRA TV HOME ثم «دائماً».\n"
                        + (can ? "٣. أو من إعدادات التطبيقات الافتراضية ← تطبيق الشاشة الرئيسية (الزر تحت).\n" : "")
                        + "\nملاحظة: Google TV وبعض الأجهزة ترجع واجهتها؛ المثبّت يعطّلها مؤقتاً ويرجّعها وقت ما تريد.",
                "To make the Home button open IBRA TV HOME:\n\n"
                        + "1. Easiest: the IBRA TV HOME phone installer or the IBRA TV Tuner PC app does it in one click and can bring the old one back.\n"
                        + "2. Or press Home on the remote; if a chooser appears pick IBRA TV HOME, then “Always”.\n"
                        + (can ? "3. Or Default apps → Home app (button below).\n" : "")
                        + "\nGoogle TV and some boxes take Home back; the installer can disable theirs temporarily and restore it any time.");
        if (!can) { Dlg.info(this, cfg, s("تعيين كواجهة رئيسية", "Set as Home"), msg); return; }
        Dlg.choose(this, cfg, msg, new String[]{s("افتح إعدادات الشاشة الرئيسية", "Open Home settings"), s("حسناً", "OK")}, new String[]{"i_settings", "i_check"}, null, 0,
                new Dlg.Pick() {
                    public void on(int i) {
                        if (i == 0) try { startActivity(new Intent(Settings.ACTION_HOME_SETTINGS)); } catch (Exception ignored) { }
                    }
                });
    }

    private File backupFile() {
        File dir = getExternalFilesDir(null);
        if (dir == null) dir = getFilesDir();
        return new File(dir, BACKUP_NAME);
    }

    private void backup() {
        try {
            String json = cfg.toBackupJson();
            getSharedPreferences("ibra_backup", MODE_PRIVATE).edit().putString("json", json).putLong("at", System.currentTimeMillis()).apply();
            FileOutputStream o = new FileOutputStream(backupFile());
            try { o.write(json.getBytes("UTF-8")); } finally { o.close(); }
            Dlg.info(this, cfg, s("تم الحفظ ✓", "Saved ✓"), s("انحفظت الإعدادات داخل التطبيق وبملف:\n", "Settings saved inside the app and to:\n") + backupFile().getPath()
                    + s("\n\n(مفتاح TMDB ما ينحفظ بالملف)", "\n\n(The TMDB key is not written to the file)"));
        } catch (Exception e) {
            Toast.makeText(this, s("ما گدرت أحفظ: ", "Could not save: ") + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void restore() {
        String json = null;
        String from = null;
        File f = backupFile();
        if (f.isFile() && f.length() < 512 * 1024) {
            try {
                FileInputStream in = new FileInputStream(f);
                byte[] b = new byte[(int) f.length()];
                int n = 0, r;
                while (n < b.length && (r = in.read(b, n, b.length - n)) > 0) n += r;
                in.close();
                json = new String(b, 0, n, "UTF-8");
                from = f.getPath();
            } catch (Exception ignored) { }
        }
        if (json == null) { json = getSharedPreferences("ibra_backup", MODE_PRIVATE).getString("json", null); from = s("النسخة الداخلية", "internal copy"); }
        if (json == null) { Dlg.info(this, cfg, s("استعادة", "Restore"), s("ماكو نسخة احتياطية بعد.", "No backup yet.")); return; }
        final String data = json, src = from;
        Dlg.confirm(this, cfg, s("استعادة الإعدادات؟", "Restore settings?"), src, new Runnable() {
            public void run() {
                try {
                    String key = cfg.tmdbKey;
                    Config c = new Config().apply(new org.json.JSONObject(data));
                    if (c.tmdbKey.isEmpty()) c.tmdbKey = key;
                    c.save(SettingsActivity.this);
                    PrayerAlarm.schedule(SettingsActivity.this);
                    build();
                    Toast.makeText(SettingsActivity.this, s("رجعت الإعدادات ✓", "Settings restored ✓"), Toast.LENGTH_SHORT).show();
                } catch (Exception e) {
                    Toast.makeText(SettingsActivity.this, s("الملف تالف", "The backup file is damaged"), Toast.LENGTH_LONG).show();
                }
            }
        });
    }

    private void reset() {
        Dlg.confirm(this, cfg, s("إعادة التصميم الافتراضي؟", "Reset the design?"),
                s("يرجع الثيم والخلفية والترتيب والإظهار. المفضلة والمدينة والصلاة تبقى.", "Theme, background, order and visibility go back to default. Favourites, city and prayer stay."),
                new Runnable() {
                    public void run() {
                        Config d = new Config();
                        cfg.theme = d.theme; cfg.background = d.background; cfg.cardAlpha = d.cardAlpha; cfg.iconSize = d.iconSize;
                        cfg.columns = d.columns; cfg.effects = d.effects; cfg.largeText = d.largeText; cfg.title = d.title;
                        cfg.showClock = cfg.showWeather = cfg.showContent = cfg.showPrayer = cfg.showFavorites = cfg.showAllApps = true;
                        cfg.cardsOrder.clear(); cfg.cardsOrder.addAll(d.cardsOrder);
                        cfg.sectionsOrder.clear(); cfg.sectionsOrder.addAll(d.sectionsOrder);
                        cfg.contentCats.clear(); cfg.contentCats.addAll(d.contentCats);
                        changed();
                    }
                });
    }

    private void about() {
        Dlg.info(this, cfg, "IBRA TV HOME " + version(), s(
                "واجهة تلفزيون عربية سريعة: ساعة وطقس، مركز ترفيه، مواقيت الصلاة الجعفرية، تطبيقاتك المفضلة.\n\nتصميم وتطوير إبراهيم الخزرجي\nt.me/ibraprogram · TikTok @ibraprogram\n\nالمواقيت تُحسب على الجهاز (PrayTimes، طريقة جعفري). التاريخ الهجري: تقويم أم القرى مع تصحيح يدوي. الأيقونات: Material Icons (Apache 2.0).",
                "A fast Arabic TV launcher: clock & weather, entertainment centre, Ja'fari prayer times, favourite apps.\n\nDesigned & developed by Ibrahim Al-Khazraji\nt.me/ibraprogram · TikTok @ibraprogram\n\nPrayer times are computed on the TV (PrayTimes, Ja'fari). Hijri: Umm al-Qura with manual correction. Icons: Material Icons (Apache 2.0)."));
    }
}
