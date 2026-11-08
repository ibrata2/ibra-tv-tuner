package tv.ibra.tuner.launcher;

import java.util.Calendar;
import java.util.TimeZone;

/**
 * Offline prayer times. Port of PrayTimes.org v2.3 by Hamid Zarrabi-Zadeh — GNU LGPL v3.0, http://praytimes.org
 * (this file stays under LGPL-3.0 with that credit).
 * Methods are the two Ja'fari (Shia Ithna-Ashari) references used in Iraq and Iran:
 *   JAFARI  - Leva Institute, Qum: Fajr 16°, Maghrib 4°, Isha 14°, midnight = sunset→fajr
 *   TEHRAN  - Institute of Geophysics, Tehran: Fajr 17.7°, Maghrib 4.5°, Isha 14°, midnight = sunset→fajr
 * Asr uses the standard shadow factor (1). Maghrib is NOT sunset: it is when the sun is 4° below the horizon.
 * No Android imports, so it is unit-tested on a desktop JVM.
 */
public final class Prayer {

    public static final int FAJR = 0, SUNRISE = 1, DHUHR = 2, ASR = 3, SUNSET = 4, MAGHRIB = 5, ISHA = 6, MIDNIGHT = 7;
    public static final int COUNT = 8;

    public static final int JAFARI = 0, TEHRAN = 1;
    static final double[][] PARAMS = {
            // fajr angle, maghrib angle, isha angle
            {16, 4, 14},
            {17.7, 4.5, 14},
    };

    public static String[] NAMES_AR = {"الفجر", "الشروق", "الظهر", "العصر", "الغروب", "المغرب", "العشاء", "منتصف الليل"};
    public static String[] NAMES_EN = {"Fajr", "Sunrise", "Dhuhr", "Asr", "Sunset", "Maghrib", "Isha", "Midnight"};

    // ------------------------------------------------------------------ math (degrees)

    static double dsin(double d) { return Math.sin(Math.toRadians(d)); }
    static double dcos(double d) { return Math.cos(Math.toRadians(d)); }
    static double dtan(double d) { return Math.tan(Math.toRadians(d)); }
    static double darcsin(double x) { return Math.toDegrees(Math.asin(x)); }
    static double darccos(double x) { return Math.toDegrees(Math.acos(x)); }
    static double darctan2(double y, double x) { return Math.toDegrees(Math.atan2(y, x)); }
    static double darccot(double x) { return Math.toDegrees(Math.atan(1 / x)); }
    static double fix(double a, double b) { a = a - b * Math.floor(a / b); return a < 0 ? a + b : a; }
    static double fixangle(double a) { return fix(a, 360); }
    static double fixhour(double a) { return fix(a, 24); }

    static double julian(int y, int m, int d) {
        if (m <= 2) { y -= 1; m += 12; }
        double a = Math.floor(y / 100.0);
        double b = 2 - a + Math.floor(a / 4);
        return Math.floor(365.25 * (y + 4716)) + Math.floor(30.6001 * (m + 1)) + d + b - 1524.5;
    }

    /** {declination, equation of time} */
    static double[] sunPosition(double jd) {
        double d = jd - 2451545.0;
        double g = fixangle(357.529 + 0.98560028 * d);
        double q = fixangle(280.459 + 0.98564736 * d);
        double l = fixangle(q + 1.915 * dsin(g) + 0.020 * dsin(2 * g));
        double e = 23.439 - 0.00000036 * d;
        double ra = darctan2(dcos(e) * dsin(l), dcos(l)) / 15;
        double eqt = q / 15 - fixhour(ra);
        double decl = darcsin(dsin(e) * dsin(l));
        return new double[]{decl, eqt};
    }

    private final double lat, lng, jdate;

    private Prayer(double lat, double lng, double jdate) { this.lat = lat; this.lng = lng; this.jdate = jdate; }

    double midDay(double time) {
        double eqt = sunPosition(jdate + time)[1];
        return fixhour(12 - eqt);
    }

