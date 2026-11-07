package tv.ibra.home.installer;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigInteger;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;

import javax.crypto.Cipher;

/**
 * Minimal ADB client over TCP (the "ADB over network" / port 5555 mode found on Android TVs and boxes).
 * Pure Java with no Android imports, so it can be tested on a desktop JVM.
 * Supports: RSA authentication (the TV shows "Allow debugging?"), shell commands, and file push (sync).
 * Not supported: the TLS "Wireless debugging" pairing port of Android 11+ (it answers with STLS).
 */
public final class AdbClient implements AutoCloseable {

    static final int A_CNXN = 0x4e584e43, A_AUTH = 0x48545541, A_OPEN = 0x4e45504f, A_OKAY = 0x59414b4f,
            A_CLSE = 0x45534c43, A_WRTE = 0x45545257, A_STLS = 0x534c5453;
    static final int VERSION = 0x01000001;
    static final int AUTH_TOKEN = 1, AUTH_SIGNATURE = 2, AUTH_RSAPUBLICKEY = 3;

    /** Why a connection failed, so the UI can say something useful. */
    public static final class AdbException extends IOException {
        public final String code;
        public AdbException(String code, String msg) { super(msg); this.code = code; }
    }

    public interface Listener {
        /** The TV is now showing the "Allow debugging?" prompt. */
        void onAllowPrompt();
    }

    static final class Msg {
        int cmd, arg0, arg1;
        byte[] data;
    }

    // ------------------------------------------------------------------ key

    /** RSA-2048 key, stored as PKCS#8 + X.509 next to each other. */
    public static final class Key {
        final KeyPair pair;
        Key(KeyPair p) { pair = p; }

        public static Key loadOrCreate(File dir) throws Exception {
            File priv = new File(dir, "adbkey.pk8"), pub = new File(dir, "adbkey.x509");
            KeyFactory kf = KeyFactory.getInstance("RSA");
            if (priv.isFile() && pub.isFile()) {
                try {
                    PrivateKey pk = kf.generatePrivate(new PKCS8EncodedKeySpec(read(priv)));
                    java.security.PublicKey pu = kf.generatePublic(new X509EncodedKeySpec(read(pub)));
                    return new Key(new KeyPair(pu, pk));
                } catch (Exception ignored) { /* regenerate below */ }
            }
            KeyPairGenerator g = KeyPairGenerator.getInstance("RSA");
            g.initialize(2048);
            KeyPair kp = g.generateKeyPair();
            dir.mkdirs();
            write(priv, kp.getPrivate().getEncoded());
            write(pub, kp.getPublic().getEncoded());
            return new Key(kp);
        }

        /** ADB signs the 20-byte token as if it were a SHA-1 digest (PKCS#1 v1.5, type 1). */
        byte[] sign(byte[] token) throws Exception {
            byte[] prefix = {0x30, 0x21, 0x30, 0x09, 0x06, 0x05, 0x2b, 0x0e, 0x03, 0x02, 0x1a, 0x05, 0x00, 0x04, 0x14};
            int k = 256;
            byte[] em = new byte[k];
            em[0] = 0; em[1] = 1;
            int ps = k - 3 - prefix.length - token.length;
            for (int i = 0; i < ps; i++) em[2 + i] = (byte) 0xff;
            em[2 + ps] = 0;
            System.arraycopy(prefix, 0, em, 3 + ps, prefix.length);
            System.arraycopy(token, 0, em, 3 + ps + prefix.length, token.length);
            Cipher c = Cipher.getInstance("RSA/ECB/NoPadding");
            c.init(Cipher.ENCRYPT_MODE, pair.getPrivate());
            return c.doFinal(em);
        }

        /** The public key in adbd's format: base64(RSAPublicKey struct) + " name" + NUL. */
        byte[] adbPublicKey(String name) throws Exception {
            RSAPublicKey pub = (RSAPublicKey) pair.getPublic();
            BigInteger n = pub.getModulus();
            BigInteger r32 = BigInteger.ONE.shiftLeft(32);
            BigInteger n0inv = n.mod(r32).modInverse(r32).negate().mod(r32);
            BigInteger rr = BigInteger.ONE.shiftLeft(2048).pow(2).mod(n);
            ByteBuffer b = ByteBuffer.allocate(524).order(ByteOrder.LITTLE_ENDIAN);
            b.putInt(64);
            b.putInt(n0inv.intValue());
            b.put(le(n, 256));
            b.put(le(rr, 256));
            b.putInt(pub.getPublicExponent().intValue());
            String s = Base64.encode(b.array()) + " " + name;
            byte[] t = s.getBytes("UTF-8");
            byte[] out = new byte[t.length + 1];
            System.arraycopy(t, 0, out, 0, t.length);
            return out;
        }

        static byte[] le(BigInteger v, int len) {
            byte[] be = v.toByteArray();
            byte[] out = new byte[len];
            for (int i = 0; i < len && i < be.length; i++) out[i] = be[be.length - 1 - i];
            return out;
        }
    }

    // ------------------------------------------------------------------ connection

