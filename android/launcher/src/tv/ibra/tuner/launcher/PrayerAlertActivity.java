package tv.ibra.tuner.launcher;

import android.app.Activity;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.Ringtone;
import android.os.Bundle;
import android.os.Handler;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.File;
import java.util.Calendar;
import java.util.TimeZone;

/**
 * "It is time for Maghrib" screen. Plays adhan.mp3 from the launcher's files folder when the user chose adhan
 * (the app ships no recording), otherwise the notification tone. OK / BACK closes it; it closes itself too.
 */
public class PrayerAlertActivity extends Activity {
    private MediaPlayer player;
    private Ringtone ring;
    private final Handler h = new Handler();

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        getSharedPreferences("ibra_alarm", MODE_PRIVATE).edit().putLong(PrayerAlarm.SHOWN, System.currentTimeMillis()).apply();
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
                | WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED);
        Config cfg = Config.load(this);
        boolean ar = cfg.arabic();
        int index = getIntent().getIntExtra("index", Prayer.MAGHRIB);

        FrameLayout root = new FrameLayout(this);
        root.setLayoutDirection(ar ? View.LAYOUT_DIRECTION_RTL : View.LAYOUT_DIRECTION_LTR);
        root.setBackgroundColor(0xCC000000);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER_HORIZONTAL);
        box.setPadding(Ui.u(this, 70), Ui.u(this, 50), Ui.u(this, 70), Ui.u(this, 50));
        box.setBackground(Dlg.panelBg(this, cfg));
        box.addView(Ui.icon(this, "i_mosque", Ui.accent(cfg), 150));
        TextView t = Ui.tv(this, cfg, (ar ? "حان الآن موعد صلاة " : "It is time for ") + Prayer.name(index, ar, cfg.prayerCombined), 54, true);
        t.setGravity(Gravity.CENTER);
        t.setPadding(0, Ui.u(this, 26), 0, Ui.u(this, 10));
        box.addView(t);
        TimeZone tz = TimeZone.getTimeZone(cfg.tz);
        Calendar now = Calendar.getInstance(tz);
        TextView sub = Ui.tv(this, cfg, String.format(java.util.Locale.US, "%02d:%02d", now.get(Calendar.HOUR_OF_DAY), now.get(Calendar.MINUTE))
                + "  ·  " + cfg.city + "  ·  " + Prayer.hijriText(now, cfg.hijriOffset, ar), 28, false);
        sub.setTextColor(Ui.muted(cfg));
        box.addView(sub);
        LinearLayout ok = Dlg.row(this, cfg, "i_check", ar ? "إيقاف" : "Stop", null, false);
        ok.setGravity(Gravity.CENTER);
        ok.setBackground(Ui.pill(this, Ui.alpha(Ui.accent(cfg), 0.35f), Ui.accent(cfg), 20));
        LinearLayout.LayoutParams ol = new LinearLayout.LayoutParams(Ui.u(this, 320), -2);
        ol.topMargin = Ui.u(this, 34);
        box.addView(ok, ol);
        ok.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { finish(); } });
        root.addView(box, new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER));
        setContentView(root);
        ok.requestFocus();

        File adhan = getExternalFilesDir(null) == null ? null : new File(getExternalFilesDir(null), "adhan.mp3");
        if ("adhan".equals(cfg.prayerAlert) && adhan != null && adhan.isFile()) {
            try {
                player = new MediaPlayer();
                player.setAudioStreamType(AudioManager.STREAM_MUSIC);
                player.setDataSource(adhan.getPath());
                player.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                    @Override public void onCompletion(MediaPlayer mp) { h.postDelayed(close, 15000); }
                });
                player.prepare();
                player.start();
            } catch (Exception e) {
                player = null;
            }
        }
        if (player == null) {
            ring = PrayerAlarm.tone(this);
            h.postDelayed(close, 60000);
        }
    }

    private final Runnable close = new Runnable() { @Override public void run() { finish(); } };

    @Override
    protected void onDestroy() {
        h.removeCallbacks(close);
        try { if (player != null) { player.stop(); player.release(); } } catch (Exception ignored) { }
        try { if (ring != null) ring.stop(); } catch (Exception ignored) { }
        super.onDestroy();
    }

}
