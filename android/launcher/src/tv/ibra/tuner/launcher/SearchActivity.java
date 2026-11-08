package tv.ibra.tuner.launcher;

import android.app.Activity;
import android.content.Intent;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.Locale;

/** Search installed apps and the titles already loaded in the entertainment centre; or search YouTube. */
public class SearchActivity extends Activity {
    private Config cfg;
    private boolean ar;
    private EditText box;
    private LinearLayout results;

    private String s(String a, String e) { return ar ? a : e; }
    private int u(float v) { return Ui.u(this, v); }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        cfg = Config.load(this);
        ar = cfg.arabic();
        FrameLayout root = new FrameLayout(this);
        root.setLayoutDirection(ar ? View.LAYOUT_DIRECTION_RTL : View.LAYOUT_DIRECTION_LTR);
        root.addView(new Ui.Background(this, cfg), Dlg.match());
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(u(260), u(60), u(260), u(30));
        root.addView(col, Dlg.match());

        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(u(28), u(8), u(28), u(8));
        final GradientDrawable g = Ui.pill(this, Ui.glassFill(cfg), Ui.accent(cfg), 40);
        bar.setBackground(g);
        bar.addView(Ui.icon(this, "i_search", Ui.text(cfg), 40));
        box = new EditText(this);
        box.setHint(s("ابحث عن تطبيق أو مسلسل أو فيلم…", "Search apps, series, movies…"));
        box.setSingleLine(true);
        box.setBackground(null);
        box.setTextColor(Ui.text(cfg));
        box.setHintTextColor(Ui.muted(cfg));
        box.setTextSize(TypedValue.COMPLEX_UNIT_PX, u(32));
        box.setPadding(u(18), u(14), u(18), u(14));
        box.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence c, int a, int b2, int d) { }
            @Override public void onTextChanged(CharSequence c, int a, int b2, int d) { search(); }
            @Override public void afterTextChanged(Editable e) { }
        });
        bar.addView(box, new LinearLayout.LayoutParams(0, -2, 1));
        col.addView(bar);

        ScrollView sv = new ScrollView(this);
        sv.setVerticalScrollBarEnabled(false);
        results = new LinearLayout(this);
        results.setOrientation(LinearLayout.VERTICAL);
        results.setPadding(0, u(20), 0, 0);
        sv.addView(results);
        col.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(root);
        box.requestFocus();
        box.postDelayed(new Runnable() {
            @Override public void run() {
                InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
                if (imm != null) imm.showSoftInput(box, InputMethodManager.SHOW_IMPLICIT);
            }
        }, 250);
        search();
    }

    private void search() {
        results.removeAllViews();
        final String q = box.getText().toString().trim();
        String ql = q.toLowerCase(Locale.ROOT);
        if (q.isEmpty()) {
            TextView t = Ui.tv(this, cfg, s("اكتب بالريموت أو لوحة المفاتيح. النتائج: تطبيقاتك + العناوين المحمّلة بالواجهة.",
                    "Type with the remote or keyboard. Results: your apps + titles loaded on the home screen."), 24, false);
            t.setTextColor(Ui.muted(cfg));
            results.addView(t);
            return;
        }
        int n = 0;
        for (final Apps.App a : Apps.all(this)) {
            if (!a.label.toLowerCase(Locale.ROOT).contains(ql)) continue;
            LinearLayout r = Dlg.row(this, cfg, "i_apps", a.label, s("تطبيق", "app"), false);
            r.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { Apps.launch(SearchActivity.this, a); } });
            results.addView(r);
            if (++n >= 12) break;
        }
        int m = 0;
        for (final Content.Item it : Content.allCached(this, cfg)) {
            if (!it.title.toLowerCase(Locale.ROOT).contains(ql)) continue;
            LinearLayout r = Dlg.row(this, cfg, "i_movie", it.title, it.year, false);
            r.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { youtube(it.title + " trailer"); }
            });
            results.addView(r);
            if (++m >= 12) break;
        }
        LinearLayout yt = Dlg.row(this, cfg, "i_trailer", s("ابحث في يوتيوب عن «", "Search YouTube for “") + q + s("»", "”"), null, false);
        yt.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { youtube(q); } });
        results.addView(yt);
    }

    private void youtube(String q) {
        Uri uri = Uri.parse("https://www.youtube.com/results?search_query=" + Feeds.enc(q));
        for (String p : new String[]{"com.google.android.youtube.tv", "com.google.android.youtube"}) {
            try { startActivity(new Intent(Intent.ACTION_VIEW, uri).setPackage(p).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); return; }
            catch (Exception ignored) { }
        }
        try { startActivity(new Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); } catch (Exception ignored) { }
    }

}
