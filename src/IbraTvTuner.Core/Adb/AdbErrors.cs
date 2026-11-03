namespace IbraTvTuner.Core.Adb;

public enum AdbErrorKind
{
    None,
    AdbMissing,
    Unreachable,
    ConnectionRefused,
    Unauthorized,
    Offline,
    PairingFailed,
    PairingCodeWrong,
    Timeout,
    NotFound,
    PermissionDenied,
    NotSupportedByDevice,
    SignatureMismatch,
    Unknown,
}

public sealed record AdbError(AdbErrorKind Kind, string Arabic, string English, string Raw)
{
    public string Message(bool arabic) => arabic ? Arabic : English;
}

/// <summary>Turns adb output into a short, human explanation in Arabic and English.</summary>
public static class AdbErrorTranslator
{
    public static AdbError? FromResult(AdbResult r)
    {
        var text = r.Combined;
        var low = text.ToLowerInvariant();

        // Success output (getprop, package lists) can legitimately contain words like "failure";
        // only look for problems when adb failed or a line itself reports one.
        if (r.Ok && string.IsNullOrWhiteSpace(r.StdErr) && !LooksLikeError(r.StdOut)) return null;

        if (r.TimedOut)
            return E(AdbErrorKind.Timeout, "انتهت المهلة بدون رد من الجهاز. تأكد إنه شغّال وعلى نفس الشبكة.",
                "The device did not answer in time. Check it is on and on the same network.", text);
        if (low.Contains("adb_start_failed"))
            return E(AdbErrorKind.AdbMissing, "ما گدرت أشغّل ADB. حدّد مسار adb.exe من الإعدادات أو حمّل Platform-Tools الرسمية.",
                "Could not start ADB. Choose adb.exe in Settings or download the official Platform-Tools.", text);
        if (low.Contains("unauthorized"))
            return E(AdbErrorKind.Unauthorized, "إذن ADB بانتظار التأكيد: اضغط «سماح» على شاشة التلفزيون ثم أعد المحاولة.",
                "ADB permission is waiting: press Allow on the TV screen, then retry.", text);
        if (low.Contains("wrong password") || low.Contains("incorrect pairing code") || low.Contains("pairing code"))
            return E(AdbErrorKind.PairingCodeWrong, "رمز الاقتران غلط أو انتهت صلاحيته. افتح نافذة الاقتران من جديد على الجهاز.",
                "The pairing code is wrong or expired. Open the pairing dialog on the device again.", text);
        if (low.Contains("failed to pair") || low.Contains("unable to start pairing") || (low.Contains("pair") && low.Contains("fail")))
            return E(AdbErrorKind.PairingFailed, "فشل الاقتران. تأكد إنك كتبت منفذ الاقتران (مو منفذ الاتصال) والرمز الظاهر حالياً.",
                "Pairing failed. Make sure you used the PAIRING port (not the connect port) and the code shown now.", text);
        if (low.Contains("connection refused") || low.Contains("actively refused") || low.Contains("10061"))
            return E(AdbErrorKind.ConnectionRefused, "الجهاز رفض الاتصال: تصحيح ADB عبر الشبكة غير مفعّل أو المنفذ غلط.",
                "The device refused the connection: network ADB is off or the port is wrong.", text);
        if (low.Contains("no route") || low.Contains("unreachable") || low.Contains("timed out") || low.Contains("10060") || low.Contains("cannot connect"))
            return E(AdbErrorKind.Unreachable, "الجهاز غير متاح على الشبكة. تأكد من الـIP وإن الحاسبة والتلفزيون على نفس الشبكة المحلية.",
                "The device is not reachable. Check the IP and that the PC and the TV share the local network.", text);
        if (low.Contains("offline"))
            return E(AdbErrorKind.Offline, "الجهاز ظاهر لكن حالته offline. أطفئ تصحيح ADB وشغّله، ثم أعد الاتصال.",
                "The device is offline. Toggle ADB debugging on the device and reconnect.", text);
        if (low.Contains("install_failed_update_incompatible") || low.Contains("signatures do not match"))
            return E(AdbErrorKind.SignatureMismatch, "نسخة قديمة من الواجهة موجودة بتوقيع مختلف. احذفها من صفحة المظهر ثم ثبّت من جديد.",
                "An older launcher build with a different signature is installed. Remove it from Appearance, then install again.", text);
        if (low.Contains("unknown command") || low.Contains("unknown option") || low.Contains("not found") && low.Contains("cmd"))
            return E(AdbErrorKind.NotSupportedByDevice, "النظام على هذا الجهاز ما يدعم هذا الأمر.",
                "The system on this device does not support this command.", text);
        if (low.Contains("securityexception") || low.Contains("permission denial") || low.Contains("not allowed") || low.Contains("cannot disable"))
            return E(AdbErrorKind.PermissionDenied, "النظام منع هذا الإجراء على هذه الحزمة.",
                "The system refused this action for this package.", text);
        if (low.Contains("device '") && low.Contains("not found") || low.Contains("no devices") || low.Contains("device not found"))
            return E(AdbErrorKind.NotFound, "الجهاز غير متصل بـADB حالياً. أعد الاتصال من صفحة الأجهزة.",
                "The device is not connected to ADB. Reconnect from Devices.", text);
        if (!r.Ok || low.StartsWith("error") || low.Contains("exception") || low.Contains("failure"))
            return E(AdbErrorKind.Unknown, "صار خطأ غير معروف. التفاصيل بالسجل.", "Unknown error. See the log for details.", text);
        return null;
    }

    public static bool LooksLikeError(string stdout) =>
        stdout.Replace("\r", "").Split('\n').Select(l => l.TrimStart()).Any(l =>
            l.StartsWith("Error", StringComparison.OrdinalIgnoreCase) ||
            l.StartsWith("Failure", StringComparison.Ordinal) ||
            l.StartsWith("Exception", StringComparison.Ordinal) ||
            l.StartsWith("java.lang.", StringComparison.Ordinal) ||
            l.StartsWith("Security exception", StringComparison.OrdinalIgnoreCase) ||
            l.StartsWith("adb: error", StringComparison.OrdinalIgnoreCase));

    private static AdbError E(AdbErrorKind k, string ar, string en, string raw) => new(k, ar, en, raw);
}
