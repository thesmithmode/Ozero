package ru.ozero.enginegfvpn;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

/** An authenticated SOCKS5 CONNECT followed by a verified HTTPS 204 request. */
final class GfVpnSocksProbe {
    private static final int TIMEOUT_MS = 4000;
    private static final String HOST = "www.gstatic.com";

    static long probe(int port, String username, String password) throws IOException {
        long start = System.nanoTime();
        try (Socket socket = new Socket(Proxy.NO_PROXY)) {
            socket.connect(new InetSocketAddress("127.0.0.1", port), TIMEOUT_MS);
            socket.setSoTimeout(TIMEOUT_MS);
            connect(socket, username, password, HOST, 443);
            SSLSocketFactory factory = (SSLSocketFactory) SSLSocketFactory.getDefault();
            try (SSLSocket tls = (SSLSocket) factory.createSocket(socket, HOST, 443, true)) {
                tls.setSoTimeout(TIMEOUT_MS);
                javax.net.ssl.SSLParameters params = tls.getSSLParameters();
                params.setEndpointIdentificationAlgorithm("HTTPS");
                tls.setSSLParameters(params);
                tls.startHandshake();
                tls.getOutputStream().write(("GET /generate_204 HTTP/1.1\r\nHost: " + HOST +
                    "\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
                DataInputStream input = new DataInputStream(tls.getInputStream());
                StringBuilder status = new StringBuilder();
                while (status.length() < 256) {
                    int ch = input.readUnsignedByte();
                    if (ch == '\n') break;
                    if (ch != '\r') status.append((char) ch);
                }
                if (!status.toString().matches("HTTP/1\\.[01] 204( .*)?")) {
                    throw new IOException("Routed probe did not return 204");
                }
            }
        }
        return (System.nanoTime() - start) / 1_000_000L;
    }

    static void connect(Socket socket, String user, String password, String host, int port) throws IOException {
        byte[] u = user.getBytes(StandardCharsets.UTF_8);
        byte[] p = password.getBytes(StandardCharsets.UTF_8);
        byte[] h = host.getBytes(StandardCharsets.US_ASCII);
        if (u.length == 0 || u.length > 255 || p.length == 0 || p.length > 255 ||
            h.length == 0 || h.length > 255 || port < 1 || port > 65535) {
            throw new IOException("Invalid SOCKS parameters");
        }
        OutputStream out = socket.getOutputStream();
        DataInputStream in = new DataInputStream(socket.getInputStream());
        out.write(new byte[] {5, 1, 2});
        out.flush();
        if (in.readUnsignedByte() != 5 || in.readUnsignedByte() != 2) throw new IOException("SOCKS authentication required");
        out.write(new byte[] {1, (byte) u.length});
        out.write(u);
        out.write(p.length);
        out.write(p);
        out.flush();
        if (in.readUnsignedByte() != 1 || in.readUnsignedByte() != 0) throw new IOException("SOCKS authentication failed");
        out.write(new byte[] {5, 1, 0, 3, (byte) h.length});
        out.write(h);
        out.write(port >>> 8);
        out.write(port & 255);
        out.flush();
        if (in.readUnsignedByte() != 5 || in.readUnsignedByte() != 0 || in.readUnsignedByte() != 0) {
            throw new IOException("SOCKS connection failed");
        }
        int type = in.readUnsignedByte();
        int length;
        switch (type) {
            case 1: length = 4; break;
            case 4: length = 16; break;
            case 3: length = in.readUnsignedByte(); break;
            default: throw new IOException("Invalid SOCKS address type");
        }
        in.readFully(new byte[length + 2]);
    }
}
