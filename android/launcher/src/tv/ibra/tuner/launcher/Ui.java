package tv.ibra.tuner.launcher;

import android.animation.ValueAnimator;
import android.app.ActivityManager;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.LinearInterpolator;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Shared look: sizes are written in 1920×1080 reference pixels and scaled to the real screen, so the layout is
 * identical on 720p boxes, 1080p and 4K TVs. Glass cards, focus effect, icons and the animated background.
 */
public final class Ui {
    private Ui() { }

    private static float scale = 0;

    public static float scale(Context c) {
        if (scale == 0) {
            DisplayMetrics m = new DisplayMetrics();
            ((WindowManager) c.getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay().getRealMetrics(m);
            int w = Math.max(m.widthPixels, m.heightPixels), h = Math.min(m.widthPixels, m.heightPixels);
            scale = Math.min(w / 1920f, h / 1080f);
            if (scale <= 0) scale = 1;
        }
        return scale;
    }

    /** Reference pixels (1920×1080 design) → real pixels. */
    public static int u(Context c, float px) { return Math.round(px * scale(c)); }

    public static boolean lowEnd(Context c) {
        try {
            ActivityManager am = (ActivityManager) c.getSystemService(Context.ACTIVITY_SERVICE);
            if (am.isLowRamDevice()) return true;
            ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
            am.getMemoryInfo(mi);
            return mi.totalMem > 0 && mi.totalMem < 1600L * 1024 * 1024;
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean effects(Context c, Config cfg) {
        if ("on".equals(cfg.effects)) return true;
        if ("off".equals(cfg.effects)) return false;
        return !lowEnd(c);
    }

    static int alpha(int color, float a) {
        return (Math.round(Math.max(0, Math.min(1, a)) * 255) << 24) | (color & 0x00FFFFFF);
    }

    static int mix(int a, int b, float t) {
        int r = Math.round(Color.red(a) + (Color.red(b) - Color.red(a)) * t);
        int g = Math.round(Color.green(a) + (Color.green(b) - Color.green(a)) * t);
        int bl = Math.round(Color.blue(a) + (Color.blue(b) - Color.blue(a)) * t);
        return Color.rgb(r, g, bl);
    }

    // ------------------------------------------------------------------ colours from config

    public static int text(Config cfg) { return cfg.palette()[3]; }
    public static int muted(Config cfg) { return alpha(cfg.palette()[3], 0.68f); }
    public static int accent(Config cfg) { return cfg.palette()[2]; }

    /** Glass fill: a lighter tint of the background at the configured opacity. */
    public static int glassFill(Config cfg) {
        int[] p = cfg.palette();
        int base = cfg.light() ? Color.WHITE : mix(p[1], Color.WHITE, 0.10f);
        return alpha(base, cfg.cardAlpha / 100f * (cfg.light() ? 1.2f : 1f));
    }

    public static int glassStroke(Config cfg) {
        return cfg.light() ? alpha(Color.WHITE, 0.85f) : alpha(Color.WHITE, 0.14f);
    }

    /** Rounded glass panel with a soft top highlight. */
    public static Drawable glass(Context c, Config cfg, float radius) {
        GradientDrawable base = new GradientDrawable();
        base.setCornerRadius(u(c, radius));
        base.setColor(glassFill(cfg));
        base.setStroke(Math.max(1, u(c, 1.5f)), glassStroke(cfg));
        GradientDrawable shine = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{alpha(Color.WHITE, cfg.light() ? 0.35f : 0.07f), 0x00FFFFFF});
        shine.setCornerRadius(u(c, radius));
        return new LayerDrawable(new Drawable[]{base, shine});
    }

    public static GradientDrawable pill(Context c, int fill, int stroke, float radius) {
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(u(c, radius));
        g.setColor(fill);
        if (stroke != 0) g.setStroke(Math.max(1, u(c, 1.5f)), stroke);
        return g;
    }

    // ------------------------------------------------------------------ views

    public static TextView tv(Context c, Config cfg, String s, float sizeRef, boolean bold) {
        TextView v = new TextView(c);
        v.setText(s);
        v.setTextColor(text(cfg));
        v.setTextSize(TypedValue.COMPLEX_UNIT_PX, u(c, sizeRef * (cfg.largeText ? 1f : 0.9f)));
        v.setTypeface(Typeface.create("sans-serif", bold ? Typeface.BOLD : Typeface.NORMAL));
        v.setIncludeFontPadding(false);
        v.setLineSpacing(0, 1.12f);
        // paragraph direction follows the launcher language, so Latin titles sit on the right in Arabic
        v.setTextDirection(cfg.arabic() ? View.TEXT_DIRECTION_RTL : View.TEXT_DIRECTION_LTR);
        return v;
    }

    public static int drawable(Context c, String name) {
        return c.getResources().getIdentifier(name, "drawable", c.getPackageName());
    }

    public static ImageView icon(Context c, String name, int tint, float sizeRef) {
        ImageView iv = new ImageView(c);
        int id = drawable(c, name);
        if (id != 0) iv.setImageResource(id);
        if (tint != 0) iv.setColorFilter(tint);
        iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
        int s = u(c, sizeRef);
        iv.setLayoutParams(new LinearLayout.LayoutParams(s, s));
        return iv;
    }

    /** Icon + label in a row, e.g. a pill button. */
    public static LinearLayout iconText(Context c, Config cfg, String icon, String label, float textRef, float iconRef, int color) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER);
        if (icon != null) {
            ImageView iv = icon(c, icon, color, iconRef);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(u(c, iconRef), u(c, iconRef));
            lp.setMarginEnd(u(c, 12));
            l.addView(iv, lp);
        }
        TextView t = tv(c, cfg, label, textRef, true);
        t.setTextColor(color);
        l.addView(t);
        return l;
    }

