package tv.ibra.tuner.launcher;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * IBRA HOME screen (live weather, football, today's episodes, Iraqi apps row, all apps). Remote friendly: D-pad moves focus, OK opens, long-press OK or MENU opens tile
 * options, BACK never leaves Home. Arabic (RTL) and English.
 */
public class HomeActivity extends Activity implements SharedPreferences.OnSharedPreferenceChangeListener {

    static final class App {
        String pkg;
        String label;
        ComponentName component;
        Drawable banner;
        Drawable icon;
        boolean special;
        boolean missing;   // not installed: opening it goes to the store page
        Intent intent;
    }

    /** Built-in Iraqi apps row. Each entry: label, then package alternatives (TV build first). */
    static final String[][] IRAQI_APPS = {
            {"سينمانا", "com.shabakaty.cbox.cinemana", "com.shabakaty.cbox.cinemanb", "com.shabakaty.cbox.cinemanx"},
            {"YouTube", "com.google.android.youtube.tv", "com.google.android.youtube"},
            {"شاهد", "net.mbc.shahidTV", "net.mbc.shahid"},
            {"Netflix", "com.netflix.ninja", "com.netflix.mediaclient"},
            {"Prime Video", "com.amazon.amazonvideo.livingroom", "com.amazon.avod.thirdpartyclient"},
            {"Downloader", "com.esaba.downloader"},
    };

    private Config cfg;
    private LinearLayout content;
    private TextView clock;
    private final Handler handler = new Handler();
    private boolean showHidden = false;
    private String focusPkg = null;
    private final AtomicBoolean refreshing = new AtomicBoolean(false);

    private final Runnable tick = new Runnable() {
        @Override public void run() {
            if (clock != null) clock.setText(DateFormat.getTimeFormat(HomeActivity.this).format(new Date()));
            handler.postDelayed(this, 15000);
        }
    };

    private String s(String ar, String en) { return cfg != null && cfg.arabic() ? ar : en; }

    private int dp(float v) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics());
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        getSharedPreferences(Config.PREFS, MODE_PRIVATE).registerOnSharedPreferenceChangeListener(this);
    }

    @Override
    protected void onDestroy() {
        getSharedPreferences(Config.PREFS, MODE_PRIVATE).unregisterOnSharedPreferenceChangeListener(this);
        super.onDestroy();
    }

    @Override
    protected void onResume() {
        super.onResume();
        rebuild();
        handler.removeCallbacks(tick);
        handler.post(tick);
        refreshFeeds(false);
    }

    /** Refreshes stale feeds in the background; rebuilds the screen if anything new arrived. */
    private void refreshFeeds(final boolean force) {
        if (cfg == null || !(cfg.showWeather || cfg.showFootball || cfg.showShows)) return;
        if (!refreshing.compareAndSet(false, true)) return;
        final double lat = cfg.lat, lon = cfg.lon;
        new Thread(new Runnable() {
            @Override public void run() {
                boolean changed = false;
                try { changed = Feeds.refresh(getApplicationContext(), lat, lon, force); }
                catch (Throwable ignored) { }
                finally { refreshing.set(false); }
                final boolean c = changed;
                handler.post(new Runnable() { @Override public void run() {
                    if (c && !isFinishing()) rebuild();
                    else if (force) Toast.makeText(HomeActivity.this,
                            s("ما وصلت معلومات جديدة، تأكد من الإنترنت", "No new data — check the internet"), Toast.LENGTH_SHORT).show();
                }});
            }
        }, "ibra-feeds").start();
    }

    @Override
    protected void onPause() {
        handler.removeCallbacks(tick);
        super.onPause();
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences p, String key) {
        handler.post(new Runnable() { @Override public void run() { rebuild(); } });
    }

    @Override
    public void onBackPressed() {
        // Home stays Home.
    }

    // ------------------------------------------------------------------ apps

    private List<App> loadApps() {
        PackageManager pm = getPackageManager();
        Map<String, App> map = new LinkedHashMap<String, App>();
        Intent lb = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
        Intent la = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        for (int pass = 0; pass < 2; pass++) {
            List<ResolveInfo> list = pm.queryIntentActivities(pass == 0 ? lb : la, 0);
            for (ResolveInfo ri : list) {
                ActivityInfo ai = ri.activityInfo;
                if (ai == null || getPackageName().equals(ai.packageName) || map.containsKey(ai.packageName)) continue;
                App a = new App();
                a.pkg = ai.packageName;
                a.component = new ComponentName(ai.packageName, ai.name);
                CharSequence l = ri.loadLabel(pm);
                a.label = l == null ? ai.packageName : l.toString();
                try { a.banner = ai.loadBanner(pm); } catch (Exception ignored) { }
                if (a.banner == null) {
                    try { a.banner = pm.getApplicationBanner(ai.packageName); } catch (Exception ignored) { }
                }
                try { a.icon = ri.loadIcon(pm); } catch (Exception ignored) { }
                Intent i = pass == 0 ? pm.getLeanbackLaunchIntentForPackage(a.pkg) : null;
                if (i == null) i = new Intent(Intent.ACTION_MAIN).addCategory(pass == 0 ? Intent.CATEGORY_LEANBACK_LAUNCHER : Intent.CATEGORY_LAUNCHER).setComponent(a.component);
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
        return apps;
    }

    private List<App> specials(PackageManager pm) {
        List<App> list = new ArrayList<App>();
        App st = new App();
        st.special = true; st.pkg = "#settings";
        st.label = s("الإعدادات", "Settings");
        st.intent = new Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        st.icon = getResources().getDrawable(android.R.drawable.ic_menu_preferences);
        list.add(st);

        if (cfg.stockHome != null) {
            ComponentName cn = ComponentName.unflattenFromString(cfg.stockHome);
            if (cn != null) {
                Intent i = new Intent(Intent.ACTION_MAIN).setComponent(cn).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                if (pm.resolveActivity(i, 0) != null) {
                    App o = new App();
                    o.special = true; o.pkg = "#stock";
                    o.label = s("الواجهة الأصلية", "Original home");
                    o.intent = i;
                    try { o.icon = pm.getApplicationIcon(cn.getPackageName()); } catch (Exception ignored) { }
                    list.add(o);
                }
            }
        }

        App h = new App();
        h.special = true; h.pkg = "#hidden";
        h.label = showHidden ? s("إخفاء المخفية", "Hide hidden apps") : s("التطبيقات المخفية", "Hidden apps");
        h.icon = getResources().getDrawable(android.R.drawable.ic_menu_view);
        list.add(h);
        return list;
    }

    // ------------------------------------------------------------------ UI

    private void rebuild() {
        cfg = Config.load(this);
        View focused = getCurrentFocus();
        if (focused != null && focused.getTag() instanceof String) focusPkg = (String) focused.getTag();

        FrameLayout root = new FrameLayout(this);
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{cfg.bg1, cfg.bg2});
        root.setBackground(bg);
        root.setLayoutDirection(cfg.arabic() ? View.LAYOUT_DIRECTION_RTL : View.LAYOUT_DIRECTION_LTR);

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(48), dp(28), dp(48), dp(16));
        root.addView(col, new FrameLayout.LayoutParams(-1, -1));

        // header: title + clock
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = text(cfg.title, 30, true);
        title.setTextColor(cfg.accent);
        title.setLetterSpacing(0.08f);
        header.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        TextView date = text(new java.text.SimpleDateFormat("EEEE d MMMM",
                cfg.arabic() ? new java.util.Locale("ar") : java.util.Locale.US).format(new Date()), 18, false);
        date.setAlpha(0.75f);
        date.setPadding(dp(16), 0, dp(16), 0);
        header.addView(date);
        clock = text("", 28, false);
        clock.setVisibility(cfg.showClock ? View.VISIBLE : View.GONE);
        header.addView(clock);
        col.addView(header);

        ScrollView scroll = new ScrollView(this);
        scroll.setClipToPadding(false);
        scroll.setPadding(0, dp(8), 0, dp(32));
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setClipChildren(false);
        content.setClipToPadding(false);
        scroll.addView(content, new FrameLayout.LayoutParams(-1, -2));
        col.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        PackageManager pm = getPackageManager();
        List<App> apps = loadApps();
        Map<String, App> byPkg = new LinkedHashMap<String, App>();
        for (App a : apps) byPkg.put(a.pkg, a);

        View first = infoRow();

        List<App> favs = new ArrayList<App>();
        for (String p : cfg.favorites) if (byPkg.containsKey(p)) favs.add(byPkg.get(p));
        List<App> iraqi = cfg.showIraqiApps ? iraqiApps(byPkg) : new ArrayList<App>();
        java.util.Set<String> inIraqi = new java.util.HashSet<String>();
        for (App a : iraqi) inIraqi.add(a.pkg);
        List<App> rest = new ArrayList<App>();
        List<App> hidden = new ArrayList<App>();
        for (App a : apps) {
            if (cfg.favorites.contains(a.pkg) || inIraqi.contains(a.pkg)) continue;
            if (cfg.hidden.contains(a.pkg)) hidden.add(a); else rest.add(a);
        }

        if (!favs.isEmpty()) first = section(s("المفضلة", "Favorites"), favs, first);
        if (!iraqi.isEmpty()) first = section(s("تطبيقات عراقية", "Iraqi apps"), iraqi, first);
        if (cfg.showAllApps || favs.isEmpty()) first = section(s("كل التطبيقات", "All apps"), rest, first);
        if (showHidden && !hidden.isEmpty()) first = section(s("مخفية", "Hidden"), hidden, first);
        first = section(s("النظام", "System"), specials(pm), first);

        TextView credit = text(s("IBRA HOME · تصميم وتطوير إبراهيم الخزرجي · t.me/ibraprogram",
                "IBRA HOME · by Ibrahim Al-Khazraji · t.me/ibraprogram"), 13, false);
        credit.setAlpha(0.45f);
        credit.setGravity(Gravity.CENTER);
        credit.setPadding(0, dp(24), 0, 0);
        content.addView(credit);

        setContentView(root);

        View target = null;
        if (focusPkg != null) target = root.findViewWithTag(focusPkg);
        if (target == null) target = first;
        if (target != null) target.requestFocus();
    }

    private TextView text(String t, float sp, boolean bold) {
        TextView v = new TextView(this);
        v.setText(t);
        v.setTextColor(cfg.text);
        v.setTextSize(TypedValue.COMPLEX_UNIT_SP, cfg.largeText ? sp : sp * 0.85f);
        if (bold) v.setTypeface(Typeface.DEFAULT_BOLD);
        v.setShadowLayer(6, 0, 2, 0x88000000);
        return v;
    }

    private View section(String name, List<App> list, View firstSoFar) {
        if (list.isEmpty()) return firstSoFar;
        TextView h = text(name, 22, true);
        h.setAlpha(0.85f);
        h.setPadding(dp(4), dp(18), dp(4), dp(8));
        content.addView(h);

        int cols = Math.max(3, Math.min(8, cfg.columns));
        int width = getResources().getDisplayMetrics().widthPixels - dp(96);
        int gap = dp(16);
        int tileW = (width - gap * (cols - 1)) / cols;
        int tileH = tileW * 9 / 16;

        LinearLayout row = null;
        View first = firstSoFar;
        for (int i = 0; i < list.size(); i++) {
            if (i % cols == 0) {
                row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setClipChildren(false);
                content.addView(row);
            }
            View t = tile(list.get(i), tileW, tileH);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(tileW, -2);
            lp.setMarginEnd(i % cols == cols - 1 ? 0 : gap);
            lp.bottomMargin = dp(14);
            row.addView(t, lp);
            if (first == null) first = t.findViewWithTag(list.get(i).pkg);
        }
        return first;
    }

    private View tile(final App a, int w, int h) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setClipChildren(false);

        final FrameLayout card = new FrameLayout(this);
        card.setTag(a.pkg);
        card.setFocusable(true);
        card.setFocusableInTouchMode(true);
        card.setClickable(true);
        final GradientDrawable shape = new GradientDrawable();
        shape.setCornerRadius(dp(14));
        shape.setColor(0x33FFFFFF);
        shape.setStroke(dp(3), Color.TRANSPARENT);
        card.setBackground(shape);
        card.setPadding(dp(3), dp(3), dp(3), dp(3));
        card.setClipToOutline(true);

        ImageView img = new ImageView(this);
        if (a.banner != null) {
            img.setImageDrawable(a.banner);
            img.setScaleType(ImageView.ScaleType.CENTER_CROP);
            card.addView(img, new FrameLayout.LayoutParams(-1, -1));
        } else {
            if (a.icon != null) img.setImageDrawable(a.icon);
            img.setScaleType(ImageView.ScaleType.FIT_CENTER);
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(h * 3 / 5, h * 3 / 5, Gravity.CENTER);
            card.addView(img, lp);
        }
        box.addView(card, new LinearLayout.LayoutParams(w, h));

        if (a.missing) {
            img.setAlpha(0.35f);
            TextView dl = text(s("⬇ تنزيل", "⬇ Get"), 15, true);
            dl.setTextColor(cfg.accent);
            dl.setGravity(Gravity.CENTER);
            card.addView(dl, new FrameLayout.LayoutParams(-1, -1, Gravity.CENTER));
        }
        final TextView label = text(a.label, 17, false);
        label.setSingleLine(true);
        label.setEllipsize(TextUtils.TruncateAt.END);
        label.setGravity(Gravity.CENTER);
        label.setPadding(0, dp(6), 0, 0);
        label.setAlpha(0.8f);
        box.addView(label, new LinearLayout.LayoutParams(w, -2));

        card.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override public void onFocusChange(View v, boolean has) {
                float sc = has ? 1.08f : 1f;
                v.animate().scaleX(sc).scaleY(sc).setDuration(120).start();
                v.setElevation(has ? dp(10) : 0);
                shape.setStroke(dp(3), has ? cfg.accent : Color.TRANSPARENT);
                label.setAlpha(has ? 1f : 0.8f);
                if (has) focusPkg = a.pkg;
            }
        });
        card.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { open(a); }
        });
        card.setOnLongClickListener(new View.OnLongClickListener() {
            @Override public boolean onLongClick(View v) { options(a); return true; }
        });
        card.setOnKeyListener(new View.OnKeyListener() {
            @Override public boolean onKey(View v, int code, KeyEvent e) {
                if (code == KeyEvent.KEYCODE_MENU && e.getAction() == KeyEvent.ACTION_UP) { options(a); return true; }
                return false;
            }
        });
        return box;
    }

    private void open(App a) {
        if ("#hidden".equals(a.pkg)) { showHidden = !showHidden; rebuild(); return; }
        if (a.missing) { openStore(a.pkg); return; }
        try {
            startActivity(a.intent);
        } catch (Exception e) {
            Toast.makeText(this, s("ما گدرت أفتح التطبيق", "Could not open the app"), Toast.LENGTH_SHORT).show();
        }
    }

    private void options(final App a) {
        if (a.special || a.missing) return;
        final boolean fav = cfg.favorites.contains(a.pkg);
        final boolean hid = cfg.hidden.contains(a.pkg);
        final List<String> labels = new ArrayList<String>();
        final List<Runnable> actions = new ArrayList<Runnable>();

        labels.add(fav ? s("إزالة من المفضلة", "Remove from favorites") : s("إضافة للمفضلة", "Add to favorites"));
        actions.add(new Runnable() { @Override public void run() {
            if (fav) cfg.favorites.remove(a.pkg); else { cfg.favorites.add(a.pkg); cfg.hidden.remove(a.pkg); }
        }});
        if (fav) {
            labels.add(s("تحريك للأمام", "Move earlier"));
            actions.add(new Runnable() { @Override public void run() { move(a.pkg, -1); } });
            labels.add(s("تحريك للخلف", "Move later"));
            actions.add(new Runnable() { @Override public void run() { move(a.pkg, 1); } });
        }
        labels.add(hid ? s("إظهار", "Unhide") : s("إخفاء من الواجهة", "Hide from home"));
        actions.add(new Runnable() { @Override public void run() {
            if (hid) cfg.hidden.remove(a.pkg); else { cfg.hidden.add(a.pkg); cfg.favorites.remove(a.pkg); }
        }});
        labels.add(s("معلومات التطبيق", "App info"));
        actions.add(new Runnable() { @Override public void run() {
            try {
                startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + a.pkg))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            } catch (Exception ignored) { }
        }});

        new AlertDialog.Builder(this)
                .setTitle(a.label)
                .setItems(labels.toArray(new CharSequence[0]), new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int which) {
                        boolean info = which == labels.size() - 1;
                        actions.get(which).run();
                        if (!info) cfg.save(HomeActivity.this); // triggers rebuild via prefs listener
                    }
                })
                .show();
    }

    // ------------------------------------------------------------------ Iraqi apps

    private List<App> iraqiApps(Map<String, App> installed) {
        List<App> out = new ArrayList<App>();
        List<String[]> entries = new ArrayList<String[]>();
        for (String[] e : IRAQI_APPS) entries.add(e);
        for (String p : cfg.extraApps) entries.add(new String[]{null, p});
        for (String[] e : entries) {
            App found = null;
            for (int i = 1; i < e.length && found == null; i++) found = installed.get(e[i]);
            if (found != null) {
                if (!out.contains(found)) out.add(found);
            } else if (e[0] != null) {
                App m = new App();
                m.missing = true;
                m.pkg = e[1];
                m.label = e[0];
                m.icon = getResources().getDrawable(android.R.drawable.stat_sys_download);
                out.add(m);
            }
        }
        return out;
    }

    private void openStore(String pkg) {
        Intent[] tries = {
                new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + pkg)),
                new Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=" + pkg)),
        };
        for (Intent i : tries) {
            try { startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); return; } catch (Exception ignored) { }
        }
        Toast.makeText(this, s("ما لگيت متجر تطبيقات على الجهاز", "No app store on this device"), Toast.LENGTH_LONG).show();
    }

    // ------------------------------------------------------------------ info cards

    /** Weather, football and shows cards in one row. Returns the first focusable card or null. */
    private View infoRow() {
        List<View> cards = new ArrayList<View>();
        final boolean ar = cfg.arabic();

        if (cfg.showWeather) {
            final Feeds.Weather w = Feeds.weather(this);
            List<String> lines = new ArrayList<String>();
            String big;
            if (w == null) { big = "—"; lines.add(s("الطقس يتحدّث…", "Loading weather…")); }
            else {
                big = Feeds.weatherSymbol(w.code, w.day) + "  " + Math.round(w.temp) + "°";
                lines.add(cfg.city + " · " + Feeds.weatherText(w.code, ar));
                if (!w.max.isEmpty()) lines.add(s("العظمى ", "High ") + Math.round(w.max.get(0)) + "°   "
                        + s("الصغرى ", "Low ") + Math.round(w.min.get(0)) + "°");
            }
            cards.add(card("#weather", s("الطقس", "Weather"), big, lines, new Runnable() {
                @Override public void run() { weatherDialog(w); }
            }));
        }
        if (cfg.showFootball) {
            final List<Feeds.Match> ms = Feeds.matches(this);
            List<String> lines = new ArrayList<String>();
            int live = 0;
            for (Feeds.Match m : ms) if ("in".equals(m.state)) live++;
            for (int i = 0; i < ms.size() && i < 3; i++) lines.add(matchLine(ms.get(i)));
            String big = ms.isEmpty() ? "—" : live > 0 ? s("● مباشر ", "● LIVE ") + live : String.valueOf(ms.size());
            if (ms.isEmpty()) lines.add(!Feeds.has(this, "football")
                    ? s("المباريات تتحدّث…", "Loading matches…") : s("ماكو مباريات اليوم", "No matches today"));
            cards.add(card("#football", s("مباريات اليوم", "Today's matches"), big, lines, new Runnable() {
                @Override public void run() { listDialog(s("مباريات اليوم", "Today's matches"), matchLines(ms)); }
            }));
        }
        if (cfg.showShows) {
            final List<Feeds.Episode> eps = Feeds.shows(this);
            List<String> lines = new ArrayList<String>();
            for (int i = 0; i < eps.size() && i < 3; i++) lines.add(eps.get(i).show);
            if (eps.isEmpty()) lines.add(s("القائمة تتحدّث…", "Loading…"));
            cards.add(card("#shows", s("حلقات جديدة اليوم", "New episodes today"),
                    eps.isEmpty() ? "—" : String.valueOf(eps.size()), lines, new Runnable() {
                @Override public void run() {
                    List<String> l = new ArrayList<String>();
                    for (Feeds.Episode e : eps) l.add(e.show + "   S" + e.season + "·E" + e.number
                            + (e.channel.isEmpty() ? "" : "   (" + e.channel + ")"));
                    listDialog(s("حلقات جديدة اليوم", "New episodes today"), l);
                }
            }));
        }
        if (cards.isEmpty()) return null;

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setClipChildren(false);
        row.setPadding(0, dp(12), 0, dp(6));
        int gap = dp(16);
        for (int i = 0; i < cards.size(); i++) {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(150), 1);
            if (i < cards.size() - 1) lp.setMarginEnd(gap);
            row.addView(cards.get(i), lp);
        }
        content.addView(row);
        return cards.get(0);
    }

    private View card(final String tag, String title, String big, List<String> lines, final Runnable onOpen) {
        final LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setTag(tag);
        c.setFocusable(true);
        c.setFocusableInTouchMode(true);
        c.setClickable(true);
        c.setPadding(dp(18), dp(12), dp(18), dp(12));
        final GradientDrawable shape = new GradientDrawable();
        shape.setCornerRadius(dp(16));
        shape.setColor(0x26FFFFFF);
        shape.setStroke(dp(2), (cfg.accent & 0x00FFFFFF) | 0x55000000);
        c.setBackground(shape);

        TextView t = text(title, 15, true);
        t.setTextColor(cfg.accent);
        c.addView(t);
        TextView b = text(big, 28, true);
        b.setSingleLine(true);
        c.addView(b);
        for (String l : lines) {
            TextView v = text(l, 14, false);
            v.setSingleLine(true);
            v.setEllipsize(TextUtils.TruncateAt.END);
            v.setAlpha(0.85f);
            c.addView(v);
        }
        c.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override public void onFocusChange(View v, boolean has) {
                v.animate().scaleX(has ? 1.04f : 1f).scaleY(has ? 1.04f : 1f).setDuration(120).start();
                v.setElevation(has ? dp(10) : 0);
                shape.setStroke(dp(has ? 3 : 2), has ? cfg.accent : (cfg.accent & 0x00FFFFFF) | 0x55000000);
                if (has) focusPkg = tag;
            }
        });
        c.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { onOpen.run(); }
        });
        c.setOnLongClickListener(new View.OnLongClickListener() {
            @Override public boolean onLongClick(View v) { refreshFeeds(true); return true; }
        });
        c.setOnKeyListener(new View.OnKeyListener() {
            @Override public boolean onKey(View v, int code, KeyEvent e) {
                if (code == KeyEvent.KEYCODE_MENU && e.getAction() == KeyEvent.ACTION_UP) { refreshFeeds(true); return true; }
                return false;
            }
        });
        return c;
    }

    private String matchLine(Feeds.Match m) {
        if ("pre".equals(m.state)) return Feeds.time(m.kickoff) + "  " + m.home + " - " + m.away;
        String score = m.home + " " + m.homeScore + "-" + m.awayScore + " " + m.away;
        return ("in".equals(m.state) ? "● " : "") + score;
    }

    private List<String> matchLines(List<Feeds.Match> ms) {
        List<String> out = new ArrayList<String>();
        String lastLeague = null;
        for (Feeds.Match m : ms) {
            if (!m.league.equals(lastLeague)) { out.add("— " + m.league + " —"); lastLeague = m.league; }
            String extra = "in".equals(m.state) ? "   (" + m.detail + ")" : "post".equals(m.state) ? s("   (انتهت)", "   (FT)") : "";
            out.add(matchLine(m) + extra);
        }
        return out;
    }

    private void weatherDialog(Feeds.Weather w) {
        List<String> l = new ArrayList<String>();
        if (w != null) {
            for (int i = 0; i < w.days.size(); i++) {
                l.add(Feeds.dayName(w.days.get(i), cfg.arabic()) + "   " + Feeds.weatherSymbol(w.codes.get(i), true) + "  "
                        + Math.round(w.max.get(i)) + "° / " + Math.round(w.min.get(i)) + "°   "
                        + Feeds.weatherText(w.codes.get(i), cfg.arabic()));
            }
        }
        listDialog(s("طقس ", "Weather · ") + cfg.city, l);
    }

    private void listDialog(String title, List<String> lines) {
        if (lines.isEmpty()) lines.add(s("ماكو معلومات بعد. اضغط مطوّلاً على البطاقة للتحديث.",
                "Nothing yet. Long-press the card to refresh."));
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setItems(lines.toArray(new CharSequence[0]), null)
                .setPositiveButton(s("تحديث", "Refresh"), new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int w) { refreshFeeds(true); }
                })
                .setNegativeButton(s("إغلاق", "Close"), null)
                .show();
    }

    private void move(String pkg, int dir) {
        int i = cfg.favorites.indexOf(pkg);
        int j = i + dir;
        if (i < 0 || j < 0 || j >= cfg.favorites.size()) return;
        Collections.swap(cfg.favorites, i, j);
    }
}