    private final Socket socket;
    private final DataInputStream in;
    private final OutputStream out;
    private int maxData = 4096;
    private int nextLocalId = 1;
    public String banner = "";

    private AdbClient(Socket s) throws IOException {
        socket = s;
        in = new DataInputStream(s.getInputStream());
        out = s.getOutputStream();
    }

    /**
     * Connects and authenticates. If the TV has not trusted this phone yet it shows a prompt;
     * we wait up to {@code allowTimeoutMs} for the user to press Allow.
     */
    public static AdbClient connect(String host, int port, Key key, String keyName, int allowTimeoutMs, Listener l)
            throws Exception {
        Socket s = new Socket();
        try {
            s.connect(new InetSocketAddress(host, port), 5000);
        } catch (IOException e) {
            s.close();
            throw new AdbException("unreachable", "Cannot reach " + host + ":" + port + " (" + e.getMessage() + ")");
        }
        s.setTcpNoDelay(true);
        s.setSoTimeout(10000);
        AdbClient c = new AdbClient(s);
        try {
            c.handshake(key, keyName, allowTimeoutMs, l);
            return c;
        } catch (Exception e) {
            c.close();
            throw e;
        }
    }

    private void handshake(Key key, String keyName, int allowTimeoutMs, Listener l) throws Exception {
        send(A_CNXN, VERSION, 256 * 1024, "host::\0".getBytes("UTF-8"));
        boolean signed = false, sentKey = false;
        while (true) {
            Msg m;
            try {
                m = read();
            } catch (SocketTimeoutException e) {
                throw new AdbException(sentKey ? "not_allowed" : "timeout",
                        sentKey ? "The TV did not allow this phone in time" : "No answer from ADB");
            }
            if (m.cmd == A_CNXN) {
                maxData = Math.max(4096, Math.min(m.arg1, 256 * 1024));
                banner = m.data == null ? "" : new String(m.data, "UTF-8");
                socket.setSoTimeout(30000);
                return;
            }
            if (m.cmd == A_STLS) throw new AdbException("tls", "This port needs Wireless-debugging pairing (TLS)");
            if (m.cmd != A_AUTH || m.arg0 != AUTH_TOKEN) throw new AdbException("protocol", "Unexpected reply");
            if (!signed) {
                send(A_AUTH, AUTH_SIGNATURE, 0, key.sign(m.data));
                signed = true;
            } else if (!sentKey) {
                send(A_AUTH, AUTH_RSAPUBLICKEY, 0, key.adbPublicKey(keyName));
                sentKey = true;
                socket.setSoTimeout(allowTimeoutMs);
                if (l != null) l.onAllowPrompt();
            } else {
                throw new AdbException("not_allowed", "The TV rejected this phone");
            }
        }
    }

    // ------------------------------------------------------------------ framing

    private synchronized void send(int cmd, int a0, int a1, byte[] data) throws IOException {
        int len = data == null ? 0 : data.length;
        int sum = 0;
        for (int i = 0; i < len; i++) sum += data[i] & 0xff;
        ByteBuffer h = ByteBuffer.allocate(24).order(ByteOrder.LITTLE_ENDIAN);
        h.putInt(cmd).putInt(a0).putInt(a1).putInt(len).putInt(sum).putInt(~cmd);
        out.write(h.array());
        if (len > 0) out.write(data);
        out.flush();
    }

    private Msg read() throws IOException {
        byte[] h = new byte[24];
        in.readFully(h);
        ByteBuffer b = ByteBuffer.wrap(h).order(ByteOrder.LITTLE_ENDIAN);
        Msg m = new Msg();
        m.cmd = b.getInt();
        m.arg0 = b.getInt();
        m.arg1 = b.getInt();
        int len = b.getInt();
        b.getInt();
        int magic = b.getInt();
        if (magic != ~m.cmd || len < 0 || len > 1024 * 1024) throw new AdbException("protocol", "Bad ADB packet");
        if (len > 0) {
            m.data = new byte[len];
            in.readFully(m.data);
        }
        return m;
    }

    // ------------------------------------------------------------------ streams

    private final class Stream {
        final int local;
        int remote;
        boolean closed;
        Stream(int local) { this.local = local; }

        /** Next WRTE payload for this stream, or null when the device closed it. */
        byte[] next() throws IOException {
            while (true) {
                Msg m = read();
                if (m.arg1 != local) continue;           // stray packet of an old stream
                if (m.cmd == A_WRTE) { send(A_OKAY, local, remote, null); return m.data == null ? new byte[0] : m.data; }
                if (m.cmd == A_CLSE) { closed = true; return null; }
                // OKAY for our writes are consumed in write()
            }
        }

        void write(byte[] data, int off, int len) throws IOException {
            while (len > 0) {
                int n = Math.min(len, maxData);
                byte[] chunk = new byte[n];
                System.arraycopy(data, off, chunk, 0, n);
                send(A_WRTE, local, remote, chunk);
                while (true) {
                    Msg m = read();
                    if (m.arg1 != local) continue;
                    if (m.cmd == A_OKAY) break;
                    if (m.cmd == A_CLSE) { closed = true; throw new AdbException("closed", "Stream closed by device"); }
                    if (m.cmd == A_WRTE) { send(A_OKAY, local, remote, null); }
                }
                off += n;
                len -= n;
            }
        }

