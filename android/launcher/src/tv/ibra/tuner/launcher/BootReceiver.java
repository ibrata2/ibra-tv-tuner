package tv.ibra.tuner.launcher;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Reschedules the optional prayer alert after boot or a clock / timezone change. */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent i) {
        String a = i == null ? null : i.getAction();
        if (Intent.ACTION_BOOT_COMPLETED.equals(a) || Intent.ACTION_TIME_CHANGED.equals(a) || Intent.ACTION_TIMEZONE_CHANGED.equals(a))
            PrayerAlarm.schedule(c);
    }
}
