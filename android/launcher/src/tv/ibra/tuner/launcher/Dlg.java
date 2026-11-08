package tv.ibra.tuner.launcher;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.text.InputType;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.List;

/** Glass dialogs for the remote: menus, single/multi choice, reorder, text input and free panels. */
public final class Dlg {
    private Dlg() { }

    public interface Pick { void on(int index); }
    public interface Multi { void on(boolean[] checked); }
    public interface Text { void on(String value); }

    static int u(Activity a, float v) { return Ui.u(a, v); }

    /** Opaque-ish glass panel so text stays readable over anything. */
    static GradientDrawable panelBg(Activity a, Config cfg) {
        int[] p = cfg.palette();
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{Ui.alpha(Ui.mix(p[1], cfg.light() ? Color.WHITE : p[0], 0.35f), 0.97f), Ui.alpha(p[1], 0.97f)});
        g.setCornerRadius(u(a, 32));
        g.setStroke(Math.max(1, u(a, 2)), Ui.alpha(p[2], 0.55f));
        return g;
    }

    /** A dialog with a title and any body. widthRef is in 1920-wide reference pixels. */
    public static Dialog panel(Activity a, Config cfg, String title, View body, float widthRef) {
        final Dialog d = new Dialog(a);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout box = new LinearLayout(a);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setLayoutDirection(cfg.arabic() ? View.LAYOUT_DIRECTION_RTL : View.LAYOUT_DIRECTION_LTR);
        box.setBackground(panelBg(a, cfg));
        box.setPadding(u(a, 36), u(a, 28), u(a, 36), u(a, 28));
        if (title != null) {
            TextView t = Ui.tv(a, cfg, title, 34, true);
            t.setPadding(0, 0, 0, u(a, 18));
            box.addView(t);
        }
        box.addView(body, new LinearLayout.LayoutParams(-1, -2));
        d.setContentView(box);
        Window w = d.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            w.setLayout(u(a, widthRef), ViewGroup.LayoutParams.WRAP_CONTENT);
            w.setDimAmount(0.55f);
            w.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        }
        return d;
    }

    /** A focusable row: optional icon, label, optional trailing text/check. */
    public static LinearLayout row(Activity a, Config cfg, String icon, String label, String trailing, boolean checked) {
        LinearLayout r = new LinearLayout(a);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(u(a, 22), u(a, 14), u(a, 22), u(a, 14));
        r.setMinimumHeight(u(a, 72));
        final GradientDrawable bg = Ui.pill(a, 0x00000000, 0x00000000, 18);
        r.setBackground(bg);
        if (icon != null) {
            ImageView iv = Ui.icon(a, icon, Ui.text(cfg), 34);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(u(a, 34), u(a, 34));
            lp.setMarginEnd(u(a, 18));
            r.addView(iv, lp);
        }
        TextView t = Ui.tv(a, cfg, label, 27, false);
        r.addView(t, new LinearLayout.LayoutParams(0, -2, 1));
        if (trailing != null) {
            TextView tr = Ui.tv(a, cfg, trailing, 24, false);
            tr.setTextColor(Ui.muted(cfg));
            tr.setPadding(u(a, 16), 0, 0, 0);
            r.addView(tr);
        }
        if (checked) {
            ImageView ck = Ui.icon(a, "i_check", Ui.accent(cfg), 32);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(u(a, 32), u(a, 32));
            lp.setMarginStart(u(a, 14));
            r.addView(ck, lp);
        }
        final int accent = Ui.accent(cfg);
        r.setFocusable(true);
        r.setFocusableInTouchMode(true);
        r.setClickable(true);
        r.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override public void onFocusChange(View v, boolean has) {
                bg.setColor(has ? Ui.alpha(accent, 0.28f) : 0x00000000);
                bg.setStroke(has ? u(a, 2) : 0, has ? accent : 0x00000000);
            }
        });
        return r;
    }

    static ScrollView scroll(Activity a, View content, float maxHeightRef) {
        final int max = u(a, maxHeightRef);
        ScrollView s = new ScrollView(a) {
            @Override protected void onMeasure(int w, int h) {
                super.onMeasure(w, MeasureSpec.makeMeasureSpec(max, MeasureSpec.AT_MOST));
            }
        };
        s.setVerticalScrollBarEnabled(false);
        s.addView(content);
        return s;
    }

    /** Menu / single choice. {@code icons} and {@code trailing} may be null; checked = -1 for none. */
    public static Dialog choose(final Activity a, Config cfg, String title, String[] labels, String[] icons,
                                String[] trailing, int checked, final Pick cb) {
        LinearLayout list = new LinearLayout(a);
        list.setOrientation(LinearLayout.VERTICAL);
        final Dialog[] holder = new Dialog[1];
        View first = null;
        for (int i = 0; i < labels.length; i++) {
            final int idx = i;
            LinearLayout r = row(a, cfg, icons == null ? null : icons[i], labels[i], trailing == null ? null : trailing[i], i == checked);
            r.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { holder[0].dismiss(); cb.on(idx); }
            });
            list.addView(r);
            if (i == checked || (first == null && checked < 0)) first = r;
        }
        Dialog d = panel(a, cfg, title, scroll(a, list, 700), 760);
        holder[0] = d;
        d.show();
        if (first != null) first.requestFocus();
        return d;
    }

    public static Dialog choose(Activity a, Config cfg, String title, List<String> labels, int checked, Pick cb) {
        return choose(a, cfg, title, labels.toArray(new String[0]), null, null, checked, cb);
    }

    /** Multi choice with a Done button. */
    public static void multi(final Activity a, final Config cfg, String title, String[] labels, final boolean[] checked, final Multi cb) {
        LinearLayout list = new LinearLayout(a);
        list.setOrientation(LinearLayout.VERTICAL);
        final boolean[] state = checked.clone();
        final Dialog[] holder = new Dialog[1];
        final LinearLayout body = new LinearLayout(a);
        body.setOrientation(LinearLayout.VERTICAL);
        for (int i = 0; i < labels.length; i++) {
            final int idx = i;
            final LinearLayout r = row(a, cfg, state[i] ? "i_check" : null, labels[i], null, false);
            final ImageView mark = Ui.icon(a, "i_check", Ui.accent(cfg), 34);
            r.removeAllViews();
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(u(a, 34), u(a, 34));
            lp.setMarginEnd(u(a, 18));
            r.addView(mark, lp);
            mark.setAlpha(state[i] ? 1f : 0.12f);
            r.addView(Ui.tv(a, cfg, labels[i], 27, false), new LinearLayout.LayoutParams(0, -2, 1));
            r.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { state[idx] = !state[idx]; mark.setAlpha(state[idx] ? 1f : 0.12f); }
            });
            list.addView(r);
        }
        body.addView(scroll(a, list, 640));
        LinearLayout done = row(a, cfg, "i_check", cfg.arabic() ? "تم" : "Done", null, false);
        done.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams dl = new LinearLayout.LayoutParams(-1, -2);
        dl.topMargin = u(a, 16);
        done.setBackground(Ui.pill(a, Ui.alpha(Ui.accent(cfg), 0.35f), Ui.accent(cfg), 18));
        body.addView(done, dl);
        done.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { holder[0].dismiss(); cb.on(state); }
        });
        holder[0] = panel(a, cfg, title, body, 760);
        holder[0].show();
        if (list.getChildCount() > 0) list.getChildAt(0).requestFocus();
    }

    /** Reorder: pick an item, then it moves up/down with the arrows; OK puts it down. */
    public static void reorder(final Activity a, final Config cfg, String title, final List<String> keys,
                               final List<String> labels, final Runnable onDone) {
        final LinearLayout list = new LinearLayout(a);
        list.setOrientation(LinearLayout.VERTICAL);
        final int[] moving = {-1};
        final Dialog[] holder = new Dialog[1];
        final boolean ar = cfg.arabic();
        final Runnable[] render = new Runnable[1];
        render[0] = new Runnable() {
            @Override public void run() {
                list.removeAllViews();
                for (int i = 0; i < keys.size(); i++) {
                    final int idx = i;
                    String lbl = (i + 1) + ".  " + labels.get(i);
                    LinearLayout r = row(a, cfg, moving[0] == i ? "i_sort" : null, lbl,
                            moving[0] == i ? (ar ? "↑↓ ثم OK" : "↑↓ then OK") : null, false);
                    if (moving[0] == i) r.setBackground(Ui.pill(a, Ui.alpha(Ui.accent(cfg), 0.35f), Ui.accent(cfg), 18));
                    r.setOnClickListener(new View.OnClickListener() {
                        @Override public void onClick(View v) {
                            moving[0] = moving[0] == idx ? -1 : idx;
                            render[0].run();
                            list.getChildAt(idx).requestFocus();
                        }
                    });
                    r.setOnKeyListener(new View.OnKeyListener() {
                        @Override public boolean onKey(View v, int code, KeyEvent e) {
                            if (moving[0] != idx || e.getAction() != KeyEvent.ACTION_DOWN) return false;
                            int to = code == KeyEvent.KEYCODE_DPAD_UP ? idx - 1 : code == KeyEvent.KEYCODE_DPAD_DOWN ? idx + 1 : -2;
                            if (to == -2) return false;
                            if (to < 0 || to >= keys.size()) return true;
                            java.util.Collections.swap(keys, idx, to);
                            java.util.Collections.swap(labels, idx, to);
                            moving[0] = to;
                            render[0].run();
                            list.getChildAt(to).requestFocus();
                            return true;
                        }
                    });
                    list.addView(r);
                }
            }
        };
        render[0].run();
        LinearLayout body = new LinearLayout(a);
        body.setOrientation(LinearLayout.VERTICAL);
        TextView hint = Ui.tv(a, cfg, ar ? "اختار عنصر بـ OK، حرّكه بالسهمين فوق وجوّه، واضغط OK لتثبيته." :
                "Select an item with OK, move it with up/down, press OK to drop it.", 22, false);
        hint.setTextColor(Ui.muted(cfg));
        hint.setPadding(0, 0, 0, u(a, 14));
        body.addView(hint);
        body.addView(list);
        LinearLayout done = row(a, cfg, "i_check", ar ? "تم" : "Done", null, false);
        done.setBackground(Ui.pill(a, Ui.alpha(Ui.accent(cfg), 0.35f), Ui.accent(cfg), 18));
        LinearLayout.LayoutParams dl = new LinearLayout.LayoutParams(-1, -2);
        dl.topMargin = u(a, 16);
        body.addView(done, dl);
        done.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { holder[0].dismiss(); onDone.run(); }
        });
        holder[0] = panel(a, cfg, title, body, 760);
        holder[0].show();
        if (list.getChildCount() > 0) list.getChildAt(0).requestFocus();
    }

    /** Text input with the TV keyboard. */
    public static void input(final Activity a, Config cfg, String title, String hint, String initial, boolean password, final Text cb) {
        LinearLayout body = new LinearLayout(a);
        body.setOrientation(LinearLayout.VERTICAL);
        final EditText e = new EditText(a);
        e.setHint(hint);
        e.setText(initial == null ? "" : initial);
        e.setSingleLine(true);
        e.setTextColor(Ui.text(cfg));
        e.setHintTextColor(Ui.muted(cfg));
        e.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, u(a, 28));
        e.setPadding(u(a, 22), u(a, 16), u(a, 22), u(a, 16));
        e.setBackground(Ui.pill(a, Ui.alpha(Color.WHITE, cfg.light() ? 0.7f : 0.08f), Ui.accent(cfg), 18));
        e.setInputType(password ? InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD : InputType.TYPE_CLASS_TEXT);
        e.setImeOptions(EditorInfo.IME_ACTION_DONE);
        body.addView(e);
        LinearLayout ok = row(a, cfg, "i_check", cfg.arabic() ? "حفظ" : "Save", null, false);
        ok.setBackground(Ui.pill(a, Ui.alpha(Ui.accent(cfg), 0.35f), Ui.accent(cfg), 18));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = u(a, 18);
        body.addView(ok, lp);
        final Dialog d = panel(a, cfg, title, body, 860);
        final Runnable submit = new Runnable() {
            @Override public void run() { d.dismiss(); cb.on(e.getText().toString().trim()); }
        };
        ok.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { submit.run(); } });
        e.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override public boolean onEditorAction(TextView v, int id, KeyEvent ev) { submit.run(); return true; }
        });
        d.show();
        e.requestFocus();
        e.postDelayed(new Runnable() {
            @Override public void run() {
                InputMethodManager imm = (InputMethodManager) a.getSystemService(Activity.INPUT_METHOD_SERVICE);
                if (imm != null) imm.showSoftInput(e, InputMethodManager.SHOW_IMPLICIT);
            }
        }, 200);
    }

    public static void info(Activity a, Config cfg, String title, String message) {
        LinearLayout body = new LinearLayout(a);
        body.setOrientation(LinearLayout.VERTICAL);
        TextView t = Ui.tv(a, cfg, message, 25, false);
        t.setLineSpacing(0, 1.3f);
        body.addView(scroll(a, t, 620));
        final Dialog[] d = new Dialog[1];
        LinearLayout ok = row(a, cfg, "i_check", cfg.arabic() ? "حسناً" : "OK", null, false);
        ok.setBackground(Ui.pill(a, Ui.alpha(Ui.accent(cfg), 0.35f), Ui.accent(cfg), 18));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = u(a, 18);
        body.addView(ok, lp);
        ok.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { d[0].dismiss(); } });
        d[0] = panel(a, cfg, title, body, 900);
        d[0].show();
        ok.requestFocus();
    }

    public static void confirm(Activity a, Config cfg, String title, String message, final Runnable yes) {
        boolean ar = cfg.arabic();
        choose(a, cfg, title + "\n" + message, new String[]{ar ? "نعم" : "Yes", ar ? "لا" : "No"},
                new String[]{"i_check", "i_delete"}, null, 1, new Pick() {
                    @Override public void on(int i) { if (i == 0) yes.run(); }
                });
    }

    /** Vertical spacer. */
    static View gap(Activity a, float ref) {
        View v = new View(a);
        v.setLayoutParams(new LinearLayout.LayoutParams(1, u(a, ref)));
        return v;
    }

    static FrameLayout.LayoutParams match() { return new FrameLayout.LayoutParams(-1, -1); }
}