    double sunAngleTime(double angle, double time, boolean ccw) {
        double decl = sunPosition(jdate + time)[0];
        double noon = midDay(time);
        double t = darccos((-dsin(angle) - dsin(decl) * dsin(lat)) / (dcos(decl) * dcos(lat))) / 15;
        return noon + (ccw ? -t : t);
    }

    double asrTime(double factor, double time) {
        double decl = sunPosition(jdate + time)[0];
        double angle = -darccot(factor + dtan(Math.abs(lat - decl)));
        return sunAngleTime(angle, time, false);
    }

    static double timeDiff(double a, double b) { return fixhour(b - a); }

    /**
     * Times in hours (local clock) for the given day. Missing values (polar days) come back as NaN only
     * if the high-latitude rule also cannot fill them, which does not happen at Iraqi latitudes.
     */
    public static double[] compute(int year, int month, int day, double lat, double lng, double tzHours, int method) {
        double[] p = PARAMS[method == TEHRAN ? TEHRAN : JAFARI];
        Prayer c = new Prayer(lat, lng, julian(year, month, day) - lng / (15 * 24.0));
        double rise = 0.833;   // elevation 0

        // one iteration from the default guesses (as PrayTimes does)
        double[] t = {5, 6, 12, 13, 18, 18, 18};
        for (int i = 0; i < t.length; i++) t[i] /= 24;
        double fajr = c.sunAngleTime(p[0], t[0], true);
        double sunrise = c.sunAngleTime(rise, t[1], true);
        double dhuhr = c.midDay(t[2]);
        double asr = c.asrTime(1, t[3]);
        double sunset = c.sunAngleTime(rise, t[4], false);
        double maghrib = c.sunAngleTime(p[1], t[5], false);
        double isha = c.sunAngleTime(p[2], t[6], false);

        double[] r = {fajr, sunrise, dhuhr, asr, sunset, maghrib, isha, 0};
        double shift = tzHours - lng / 15;
        for (int i = 0; i < 7; i++) r[i] += shift;

        // high latitudes: NightMiddle
        double night = timeDiff(r[SUNSET], r[SUNRISE]);
        r[FAJR] = adjustHL(r[FAJR], r[SUNRISE], night, true);
        r[ISHA] = adjustHL(r[ISHA], r[SUNSET], night, false);
        r[MAGHRIB] = adjustHL(r[MAGHRIB], r[SUNSET], night, false);

        // Ja'fari midnight: half way between sunset and fajr
        r[MIDNIGHT] = r[SUNSET] + timeDiff(r[SUNSET], r[FAJR]) / 2;
        return r;
    }

    static double adjustHL(double time, double base, double night, boolean ccw) {
        double portion = night / 2;
        double diff = ccw ? timeDiff(time, base) : timeDiff(base, time);
        if (Double.isNaN(time) || diff > portion) time = base + (ccw ? -portion : portion);
        return time;
    }

    /** Minutes after local midnight, rounded like PrayTimes (to the nearest minute), with an offset in minutes. */
    public static int minutes(double hours, int offsetMin) {
        if (Double.isNaN(hours)) return -1;
        double h = fixhour(hours + 0.5 / 60);   // PrayTimes rounds by adding half a minute
        return ((int) Math.floor(h * 60) + offsetMin + 1440) % 1440;
    }

    public static String hhmm(int minutes) {
        if (minutes < 0) return "--:--";
        return String.format(java.util.Locale.US, "%02d:%02d", minutes / 60, minutes % 60);
    }

    /** Timezone offset in hours on that calendar day (DST-aware). */
    public static double tzHours(TimeZone tz, int year, int month, int day) {
        Calendar c = Calendar.getInstance(tz);
        c.clear();
        c.set(year, month - 1, day, 12, 0, 0);
        return tz.getOffset(c.getTimeInMillis()) / 3600000.0;
    }

    // ------------------------------------------------------------------ day schedule

