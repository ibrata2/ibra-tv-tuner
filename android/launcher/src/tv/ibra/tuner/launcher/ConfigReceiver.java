package tv.ibra.tuner.launcher;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Base64;

/**
 * Receives settings from IBRA TV Tuner:
 *   am broadcast -n tv.ibra.tuner.launcher/.ConfigReceiver -a tv.ibra.tuner.APPLY_CONFIG --es cfg BASE64JSON [--es open settings|apps|search|home]
 * Only the keys present in the JSON change (merge).
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
                    // Merge: only the keys that were sent change; everything set on the TV stays.
                    Config c = Config.load(context).apply(new org.json.JSONObject(json));
                    c.save(context);
                    result = 1;
                    open(context, intent.getStringExtra("open"));
                }
            }
        } catch (Exception ignored) { }
        if (isOrderedBroadcast()) setResultCode(result);
    }

    /** Optional: open a screen on the TV (used by the PC app and the screenshot test). */
    static void open(Context c, String what) {
        if (what == null) return;
        Class<?> cls = "settings".equals(what) ? SettingsActivity.class : "apps".equals(what) ? AllAppsActivity.class
                : "search".equals(what) ? SearchActivity.class : "home".equals(what) ? HomeActivity.class : null;
        if (cls == null) return;
        try { c.startActivity(new Intent(c, cls).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); } catch (Exception ignored) { }
    }
}