        void close() throws IOException {
            if (!closed) send(A_CLSE, local, remote, null);
            closed = true;
        }
    }

    private Stream open(String service) throws IOException {
        Stream s = new Stream(nextLocalId++);
        send(A_OPEN, s.local, 0, (service + "\0").getBytes("UTF-8"));
        while (true) {
            Msg m = read();
            if (m.cmd == A_OKAY && m.arg1 == s.local) { s.remote = m.arg0; return s; }
            if (m.cmd == A_CLSE && m.arg1 == s.local) throw new AdbException("refused", "Device refused " + service);
        }
    }

    /** Runs a shell command and returns everything it printed. */
    public String shell(String cmd) throws IOException {
        Stream s = open("shell:" + cmd);
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        byte[] d;
        while ((d = s.next()) != null) buf.write(d);
        s.close();
        return buf.toString("UTF-8").replace("\r\n", "\n").trim();
    }

    public interface Progress { void onProgress(long sent, long total); }

    /** Pushes a file with the sync protocol (works on every Android version). */
    public void push(InputStream src, long size, String remotePath, int mode, Progress p) throws IOException {
        Stream s = open("sync:");
        ByteArrayOutputStream pending = new ByteArrayOutputStream();
        byte[] spec = (remotePath + "," + mode).getBytes("UTF-8");
        syncHeader(pending, "SEND", spec.length);
        pending.write(spec);
        byte[] buf = new byte[64 * 1024];
        long sent = 0;
        int n;
        while ((n = src.read(buf)) > 0) {
            syncHeader(pending, "DATA", n);
            pending.write(buf, 0, n);
            if (pending.size() >= maxData) { flush(s, pending); }
            sent += n;
            if (p != null) p.onProgress(sent, size);
        }
        syncHeader(pending, "DONE", (int) (System.currentTimeMillis() / 1000));
        flush(s, pending);

        // reply: OKAY or FAIL + message
        ByteArrayOutputStream reply = new ByteArrayOutputStream();
        while (reply.size() < 8) {
            byte[] d = s.next();
            if (d == null) throw new AdbException("push", "Device closed during push");
            reply.write(d);
        }
        byte[] r = reply.toByteArray();
        String id = new String(r, 0, 4, "US-ASCII");
        if (!"OKAY".equals(id)) {
            int len = ByteBuffer.wrap(r, 4, 4).order(ByteOrder.LITTLE_ENDIAN).getInt();
            while (reply.size() < 8 + len) {
                byte[] d = s.next();
                if (d == null) break;
                reply.write(d);
            }
            r = reply.toByteArray();
            String msg = new String(r, 8, Math.max(0, Math.min(len, r.length - 8)), "UTF-8");
            s.close();
            throw new AdbException("push", "Push failed: " + msg);
        }
        ByteArrayOutputStream quit = new ByteArrayOutputStream();
        syncHeader(quit, "QUIT", 0);
        try { flush(s, quit); } catch (IOException ignored) { }
        s.close();
    }

    private void flush(Stream s, ByteArrayOutputStream pending) throws IOException {
        byte[] b = pending.toByteArray();
        s.write(b, 0, b.length);
        pending.reset();
    }

    private static void syncHeader(ByteArrayOutputStream o, String id, int arg) throws IOException {
        o.write(id.getBytes("US-ASCII"));
        o.write(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(arg).array());
    }

    @Override
    public void close() {
        try { socket.close(); } catch (IOException ignored) { }
    }

    // ------------------------------------------------------------------ helpers

    static byte[] read(File f) throws IOException {
        FileInputStream in = new FileInputStream(f);
        try {
            ByteArrayOutputStream o = new ByteArrayOutputStream();
            byte[] b = new byte[4096];
            int n;
            while ((n = in.read(b)) > 0) o.write(b, 0, n);
            return o.toByteArray();
        } finally { in.close(); }
    }

    static void write(File f, byte[] data) throws IOException {
        FileOutputStream o = new FileOutputStream(f);
        try { o.write(data); } finally { o.close(); }
    }

    /** Small standard Base64 encoder (java.util.Base64 needs API 26). */
    static final class Base64 {
        private static final char[] A = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/".toCharArray();
        static String encode(byte[] d) {
            StringBuilder sb = new StringBuilder((d.length + 2) / 3 * 4);
            for (int i = 0; i < d.length; i += 3) {
                int b0 = d[i] & 0xff, b1 = i + 1 < d.length ? d[i + 1] & 0xff : 0, b2 = i + 2 < d.length ? d[i + 2] & 0xff : 0;
                sb.append(A[b0 >> 2]).append(A[((b0 & 3) << 4) | (b1 >> 4)]);
                sb.append(i + 1 < d.length ? A[((b1 & 15) << 2) | (b2 >> 6)] : '=');
                sb.append(i + 2 < d.length ? A[b2 & 63] : '=');
            }
            return sb.toString();
        }
    }
}
