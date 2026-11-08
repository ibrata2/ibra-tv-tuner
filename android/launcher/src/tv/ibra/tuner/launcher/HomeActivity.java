package tv.ibra.tuner.launcher;

import android.app.Activity;
import android.app.Dialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * IBRA TV HOME. Layout (Arabic, right to left):
 *   [ clock + weather ] [ entertainment centre ] [ Ja'fari prayer times ]   [sidebar]
 *   [ my favourite apps ........................................ + add ]
 *                          [ all apps ]
 * Everything works with the remote: D-pad moves, OK opens, long-press OK or MENU = options, BACK stays home.
 */
public class HomeActivity extends Activity implements SharedPreferences.OnSharedPreferenceChangeListener {

    private Config cfg;
    private boolean ar;
    private final Handler h = new Handler();
    private Ui.Background bg;
    private long selfSaveAt;
    private String focusTag;
    private final AtomicBoolean refreshing = new AtomicBoolean(false);

    // weather card
    private TextView clockV, dateV, hijriV, tempV, condV;
    private ImageView wIconV;
    // prayer card
    private LinearLayout prayerRows;
    private TextView nextNameV, nextTimeV, nextLeftV;
    // content card
    private LinearLayout strip, dots, contentBody;
    private ImageView heroImg;
    private TextView heroTitle, heroSub, catLabelV;
    private ImageView catIconV;
    private List<Content.Item> items = new ArrayList<Content.Item>();
    private int heroIndex = 0;
    private Content.Item heroItem;
    // favourites
    private LinearLayout favRow;
    private TextView editLabel;
    private boolean editMode;
    private String moving;

    private BroadcastReceiver pkgReceiver;

    private String s(String a, String e) { return ar ? a : e; }
    private int u(float v) { return Ui.u(this, v); }

    // ================================================================== lifecycle

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        getSharedPreferences(Config.PREFS, MODE_PRIVATE).registerOnSharedPreferenceChangeListener(this);
        pkgReceiver = new BroadcastReceiver() {
            @Override public void onReceive(Context c, Intent i) {
                Apps.invalidate();
                h.post(new Runnable() { @Override public void run() { if (favRow != null) fillFavorites(); } });
            }
        };
        IntentFilter f = new IntentFilter();
        f.addAction(Intent.ACTION_PACKAGE_ADDED);
        f.addAction(Intent.ACTION_PACKAGE_REMOVED);
        f.addAction(Intent.ACTION_PACKAGE_CHANGED);
        f.addAction(Intent.ACTION_PACKAGE_REPLACED);
        f.addDataScheme("package");
        registerReceiver(pkgReceiver, f);
    }

    @Override
    protected void onDestroy() {
        getSharedPreferences(Config.PREFS, MODE_PRIVATE).unregisterOnSharedPreferenceChangeListener(this);
        try { unregisterReceiver(pkgReceiver); } catch (Exception ignored) { }
        super.onDestroy();
    }

    @Override
    protected void onResume() {
        super.onResume();
        resumed = true;
        build();
        h.removeCallbacks(tick);
        h.post(tick);
        h.removeCallbacks(rotate);
        h.postDelayed(rotate, 9000);
        refresh(false);
        PrayerAlarm.schedule(this);
    }

    @Override
    protected void onPause() {
        resumed = false;
        h.removeCallbacks(tick);
        h.removeCallbacks(rotate);
        if (bg != null) bg.stop();
        super.onPause();
    }

    private boolean resumed;

    @Override
    public void onSharedPreferenceChanged(SharedPreferences p, String key) {
        if (!resumed) return;                                          // onResume rebuilds anyway
        if (System.currentTimeMillis() - selfSaveAt < 1500) return;   // our own save
        h.post(new Runnable() { @Override public void run() { if (!isFinishing()) build(); } });
    }

    private void save() {
        selfSaveAt = System.currentTimeMillis();
        cfg.save(this);
    }

    @Override
    public void onBackPressed() {
        if (moving != null) { moving = null; fillFavorites(); return; }
        if (editMode) { setEdit(false); return; }
        // Home stays Home.
    }

    @Override
    public boolean onKeyDown(int code, KeyEvent e) {
        if (code == KeyEvent.KEYCODE_SEARCH) { startActivity(new Intent(this, SearchActivity.class)); return true; }
        return super.onKeyDown(code, e);
    }

    private final Runnable tick = new Runnable() {
        @Override public void run() {
            updateClock();
            updatePrayer();
            h.postDelayed(this, 10000);
        }
    };

    private final Runnable rotate = new Runnable() {
        @Override public void run() {
            View f = getCurrentFocus();
            boolean busy = f != null && f.getTag() instanceof String && (((String) f.getTag()).startsWith("poster:") || "hero".equals(f.getTag()));
            int n = Math.min(5, items.size());
            if (!busy && n > 1 && heroImg != null) {
                heroIndex = (heroIndex + 1) % n;
                setHero(items.get(heroIndex));
            }
            h.postDelayed(this, 9000);
        }
    };

    /** Background refresh of weather/football and content; redraws the parts that changed. */
    void refresh(final boolean force) {
        if (!refreshing.compareAndSet(false, true)) return;
        final Config c = cfg;
        new Thread(new Runnable() {
            @Override public void run() {
                boolean feeds = false;
                List<String> cats = new ArrayList<String>();
                try {
                    feeds = Feeds.refresh(getApplicationContext(), c, force);
                    cats = Content.refresh(getApplicationContext(), c, force);
                } catch (Throwable ignored) {
                } finally { refreshing.set(false); }
                final boolean f = feeds;
                final List<String> cs = cats;
                h.post(new Runnable() {
                    @Override public void run() {
                        if (isFinishing()) return;
                        if (f) updateWeather();
                        if (cs.contains(cfg.contentCat) || (f && "football".equals(cfg.contentCat))) loadContent(false);
                        if (force && !f && cs.isEmpty())
                            Toast.makeText(HomeActivity.this, s("ما وصلت معلومات جديدة، تأكد من الإنترنت", "Nothing new — check the internet"), Toast.LENGTH_SHORT).show();
                    }
                });
            }
        }, "ibra-refresh").start();
    }

    // ================================================================== build

    private void build() {
        cfg = Config.load(this);
        ar = cfg.arabic();
        if (!cfg.favoritesInit) selfSaveAt = System.currentTimeMillis();
        Apps.initFavorites(this, cfg);
        View focused = getCurrentFocus();
        if (focused != null && focused.getTag() instanceof String) focusTag = (String) focused.getTag();

        FrameLayout root = new FrameLayout(this);
        root.setLayoutDirection(ar ? View.LAYOUT_DIRECTION_RTL : View.LAYOUT_DIRECTION_LTR);
        if (bg != null) bg.stop();
        bg = new Ui.Background(this, cfg);
        root.addView(bg, Dlg.match());

        LinearLayout frame = new LinearLayout(this);
        frame.setOrientation(LinearLayout.HORIZONTAL);
        frame.setClipChildren(false);
        root.addView(frame, Dlg.match());

        LinearLayout main = new LinearLayout(this);
        main.setOrientation(LinearLayout.VERTICAL);
        main.setClipChildren(false);
        main.setClipToPadding(false);
        main.setPadding(u(36), u(26), u(36), u(26));
        View side = sidebar();
        // the sidebar sits on the left in both languages
        if (ar) { frame.addView(main, new LinearLayout.LayoutParams(0, -1, 1)); frame.addView(side); }
        else { frame.addView(side); frame.addView(main, new LinearLayout.LayoutParams(0, -1, 1)); }

        main.addView(topBar());
        favRow = null;
        prayerRows = null;
        contentBody = null;
        clockV = null;
        tempV = null;
        for (String sec : cfg.sectionsOrder) {
            if ("cards".equals(sec)) {
                View row = cardsRow();
                if (row != null) main.addView(row);
            } else if ("favorites".equals(sec) && cfg.showFavorites) {
                main.addView(favoritesSection());
            }
        }
        main.addView(new View(this), new LinearLayout.LayoutParams(1, 0, 1));
        if (cfg.showAllApps) main.addView(allAppsButton());

        setContentView(root);
        bg.start();
        updateClock();
        updateWeather();
        updatePrayer();
        loadContent(true);
        if (favRow != null) fillFavorites();

        View target = focusTag == null ? null : root.findViewWithTag(focusTag);
        if (target == null && favRow != null && favRow.getChildCount() > 0) target = favRow.getChildAt(0);
        if (target == null) target = root.findViewWithTag("drop");
        if (target == null) target = root.findViewWithTag("side:home");
        final View first = target;
        if (first != null) {
            first.requestFocus();
            root.post(new Runnable() { @Override public void run() { first.requestFocus(); } });   // after the first layout
        }
    }

    // ------------------------------------------------------------------ sidebar + top bar

    private View sidebar() {
        LinearLayout s = new LinearLayout(this);
        s.setOrientation(LinearLayout.VERTICAL);
        s.setGravity(Gravity.CENTER_HORIZONTAL);
        s.setPadding(u(22), u(150), u(22), 0);
        s.setClipChildren(false);
        String[][] items = {
                {"side:home", "i_home"}, {"side:search", "i_search"}, {"side:tv", "i_tv"},
                {"side:apps", "i_apps"}, {"side:settings", "i_settings"},
        };
        for (final String[] it : items) {
            FrameLayout b = new FrameLayout(this);
            b.setTag(it[0]);
            boolean home = "side:home".equals(it[0]);
            GradientDrawable g = Ui.pill(this, home ? Ui.accent(cfg) : Ui.glassFill(cfg), home ? 0 : Ui.glassStroke(cfg), 24);
            b.setBackground(g);
            ImageView iv = Ui.icon(this, it[1], home ? Color.WHITE : Ui.text(cfg), 40);
            b.addView(iv, new FrameLayout.LayoutParams(u(40), u(40), Gravity.CENTER));
            Ui.focusable(this, cfg, b, g, 1.12f, null);
            b.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { side(it[0]); }
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(u(84), u(84));
            lp.bottomMargin = u(28);
            s.addView(b, lp);
        }
        return s;
    }

    private void side(String tag) {
        if ("side:search".equals(tag)) startActivity(new Intent(this, SearchActivity.class));
        else if ("side:apps".equals(tag)) startActivity(new Intent(this, AllAppsActivity.class));
        else if ("side:settings".equals(tag)) startActivity(new Intent(this, SettingsActivity.class));
        else if ("side:tv".equals(tag)) openLiveTv();
        else refresh(true);
    }

    private void openLiveTv() {
        String[] pkgs = {"com.google.android.tv", "com.android.tv", "com.google.android.apps.tv.dreamx"};
        for (String p : pkgs) {
            Intent i = getPackageManager().getLeanbackLaunchIntentForPackage(p);
            if (i == null) i = getPackageManager().getLaunchIntentForPackage(p);
            if (i != null) { try { startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); return; } catch (Exception ignored) { } }
        }
        try { startActivity(new Intent("com.android.tv.action.VIEW_INPUTS").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); return; }
        catch (Exception ignored) { }
        Toast.makeText(this, s("ماكو تطبيق تلفزيون مباشر على الجهاز", "No live TV app on this device"), Toast.LENGTH_SHORT).show();
    }

    private View topBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setClipChildren(false);
        FrameLayout logo = new FrameLayout(this);
        logo.setBackground(Ui.pill(this, Ui.accent(cfg), 0, 16));
        logo.addView(Ui.icon(this, "i_tv", Color.WHITE, 34), new FrameLayout.LayoutParams(u(34), u(34), Gravity.CENTER));
        bar.addView(logo, new LinearLayout.LayoutParams(u(58), u(58)));
        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        titles.setPadding(u(16), 0, u(16), 0);
        TextView t = Ui.tv(this, cfg, cfg.title, 34, true);
        t.setLetterSpacing(0.04f);
        titles.addView(t);
        TextView sub = Ui.tv(this, cfg, s("واجهة التلفزيون", "TV launcher"), 18, false);
        sub.setTextColor(Ui.muted(cfg));
        titles.addView(sub);
        bar.addView(titles, new LinearLayout.LayoutParams(0, -2, 1));

        String net = "i_wifi_off";
        try {
            NetworkInfo ni = ((ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE)).getActiveNetworkInfo();
            if (ni != null && ni.isConnected()) net = ni.getType() == ConnectivityManager.TYPE_ETHERNET ? "i_ethernet" : "i_wifi";
        } catch (Exception ignored) { }
        bar.addView(topIcon("top:net", net, Settings.ACTION_WIFI_SETTINGS));
        bar.addView(topIcon("top:bt", "i_bluetooth", Settings.ACTION_BLUETOOTH_SETTINGS));
        bar.addView(topIcon("top:settings", "i_settings", null));
        bar.setPadding(0, 0, 0, u(14));
        return bar;
    }

    private View topIcon(String tag, String icon, final String action) {
        FrameLayout f = new FrameLayout(this);
        f.setTag(tag);
        GradientDrawable g = Ui.pill(this, 0x00000000, 0x00000000, 18);
        f.setBackground(g);
        f.addView(Ui.icon(this, icon, Ui.text(cfg), 36), new FrameLayout.LayoutParams(u(36), u(36), Gravity.CENTER));
        Ui.focusable(this, cfg, f, g, 1.15f, null);
        f.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                try {
                    if (action == null) startActivity(new Intent(HomeActivity.this, SettingsActivity.class));
                    else startActivity(new Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                } catch (Exception e) {
                    try { startActivity(new Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); } catch (Exception ignored) { }
                }
            }
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(u(64), u(64));
        lp.setMarginStart(u(10));
        f.setLayoutParams(lp);
        return f;
    }

    // ------------------------------------------------------------------ cards row

    private View cardsRow() {
        boolean wShow = cfg.showClock || cfg.showWeather;
        List<View> cards = new ArrayList<View>();
        List<String> keys = new ArrayList<String>();
        for (String k : cfg.cardsOrder) {
            View v = null;
            if ("weather".equals(k) && wShow) v = weatherCard();
            else if ("content".equals(k) && cfg.showContent) v = contentCard();
            else if ("prayer".equals(k) && cfg.showPrayer) v = prayerCard();
            if (v != null) { cards.add(v); keys.add(k); }
        }
        if (cards.isEmpty()) return null;
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setClipChildren(false);
        boolean hasContent = keys.contains("content");
        for (int i = 0; i < cards.size(); i++) {
            String k = keys.get(i);
            LinearLayout.LayoutParams lp;
            if (!hasContent || "content".equals(k)) lp = new LinearLayout.LayoutParams(0, -1, 1);
            else lp = new LinearLayout.LayoutParams(u("weather".equals(k) ? 400 : 430), -1);
            if (i < cards.size() - 1) lp.setMarginEnd(u(24));
            row.addView(cards.get(i), lp);
        }
        LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(-1, u(505));
        rl.topMargin = u(6);
        row.setLayoutParams(rl);
        return row;
    }

    private LinearLayout card(String tag, float zoom, View.OnClickListener click) {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setTag(tag);
        c.setClipChildren(false);
        android.graphics.drawable.Drawable d = Ui.glass(this, cfg, 34);
        c.setBackground(d);
        GradientDrawable ring = (GradientDrawable) ((android.graphics.drawable.LayerDrawable) d).getDrawable(0);
        if (click != null) {
            Ui.focusable(this, cfg, c, ring, zoom, null);
            c.setOnClickListener(click);
        }
        return c;
    }

    // ---- weather / clock

    private View weatherCard() {
        LinearLayout c = card("card:weather", 1.03f, new View.OnClickListener() {
            @Override public void onClick(View v) { weatherDialog(); }
        });
        c.setGravity(Gravity.CENTER_HORIZONTAL);
        c.setPadding(u(26), u(30), u(26), u(26));
        clockV = dateV = hijriV = tempV = condV = null;
        wIconV = null;
        if (cfg.showClock) {
            clockV = Ui.tv(this, cfg, "", 124, true);
            clockV.setGravity(Gravity.CENTER);
            clockV.setTextDirection(View.TEXT_DIRECTION_LTR);
            c.addView(clockV);
            dateV = Ui.tv(this, cfg, "", 26, false);
            dateV.setGravity(Gravity.CENTER);
            dateV.setPadding(0, u(8), 0, 0);
            c.addView(dateV);
            hijriV = Ui.tv(this, cfg, "", 23, false);
            hijriV.setTextColor(Ui.muted(cfg));
            hijriV.setGravity(Gravity.CENTER);
            hijriV.setPadding(0, u(6), 0, 0);
            c.addView(hijriV);
        }
        if (cfg.showClock && cfg.showWeather) {
            View div = new View(this);
            div.setBackgroundColor(Ui.alpha(Ui.text(cfg), 0.16f));
            LinearLayout.LayoutParams dl = new LinearLayout.LayoutParams(-1, Math.max(1, u(2)));
            dl.topMargin = u(22);
            dl.bottomMargin = u(18);
            c.addView(div, dl);
        }
        if (cfg.showWeather) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER);
            LinearLayout col = new LinearLayout(this);
            col.setOrientation(LinearLayout.VERTICAL);
            tempV = Ui.tv(this, cfg, "--°", 66, true);
            tempV.setTextDirection(View.TEXT_DIRECTION_LTR);
            col.addView(tempV);
            condV = Ui.tv(this, cfg, "", 24, false);
            col.addView(condV);
            row.addView(col);
            wIconV = Ui.icon(this, "w_cloud_sun", 0, 104);
            LinearLayout.LayoutParams il = new LinearLayout.LayoutParams(u(104), u(104));
            il.setMarginStart(u(22));
            row.addView(wIconV, il);
            c.addView(row);
            LinearLayout city = Ui.iconText(this, cfg, "i_pin", cfg.city, 23, 26, Ui.muted(cfg));
            city.setPadding(0, u(12), 0, 0);
            c.addView(city);
        }
        return c;
    }

    private void updateClock() {
        if (clockV == null) return;
        Calendar now = Calendar.getInstance(TimeZone.getDefault());
        boolean h24 = android.text.format.DateFormat.is24HourFormat(this);
        int hr = now.get(Calendar.HOUR_OF_DAY);
        if (!h24) { hr = hr % 12; if (hr == 0) hr = 12; }
        clockV.setText(String.format(Locale.US, h24 ? "%02d:%02d" : "%d:%02d", hr, now.get(Calendar.MINUTE)));
        dateV.setText(Prayer.gregorianText(now, ar));
        hijriV.setText(Prayer.hijriText(now, cfg.hijriOffset, ar));
    }

    private void updateWeather() {
        if (tempV == null) return;
        Feeds.Weather w = Feeds.weather(this, cfg);
        if (w == null) {
            tempV.setText("--°");
            condV.setText(s("الطقس يتحدّث…", "Loading…"));
            return;
        }
        tempV.setText(Math.round(w.temp) + "°C");
        condV.setText(Feeds.weatherText(w.code, ar));
        int id = Ui.drawable(this, Feeds.weatherIcon(w.code, w.day));
        if (id != 0) wIconV.setImageResource(id);
    }

    private void weatherDialog() {
        Feeds.Weather w = Feeds.weather(this, cfg);
        TimeZone tz = TimeZone.getTimeZone(cfg.tz);
        List<String> labels = new ArrayList<String>(), icons = new ArrayList<String>(), trail = new ArrayList<String>();
        if (w != null) {
            labels.add(s("الآن: ", "Now: ") + Math.round(w.temp) + "°  " + Feeds.weatherText(w.code, ar));
            icons.add(Feeds.weatherIcon(w.code, w.day));
            trail.add((w.humidity >= 0 ? s("رطوبة ", "Hum ") + w.humidity + "%" : "") + (w.wind >= 0 ? "  " + s("رياح ", "Wind ") + Math.round(w.wind) + s(" كم/س", " km/h") : ""));
            for (int i = 0; i < w.days.size(); i++) {
                labels.add((i == 0 ? s("اليوم", "Today") : Feeds.dayName(w.days.get(i), ar, tz)) + "   " + Feeds.weatherText(w.codes.get(i), ar));
                icons.add(Feeds.weatherIcon(w.codes.get(i), true));
                trail.add(Math.round(w.max.get(i)) + "° / " + Math.round(w.min.get(i)) + "°");
            }
        }
        final int changeCity = labels.size();
        labels.add(s("تغيير المدينة", "Change city"));
        icons.add("i_pin");
        trail.add(cfg.city);
        final int refresh = labels.size();
        labels.add(s("تحديث الآن", "Refresh now"));
        icons.add("i_refresh");
        trail.add(null);
        Dlg.choose(this, cfg, s("الطقس — ", "Weather — ") + cfg.city, labels.toArray(new String[0]), icons.toArray(new String[0]),
                trail.toArray(new String[0]), -1, new Dlg.Pick() {
                    @Override public void on(int i) {
                        if (i == changeCity) startActivity(new Intent(HomeActivity.this, SettingsActivity.class).putExtra("open", "city"));
                        else if (i == refresh) refresh(true);
                    }
                });
    }

    // ---- prayer

    private View prayerCard() {
        LinearLayout c = card("card:prayer", 1.03f, new View.OnClickListener() {
            @Override public void onClick(View v) { prayerDialog(); }
        });
        c.setPadding(u(26), u(24), u(26), u(18));
        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        titles.addView(Ui.tv(this, cfg, s("مواقيت الصلاة", "Prayer times"), 31, true));
        TextView m = Ui.tv(this, cfg, s("المذهب الجعفري", "Ja'fari") + " · " + cfg.city, 20, false);
        m.setTextColor(Ui.muted(cfg));
        m.setPadding(0, u(4), 0, 0);
        titles.addView(m);
        head.addView(titles, new LinearLayout.LayoutParams(0, -2, 1));
        head.addView(Ui.icon(this, "i_mosque", Ui.accent(cfg), 58));
        c.addView(head);

        LinearLayout next = new LinearLayout(this);
        next.setOrientation(LinearLayout.HORIZONTAL);
        next.setGravity(Gravity.CENTER_VERTICAL);
        next.setPadding(u(20), u(12), u(20), u(12));
        next.setBackground(Ui.pill(this, Ui.alpha(Ui.accent(cfg), 0.22f), Ui.alpha(Ui.accent(cfg), 0.7f), 20));
        TextView lbl = Ui.tv(this, cfg, s("القادمة", "Next"), 21, true);
        lbl.setTextColor(Ui.accent(cfg));
        next.addView(lbl);
        nextNameV = Ui.tv(this, cfg, "", 26, true);
        nextNameV.setPadding(u(14), 0, u(14), 0);
        next.addView(nextNameV, new LinearLayout.LayoutParams(0, -2, 1));
        nextTimeV = Ui.tv(this, cfg, "", 27, true);
        nextTimeV.setTextDirection(View.TEXT_DIRECTION_LTR);
        next.addView(nextTimeV);
        LinearLayout.LayoutParams nl = new LinearLayout.LayoutParams(-1, -2);
        nl.topMargin = u(16);
        c.addView(next, nl);
        nextLeftV = Ui.tv(this, cfg, "", 20, false);
        nextLeftV.setTextColor(Ui.muted(cfg));
        nextLeftV.setPadding(u(6), u(6), u(6), u(6));
        c.addView(nextLeftV);

        prayerRows = new LinearLayout(this);
        prayerRows.setOrientation(LinearLayout.VERTICAL);
        c.addView(prayerRows);
        return c;
    }

    private void updatePrayer() {
        if (prayerRows == null) return;
        TimeZone tz = TimeZone.getTimeZone(cfg.tz);
        long now = System.currentTimeMillis();
        Calendar today = Calendar.getInstance(tz);
        int[] t = Prayer.day(today, cfg.lat, cfg.lon, tz, cfg.prayerMethod, cfg.prayerOffsets);
        Prayer.Next n = Prayer.next(now, cfg.lat, cfg.lon, tz, cfg.prayerMethod, cfg.prayerOffsets, cfg.prayerCombined);
        int nowMin = today.get(Calendar.HOUR_OF_DAY) * 60 + today.get(Calendar.MINUTE);
        if (n != null) {
            nextNameV.setText(Prayer.name(n.index, ar, cfg.prayerCombined));
            Calendar at = Calendar.getInstance(tz);
            at.setTimeInMillis(n.atMillis);
            nextTimeV.setText(Prayer.hhmm(at.get(Calendar.HOUR_OF_DAY) * 60 + at.get(Calendar.MINUTE)));
            nextLeftV.setText(remaining((n.atMillis - now) / 60000 + 1));
        }
        prayerRows.removeAllViews();
        int[] list = cfg.prayerCombined ? Prayer.COMBINED : Prayer.PRAYERS;
        for (int idx : list) {
            boolean isNext = n != null && !n.tomorrow && n.index == idx;
            boolean passed = t[idx] >= 0 && t[idx] <= nowMin && !isNext;
            LinearLayout r = new LinearLayout(this);
            r.setOrientation(LinearLayout.HORIZONTAL);
            r.setGravity(Gravity.CENTER_VERTICAL);
            r.setPadding(u(10), u(5), u(10), u(5));
            ImageView ck = Ui.icon(this, "i_check", Ui.muted(cfg), 22);
            ck.setAlpha(passed ? 1f : 0f);
            r.addView(ck, new LinearLayout.LayoutParams(u(22), u(22)));
            TextView name = Ui.tv(this, cfg, Prayer.name(idx, ar, cfg.prayerCombined), 25, isNext);
            name.setPadding(u(12), 0, u(12), 0);
            TextView time = Ui.tv(this, cfg, Prayer.hhmm(t[idx]), 25, isNext);
            time.setTextDirection(View.TEXT_DIRECTION_LTR);
            if (isNext) { name.setTextColor(Ui.accent(cfg)); time.setTextColor(Ui.accent(cfg)); }
            else if (passed) { name.setTextColor(Ui.muted(cfg)); time.setTextColor(Ui.muted(cfg)); }
            r.addView(name, new LinearLayout.LayoutParams(0, -2, 1));
            r.addView(time);
            prayerRows.addView(r);
        }
    }

    private String remaining(long min) {
        if (min <= 1) return s("الآن", "now");
        long hh = min / 60, mm = min % 60;
        if (ar) {
            String hs = hh == 0 ? "" : hh == 1 ? "ساعة" : hh == 2 ? "ساعتين" : hh + " ساعات";
            String ms = mm == 0 ? "" : mm + " دقيقة";
            return "بعد " + hs + (hs.isEmpty() || ms.isEmpty() ? "" : " و ") + ms;
        }
        return "in " + (hh > 0 ? hh + " h " : "") + (mm > 0 ? mm + " min" : "");
    }

    private void prayerDialog() {
        TimeZone tz = TimeZone.getTimeZone(cfg.tz);
        Calendar today = Calendar.getInstance(tz);
        int[] t = Prayer.day(today, cfg.lat, cfg.lon, tz, cfg.prayerMethod, cfg.prayerOffsets);
        int[] order = {Prayer.FAJR, Prayer.SUNRISE, Prayer.DHUHR, Prayer.ASR, Prayer.SUNSET, Prayer.MAGHRIB, Prayer.ISHA, Prayer.MIDNIGHT};
        List<String> labels = new ArrayList<String>(), trail = new ArrayList<String>(), icons = new ArrayList<String>();
        for (int i : order) {
            labels.add(Prayer.name(i, ar, false));
            trail.add(Prayer.hhmm(t[i]));
            icons.add(i == Prayer.SUNRISE || i == Prayer.SUNSET ? "w_sun" : i == Prayer.MIDNIGHT ? "w_moon" : "i_mosque");
        }
        final int set = labels.size();
        labels.add(s("إعدادات الصلاة", "Prayer settings"));
        trail.add(cfg.prayerMethod == Prayer.TEHRAN ? s("طهران", "Tehran") : s("قم – ليوة", "Qum – Leva"));
        icons.add("i_settings");
        String title = s("مواقيت ", "Prayer times · ") + cfg.city + "\n" + Prayer.gregorianText(today, ar) + " · " + Prayer.hijriText(today, cfg.hijriOffset, ar);
        Dlg.choose(this, cfg, title, labels.toArray(new String[0]), icons.toArray(new String[0]), trail.toArray(new String[0]), -1,
                new Dlg.Pick() {
                    @Override public void on(int i) {
                        if (i == set) startActivity(new Intent(HomeActivity.this, SettingsActivity.class).putExtra("open", "prayer"));
                    }
                });
    }

    // ---- entertainment centre

    private View contentCard() {
        LinearLayout c = card("card:content", 1f, null);
        c.setPadding(u(22), u(18), u(22), u(14));
        c.setGravity(Gravity.CENTER_HORIZONTAL);

        LinearLayout drop = new LinearLayout(this);
        drop.setTag("drop");
        drop.setOrientation(LinearLayout.HORIZONTAL);
        drop.setGravity(Gravity.CENTER_VERTICAL);
        drop.setPadding(u(24), u(10), u(24), u(10));
        GradientDrawable dg = Ui.pill(this, Ui.alpha(Ui.accent(cfg), 0.20f), Ui.alpha(Ui.accent(cfg), 0.6f), 30);
        drop.setBackground(dg);
        catIconV = Ui.icon(this, Content.icon(cfg.contentCat), Ui.text(cfg), 32);
        drop.addView(catIconV);
        catLabelV = Ui.tv(this, cfg, Content.label(cfg.contentCat, ar), 26, true);
        catLabelV.setPadding(u(14), 0, u(14), 0);
        drop.addView(catLabelV);
        drop.addView(Ui.icon(this, "i_chevron", Ui.text(cfg), 32));
        Ui.focusable(this, cfg, drop, dg, 1.05f, null);
        drop.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { categoryMenu(v); }
        });
        c.addView(drop, new LinearLayout.LayoutParams(-2, u(60)));

        contentBody = new LinearLayout(this);
        contentBody.setOrientation(LinearLayout.HORIZONTAL);
        contentBody.setClipChildren(false);
        LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(-1, 0, 1);
        bl.topMargin = u(14);
        c.addView(contentBody, bl);

        dots = new LinearLayout(this);
        dots.setOrientation(LinearLayout.HORIZONTAL);
        dots.setGravity(Gravity.CENTER);
        dots.setPadding(0, u(10), 0, 0);
        c.addView(dots);
        return c;
    }

    private void categoryMenu(View anchor) {
        final List<String> cats = new ArrayList<String>(cfg.contentCats);
        if (cats.isEmpty()) return;
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(u(10), u(10), u(10), u(10));
        list.setBackground(Dlg.panelBg(this, cfg));
        list.setLayoutDirection(ar ? View.LAYOUT_DIRECTION_RTL : View.LAYOUT_DIRECTION_LTR);
        final PopupWindow pw = new PopupWindow(list, u(460), ViewGroup.LayoutParams.WRAP_CONTENT, true);
        pw.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
        pw.setOutsideTouchable(true);
        View sel = null;
        for (final String cat : cats) {
            LinearLayout r = Dlg.row(this, cfg, Content.icon(cat), Content.label(cat, ar), null, cat.equals(cfg.contentCat));
            r.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    pw.dismiss();
                    cfg.contentCat = cat;
                    save();
                    heroIndex = 0;
                    loadContent(true);
                    refresh(false);
                }
            });
            list.addView(r);
            if (cat.equals(cfg.contentCat)) sel = r;
        }
        pw.showAsDropDown(anchor, (anchor.getWidth() - u(460)) / 2, u(8));
        if (sel != null) sel.requestFocus();
    }

    /** Fills the content card from cache (network refresh happens in {@link #refresh}). */
    private void loadContent(boolean resetHero) {
        if (contentBody == null) return;
        catLabelV.setText(Content.label(cfg.contentCat, ar));
        int id = Ui.drawable(this, Content.icon(cfg.contentCat));
        if (id != 0) catIconV.setImageResource(id);
        items = Content.items(this, cfg, cfg.contentCat);
        if (resetHero || heroIndex >= items.size()) heroIndex = 0;
        contentBody.removeAllViews();
        dots.removeAllViews();
        heroImg = null;
        if (items.isEmpty()) {
            LinearLayout empty = new LinearLayout(this);
            empty.setOrientation(LinearLayout.VERTICAL);
            empty.setGravity(Gravity.CENTER);
            TextView t = Ui.tv(this, cfg, emptyText(), 24, false);
            t.setGravity(Gravity.CENTER);
            t.setTextColor(Ui.muted(cfg));
            empty.addView(t);
            boolean needKey = ("movies".equals(cfg.contentCat) || "trending".equals(cfg.contentCat)) && cfg.tmdbKey.isEmpty();
            if (needKey) {
                LinearLayout b = Ui.iconText(this, cfg, "i_settings", s("إعدادات المحتوى", "Content settings"), 22, 28, Ui.text(cfg));
                b.setTag("content:settings");
                b.setPadding(u(22), u(10), u(22), u(10));
                GradientDrawable g = Ui.pill(this, Ui.glassFill(cfg), Ui.glassStroke(cfg), 24);
                b.setBackground(g);
                Ui.focusable(this, cfg, b, g, 1.05f, null);
                b.setOnClickListener(new View.OnClickListener() {
                    @Override public void onClick(View v) { startActivity(new Intent(HomeActivity.this, SettingsActivity.class).putExtra("open", "content")); }
                });
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
                lp.topMargin = u(16);
                empty.addView(b, lp);
            }
            contentBody.addView(empty, new LinearLayout.LayoutParams(-1, -1));
            return;
        }
        boolean match = "football".equals(cfg.contentCat);

        // poster strip (start side) + hero (end side), like the reference design
        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        hs.setClipChildren(false);
        hs.setClipToPadding(false);
        hs.setPadding(u(6), u(10), u(6), u(10));
        strip = new LinearLayout(this);
        strip.setOrientation(LinearLayout.HORIZONTAL);
        strip.setClipChildren(false);
        hs.addView(strip, new FrameLayout.LayoutParams(-2, -1));
        for (int i = 0; i < items.size(); i++) strip.addView(match ? matchTile(items.get(i)) : poster(items.get(i)));

        if (!match) {
            contentBody.addView(hs, new LinearLayout.LayoutParams(0, -1, 1f));
            View hero = hero();
            LinearLayout.LayoutParams hl = new LinearLayout.LayoutParams(0, -1, 1.25f);
            hl.setMarginStart(u(14));
            contentBody.addView(hero, hl);
            int n = Math.min(5, items.size());
            for (int i = 0; i < n; i++) {
                View d = new View(this);
                d.setBackground(Ui.pill(this, Ui.alpha(Ui.text(cfg), 0.35f), 0, 6));
                LinearLayout.LayoutParams dl = new LinearLayout.LayoutParams(u(12), u(12));
                dl.setMargins(u(5), 0, u(5), 0);
                dots.addView(d, dl);
            }
            setHero(items.get(heroIndex));
        } else {
            contentBody.addView(hs, new LinearLayout.LayoutParams(-1, -1));
        }
    }

    private String emptyText() {
        String cat = cfg.contentCat;
        if ("favorites".equals(cat)) return s("ما عندك مفضلة بعد.\nافتح أي عنوان واضغط «أضف للمفضلة».", "No favourites yet.\nOpen a title and choose “Add to favourites”.");
        if ("continue".equals(cat)) return s("هنا تظهر العناوين اللي فتحتها من الواجهة.", "Titles you open from here appear here.");
        if ("football".equals(cat)) return s("ماكو مباريات اليوم، أو المعلومات بعدها تتحمّل.", "No matches today, or still loading.");
        if (("movies".equals(cat) || "trending".equals(cat)) && cfg.tmdbKey.isEmpty())
            return s("ما وصلت أفلام بعد.\nلعرض الأفلام والأكثر مشاهدة بالعربي ضيف مفتاح TMDB مرة وحدة.", "No movies yet.\nAdd a TMDB key once for movies and trending.");
        return s("المحتوى يتحمّل… تأكد من الإنترنت.", "Loading… check the internet.");
    }

    private View hero() {
        FrameLayout f = new FrameLayout(this);
        f.setTag("hero");
        GradientDrawable g = Ui.pill(this, Ui.alpha(Color.BLACK, 0.35f), Ui.glassStroke(cfg), 24);
        f.setBackground(g);
        f.setClipToOutline(true);
        heroImg = new ImageView(this);
        heroImg.setScaleType(ImageView.ScaleType.CENTER_CROP);
        f.addView(heroImg, Dlg.match());
        View shade = new View(this);
        shade.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{0x00000000, 0x22000000, 0xDD000000}));
        f.addView(shade, Dlg.match());
        LinearLayout info = new LinearLayout(this);
        info.setOrientation(LinearLayout.VERTICAL);
        info.setPadding(u(22), u(18), u(22), u(18));
        heroTitle = Ui.tv(this, cfg, "", 38, true);
        heroTitle.setTextColor(Color.WHITE);
        heroTitle.setMaxLines(2);
        heroTitle.setEllipsize(TextUtils.TruncateAt.END);
        heroTitle.setShadowLayer(8, 0, 2, 0xAA000000);
        info.addView(heroTitle);
        heroSub = Ui.tv(this, cfg, "", 19, false);
        heroSub.setTextColor(0xDDFFFFFF);
        heroSub.setSingleLine(true);
        heroSub.setEllipsize(TextUtils.TruncateAt.END);
        heroSub.setPadding(0, u(4), 0, u(10));
        info.addView(heroSub);
        LinearLayout play = Ui.iconText(this, cfg, "i_play", s("عرض الآن", "Watch now"), 22, 28, Color.WHITE);
        play.setPadding(u(20), u(8), u(24), u(8));
        play.setBackground(Ui.pill(this, Ui.accent(cfg), 0, 26));
        info.addView(play, new LinearLayout.LayoutParams(-2, -2));
        f.addView(info, new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM));
        Ui.focusable(this, cfg, f, g, 1.03f, null);
        f.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { if (heroItem != null) details(heroItem); }
        });
        return f;
    }

    private void setHero(Content.Item it) {
        if (heroImg == null || it == null) return;
        heroItem = it;
        heroTitle.setText(it.title);
        String sub = it.year + (it.rating > 0 ? "  ★ " + String.format(Locale.US, "%.1f", it.rating) : "") + (it.subtitle.isEmpty() ? "" : "  ·  " + it.subtitle);
        heroSub.setText(sub.trim());
        ImageLoader.get(this).load(heroImg, it.backdrop.isEmpty() ? it.poster : it.backdrop, u(560), u(320));
        int idx = items.indexOf(it);
        for (int i = 0; i < dots.getChildCount(); i++) {
            View d = dots.getChildAt(i);
            ((GradientDrawable) d.getBackground()).setColor(i == idx ? Ui.accent(cfg) : Ui.alpha(Ui.text(cfg), 0.35f));
            d.getLayoutParams().width = i == idx ? u(30) : u(12);
            d.requestLayout();
        }
    }

    private Content.Item pendingHero;
    private final Runnable heroLater = new Runnable() {
        @Override public void run() { if (pendingHero != null) setHero(pendingHero); }
    };

    private View poster(final Content.Item it) {
        FrameLayout f = new FrameLayout(this);
        f.setTag("poster:" + it.id);
        GradientDrawable g = Ui.pill(this, Ui.alpha(Ui.accent(cfg), 0.25f), Ui.glassStroke(cfg), 18);
        f.setBackground(g);
        f.setClipToOutline(true);
        ImageView iv = new ImageView(this);
        iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
        f.addView(iv, Dlg.match());
        if (!it.poster.isEmpty()) ImageLoader.get(this).load(iv, it.poster, u(160), u(240));
        View shade = new View(this);
        shade.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{0x00000000, 0x00000000, 0xCC000000}));
        f.addView(shade, Dlg.match());
        TextView t = Ui.tv(this, cfg, it.title, 19, true);
        t.setTextColor(Color.WHITE);
        t.setMaxLines(2);
        t.setEllipsize(TextUtils.TruncateAt.END);
        t.setPadding(u(10), 0, u(10), u(10));
        t.setGravity(Gravity.CENTER_HORIZONTAL);
        f.addView(t, new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM));
        if (it.isNew) {
            TextView badge = Ui.tv(this, cfg, s("جديد", "NEW"), 16, true);
            badge.setTextColor(Color.WHITE);
            badge.setPadding(u(12), u(4), u(12), u(4));
            badge.setBackground(Ui.pill(this, Ui.accent(cfg), 0, 12));
            FrameLayout.LayoutParams bl = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.START);
            bl.setMargins(u(8), u(8), u(8), u(8));
            f.addView(badge, bl);
        }
        Ui.focusable(this, cfg, f, g, 1.08f, new View.OnFocusChangeListener() {
            @Override public void onFocusChange(View v, boolean has) {
                if (has) {
                    h.removeCallbacks(heroLater);
                    pendingHero = it;
                    h.postDelayed(heroLater, 250);
                }
            }
        });
        f.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { details(it); } });
        f.setOnLongClickListener(new View.OnLongClickListener() {
            @Override public boolean onLongClick(View v) { toggleFav(it); return true; }
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(u(158), -1);
        lp.setMarginEnd(u(16));
        f.setLayoutParams(lp);
        return f;
    }

    private View matchTile(Content.Item it) {
        LinearLayout f = new LinearLayout(this);
        f.setOrientation(LinearLayout.VERTICAL);
        f.setGravity(Gravity.CENTER);
        f.setTag("poster:" + it.id);
        f.setPadding(u(18), u(14), u(18), u(14));
        GradientDrawable g = Ui.pill(this, it.isNew ? Ui.alpha(Ui.accent(cfg), 0.30f) : Ui.glassFill(cfg), Ui.glassStroke(cfg), 20);
        f.setBackground(g);
        TextView title = Ui.tv(this, cfg, it.title, 27, true);
        title.setGravity(Gravity.CENTER);
        title.setMaxLines(3);
        f.addView(title);
        TextView sub = Ui.tv(this, cfg, it.subtitle, 19, false);
        sub.setTextColor(Ui.muted(cfg));
        sub.setGravity(Gravity.CENTER);
        sub.setPadding(0, u(10), 0, 0);
        f.addView(sub);
        Ui.focusable(this, cfg, f, g, 1.05f, null);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(u(320), -1);
        lp.setMarginEnd(u(16));
        f.setLayoutParams(lp);
        return f;
    }

    private void toggleFav(Content.Item it) {
        if (cfg.previewMode || "match".equals(it.kind)) return;
        boolean was = Content.isFavorite(this, it);
        Content.toggleFavorite(this, it);
        Toast.makeText(this, was ? s("انشال من المفضلة", "Removed from favourites") : s("انضاف للمفضلة ⭐", "Added to favourites ⭐"), Toast.LENGTH_SHORT).show();
        if ("favorites".equals(cfg.contentCat)) loadContent(false);
    }

    /** Title details: poster, info, overview and actions. Play only opens apps installed on this TV. */
    private void details(final Content.Item it) {
        if ("match".equals(it.kind)) return;
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.HORIZONTAL);
        FrameLayout pf = new FrameLayout(this);
        pf.setBackground(Ui.pill(this, Ui.alpha(Ui.accent(cfg), 0.25f), 0, 18));
        pf.setClipToOutline(true);
        ImageView p = new ImageView(this);
        p.setScaleType(ImageView.ScaleType.CENTER_CROP);
        pf.addView(p, Dlg.match());
        ImageLoader.get(this).load(p, it.poster, u(260), u(390));
        body.addView(pf, new LinearLayout.LayoutParams(u(260), u(390)));

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(u(28), 0, 0, 0);
        TextView meta = Ui.tv(this, cfg, (it.year + (it.rating > 0 ? "   ★ " + String.format(Locale.US, "%.1f", it.rating) : "") + "   " + it.subtitle).trim(), 22, false);
        meta.setTextColor(Ui.muted(cfg));
        col.addView(meta);
        TextView ov = Ui.tv(this, cfg, it.overview.isEmpty() ? s("ماكو وصف.", "No description.") : it.overview, 22, false);
        ov.setLineSpacing(0, 1.3f);
        ov.setPadding(0, u(12), 0, u(12));
        col.addView(Dlg.scroll(this, ov, 200));

        final Dialog[] d = new Dialog[1];
        final boolean fav = Content.isFavorite(this, it);
        String[][] acts = {
                {"i_play", s("شغّل عبر تطبيق…", "Play with an app…")},
                {"i_trailer", s("الإعلان على يوتيوب", "Trailer on YouTube")},
                {"i_star", fav ? s("إزالة من المفضلة", "Remove from favourites") : s("أضف للمفضلة", "Add to favourites")},
                {"i_history", s("إزالة من متابعة المشاهدة", "Remove from continue watching")},
        };
        View first = null;
        for (int i = 0; i < acts.length; i++) {
            if (i == 3 && !"continue".equals(cfg.contentCat)) continue;
            if (i == 2 && cfg.previewMode) continue;
            final int idx = i;
            LinearLayout r = Dlg.row(this, cfg, acts[i][0], acts[i][1], null, false);
            r.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    d[0].dismiss();
                    if (idx == 0) playWith(it);
                    else if (idx == 1) openUrl("https://www.youtube.com/results?search_query=" + Feeds.enc(it.title + " trailer"),
                            new String[]{"com.google.android.youtube.tv", "com.google.android.youtube"});
                    else if (idx == 2) toggleFav(it);
                    else { Content.removeContinue(HomeActivity.this, it); loadContent(false); }
                }
            });
            col.addView(r);
            if (first == null) first = r;
        }
        body.addView(col, new LinearLayout.LayoutParams(0, -2, 1));
        d[0] = Dlg.panel(this, cfg, it.title, body, 1240);
        d[0].show();
        if (first != null) first.requestFocus();
    }

    private void playWith(final Content.Item it) {
        final Map<String, Apps.App> installed = Apps.byPkg(this);
        final List<Apps.App> apps = new ArrayList<Apps.App>();
        final List<String> links = new ArrayList<String>();
        java.util.Set<String> seen = new java.util.HashSet<String>();
        for (String[] p : Apps.PLAYERS) {
            Apps.App a = installed.get(p[0]);
            if (a != null && seen.add(a.label)) { apps.add(a); links.add(p[1]); }
        }
        List<String> labels = new ArrayList<String>();
        for (Apps.App a : apps) labels.add(a.label);
        labels.add(s("تطبيق آخر…", "Another app…"));
        Dlg.choose(this, cfg, s("شغّل «", "Play “") + it.title + s("» عبر", "” with"), labels, -1, new Dlg.Pick() {
            @Override public void on(int i) {
                if (!cfg.previewMode) Content.watched(HomeActivity.this, it);
                if (i >= apps.size()) { startActivity(new Intent(HomeActivity.this, AllAppsActivity.class)); return; }
                Apps.App a = apps.get(i);
                String link = links.get(i);
                if (link != null) {
                    try {
                        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(String.format(link, Feeds.enc(it.title))))
                                .setPackage(a.pkg).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                        return;
                    } catch (Exception ignored) { }
                }
                if (Apps.launch(HomeActivity.this, a))
                    Toast.makeText(HomeActivity.this, s("ابحث داخل التطبيق عن: ", "Search the app for: ") + it.title, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void openUrl(String url, String[] pkgs) {
        for (String p : pkgs) {
            try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)).setPackage(p).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); return; }
            catch (Exception ignored) { }
        }
        try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); }
        catch (Exception e) { Toast.makeText(this, s("ماكو تطبيق يفتح الرابط", "No app can open this"), Toast.LENGTH_SHORT).show(); }
    }

    // ------------------------------------------------------------------ favourites

    private View favoritesSection() {
        LinearLayout sec = new LinearLayout(this);
        sec.setOrientation(LinearLayout.VERTICAL);
        sec.setClipChildren(false);
        sec.setClipToPadding(false);
        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setPadding(u(6), u(26), u(6), u(4));
        head.addView(Ui.tv(this, cfg, s("تطبيقاتي المفضلة", "My favourite apps"), 34, true), new LinearLayout.LayoutParams(0, -2, 1));
        LinearLayout edit = new LinearLayout(this);
        edit.setTag("edit");
        edit.setOrientation(LinearLayout.HORIZONTAL);
        edit.setGravity(Gravity.CENTER_VERTICAL);
        edit.setPadding(u(22), u(10), u(26), u(10));
        GradientDrawable eg = Ui.pill(this, Ui.glassFill(cfg), Ui.glassStroke(cfg), 28);
        edit.setBackground(eg);
        edit.addView(Ui.icon(this, "i_edit", Ui.text(cfg), 28));
        editLabel = Ui.tv(this, cfg, "", 23, true);
        editLabel.setPadding(u(12), 0, 0, 0);
        edit.addView(editLabel);
        Ui.focusable(this, cfg, edit, eg, 1.06f, null);
        edit.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { setEdit(!editMode); } });
        head.addView(edit);
        sec.addView(head);

        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        hs.setClipChildren(false);
        hs.setClipToPadding(false);
        hs.setPadding(u(8), u(18), u(8), u(18));
        favRow = new LinearLayout(this);
        favRow.setOrientation(LinearLayout.HORIZONTAL);
        favRow.setClipChildren(false);
        hs.addView(favRow);
        sec.addView(hs);
        updateEditLabel();
        return sec;
    }

    private void updateEditLabel() {
        if (editLabel != null) editLabel.setText(editMode ? s("تم", "Done") : s("تعديل التطبيقات", "Edit apps"));
    }

    private void setEdit(boolean on) {
        editMode = on;
        moving = null;
        updateEditLabel();
        fillFavorites();
        if (on) Toast.makeText(this, s("اختار تطبيق بـ OK وحرّكه بالأسهم، OK للتثبيت. ضغطة مطوّلة = خيارات.",
                "Pick an app with OK, move it with the arrows, OK to drop. Long-press = options."), Toast.LENGTH_LONG).show();
    }

    private int[] tileSize() {
        int[][] s = {{185, 115}, {232, 145}, {275, 172}};
        int[] r = s[Math.max(0, Math.min(2, cfg.iconSize))];
        return new int[]{u(r[0]), u(r[1])};
    }

    private void fillFavorites() {
        if (favRow == null) return;
        View f = getCurrentFocus();
        String keep = f != null && f.getTag() instanceof String ? (String) f.getTag() : null;
        favRow.removeAllViews();
        Map<String, Apps.App> installed = Apps.byPkg(this);
        int[] sz = tileSize();
        for (String entry : new ArrayList<String>(cfg.favorites)) {
            View t = null;
            if (entry.startsWith("folder:")) {
                Config.Folder fd = cfg.folders.get(entry.substring(7));
                if (fd != null) t = folderTile(entry, fd, installed, sz);
            } else {
                Apps.App a = installed.get(entry);
                if (a != null) t = appTile(entry, a, sz);
            }
            if (t != null) favRow.addView(t);
        }
        favRow.addView(addTile(sz));
        if (keep != null) {
            View v = favRow.findViewWithTag(keep);
            if (v != null) v.requestFocus();
        }
    }

    private FrameLayout tileBase(final String entry, int[] sz) {
        FrameLayout f = new FrameLayout(this);
        f.setTag("fav:" + entry);
        boolean isMoving = entry.equals(moving);
        GradientDrawable g = Ui.pill(this, isMoving ? Ui.alpha(Ui.accent(cfg), 0.45f) : Ui.glassFill(cfg), isMoving ? Ui.accent(cfg) : Ui.glassStroke(cfg), 22);
        f.setBackground(g);
        f.setClipToOutline(true);
        Ui.focusable(this, cfg, f, g, 1.09f, null);
        f.setOnKeyListener(new View.OnKeyListener() {
            @Override public boolean onKey(View v, int code, KeyEvent e) {
                if (e.getAction() != KeyEvent.ACTION_DOWN) return false;
                if (code == KeyEvent.KEYCODE_MENU) { tileMenu(entry); return true; }
                if (!entry.equals(moving)) return false;
                if (code != KeyEvent.KEYCODE_DPAD_LEFT && code != KeyEvent.KEYCODE_DPAD_RIGHT) return false;
                boolean towardsStart = (code == KeyEvent.KEYCODE_DPAD_RIGHT) == ar;
                int i = cfg.favorites.indexOf(entry), j = towardsStart ? i - 1 : i + 1;
                if (i >= 0 && j >= 0 && j < cfg.favorites.size()) {
                    java.util.Collections.swap(cfg.favorites, i, j);
                    save();
                    fillFavorites();
                    View n = favRow.findViewWithTag("fav:" + entry);
                    if (n != null) n.requestFocus();
                }
                return true;
            }
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(sz[0], sz[1]);
        lp.setMarginEnd(u(22));
        f.setLayoutParams(lp);
        return f;
    }

    private View appTile(final String entry, final Apps.App a, int[] sz) {
        FrameLayout f = tileBase(entry, sz);
        if (a.banner != null) {
            ImageView iv = new ImageView(this);
            iv.setImageDrawable(a.banner);
            iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
            f.addView(iv, Dlg.match());
        } else {
            LinearLayout col = new LinearLayout(this);
            col.setOrientation(LinearLayout.VERTICAL);
            col.setGravity(Gravity.CENTER);
            ImageView iv = new ImageView(this);
            iv.setImageDrawable(a.icon);
            col.addView(iv, new LinearLayout.LayoutParams(sz[1] / 2, sz[1] / 2));
            TextView t = Ui.tv(this, cfg, a.label, 18, true);
            t.setSingleLine(true);
            t.setEllipsize(TextUtils.TruncateAt.END);
            t.setPadding(u(8), u(6), u(8), 0);
            col.addView(t);
            f.addView(col, Dlg.match());
        }
        f.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (moving != null) { moving = null; fillFavorites(); return; }
                if (editMode) { moving = entry; fillFavorites(); return; }
                Apps.launch(HomeActivity.this, a);
            }
        });
        f.setOnLongClickListener(new View.OnLongClickListener() {
            @Override public boolean onLongClick(View v) { tileMenu(entry); return true; }
        });
        return f;
    }

    private View folderTile(final String entry, final Config.Folder fd, Map<String, Apps.App> installed, int[] sz) {
        FrameLayout f = tileBase(entry, sz);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER);
        LinearLayout grid = new LinearLayout(this);
        grid.setOrientation(LinearLayout.HORIZONTAL);
        grid.setGravity(Gravity.CENTER);
        int n = 0;
        for (String p : fd.apps) {
            Apps.App a = installed.get(p);
            if (a == null) continue;
            ImageView iv = new ImageView(this);
            iv.setImageDrawable(a.icon);
            LinearLayout.LayoutParams il = new LinearLayout.LayoutParams(sz[1] / 4, sz[1] / 4);
            il.setMargins(u(3), 0, u(3), 0);
            grid.addView(iv, il);
            if (++n == 4) break;
        }
        if (n == 0) grid.addView(Ui.icon(this, "i_folder", Ui.text(cfg), 48));
        col.addView(grid);
        TextView t = Ui.tv(this, cfg, fd.name.isEmpty() ? s("مجلد", "Folder") : fd.name, 18, true);
        t.setSingleLine(true);
        t.setEllipsize(TextUtils.TruncateAt.END);
        t.setPadding(u(8), u(10), u(8), 0);
        col.addView(t);
        f.addView(col, Dlg.match());
        ImageView fi = Ui.icon(this, "i_folder", Ui.accent(cfg), 22);
        FrameLayout.LayoutParams pl = new FrameLayout.LayoutParams(u(22), u(22), Gravity.TOP | Gravity.START);
        pl.setMargins(u(10), u(10), u(10), u(10));
        f.addView(fi, pl);
        f.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (moving != null) { moving = null; fillFavorites(); return; }
                if (editMode) { moving = entry; fillFavorites(); return; }
                openFolder(entry.substring(7));
            }
        });
        f.setOnLongClickListener(new View.OnLongClickListener() {
            @Override public boolean onLongClick(View v) { tileMenu(entry); return true; }
        });
        return f;
    }

    private View addTile(int[] sz) {
        LinearLayout f = new LinearLayout(this);
        f.setOrientation(LinearLayout.VERTICAL);
        f.setGravity(Gravity.CENTER);
        f.setTag("fav:+");
        GradientDrawable g = Ui.pill(this, Ui.alpha(Ui.glassFill(cfg), 0.6f), Ui.glassStroke(cfg), 22);
        f.setBackground(g);
        f.addView(Ui.icon(this, "i_add", Ui.text(cfg), 46));
        TextView t = Ui.tv(this, cfg, s("إضافة تطبيق", "Add app"), 19, true);
        t.setPadding(0, u(6), 0, 0);
        f.addView(t);
        Ui.focusable(this, cfg, f, g, 1.09f, null);
        f.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { addDialog(null); } });
        f.setLayoutParams(new LinearLayout.LayoutParams(sz[0], sz[1]));
        return f;
    }

    private void tileMenu(final String entry) {
        final boolean folder = entry.startsWith("folder:");
        List<String> labels = new ArrayList<String>(), icons = new ArrayList<String>();
        final List<Runnable> acts = new ArrayList<Runnable>();
        if (folder) {
            labels.add(s("فتح المجلد", "Open folder")); icons.add("i_folder");
            acts.add(new Runnable() { @Override public void run() { openFolder(entry.substring(7)); } });
            labels.add(s("إعادة تسمية", "Rename")); icons.add("i_edit");
            acts.add(new Runnable() { @Override public void run() { renameFolder(entry.substring(7)); } });
            labels.add(s("أضف تطبيق للمجلد", "Add an app to it")); icons.add("i_add");
            acts.add(new Runnable() { @Override public void run() { addDialog(entry.substring(7)); } });
        } else {
            labels.add(s("فتح", "Open")); icons.add("i_play");
            acts.add(new Runnable() { @Override public void run() { Apps.App a = Apps.byPkg(HomeActivity.this).get(entry); if (a != null) Apps.launch(HomeActivity.this, a); } });
        }
        labels.add(s("نقل", "Move")); icons.add("i_sort");
        acts.add(new Runnable() { @Override public void run() { if (!editMode) { editMode = true; updateEditLabel(); } moving = entry; fillFavorites(); } });
        if (!folder) {
            labels.add(s("أضف إلى مجلد", "Add to a folder")); icons.add("i_folder");
            acts.add(new Runnable() { @Override public void run() { toFolder(entry); } });
            labels.add(s("معلومات التطبيق", "App info")); icons.add("i_info");
            acts.add(new Runnable() { @Override public void run() { Apps.info(HomeActivity.this, entry); } });
        }
        labels.add(folder ? s("حذف المجلد", "Delete folder") : s("إزالة من المفضلة", "Remove from favourites")); icons.add("i_delete");
        acts.add(new Runnable() {
            @Override public void run() {
                cfg.favorites.remove(entry);
                if (folder) cfg.folders.remove(entry.substring(7));
                save();
                fillFavorites();
            }
        });
        String title;
        if (folder) { Config.Folder fd = cfg.folders.get(entry.substring(7)); title = fd == null ? "" : fd.name; }
        else { Apps.App a = Apps.byPkg(this).get(entry); title = a == null ? entry : a.label; }
        Dlg.choose(this, cfg, title, labels.toArray(new String[0]), icons.toArray(new String[0]), null, -1, new Dlg.Pick() {
            @Override public void on(int i) { acts.get(i).run(); }
        });
    }

    /** Add an app to favourites (folderId == null) or to a folder. Also: new folder, suggested apps to install. */
    private void addDialog(final String folderId) {
        Map<String, Apps.App> installed = Apps.byPkg(this);
        final List<String> pkgs = new ArrayList<String>();
        List<String> labels = new ArrayList<String>(), icons = new ArrayList<String>(), trail = new ArrayList<String>();
        if (folderId == null) {
            pkgs.add("#folder"); labels.add(s("مجلد جديد", "New folder")); icons.add("i_folder"); trail.add(null);
        }
        Config.Folder target = folderId == null ? null : cfg.folders.get(folderId);
        if (folderId != null && target == null) return;
        java.util.Set<String> in = new java.util.HashSet<String>(target == null ? cfg.favorites : target.apps);
        for (Apps.App a : installed.values()) {
            if (in.contains(a.pkg)) continue;
            pkgs.add(a.pkg); labels.add(a.label); icons.add(null); trail.add(a.tv ? null : s("تطبيق موبايل", "phone app"));
        }
        if (folderId == null) {
            for (String[] sgt : Apps.SUGGESTED) {
                if (installed.containsKey(sgt[1])) continue;
                pkgs.add("#store:" + sgt[1]); labels.add(sgt[0]); icons.add("i_add"); trail.add(s("تنزيل من المتجر", "get from store"));
            }
        }
        Dlg.choose(this, cfg, folderId == null ? s("إضافة للمفضلة", "Add to favourites") : s("إضافة للمجلد", "Add to folder"),
                labels.toArray(new String[0]), icons.toArray(new String[0]), trail.toArray(new String[0]), -1, new Dlg.Pick() {
                    @Override public void on(int i) {
                        String p = pkgs.get(i);
                        if ("#folder".equals(p)) { newFolder(null); return; }
                        if (p.startsWith("#store:")) { Apps.openStore(HomeActivity.this, p.substring(7)); return; }
                        if (folderId == null) cfg.favorites.add(p);
                        else if (cfg.folders.get(folderId) != null) cfg.folders.get(folderId).apps.add(p);
                        save();
                        fillFavorites();
                    }
                });
    }

    private void newFolder(final String firstApp) {
        Dlg.input(this, cfg, s("اسم المجلد", "Folder name"), s("مثلاً: أفلام", "e.g. Movies"), "", false, new Dlg.Text() {
            @Override public void on(String name) {
                String id = cfg.newFolderId();
                Config.Folder fd = new Config.Folder();
                fd.name = name.isEmpty() ? s("مجلد", "Folder") : Config.clip(name, 30);
                int at = cfg.favorites.size();
                if (firstApp != null) {
                    fd.apps.add(firstApp);
                    int i = cfg.favorites.indexOf(firstApp);
                    if (i >= 0) { at = i; cfg.favorites.remove(i); }
                }
                cfg.folders.put(id, fd);
                cfg.favorites.add(Math.min(at, cfg.favorites.size()), "folder:" + id);
                save();
                fillFavorites();
            }
        });
    }

    private void toFolder(final String pkg) {
        final List<String> ids = new ArrayList<String>(cfg.folders.keySet());
        List<String> labels = new ArrayList<String>();
        for (String id : ids) labels.add(cfg.folders.get(id).name);
        labels.add(s("+ مجلد جديد", "+ New folder"));
        Dlg.choose(this, cfg, s("أضف إلى مجلد", "Add to a folder"), labels, -1, new Dlg.Pick() {
            @Override public void on(int i) {
                if (i >= ids.size()) { newFolder(pkg); return; }
                Config.Folder fd = cfg.folders.get(ids.get(i));
                if (!fd.apps.contains(pkg)) fd.apps.add(pkg);
                cfg.favorites.remove(pkg);
                save();
                fillFavorites();
            }
        });
    }

    private void renameFolder(final String id) {
        final Config.Folder fd = cfg.folders.get(id);
        if (fd == null) return;
        Dlg.input(this, cfg, s("إعادة تسمية", "Rename"), "", fd.name, false, new Dlg.Text() {
            @Override public void on(String name) {
                if (!name.isEmpty()) { fd.name = Config.clip(name, 30); save(); fillFavorites(); }
            }
        });
    }

    private void openFolder(final String id) {
        final Config.Folder fd = cfg.folders.get(id);
        if (fd == null) return;
        final Map<String, Apps.App> installed = Apps.byPkg(this);
        final List<Apps.App> apps = new ArrayList<Apps.App>();
        for (String p : fd.apps) if (installed.containsKey(p)) apps.add(installed.get(p));
        LinearLayout grid = new LinearLayout(this);
        grid.setOrientation(LinearLayout.VERTICAL);
        grid.setClipChildren(false);
        final Dialog[] d = new Dialog[1];
        LinearLayout row = null;
        int[] sz = tileSize();
        View first = null;
        for (int i = 0; i < apps.size(); i++) {
            if (i % 4 == 0) {
                row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setClipChildren(false);
                row.setPadding(u(8), u(12), u(8), u(12));
                grid.addView(row);
            }
            final Apps.App a = apps.get(i);
            final View t = appTile(a.pkg, a, sz);
            t.setOnKeyListener(null);
            t.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { d[0].dismiss(); Apps.launch(HomeActivity.this, a); } });
            t.setOnLongClickListener(new View.OnLongClickListener() {
                @Override public boolean onLongClick(View v) {
                    d[0].dismiss();
                    Dlg.choose(HomeActivity.this, cfg, a.label, new String[]{s("إخراج من المجلد", "Take out of folder"), s("معلومات التطبيق", "App info")},
                            new String[]{"i_delete", "i_info"}, null, -1, new Dlg.Pick() {
                                @Override public void on(int k) {
                                    if (k == 0) { fd.apps.remove(a.pkg); if (!cfg.favorites.contains(a.pkg)) cfg.favorites.add(a.pkg); save(); fillFavorites(); }
                                    else Apps.info(HomeActivity.this, a.pkg);
                                }
                            });
                    return true;
                }
            });
            row.addView(t);
            if (first == null) first = t;
        }
        if (apps.isEmpty()) grid.addView(Ui.tv(this, cfg, s("المجلد فارغ. ضغطة مطوّلة عليه ← أضف تطبيق.", "Empty folder. Long-press it → add an app."), 24, false));
        ScrollView sv = Dlg.scroll(this, grid, 640);
        sv.setClipChildren(false);
        d[0] = Dlg.panel(this, cfg, fd.name, sv, 4 * 245 + 160);
        d[0].show();
        if (first != null) first.requestFocus();
    }

    private View allAppsButton() {
        LinearLayout wrap = new LinearLayout(this);
        wrap.setGravity(Gravity.CENTER);
        wrap.setClipChildren(false);
        LinearLayout b = Ui.iconText(this, cfg, "i_apps", s("كل التطبيقات", "All apps"), 28, 34, Ui.text(cfg));
        b.setTag("allapps");
        b.setPadding(u(46), u(16), u(46), u(16));
        GradientDrawable g = Ui.pill(this, Ui.alpha(Ui.accent(cfg), 0.22f), Ui.alpha(Ui.accent(cfg), 0.7f), 40);
        b.setBackground(g);
        Ui.focusable(this, cfg, b, g, 1.07f, null);
        b.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { startActivity(new Intent(HomeActivity.this, AllAppsActivity.class)); }
        });
        wrap.addView(b, new LinearLayout.LayoutParams(-2, -2));
        wrap.setPadding(0, u(6), 0, u(4));
        return wrap;
    }
}
