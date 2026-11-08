import tv.ibra.tuner.launcher.Prayer;
import java.nio.file.*; import java.util.*;

/** Checks Prayer.java against PrayTimes.org reference output (golden CSV) and Hijri against Umm al-Qura (±1 day). */
public class PrayerTest {
  public static void main(String[] a) throws Exception {
    int fails = 0, n = 0;
    for (String line : Files.readAllLines(Paths.get("prayer_golden.csv"))) {
      if (line.isEmpty() || line.startsWith("#")) continue;
      String[] f = line.split(",");
      int method = f[0].equals("Tehran") ? Prayer.TEHRAN : Prayer.JAFARI;
      double lat = Double.parseDouble(f[2]), lng = Double.parseDouble(f[3]);
      String[] ymd = f[4].split("-");
      double[] h = Prayer.compute(Integer.parseInt(ymd[0]), Integer.parseInt(ymd[1]), Integer.parseInt(ymd[2]), lat, lng, 3, method);
      for (int i = 0; i < 8; i++) {
        String got = Prayer.hhmm(Prayer.minutes(h[i], 0)), want = f[5 + i];
        int diff = Math.abs(toMin(got) - toMin(want)); diff = Math.min(diff, 1440 - diff);
        n++;
        if (diff > 1) { fails++; if (fails < 10) System.out.println("MISMATCH " + line + " idx" + i + " got " + got); }
      }
    }
    System.out.println("prayer: " + (n - fails) + "/" + n + " within 1 min");
    int hf = 0, hn = 0, exact = 0;
    for (String line : Files.readAllLines(Paths.get("hijri_ummalqura.csv"))) {
      if (line.isEmpty() || line.startsWith("#")) continue;
      String[] f = line.split(","); String[] ymd = f[0].split("-");
      int[] h = Prayer.hijri(Integer.parseInt(ymd[0]), Integer.parseInt(ymd[1]), Integer.parseInt(ymd[2]), 0);
      int[] w = {Integer.parseInt(f[1]), Integer.parseInt(f[2]), Integer.parseInt(f[3])};
      long gotOrd = h[0] * 400L + h[1] * 31 + h[2], wantOrd = w[0] * 400L + w[1] * 31 + w[2];
      hn++;
      if (gotOrd == wantOrd) exact++;
      else {
        // allow ±1 day: compare by shifting
        int[] p = Prayer.hijri(Integer.parseInt(ymd[0]), Integer.parseInt(ymd[1]), Integer.parseInt(ymd[2]), 1);
        int[] m = Prayer.hijri(Integer.parseInt(ymd[0]), Integer.parseInt(ymd[1]), Integer.parseInt(ymd[2]), -1);
        boolean ok = Arrays.equals(p, w) || Arrays.equals(m, w);
        hf++; if (true) { System.out.println("HIJRI off " + line + " got " + Arrays.toString(h)); }
      }
    }
    System.out.println("hijri: " + exact + "/" + hn + " exact vs Umm al-Qura, " + (hn - hf) + "/" + hn + " within 1 day");
    // next-prayer sanity: Baghdad 2026-11-08 13:00 → Asr
    TimeZone tz = TimeZone.getTimeZone("Asia/Baghdad");
    Calendar c = Calendar.getInstance(tz); c.clear(); c.set(2026, 10, 8, 13, 0);
    Prayer.Next nx = Prayer.next(c.getTimeInMillis(), 33.3152, 44.3661, tz, Prayer.JAFARI, null, false);
    Prayer.Next nc = Prayer.next(c.getTimeInMillis(), 33.3152, 44.3661, tz, Prayer.JAFARI, null, true);
    c.set(2026, 10, 8, 19, 0);
    Prayer.Next late = Prayer.next(c.getTimeInMillis(), 33.3152, 44.3661, tz, Prayer.JAFARI, null, false);
    boolean nextOk = nx.index == Prayer.ASR && nc.index == Prayer.MAGHRIB && late.index == Prayer.FAJR && late.tomorrow;
    System.out.println("next: " + (nextOk ? "ok" : "FAIL " + nx.index + " " + nc.index + " " + late.index));
    Calendar g = Calendar.getInstance(tz); g.clear(); g.set(2026, 10, 8);
    System.out.println("date: " + Prayer.gregorianText(g, true) + " | " + Prayer.hijriText(g, 0, true));
    if (fails > 0 || exact != hn || !nextOk) System.exit(1);
    System.out.println("PrayerTest passed");
  }
  static int toMin(String s) { String[] p = s.split(":"); return Integer.parseInt(p[0]) * 60 + Integer.parseInt(p[1]); }
}