    /** One day's displayable times in minutes after midnight (offsets applied). Index by FAJR..MIDNIGHT. */
    public static int[] day(Calendar date, double lat, double lng, TimeZone tz, int method, int[] offsets) {
        int y = date.get(Calendar.YEAR), m = date.get(Calendar.MONTH) + 1, d = date.get(Calendar.DAY_OF_MONTH);
        double[] h = compute(y, m, d, lat, lng, tzHours(tz, y, m, d), method);
        int[] out = new int[COUNT];
        for (int i = 0; i < COUNT; i++) out[i] = minutes(h[i], offsets != null && i < offsets.length ? offsets[i] : 0);
        return out;
    }

    /** The prayers shown/announced (sunrise, sunset and midnight are informative only). */
    public static final int[] PRAYERS = {FAJR, DHUHR, ASR, MAGHRIB, ISHA};
    /** "Combined" display used by many Shia: الظهرين at Dhuhr, العشاءين at Maghrib. */
    public static final int[] COMBINED = {FAJR, DHUHR, MAGHRIB};

    public static final class Next {
        public int index;          // FAJR..ISHA
        public long atMillis;      // absolute time
        public boolean tomorrow;
    }

    /** Next prayer after {@code now} (looks into tomorrow after Isha). */
    public static Next next(long now, double lat, double lng, TimeZone tz, int method, int[] offsets, boolean combined) {
        int[] list = combined ? COMBINED : PRAYERS;
        for (int dayAdd = 0; dayAdd < 2; dayAdd++) {
            Calendar c = Calendar.getInstance(tz);
            c.setTimeInMillis(now);
            c.add(Calendar.DAY_OF_MONTH, dayAdd);
            int[] t = day(c, lat, lng, tz, method, offsets);
            for (int idx : list) {
                if (t[idx] < 0) continue;
                Calendar at = (Calendar) c.clone();
                at.set(Calendar.HOUR_OF_DAY, t[idx] / 60);
                at.set(Calendar.MINUTE, t[idx] % 60);
                at.set(Calendar.SECOND, 0);
                at.set(Calendar.MILLISECOND, 0);
                if (at.getTimeInMillis() > now) {
                    Next n = new Next();
                    n.index = idx;
                    n.atMillis = at.getTimeInMillis();
                    n.tomorrow = dayAdd > 0;
                    return n;
                }
            }
        }
        return null;
    }

    public static String name(int index, boolean ar, boolean combined) {
        if (combined && ar) {
            if (index == DHUHR) return "الظهرين";
            if (index == MAGHRIB) return "العشاءين";
        }
        if (combined && !ar) {
            if (index == DHUHR) return "Dhuhr & Asr";
            if (index == MAGHRIB) return "Maghrib & Isha";
        }
        return ar ? NAMES_AR[index] : NAMES_EN[index];
    }

    // ------------------------------------------------------------------ Hijri

    static final String[] HIJRI_AR = {"محرّم", "صفر", "ربيع الأول", "ربيع الآخر", "جمادى الأولى", "جمادى الآخرة",
            "رجب", "شعبان", "رمضان", "شوّال", "ذو القعدة", "ذو الحجة"};
    static final String[] HIJRI_EN = {"Muharram", "Safar", "Rabi' I", "Rabi' II", "Jumada I", "Jumada II",
            "Rajab", "Sha'ban", "Ramadan", "Shawwal", "Dhu al-Qa'dah", "Dhu al-Hijjah"};

    static final double ISLAMIC_EPOCH = 1948439.5;

    static double islamicToJd(int y, int m, int d) {
        return d + Math.ceil(29.5 * (m - 1)) + (y - 1) * 354 + Math.floor((3 + 11 * y) / 30.0) + ISLAMIC_EPOCH - 1;
    }