    /**
     * Makes a view focusable with the IBRA focus effect: accent ring, small zoom and (when effects are on) a lift.
     * {@code ring} is the drawable whose stroke is changed; may be null.
     */
    public static void focusable(final Context c, final Config cfg, final View v, final GradientDrawable ring,
                                 final float zoom, final View.OnFocusChangeListener extra) {
        v.setFocusable(true);
        v.setFocusableInTouchMode(true);
        v.setClickable(true);
        final boolean fx = effects(c, cfg);
        final int idle = ring == null ? 0 : glassStroke(cfg);
        v.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override public void onFocusChange(View view, boolean has) {
                float s = has ? zoom : 1f;
                view.animate().scaleX(s).scaleY(s).setDuration(fx ? 180 : 90).start();
                if (fx) view.setElevation(has ? u(c, 18) : 0);
                if (ring != null) ring.setStroke(has ? u(c, 4) : Math.max(1, u(c, 1.5f)), has ? accent(cfg) : idle);
                if (extra != null) extra.onFocusChange(view, has);
            }
        });
    }

    // ------------------------------------------------------------------ background

    /** Theme background: gradient, "horizon" (glow + bright arc like a planet edge) or slow animated lights. */
    public static final class Background extends View {
        private final Config cfg;
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private ValueAnimator anim;
        private float phase = 0;

        public Background(Context c, Config cfg) {
            super(c);
            this.cfg = cfg;
            setLayerType(View.LAYER_TYPE_HARDWARE, null);
        }

        public void start() {
            if (!"animated".equals(cfg.background) || !effects(getContext(), cfg) || anim != null) return;
            anim = ValueAnimator.ofFloat(0, 1);
            anim.setDuration(24000);
            anim.setRepeatCount(ValueAnimator.INFINITE);
            anim.setInterpolator(new LinearInterpolator());
            anim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                long last = 0;
                @Override public void onAnimationUpdate(ValueAnimator a) {
                    long now = System.currentTimeMillis();
                    if (now - last < 50) return;              // ~20 fps is plenty for slow lights
                    last = now;
                    phase = (Float) a.getAnimatedValue();
                    invalidate();
                }
            });
            anim.start();
        }

        public void stop() {
            if (anim != null) { anim.cancel(); anim = null; }
        }

        @Override protected void onDetachedFromWindow() { stop(); super.onDetachedFromWindow(); }

        @Override protected void onDraw(Canvas cv) {
            int w = getWidth(), h = getHeight();
            int[] pal = cfg.palette();
            p.setShader(new LinearGradient(0, 0, w * 0.4f, h, pal[0], pal[1], Shader.TileMode.CLAMP));
            cv.drawRect(0, 0, w, h, p);
            int glow = pal[4];
            float a = cfg.light() ? 0.25f : 0.35f;
            if ("gradient".equals(cfg.background)) {
                blob(cv, w * 0.78f, h * 0.05f, h * 0.7f, alpha(glow, a * 0.6f));
                return;
            }
            if ("animated".equals(cfg.background)) {
                double t = phase * Math.PI * 2;
                blob(cv, (float) (w * (0.25 + 0.15 * Math.sin(t))), (float) (h * (0.30 + 0.12 * Math.cos(t))), h * 0.65f, alpha(glow, a));
                blob(cv, (float) (w * (0.75 + 0.12 * Math.cos(t * 0.7))), (float) (h * (0.20 + 0.10 * Math.sin(t * 0.7))), h * 0.55f, alpha(pal[2], a * 0.7f));
                blob(cv, (float) (w * (0.55 + 0.20 * Math.sin(t * 0.5 + 1))), (float) (h * (0.85 + 0.05 * Math.cos(t))), h * 0.6f, alpha(glow, a * 0.8f));
                return;
            }
            // horizon
            blob(cv, w * 0.80f, -h * 0.05f, h * 0.75f, alpha(glow, a * 0.8f));
            blob(cv, w * 0.12f, h * 0.10f, h * 0.55f, alpha(pal[2], a * 0.45f));
            float cx = w / 2f, r = w * 1.25f, top = h * 0.80f;
            RectF arc = new RectF(cx - r, top, cx + r, top + 2 * r);
            p.setShader(new RadialGradient(cx, top + r, r * 1.04f,
                    new int[]{0x00000000, 0x00000000, alpha(glow, a * 0.9f), 0x00000000},
                    new float[]{0f, 0.955f, 0.99f, 1f}, Shader.TileMode.CLAMP));
            cv.drawOval(new RectF(cx - r * 1.05f, top - r * 0.05f, cx + r * 1.05f, top + 2.05f * r), p);
            p.setShader(null);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(Math.max(2, h / 360f));
            p.setColor(alpha(Color.WHITE, cfg.light() ? 0.6f : 0.45f));
            cv.drawArc(arc, 200, 140, false, p);
            p.setStyle(Paint.Style.FILL);
            p.setColor(alpha(pal[0], 0.55f));
            cv.drawOval(arc, p);
        }

        private void blob(Canvas cv, float x, float y, float r, int color) {
            p.setShader(new RadialGradient(x, y, r, color, color & 0x00FFFFFF, Shader.TileMode.CLAMP));
            cv.drawCircle(x, y, r, p);
            p.setShader(null);
        }
    }
}
