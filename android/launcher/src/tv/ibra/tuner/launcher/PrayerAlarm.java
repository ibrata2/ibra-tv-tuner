package tv.ibra.tuner.launcher;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import java.util.TimeZone;

/**
 * Optional prayer alert. One alarm for the next prayer; when it fires the alert screen opens (and plays the
 * tone or the adhan file), then the following prayer is scheduled. No notifications or foreground service.
 */
public final class PrayerAlarm extends BroadcastReceiver {
    static final String ACTION = "tv.ibra.tuner.launcher.PRAYER";
    static final String SHOWN = "alert_shown_at";

    static PendingIntent pi(Context c) {
        Intent i = new Intent(c, PrayerAlarm.class).setAction(ACTION);
        return PendingIntent.getBroadcast(c, 7, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    /** (Re)schedules the next alert, or cancels it when alerts are off. */
    public static void schedule(Context c) {
        try {
            AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
            if (am == null) return;
            Config cfg = Config.load(c);
            am.cancel(pi(c));
            if ("off".equals(cfg.prayerAlert) || !cfg.showPrayer) return;
            TimeZone tz = TimeZone.getTimeZone(cfg.tz);
            Prayer.Next n = Prayer.next(System.currentTimeMillis() + 30000, cfg.lat, cfg.lon, tz, cfg.prayerMethod, cfg.prayerOffsets, cfg.prayerCombined);
            if (n == null) return;
            c.getSharedPreferences("ibra_alarm", Context.MODE_PRIVATE).edit().putInt("index", n.index).putLong("at", n.atMillis).apply();
            if (Build.VERSION.SDK_INT >= 31) am.setWindow(AlarmManager.RTC_WAKEUP, n.atMillis, 60000, pi(c));   // no exact-alarm permission needed
            else if (Build.VERSION.SDK_INT >= 23) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, n.atMillis, pi(c));
            else am.setExact(AlarmManager.RTC_WAKEUP, n.atMillis, pi(c));
        } catch (Exception ignored) { }
    }

    @Override
    public void onReceive(final Context c, Intent intent) {
        if (!ACTION.equals(intent.getAction())) { schedule(c); return; }   // boot / time changes
        final int index = c.getSharedPreferences("ibra_alarm", Context.MODE_PRIVATE).getInt("index", Prayer.MAGHRIB);
        final long started = System.currentTimeMillis();
        try {
            c.startActivity(new Intent(c, PrayerAlertActivity.class).putExtra("index", index)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP));
        } catch (Exception ignored) { }
        schedule(c);
        // Android 10+ may block the alert screen while another app is open: then play a short tone instead.
        final PendingResult pr = goAsync();
        new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
            @Override public void run() {
                try {
                    long shown = c.getSharedPreferences("ibra_alarm", Context.MODE_PRIVATE).getLong(SHOWN, 0);
                    if (shown < started) {
                        Config cfg = Config.load(c);
                        tone(c);
                        Toast.makeText(c, (cfg.arabic() ? "حان موعد صلاة " : "Time for ") + Prayer.name(index, cfg.arabic(), cfg.prayerCombined),
                                Toast.LENGTH_LONG).show();
                    }
                } catch (Exception ignored) {
                } finally { pr.finish(); }
            }
        }, 2500);
    }

    static Ringtone tone(Context c) {
        try {
            Ringtone r = RingtoneManager.getRingtone(c, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION));
            if (r == null) return null;
            r.setStreamType(AudioManager.STREAM_MUSIC);
            r.play();
            return r;
        } catch (Exception e) {
            return null;
        }
    }
}