    /**
     * Umm al-Qura month lengths for 1440/1 .. 1480/12 AH (from hijri-converter, MIT): '1' = 30 days, '0' = 29.
     * UQ_BASE is the reduced Julian day (JDN - 2400000) of 1 Muharram 1440.
     */
    static final int UQ_BASE = 58373, UQ_YEAR0 = 1440;
    static final String UQ_BITS = "010111010100101011011010010101011010101010101011010110010101011101001001011101100100101110101010010110110101001010110110101001010110110100101010111010010101011100101010011101010101001101011010100101011101010010011011101001001101110100100110110101010011010110101010101010101101010010110110101001010111010100100111101010010101101101001010101101010101001101101100100110101110010010110110101010010110101101001010110110100101010111010010010111011001001011011100100101101101010010101101011001010101";

    /**
     * Hijri date: Umm al-Qura inside 1440–1480 AH, tabular (civil) calendar outside it.
     * In Iraq the month starts by moon sighting, so it can differ by a day; the user corrects it
     * with {@code offsetDays} in settings. Returns {year, month (1-12), day}.
     */
    public static int[] hijri(int year, int month, int day, int offsetDays) {
        long rjd = (long) Math.floor(julian(year, month, day) + 0.5) + offsetDays - 2400000;
        if (rjd >= UQ_BASE) {
            long start = UQ_BASE;
            for (int i = 0; i < UQ_BITS.length(); i++) {
                int len = UQ_BITS.charAt(i) == '1' ? 30 : 29;
                if (rjd < start + len) return new int[]{UQ_YEAR0 + i / 12, i % 12 + 1, (int) (rjd - start) + 1};
                start += len;
            }
        }
        return hijriTabular(year, month, day, offsetDays);
    }

    /**
     * Tabular (civil) Islamic calendar. In Iraq the month starts by moon sighting, so the result can differ by a
     * day or two; the user corrects it with {@code offsetDays} in settings.
     * Returns {year, month (1-12), day}.
     */
    static int[] hijriTabular(int year, int month, int day, int offsetDays) {
        double jd = Math.floor(julian(year, month, day) + offsetDays) + 0.5;
        int y = (int) Math.floor((30 * (jd - ISLAMIC_EPOCH) + 10646) / 10631);
        int m = (int) Math.min(12, Math.ceil((jd - (29 + islamicToJd(y, 1, 1))) / 29.5) + 1);
        if (m < 1) m = 1;
        int d = (int) (jd - islamicToJd(y, m, 1)) + 1;
        return new int[]{y, m, d};
    }

    public static String hijriText(Calendar c, int offsetDays, boolean ar) {
        int[] h = hijri(c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH), offsetDays);
        return ar ? h[2] + " " + HIJRI_AR[h[1] - 1] + " " + h[0] + " هـ"
                  : h[2] + " " + HIJRI_EN[h[1] - 1] + " " + h[0] + " AH";
    }

    static final String[] MONTHS_IQ = {"كانون الثاني", "شباط", "آذار", "نيسان", "أيار", "حزيران", "تموز", "آب",
            "أيلول", "تشرين الأول", "تشرين الثاني", "كانون الأول"};
    static final String[] MONTHS_EN = {"January", "February", "March", "April", "May", "June", "July", "August",
            "September", "October", "November", "December"};
    static final String[] DAYS_AR = {"الأحد", "الاثنين", "الثلاثاء", "الأربعاء", "الخميس", "الجمعة", "السبت"};
    static final String[] DAYS_EN = {"Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday"};

    /** "الأحد، 8 تشرين الثاني 2026" (Iraqi month names) or "Sunday, 8 November 2026". */
    public static String gregorianText(Calendar c, boolean ar) {
        int dow = c.get(Calendar.DAY_OF_WEEK) - 1;
        int m = c.get(Calendar.MONTH);
        return ar ? DAYS_AR[dow] + "، " + c.get(Calendar.DAY_OF_MONTH) + " " + MONTHS_IQ[m] + " " + c.get(Calendar.YEAR)
                  : DAYS_EN[dow] + ", " + c.get(Calendar.DAY_OF_MONTH) + " " + MONTHS_EN[m] + " " + c.get(Calendar.YEAR);
    }
}
