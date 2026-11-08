package tv.ibra.tuner.launcher;

import android.app.ActivityManager;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.widget.ImageView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Lazy poster loading: memory LRU + disk cache (cacheDir/img, trimmed to ~60 MB), decoded at the target size.
 * Views are tagged with the URL they want, so a recycled view never shows the wrong poster.
 * HTTPS only. On low-RAM devices bitmaps are decoded as RGB_565 and the memory cache is smaller.
 */
public final class ImageLoader {
    private static ImageLoader instance;

    private final LruCache<String, Bitmap> mem;
    private final File dir;
    private final ExecutorService pool = Executors.newFixedThreadPool(3);
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final boolean low;

    public static synchronized ImageLoader get(Context c) {
        if (instance == null) instance = new ImageLoader(c.getApplicationContext());
        return instance;
    }

    private ImageLoader(Context c) {
        low = Ui.lowEnd(c);
        int kb = (int) (Runtime.getRuntime().maxMemory() / 1024 / (low ? 10 : 6));
        mem = new LruCache<String, Bitmap>(kb) {
            @Override protected int sizeOf(String k, Bitmap b) { return b.getByteCount() / 1024; }
        };
        dir = new File(c.getCacheDir(), "img");
        dir.mkdirs();
        pool.execute(new Runnable() { @Override public void run() { trim(); } });
    }

    public void load(final ImageView v, final String url, final int w, final int h) {
        v.setTag(R_TAG, url);
        if (url == null || url.isEmpty() || !url.startsWith("https://")) { v.setImageDrawable(null); return; }
        final String key = url + "@" + w;
        Bitmap b = mem.get(key);
        if (b != null) { v.setImageBitmap(b); return; }
        v.setImageDrawable(null);
        pool.execute(new Runnable() {
            @Override public void run() {
                if (!url.equals(v.getTag(R_TAG))) return;          // view was reused before we started
                final Bitmap bm = fetch(url, w, h);
                if (bm == null) return;
                mem.put(key, bm);
                ui.post(new Runnable() {
                    @Override public void run() {
                        if (url.equals(v.getTag(R_TAG))) {
                            v.setImageBitmap(bm);
                            v.setAlpha(0f);
                            v.animate().alpha(1f).setDuration(180).start();
                        }
                    }
                });
            }
        });
    }

    /** View tag key (any id unique within the app). */
    static final int R_TAG = 0x7f0b1b1a;

    private Bitmap fetch(String url, int w, int h) {
        File f = new File(dir, md5(url));
        try {
            if (!f.isFile() || f.length() == 0) {
                HttpURLConnection con = (HttpURLConnection) new URL(url).openConnection();
                con.setConnectTimeout(8000);
                con.setReadTimeout(15000);
                con.setRequestProperty("User-Agent", "IBRA-TV-HOME/1.0");
                try {
                    if (con.getResponseCode() != 200) return null;
                    File tmp = new File(dir, f.getName() + ".tmp");
                    InputStream in = con.getInputStream();
                    FileOutputStream out = new FileOutputStream(tmp);
                    byte[] buf = new byte[16384];
                    int n;
                    long total = 0;
                    try {
                        while ((n = in.read(buf)) > 0) {
                            total += n;
                            if (total > 8L * 1024 * 1024) { out.close(); tmp.delete(); return null; }
                            out.write(buf, 0, n);
                        }
                    } finally { out.close(); }
                    if (!tmp.renameTo(f)) return null;
                } finally { con.disconnect(); }
            } else {
                f.setLastModified(System.currentTimeMillis());
            }
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(f.getPath(), o);
            int s = 1;
            while (o.outWidth / (s * 2) >= w && o.outHeight / (s * 2) >= h) s *= 2;
            BitmapFactory.Options d = new BitmapFactory.Options();
            d.inSampleSize = s;
            d.inPreferredConfig = low ? Bitmap.Config.RGB_565 : Bitmap.Config.ARGB_8888;
            return BitmapFactory.decodeFile(f.getPath(), d);
        } catch (Throwable e) {
            return null;
        }
    }

    private void trim() {
        File[] fs = dir.listFiles();
        if (fs == null) return;
        long total = 0;
        for (File f : fs) total += f.length();
        if (total < 60L * 1024 * 1024) return;
        java.util.Arrays.sort(fs, new java.util.Comparator<File>() {
            @Override public int compare(File a, File b) { return Long.compare(a.lastModified(), b.lastModified()); }
        });
        for (File f : fs) {
            if (total < 40L * 1024 * 1024) break;
            total -= f.length();
            f.delete();
        }
    }

    public void clear() {
        mem.evictAll();
        File[] fs = dir.listFiles();
        if (fs != null) for (File f : fs) f.delete();
    }

    static String md5(String s) {
        try {
            byte[] d = MessageDigest.getInstance("MD5").digest(s.getBytes("UTF-8"));
            StringBuilder sb = new StringBuilder();
            for (byte b : d) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return String.valueOf(s.hashCode());
        }
    }
}
