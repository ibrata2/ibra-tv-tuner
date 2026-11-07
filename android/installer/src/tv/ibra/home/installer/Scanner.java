package tv.ibra.home.installer;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/** Finds devices on the same Wi-Fi that have ADB over network open (port 5555). */
public final class Scanner {

    /** The phone's own private IPv4 address on Wi-Fi/Ethernet, or null. */
    public static String localIpv4() {
        try {
            String fallback = null;
            for (Enumeration<NetworkInterface> e = NetworkInterface.getNetworkInterfaces(); e.hasMoreElements(); ) {
                NetworkInterface ni = e.nextElement();
                if (!ni.isUp() || ni.isLoopback()) continue;
                for (Enumeration<InetAddress> a = ni.getInetAddresses(); a.hasMoreElements(); ) {
                    InetAddress ad = a.nextElement();
                    if (!(ad instanceof Inet4Address) || !ad.isSiteLocalAddress()) continue;
                    String n = ni.getName();
                    if (n.startsWith("wlan") || n.startsWith("eth")) return ad.getHostAddress();
                    if (fallback == null) fallback = ad.getHostAddress();
                }
            }
            return fallback;
        } catch (Exception e) {
            return null;
        }
    }

    public static List<String> scan(String ownIp, final int port, int timeoutMs) {
        final List<String> found = Collections.synchronizedList(new ArrayList<String>());
        if (ownIp == null) return found;
        String base = ownIp.substring(0, ownIp.lastIndexOf('.') + 1);
        ExecutorService pool = Executors.newFixedThreadPool(48);
        final int t = timeoutMs;
        for (int i = 1; i < 255; i++) {
            final String ip = base + i;
            if (ip.equals(ownIp)) continue;
            pool.execute(new Runnable() {
                @Override public void run() {
                    Socket s = new Socket();
                    try {
                        s.connect(new InetSocketAddress(ip, port), t);
                        found.add(ip);
                    } catch (Exception ignored) {
                    } finally {
                        try { s.close(); } catch (Exception ignored) { }
                    }
                }
            });
        }
        pool.shutdown();
        try { pool.awaitTermination(t * 8L + 4000, TimeUnit.MILLISECONDS); } catch (InterruptedException ignored) { }
        List<String> out = new ArrayList<String>(found);
        Collections.sort(out, new java.util.Comparator<String>() {
            @Override public int compare(String a, String b) {
                return Integer.parseInt(a.substring(a.lastIndexOf('.') + 1)) - Integer.parseInt(b.substring(b.lastIndexOf('.') + 1));
            }
        });
        return out;
    }
}
