package tv.ibra.tuner.launcher;

import android.app.Activity;
import android.content.Intent;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Grid of every launchable app (TV and phone apps) with search, A–Z / recent sort and per-app options. */
public class AllAppsActivity extends Activity {
    private Config cfg;
    private boolean ar;
    private LinearLayout grid;
    private EditText search;
    private TextView sortLabel, hiddenLabel, countV;
    private boolean showHidden;

    private String s(String a, String e) { return ar ? a : e; }
    private int u(float v) { return Ui.u(this, v); }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        cfg = Config.load(this);
        ar = cfg.arabic();
        Ui.apply(cfg);
        FrameLayout root = new FrameLayout(this);
        root.setLayoutDirection(ar ? View.LAYOUT_DIRECTION_RTL : View.LAYOUT_DIRECTION_LTR);
        root.addView(new Ui.Background(this, cfg), Dlg.match());

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(u(60), u(36), u(60), 0);
        col.setClipChildren(false);
        root.addView(col, Dlg.match());

        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setClipChildren(false);
        head.addView(Ui.icon(this, "i_apps", Ui.accent(cfg), 44));
        TextView title = Ui.tv(this, cfg, s("كل التطبيقات", "All apps"), 40, true);
        title.setPadding(u(16), 0, u(10), 0);
        head.addView(title);
        countV = Ui.tv(this, cfg, "", 26, false);
        countV.setTextColor(Ui.muted(cfg));
        head.addView(countV, new LinearLayout.LayoutParams(0, -2, 1));

