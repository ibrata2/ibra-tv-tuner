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

    // ------------------------------------------------------------------ style (set once per screen build)

    /** Corner multiplier from the chosen shape: round 1, soft .5, sharp .12, pill = capsule. */
    static float rMul = 1f;
    static String focusStyle = "zoom";

    /** Call at the start of every screen build so shapes, focus and fonts follow the settings. */
    public static void apply(Config cfg) {
        rMul = "soft".equals(cfg.corners) ? 0.5f : "sharp".equals(cfg.corners) ? 0.12f : "pill".equals(cfg.corners) ? 3f : 1f;
        focusStyle = cfg.focusStyle;
    }

    static float r(float radius) { return radius * rMul; }

    /** Card fill for the chosen surface: glass (tinted, see-through), solid, outline (almost empty) or clear. */
    public static int glassFill(Config cfg) {
        int[] p = cfg.palette();
        if ("clear".equals(cfg.surface)) return 0x00000000;
        if ("outline".equals(cfg.surface)) return alpha(cfg.light() ? Color.WHITE : p[0], 0.18f);
        if ("solid".equals(cfg.surface)) return alpha(cfg.light() ? Color.WHITE : mix(p[1], Color.WHITE, 0.08f), 0.97f);
        int base = cfg.light() ? Color.WHITE : mix(p[1], Color.WHITE, 0.10f);
        return alpha(base, cfg.cardAlpha / 100f * (cfg.light() ? 1.2f : 1f));
    }

    public static int glassStroke(Config cfg) {
        if ("clear".equals(cfg.surface)) return 0x00000000;
        if ("outline".equals(cfg.surface)) return alpha(cfg.palette()[2], 0.75f);
        if ("solid".equals(cfg.surface)) return cfg.light() ? alpha(Color.BLACK, 0.08f) : alpha(Color.WHITE, 0.07f);
        return cfg.light() ? alpha(Color.WHITE, 0.85f) : alpha(Color.WHITE, 0.14f);
    }

    /** Card panel in the chosen surface; glass gets a soft top highlight. */
    public static Drawable glass(Context c, Config cfg, float radius) {
        GradientDrawable base = new GradientDrawable();
        base.setCornerRadius(u(c, r(radius)));
        base.setColor(glassFill(cfg));
        int st = glassStroke(cfg);
        base.setStroke(st == 0 ? 0 : Math.max(1, u(c, "outline".equals(cfg.surface) ? 2.5f : 1.5f)), st);
        boolean shiny = "glass".equals(cfg.surface);
        GradientDrawable shine = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{alpha(Color.WHITE, shiny ? (cfg.light() ? 0.35f : 0.07f) : 0f), 0x00FFFFFF});
        shine.setCornerRadius(u(c, r(radius)));
        return new LayerDrawable(new Drawable[]{base, shine});
    }

    public static GradientDrawable pill(Context c, int fill, int stroke, float radius) {
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(u(c, r(radius)));
        g.setColor(fill);
        if (stroke != 0) g.setStroke(Math.max(1, u(c, 1.5f)), stroke);
        return g;
    }

    // ------------------------------------------------------------------ views

    public static TextView tv(Context c, Config cfg, String s, float sizeRef, boolean bold) {
        TextView v = new TextView(c);
        v.setText(s);
        v.setTextColor(text(cfg));
        v.setTextSize(TypedValue.COMPLEX_UNIT_PX, u(c, sizeRef * (cfg.largeText ? 1f : 0.9f) * fontScale(cfg)));
        v.setTypeface(typeface(c, cfg, bold));
        v.setIncludeFontPadding(false);
        v.setLineSpacing(0, 1.12f);
        // paragraph direction follows the launcher language, so Latin titles sit on the right in Arabic
        v.setTextDirection(cfg.arabic() ? View.TEXT_DIRECTION_RTL : View.TEXT_DIRECTION_LTR);
        return v;
    }

    /** Cairo and Almarai have taller lines than the system font; scale them so cards keep the same height. */
    static float fontScale(Config cfg) {
        if ("cairo".equals(cfg.font)) return 0.86f;
        if ("almarai".equals(cfg.font)) return 0.92f;
        if ("reemkufi".equals(cfg.font)) return 0.95f;
        return 1f;
    }

    private static final java.util.Map<String, Typeface> fonts = new java.util.HashMap<String, Typeface>();

    /** Bundled Arabic fonts (OFL) or the system font. */
    public static Typeface typeface(Context c, Config cfg, boolean bold) {
        if ("system".equals(cfg.font)) return Typeface.create("sans-serif", bold ? Typeface.BOLD : Typeface.NORMAL);
        String key = cfg.font + (bold ? "-bold" : "-regular");
        Typeface t = fonts.get(key);
        if (t == null) {
            try { t = Typeface.createFromAsset(c.getAssets(), "fonts/" + key + ".ttf"); }
            catch (Exception e) { t = Typeface.create("sans-serif", bold ? Typeface.BOLD : Typeface.NORMAL); }
            fonts.put(key, t);
        }
        return t;
    }

    /** Average colour of an app icon, for the colourful tile style. */
    public static int iconColor(Drawable d, int fallback) {
        if (d == null) return fallback;
        try {
            android.graphics.Bitmap b = android.graphics.Bitmap.createBitmap(12, 12, android.graphics.Bitmap.Config.ARGB_8888);
            android.graphics.Canvas cv = new android.graphics.Canvas(b);
            d.setBounds(0, 0, 12, 12);
            d.draw(cv);
            long r = 0, g = 0, bl = 0, n = 0;
            for (int y = 0; y < 12; y++) for (int x = 0; x < 12; x++) {
                int px = b.getPixel(x, y);
                if (Color.alpha(px) < 128) continue;
                float[] hsv = new float[3];
                Color.colorToHSV(px, hsv);
                if (hsv[1] < 0.25f || hsv[2] < 0.2f) continue;      // skip greys, whites and blacks
                r += Color.red(px); g += Color.green(px); bl += Color.blue(px); n++;
            }
            b.recycle();
            if (n == 0) return fallback;
            int c = Color.rgb((int) (r / n), (int) (g / n), (int) (bl / n));
            float[] hsv = new float[3];
            Color.colorToHSV(c, hsv);
            hsv[1] = Math.min(1f, hsv[1] * 1.1f);
            hsv[2] = Math.min(0.78f, Math.max(0.45f, hsv[2]));   // readable white text on top
            return Color.HSVToColor(hsv);
        } catch (Exception e) {
            return fallback;
        }
    }

    /**
     * Fills an app tile in the chosen icon style: banner (TV banner, or icon + name), circle (icon in a round
     * badge), square (icon + name on the card) or colorful (card painted in the icon's colour).
     */
    public static void appFace(Context c, Config cfg, Apps.App a, android.widget.FrameLayout tile, GradientDrawable bg,
                               int w, int h, boolean label) {
        String st = cfg.iconStyle;
        if ("banner".equals(st) && a.banner != null) {
            ImageView iv = new ImageView(c);
            iv.setImageDrawable(a.banner);
            iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
            tile.addView(iv, new android.widget.FrameLayout.LayoutParams(-1, -1));
            return;
        }
        int textColor = text(cfg);
        if ("colorful".equals(st)) {
            int col = iconColor(a.icon, accent(cfg));
            if (bg != null) bg.setColor(col);
            textColor = Color.WHITE;
        }
        LinearLayout col = new LinearLayout(c);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER);
        int iconSize = label ? h * 45 / 100 : h * 58 / 100;
        if ("circle".equals(st)) {
            android.widget.FrameLayout badge = new android.widget.FrameLayout(c);
            GradientDrawable circle = new GradientDrawable();
            circle.setShape(GradientDrawable.OVAL);
            circle.setColor(cfg.light() ? alpha(Color.WHITE, 0.9f) : alpha(Color.WHITE, 0.12f));
            circle.setStroke(Math.max(1, u(c, 1.5f)), alpha(accent(cfg), 0.6f));
            badge.setBackground(circle);
            ImageView iv = new ImageView(c);
            iv.setImageDrawable(a.icon);
            badge.addView(iv, new android.widget.FrameLayout.LayoutParams(iconSize * 70 / 100, iconSize * 70 / 100, Gravity.CENTER));
            col.addView(badge, new LinearLayout.LayoutParams(iconSize, iconSize));
        } else {
            ImageView iv = new ImageView(c);
            iv.setImageDrawable(a.icon);
            col.addView(iv, new LinearLayout.LayoutParams(iconSize, iconSize));
        }
        if (label) {
            TextView t = tv(c, cfg, a.label, 18, true);
            t.setTextColor(textColor);
            t.setSingleLine(true);
            t.setEllipsize(android.text.TextUtils.TruncateAt.END);
            t.setGravity(Gravity.CENTER);
            t.setPadding(u(c, 8), u(c, 6), u(c, 8), 0);
            col.addView(t, new LinearLayout.LayoutParams(-1, -2));
        }
        tile.addView(col, new android.widget.FrameLayout.LayoutParams(-1, -1));
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
    /** View tag: when TRUE the focus ring disappears completely on blur (icons without a frame). */
    static final int IDLE_CLEAR = 0x7f0b1b1b;

    public static void focusable(final Context c, final Config cfg, final View v, final GradientDrawable ring,
                                 final float zoom, final View.OnFocusChangeListener extra) {
        v.setFocusable(true);
        v.setFocusableInTouchMode(true);
        v.setClickable(true);
        final boolean fx = effects(c, cfg);
        final int idle = ring == null || Boolean.TRUE.equals(v.getTag(IDLE_CLEAR)) ? 0 : glassStroke(cfg);
        final int idleW = idle == 0 ? 0 : Math.max(1, u(c, "outline".equals(cfg.surface) ? 2.5f : 1.5f));
        final String fs = cfg.focusStyle;
        // zoom: ring + zoom · glow: wide soft ring + light zoom · ring: ring only · lift: bigger zoom + shadow, no ring
        final float z = "ring".equals(fs) ? 1f : "glow".equals(fs) ? 1f + (zoom - 1f) * 0.6f : "lift".equals(fs) ? 1f + (zoom - 1f) * 1.4f : zoom;
        final int ringW = "glow".equals(fs) ? u(c, 7) : "ring".equals(fs) ? u(c, 5) : u(c, 4);
        final int ringC = "glow".equals(fs) ? alpha(accent(cfg), 0.65f) : accent(cfg);
        final boolean showRing = !"lift".equals(fs);
        v.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override public void onFocusChange(View view, boolean has) {
                float s = has ? z : 1f;
                view.animate().scaleX(s).scaleY(s).setDuration(fx ? 180 : 90).start();
                if (fx) view.setElevation(has ? u(c, "lift".equals(fs) ? 30 : 18) : 0);
                if (ring != null) {
                    if (has && showRing) ring.setStroke(ringW, ringC);
                    else ring.setStroke(idleW, idle);
                }
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
