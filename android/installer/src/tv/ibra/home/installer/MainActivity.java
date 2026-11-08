package tv.ibra.home.installer;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.AssetFileDescriptor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.InputStream;
import java.util.List;

/**
 * IBRA TV HOME installer (phone app). Connects to the TV over Wi-Fi with ADB (port 5555),
 * installs the IBRA TV HOME launcher it carries inside, makes it the Home app, and can bring the
 * original Home back. Arabic, black & gold.
 */
public class MainActivity extends Activity {

    static final int GOLD = Color.parseColor("#C9A84C");
    static final int GOLD_SOFT = Color.parseColor("#33C9A84C");
    static final int BG1 = Color.parseColor("#0A0A0A");
    static final int BG2 = Color.parseColor("#1C1608");
    static final int TEXT = Color.parseColor("#F5F0E1");
    static final int OK = Color.parseColor("#7BD88F");
    static final int BAD = Color.parseColor("#FF7A7A");

    private final Handler ui = new Handler(Looper.getMainLooper());
    private SharedPreferences prefs;
    private EditText ipBox, portBox;
    private LinearLayout found, logBox;
    private TextView status;
    private CheckBox disableStock;
    private Button btnInstall, btnRestore, btnRemove, btnScan;
    private volatile boolean busy;

    private int dp(float v) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics());
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = getSharedPreferences("tvs", MODE_PRIVATE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        if (Build.VERSION.SDK_INT >= 21) getWindow().setStatusBarColor(BG1);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{BG1, BG2}));
        scroll.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(18), dp(22), dp(18), dp(28));
        scroll.addView(col);

        TextView title = text("IBRA TV HOME", 34, true);
        title.setTextColor(GOLD);
        title.setGravity(Gravity.CENTER);
        title.setLetterSpacing(0.1f);
        col.addView(title);
        TextView sub = text("مثبّت الواجهة على التلفزيون والبوكس", 15, false);
        sub.setGravity(Gravity.CENTER);
        sub.setAlpha(0.8f);
        col.addView(sub);

        updateBox = new LinearLayout(this);
        updateBox.setOrientation(LinearLayout.VERTICAL);
        col.addView(updateBox);

        // 1 — prepare the TV
        LinearLayout c1 = card(col, "١. جهّز التلفزيون (مرة وحدة)");
        String[] steps = {
                "الإعدادات ← تفضيلات الجهاز ← لمحة (حول).",
                "اضغط على «الإصدار / Build» ٧ مرات لحد ما يطلع «أصبحت مطوّراً».",
                "ارجع ← «خيارات مطوّر البرامج» ← فعّل «تصحيح USB» و «تصحيح ADB عبر الشبكة» إذا موجود.",
                "خلي التلفزيون والموبايل على نفس الواي فاي.",
                "عنوان IP التلفزيون تلگاه بإعدادات الشبكة، أو اضغط «ابحث بالشبكة» تحت.",
        };
        for (int i = 0; i < steps.length; i++) {
            TextView t = text("•  " + steps[i], 14, false);
            t.setPadding(0, dp(3), 0, dp(3));
            c1.addView(t);
        }

        // 2 — the TV
        LinearLayout c2 = card(col, "٢. اختار التلفزيون");
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        ipBox = input("IP مثل 192.168.1.20", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        ipBox.setText(prefs.getString("last_ip", ""));
        row.addView(ipBox, new LinearLayout.LayoutParams(0, -2, 3));
        portBox = input("المنفذ", InputType.TYPE_CLASS_NUMBER);
        portBox.setText(prefs.getString("last_port", "5555"));
        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(0, -2, 1);
        plp.setMarginStart(dp(8));
        row.addView(portBox, plp);
        c2.addView(row);
        btnScan = button("🔍  ابحث بالشبكة", false);
        btnScan.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { scan(); } });
        c2.addView(btnScan, lp(dp(10)));
        found = new LinearLayout(this);
        found.setOrientation(LinearLayout.VERTICAL);
        c2.addView(found);

        // 3 — actions
        LinearLayout c3 = card(col, "٣. ثبّت");
        disableStock = new CheckBox(this);
        disableStock.setText("إذا الجهاز يرجع للواجهة القديمة بنفسه (مثل Google TV): عطّل الواجهة الأصلية مؤقتاً. ترجع بزر الاسترجاع.");
        disableStock.setTextColor(TEXT);
        disableStock.setTextSize(13);
        disableStock.setChecked(prefs.getBoolean("disable_stock", false));
        c3.addView(disableStock);
        btnInstall = button("ثبّت IBRA TV HOME واجعلها الرئيسية", true);
        btnInstall.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { run(Action.INSTALL); } });
        c3.addView(btnInstall, lp(dp(12)));
        btnRestore = button("رجّع الواجهة الأصلية", false);
        btnRestore.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { run(Action.RESTORE); } });
        c3.addView(btnRestore, lp(dp(10)));
        btnRemove = button("رجّع الأصلية واحذف IBRA TV HOME", false);
        btnRemove.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { run(Action.REMOVE); } });
        c3.addView(btnRemove, lp(dp(10)));

        status = text("", 15, true);
        status.setPadding(0, dp(14), 0, dp(4));
        c3.addView(status);
        logBox = new LinearLayout(this);
        logBox.setOrientation(LinearLayout.VERTICAL);
        c3.addView(logBox);

        TextView credit = text("تصميم وتطوير إبراهيم الخزرجي", 13, false);
        credit.setGravity(Gravity.CENTER);
        credit.setAlpha(0.6f);
        credit.setPadding(0, dp(22), 0, 0);
        col.addView(credit);
        TextView tg = text("t.me/ibraprogram", 13, true);
        tg.setTextColor(GOLD);
        tg.setGravity(Gravity.CENTER);
        tg.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/ibraprogram"))); } catch (Exception ignored) { }
            }
        });
        col.addView(tg);

        setContentView(scroll);
        checkSelfUpdate();
    }

    // ------------------------------------------------------------------ self-update

    private LinearLayout updateBox;

    private void checkSelfUpdate() {
        tv.ibra.common.Updater.setLanguage(this, true);
        if (!tv.ibra.common.Updater.due(this, 6 * 3600 * 1000L) && !tv.ibra.common.Updater.available(this)) return;
        new Thread(new Runnable() {
            @Override public void run() {
                try {
                    final tv.ibra.common.Updater.Info i = tv.ibra.common.Updater.check(getApplicationContext(), "installer");
                    if (i != null) ui.post(new Runnable() { @Override public void run() { showUpdate(i); } });
                } catch (Exception ignored) { }
            }
        }, "update").start();
    }

    private void showUpdate(final tv.ibra.common.Updater.Info i) {
        updateBox.removeAllViews();
        LinearLayout c = card(updateBox, "⬆ تحديث جديد: " + i.versionName);
        if (!i.notesAr.isEmpty()) c.addView(text(i.notesAr, 14, false));
        if (!i.sameKey) {
            c.addView(text("هذه النسخة موقّعة بمفتاح جديد: احذف المثبّت الحالي ونزّل الجديد من صفحة الإصدارات.", 14, false));
            Button open = button("افتح صفحة الإصدارات", false);
            open.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(tv.ibra.common.Updater.RELEASES))); } catch (Exception ignored) { }
                }
            });
            c.addView(open, lp(dp(10)));
            return;
        }
        final Button go = button("حدّث المثبّت الآن (والواجهة الجديدة معه)", true);
        go.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (!tv.ibra.common.Updater.canInstall(MainActivity.this)) {
                    setStatus("اسمح للمثبّت بتثبيت التطبيقات ثم ارجع واضغط تحديث.");
                    tv.ibra.common.Updater.openInstallPermission(MainActivity.this);
                    return;
                }
                go.setEnabled(false);
                new Thread(new Runnable() {
                    @Override public void run() {
                        try {
                            java.io.File apk = tv.ibra.common.Updater.download(getApplicationContext(), i, new tv.ibra.common.Updater.Progress() {
                                int last = -1;
                                @Override public void on(long d, long t) {
                                    final int pct = t > 0 ? (int) (d * 100 / t) : 0;
                                    if (pct != last) { last = pct; ui.post(new Runnable() { @Override public void run() { go.setText("ينزل التحديث… " + pct + "٪"); } }); }
                                }
                            });
                            tv.ibra.common.Updater.install(getApplicationContext(), apk);
                        } catch (final Exception e) {
                            ui.post(new Runnable() { @Override public void run() { go.setEnabled(true); go.setText("أعد المحاولة"); setStatus("فشل التحديث: " + e.getMessage()); } });
                        }
                    }
                }, "update").start();
            }
        });
        c.addView(go, lp(dp(10)));
    }

    // ------------------------------------------------------------------ views

    private TextView text(String s, float sp, boolean bold) {
        TextView v = new TextView(this);
        v.setText(s);
        v.setTextColor(TEXT);
        v.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        if (bold) v.setTypeface(Typeface.DEFAULT_BOLD);
        v.setLineSpacing(0, 1.15f);
        return v;
    }

    private LinearLayout card(LinearLayout parent, String heading) {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(dp(16), dp(14), dp(16), dp(16));
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(dp(18));
        g.setColor(0x1AFFFFFF);
        g.setStroke(dp(1), GOLD_SOFT);
        c.setBackground(g);
        TextView h = text(heading, 17, true);
        h.setTextColor(GOLD);
        h.setPadding(0, 0, 0, dp(8));
        c.addView(h);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = dp(16);
        parent.addView(c, lp);
        return c;
    }

    private EditText input(String hint, int type) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setHintTextColor(0x88F5F0E1);
        e.setTextColor(TEXT);
        e.setInputType(type);
        e.setSingleLine(true);
        e.setTextDirection(View.TEXT_DIRECTION_LTR);
        e.setPadding(dp(12), dp(10), dp(12), dp(10));
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(dp(12));
        g.setColor(0x26FFFFFF);
        g.setStroke(dp(1), GOLD_SOFT);
        e.setBackground(g);
        return e;
    }

    private Button button(String label, boolean primary) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextSize(15);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setTextColor(primary ? BG1 : GOLD);
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(dp(14));
        g.setColor(primary ? GOLD : 0x00000000);
        g.setStroke(dp(primary ? 0 : 2), GOLD);
        b.setBackground(g);
        b.setPadding(dp(12), dp(12), dp(12), dp(12));
        return b;
    }

    private LinearLayout.LayoutParams lp(int top) {
        LinearLayout.LayoutParams l = new LinearLayout.LayoutParams(-1, -2);
        l.topMargin = top;
        return l;
    }

    private void setBusy(final boolean b) {
        busy = b;
        ui.post(new Runnable() { @Override public void run() {
            for (Button x : new Button[]{btnInstall, btnRestore, btnRemove, btnScan}) {
                x.setEnabled(!b);
                x.setAlpha(b ? 0.5f : 1f);
            }
        }});
    }

    private void setStatus(final String s) {
        ui.post(new Runnable() { @Override public void run() { status.setText(s); } });
    }

    private void addLog(final boolean ok, final String s, final boolean neutral) {
        ui.post(new Runnable() { @Override public void run() {
            TextView t = text((neutral ? "•  " : ok ? "✓  " : "✗  ") + s, 14, false);
            t.setTextColor(neutral ? TEXT : ok ? OK : BAD);
            t.setPadding(0, dp(3), 0, dp(3));
            logBox.addView(t);
        }});
    }

    // ------------------------------------------------------------------ scan

    private void scan() {
        if (busy) return;
        setBusy(true);
        found.removeAllViews();
        setStatus("ندوّر على أجهزة بالشبكة…");
        new Thread(new Runnable() {
            @Override public void run() {
                String own = Scanner.localIpv4();
                final List<String> ips = Scanner.scan(own, port(), 600);
                ui.post(new Runnable() { @Override public void run() {
                    if (ips.isEmpty()) {
                        status.setText(own == null ? "الموبايل مو متصل بالواي فاي." : "ما لگينا جهاز فاتح ADB. تأكد من الخطوة ١، أو اكتب الـ IP بيدك.");
                    } else {
                        status.setText("لگينا " + ips.size() + ". اختار التلفزيون:");
                        for (final String ip : ips) {
                            Button b = button("📺  " + ip, false);
                            b.setOnClickListener(new View.OnClickListener() {
                                @Override public void onClick(View v) { ipBox.setText(ip); status.setText("اخترت " + ip); }
                            });
                            found.addView(b, lp(dp(8)));
                        }
                    }
                }});
                setBusy(false);
            }
        }, "scan").start();
    }

    private int port() {
        try {
            int p = Integer.parseInt(portBox.getText().toString().trim());
            return p > 0 && p < 65536 ? p : 5555;
        } catch (Exception e) { return 5555; }
    }

    // ------------------------------------------------------------------ install / restore

    enum Action { INSTALL, RESTORE, REMOVE }

    private void run(final Action action) {
        if (busy) return;
        final String ip = ipBox.getText().toString().trim();
        if (!ip.matches("^(\\d{1,3}\\.){3}\\d{1,3}$")) { setStatus("اكتب IP التلفزيون أول (مثل 192.168.1.20)."); return; }
        final int port = port();
        final boolean disable = disableStock.isChecked();
        prefs.edit().putString("last_ip", ip).putString("last_port", String.valueOf(port)).putBoolean("disable_stock", disable).apply();
        logBox.removeAllViews();
        setBusy(true);
        setStatus("نتصل بالتلفزيون…");

        new Thread(new Runnable() {
            @Override public void run() {
                AdbClient adb = null;
                try {
                    AdbClient.Key key = AdbClient.Key.loadOrCreate(getFilesDir());
                    adb = AdbClient.connect(ip, port, key, "IBRA-HOME-Installer@" + Build.MODEL.replace(' ', '_'), 90000,
                            new AdbClient.Listener() {
                                @Override public void onAllowPrompt() {
                                    setStatus("📺 على التلفزيون: اضغط «سماح» (وفعّل «السماح دائماً من هذا الجهاز»)");
                                }
                            });
                    TvInstaller.Store store = new TvInstaller.Store() {
                        @Override public String get(String k) { return prefs.getString(k, null); }
                        @Override public void put(String k, String v) { prefs.edit().putString(k, v).apply(); }
                    };
                    TvInstaller.Log log = new TvInstaller.Log() {
                        @Override public void step(boolean ok, String s) { addLog(ok, s, false); }
                        @Override public void info(String s) { addLog(true, s, true); }
                    };
                    TvInstaller tv = new TvInstaller(adb, ip, store, log);
                    tv.probe();

                    if (action == Action.INSTALL) {
                        setStatus("نرفع IBRA TV HOME…");
                        AssetFileDescriptor fd = null;
                        long size;
                        InputStream in;
                        try {
                            fd = getAssets().openFd("ibra-home.apk");
                            size = fd.getLength();
                            in = fd.createInputStream();
                        } catch (Exception e) {
                            InputStream raw = getAssets().open("ibra-home.apk");
                            java.io.ByteArrayOutputStream all = new java.io.ByteArrayOutputStream();
                            byte[] buf = new byte[16384];
                            int n;
                            while ((n = raw.read(buf)) > 0) all.write(buf, 0, n);
                            raw.close();
                            size = all.size();
                            in = new java.io.ByteArrayInputStream(all.toByteArray());
                        }
                        try {
                            boolean ok = tv.install(in, size, new AdbClient.Progress() {
                                int last = -1;
                                @Override public void onProgress(long s, long t) {
                                    int pct = t > 0 ? (int) (s * 100 / t) : 0;
                                    if (pct != last) { last = pct; setStatus("نرفع IBRA TV HOME… " + pct + "٪"); }
                                }
                            });
                            if (!ok) { setStatus("ما تم التثبيت ✗"); return; }
                        } finally {
                            try { in.close(); } catch (Exception ignored) { }
                            if (fd != null) try { fd.close(); } catch (Exception ignored) { }
                        }
                        setStatus("نخليها الواجهة الرئيسية…");
                        boolean home = tv.setAsHome(disable);
                        setStatus(home ? "خلصنا ✓ شوف التلفزيون" : "انثبتت، بس اختارها يدوياً من زر Home");
                    } else {
                        setStatus("نرجّع الواجهة الأصلية…");
                        boolean ok = tv.restore(action == Action.REMOVE);
                        setStatus(ok ? "رجعت الواجهة الأصلية ✓" : "اضغط Home على الريموت واختار الأصلية");
                    }
                } catch (AdbClient.AdbException e) {
                    setStatus("✗ " + explain(e.code));
                    addLog(false, e.getMessage(), true);
                } catch (Exception e) {
                    setStatus("✗ صار خطأ: " + e.getMessage());
                } finally {
                    if (adb != null) adb.close();
                    setBusy(false);
                }
            }
        }, "install").start();
    }

    static String explain(String code) {
        if ("unreachable".equals(code)) return "ما وصلنا للتلفزيون. تأكد إنه على نفس الواي فاي، والـ IP صحيح، و«تصحيح ADB عبر الشبكة» أو «تصحيح USB» مفعّل.";
        if ("not_allowed".equals(code)) return "التلفزيون ما سمح. أعد المحاولة واضغط «سماح» على التلفزيون (ويفضّل «السماح دائماً»).";
        if ("tls".equals(code)) return "هذا منفذ «تصحيح الأخطاء اللاسلكي» ويحتاج اقتران. استعمل المنفذ 5555 بعد تفعيل «ADB عبر الشبكة»، أو برنامج IBRA TV Tuner على الحاسبة.";
        if ("refused".equals(code)) return "التلفزيون رفض الأمر. أعد تشغيل التلفزيون وجرّب مرة ثانية.";
        return "انقطع الاتصال. أعد المحاولة.";
    }
}