        search = new EditText(this);
        search.setHint(s("ابحث عن تطبيق…", "Search apps…"));
        search.setSingleLine(true);
        search.setTextColor(Ui.text(cfg));
        search.setHintTextColor(Ui.muted(cfg));
        search.setTextSize(TypedValue.COMPLEX_UNIT_PX, u(26));
        search.setPadding(u(24), u(12), u(24), u(12));
        final GradientDrawable sg = Ui.pill(this, Ui.glassFill(cfg), Ui.glassStroke(cfg), 30);
        search.setBackground(sg);
        search.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override public void onFocusChange(View v, boolean has) { sg.setStroke(u(has ? 3 : 1.5f), has ? Ui.accent(cfg) : Ui.glassStroke(cfg)); }
        });
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence c, int a, int b2, int d) { }
            @Override public void onTextChanged(CharSequence c, int a, int b2, int d) { fill(); }
            @Override public void afterTextChanged(Editable e) { }
        });
        LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(u(520), -2);
        sl.setMarginEnd(u(16));
        head.addView(search, sl);

        sortLabel = pill(head, "i_sort", "", new View.OnClickListener() {
            @Override public void onClick(View v) { cfg.sortRecent = !cfg.sortRecent; cfg.save(AllAppsActivity.this); fill(); }
        });
        hiddenLabel = pill(head, "i_info", "", new View.OnClickListener() {
            @Override public void onClick(View v) { showHidden = !showHidden; fill(); }
        });
        col.addView(head);

        ScrollView sv = new ScrollView(this);
        sv.setVerticalScrollBarEnabled(false);
        sv.setClipChildren(false);
        sv.setClipToPadding(false);
        sv.setPadding(0, u(20), 0, u(40));
        grid = new LinearLayout(this);
        grid.setOrientation(LinearLayout.VERTICAL);
        grid.setClipChildren(false);
        sv.addView(grid);
        col.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(root);
        fill();
        if (grid.getChildCount() > 0 && ((LinearLayout) grid.getChildAt(0)).getChildCount() > 0)
            ((LinearLayout) grid.getChildAt(0)).getChildAt(0).requestFocus();
    }

    private TextView pill(LinearLayout parent, String icon, String text, View.OnClickListener l) {
        LinearLayout p = new LinearLayout(this);
        p.setOrientation(LinearLayout.HORIZONTAL);
        p.setGravity(Gravity.CENTER_VERTICAL);
        p.setPadding(u(20), u(10), u(22), u(10));
        GradientDrawable g = Ui.pill(this, Ui.glassFill(cfg), Ui.glassStroke(cfg), 28);
        p.setBackground(g);
        p.addView(Ui.icon(this, icon, Ui.text(cfg), 28));
        TextView t = Ui.tv(this, cfg, text, 22, true);
        t.setPadding(u(10), 0, 0, 0);
        p.addView(t);
        Ui.focusable(this, cfg, p, g, 1.06f, null);
        p.setOnClickListener(l);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.setMarginStart(u(12));
        parent.addView(p, lp);
        return t;
    }

    private void fill() {
        sortLabel.setText(cfg.sortRecent ? s("آخر استخدام", "Recent") : s("أبجدي", "A–Z"));
        hiddenLabel.setText(showHidden ? s("إخفاء المخفية", "Hide hidden") : s("إظهار المخفية", "Show hidden"));
        String q = search.getText().toString().trim().toLowerCase(Locale.ROOT);
        List<Apps.App> all = Apps.all(this);
        if (cfg.sortRecent) all = Apps.sortedByRecent(this, all);
        List<Apps.App> list = new ArrayList<Apps.App>();
        for (Apps.App a : all) {
            boolean hid = cfg.hidden.contains(a.pkg);
            if (hid && !showHidden) continue;
            if (!q.isEmpty() && !a.label.toLowerCase(Locale.ROOT).contains(q) && !a.pkg.toLowerCase(Locale.ROOT).contains(q)) continue;
            list.add(a);
        }
        countV.setText("(" + list.size() + ")");
        grid.removeAllViews();
        int cols = Math.max(4, Math.min(8, cfg.columns));
        int width = Math.round(1920 * Ui.scale(this)) - u(120);
        int gap = u(26);
        int w = (width - gap * (cols - 1)) / cols, h = w * 10 / 16;
        LinearLayout row = null;
        for (int i = 0; i < list.size(); i++) {
            if (i % cols == 0) {
                row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setClipChildren(false);
                LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(-1, -2);
                rl.bottomMargin = u(22);
                grid.addView(row, rl);
            }
            View t = tile(list.get(i), w, h);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(w, -2);
            if (i % cols != cols - 1) lp.setMarginEnd(gap);
            row.addView(t, lp);
        }
        if (list.isEmpty()) grid.addView(Ui.tv(this, cfg, s("ماكو نتائج.", "Nothing found."), 28, false));
    }

    private View tile(final Apps.App a, int w, int h) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setClipChildren(false);
        FrameLayout card = new FrameLayout(this);
        card.setTag("app:" + a.pkg);
        GradientDrawable g = Ui.pill(this, Ui.glassFill(cfg), Ui.glassStroke(cfg), 20);
        card.setBackground(g);
        card.setClipToOutline(true);
        Ui.appFace(this, cfg, a, card, g, w, h, false);
        if (cfg.favorites.contains(a.pkg)) {
            ImageView st = Ui.icon(this, "i_star", Ui.accent(cfg), 26);
            FrameLayout.LayoutParams pl = new FrameLayout.LayoutParams(u(26), u(26), Gravity.TOP | Gravity.START);
            pl.setMargins(u(8), u(8), u(8), u(8));
            card.addView(st, pl);
        }
        box.addView(card, new LinearLayout.LayoutParams(w, h));
        final TextView label = Ui.tv(this, cfg, a.label + (a.tv ? "" : "  📱"), 20, false);
        label.setSingleLine(true);
        label.setEllipsize(TextUtils.TruncateAt.END);
        label.setGravity(Gravity.CENTER);
        label.setPadding(0, u(8), 0, 0);
        label.setAlpha(0.8f);
        box.addView(label, new LinearLayout.LayoutParams(w, -2));
        Ui.focusable(this, cfg, card, g, 1.08f, new View.OnFocusChangeListener() {
            @Override public void onFocusChange(View v, boolean has) { label.setAlpha(has ? 1f : 0.8f); }
        });
        card.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { Apps.launch(AllAppsActivity.this, a); } });
        card.setOnLongClickListener(new View.OnLongClickListener() { @Override public boolean onLongClick(View v) { menu(a); return true; } });
        card.setOnKeyListener(new View.OnKeyListener() {
            @Override public boolean onKey(View v, int code, KeyEvent e) {
                if (code == KeyEvent.KEYCODE_MENU && e.getAction() == KeyEvent.ACTION_UP) { menu(a); return true; }
                return false;
            }
        });
        return box;
    }

    private void menu(final Apps.App a) {
        final boolean fav = cfg.favorites.contains(a.pkg), hid = cfg.hidden.contains(a.pkg);
        String[] labels = {
                fav ? s("إزالة من المفضلة", "Remove from favourites") : s("أضف للمفضلة", "Add to favourites"),
                hid ? s("إظهار", "Unhide") : s("إخفاء من القائمة", "Hide from list"),
                s("معلومات التطبيق", "App info"),
                s("إلغاء التثبيت", "Uninstall"),
        };
        String note = a.tv ? null : s("تطبيق موبايل: قد يحتاج ماوس", "Phone app: may need a mouse");
        Dlg.choose(this, cfg, a.label + (note == null ? "" : "\n" + note), labels, new String[]{"i_star", "i_info", "i_info", "i_delete"}, null, -1,
                new Dlg.Pick() {
                    @Override public void on(int i) {
                        if (i == 0) { if (fav) cfg.favorites.remove(a.pkg); else { cfg.favorites.add(a.pkg); cfg.hidden.remove(a.pkg); } cfg.save(AllAppsActivity.this); fill(); }
                        else if (i == 1) { if (hid) cfg.hidden.remove(a.pkg); else cfg.hidden.add(a.pkg); cfg.save(AllAppsActivity.this); fill(); }
                        else if (i == 2) Apps.info(AllAppsActivity.this, a.pkg);
                        else {
                            try { startActivity(new Intent(Intent.ACTION_DELETE, Uri.parse("package:" + a.pkg)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); }
                            catch (Exception e) { Apps.info(AllAppsActivity.this, a.pkg); }
                        }
                    }
                });
    }

    private boolean resumedOnce;

    @Override
    protected void onResume() {
        super.onResume();
        if (resumedOnce) { Apps.invalidate(); fill(); }   // an app may have been uninstalled meanwhile
        resumedOnce = true;
    }
}
