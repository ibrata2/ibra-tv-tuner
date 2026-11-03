using System.Net;
using System.Text.RegularExpressions;

namespace IbraTvTuner.Core;

/// <summary>
/// Every value that ends up on an adb command line passes through one of these checks.
/// Nothing typed by the user is ever forwarded to adb as free text.
/// </summary>
public static partial class Validators
{
    [GeneratedRegex(@"^[A-Za-z][A-Za-z0-9_]*(\.[A-Za-z0-9_]+)+$")]
    private static partial Regex PackageRx();

    [GeneratedRegex(@"^[A-Za-z][A-Za-z0-9_]*(\.[A-Za-z0-9_]+)+/[A-Za-z0-9_.$]+$")]
    private static partial Regex ComponentRx();

    [GeneratedRegex(@"^[A-Za-z0-9._:\-\[\]]+$")]
    private static partial Regex SerialRx();

    [GeneratedRegex(@"^[0-9]{6}$")]
    private static partial Regex PairCodeRx();

    [GeneratedRegex(@"^[A-Za-z0-9+/=]+$")]
    private static partial Regex Base64Rx();

    [GeneratedRegex(@"^[a-z0-9.]+$")]
    private static partial Regex PropRx();

    public static bool IsPackage(string? s) => s is { Length: > 2 and < 256 } && PackageRx().IsMatch(s);
    public static bool IsComponent(string? s) => s is { Length: > 3 and < 512 } && ComponentRx().IsMatch(s);
    public static bool IsSerial(string? s) => s is { Length: > 0 and < 128 } && SerialRx().IsMatch(s);
    public static bool IsPairingCode(string? s) => s is not null && PairCodeRx().IsMatch(s);
    public static bool IsBase64(string? s) => s is { Length: > 0 and < 16000 } && Base64Rx().IsMatch(s);
    public static bool IsPropName(string? s) => s is { Length: > 0 and < 92 } && PropRx().IsMatch(s);
    public static bool IsPort(int p) => p is > 0 and < 65536;

    /// <summary>Accepts an IPv4/IPv6 literal or a simple host name (letters, digits, dots, dashes).</summary>
    public static bool IsHost(string? s)
    {
        if (string.IsNullOrWhiteSpace(s) || s.Length > 253) return false;
        if (IPAddress.TryParse(s, out _)) return true;
        return Uri.CheckHostName(s) == UriHostNameType.Dns && s.All(c => char.IsAsciiLetterOrDigit(c) || c is '.' or '-');
    }

    public static string RequirePackage(string s) => IsPackage(s) ? s : throw new InvalidInputException("package", s);
    public static string RequireComponent(string s) => IsComponent(s) ? s : throw new InvalidInputException("component", s);
    public static string RequireSerial(string s) => IsSerial(s) ? s : throw new InvalidInputException("serial", s);
    public static string RequireHost(string s) => IsHost(s) ? s.Trim() : throw new InvalidInputException("host", s);
    public static int RequirePort(int p) => IsPort(p) ? p : throw new InvalidInputException("port", p.ToString());
    public static string RequirePairingCode(string s) => IsPairingCode(s) ? s : throw new InvalidInputException("pairing code", "******");
    public static string RequireBase64(string s) => IsBase64(s) ? s : throw new InvalidInputException("payload", "(base64)");
    public static string RequireProp(string s) => IsPropName(s) ? s : throw new InvalidInputException("property", s);
}

public sealed class InvalidInputException(string what, string value)
    : ArgumentException($"Invalid {what}: '{value}'")
{
    public string What { get; } = what;
}
