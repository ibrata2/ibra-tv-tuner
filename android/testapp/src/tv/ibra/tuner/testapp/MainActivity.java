package tv.ibra.tuner.testapp;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.TextView;

/** Shows one line of text. Exists only so IBRA TV Tuner can test disable/enable safely. */
public class MainActivity extends Activity {
    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        TextView t = new TextView(this);
        t.setText("IBRA TV Tuner — test app\nتطبيق اختباري، آمن للحذف");
        t.setTextSize(32);
        t.setTextColor(Color.WHITE);
        t.setGravity(Gravity.CENTER);
        t.setBackgroundColor(Color.rgb(12, 18, 32));
        setContentView(t);
    }
}
