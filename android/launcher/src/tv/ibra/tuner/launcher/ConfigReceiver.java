package tv.ibra.tuner.launcher;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Base64;

/**
 * Receives settings from IBRA TV Tuner:
 *   am broadcast -n tv.ibra.tuner.launcher/.ConfigReceiver -a tv.ibra.tuner.APPLY_CONFIG --es cfg BASE64JSON
 * The manifest requires android.permission.DUMP, which adb shell holds and ordinary apps do not.
 * Result code 1 = accepted, 0 = rejected.
 */
public class ConfigReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        int result = 0;
        try {
            if ("tv.ibra.tuner.APPLY_CONFIG".equals(intent.getAction())) {
                String b64 = intent.getStringExtra("cfg");
                if (b64 != null && b64.length() < 16000) {
                    String json = new String(Base64.decode(b64, Base64.DEFAULT), "UTF-8");
                    Config c = Config.parse(json);
                    if (c != null) {
                        c.save(context);
                        result = 1;
                    }
                }
            }
        } catch (Exception ignored) { }
        if (isOrderedBroadcast()) setResultCode(result);
    }
}
