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

/**
 * IBRA TV home screen. Remote friendly: D-pad moves focus, OK opens, long-press OK or MENU opens tile
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
        Intent intent;
    }

    private Config cfg;
    private LinearLayout content;
    private TextView clock;
    private final Handler handler = new Handler();
    private boolean showHidden = false;
    private String focusPkg = null;

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
        header.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
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

        List<App> favs = new ArrayList<App>();
        for (String p : cfg.favorites) if (byPkg.containsKey(p)) favs.add(byPkg.get(p));
        List<App> rest = new ArrayList<App>();
        List<App> hidden = new ArrayList<App>();
        for (App a : apps) {
            if (cfg.favorites.contains(a.pkg)) continue;
            if (cfg.hidden.contains(a.pkg)) hidden.add(a); else rest.add(a);
        }

        View first = null;
        if (!favs.isEmpty()) first = section(s("المفضلة", "Favorites"), favs, first);
        if (cfg.showAllApps || favs.isEmpty()) first = section(s("كل التطبيقات", "All apps"), rest, first);
        if (showHidden && !hidden.isEmpty()) first = section(s("مخفية", "Hidden"), hidden, first);
        first = section(s("النظام", "System"), specials(pm), first);

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
        try {
            startActivity(a.intent);
        } catch (Exception e) {
            Toast.makeText(this, s("ما گدرت أفتح التطبيق", "Could not open the app"), Toast.LENGTH_SHORT).show();
        }
    }

    private void options(final App a) {
        if (a.special) return;
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

    private void move(String pkg, int dir) {
        int i = cfg.favorites.indexOf(pkg);
        int j = i + dir;
        if (i < 0 || j < 0 || j >= cfg.favorites.size()) return;
        Collections.swap(cfg.favorites, i, j);
    }
}
