package ru.ozero.enginegfvpn;

import static org.junit.jupiter.api.Assertions.*;
import java.io.DataInputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class GfVpnSocksProbeTest {
    @Test void authenticatesAndConnectsByDomain() throws Exception {
        ExecutorService worker = Executors.newSingleThreadExecutor();
        try (ServerSocket server = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            Future<?> peer = worker.submit(() -> {
                try (Socket s = server.accept()) {
                    s.setSoTimeout(2000);
                    DataInputStream in = new DataInputStream(s.getInputStream());
                    byte[] greeting = new byte[3];
                    in.readFully(greeting);
                    assertArrayEquals(new byte[]{5, 1, 2}, greeting);
                    s.getOutputStream().write(new byte[]{5, 2});
                    assertEquals(1, in.readUnsignedByte());
                    byte[] user = new byte[in.readUnsignedByte()];
                    in.readFully(user);
                    byte[] password = new byte[in.readUnsignedByte()];
                    in.readFully(password);
                    assertEquals("user", new String(user, StandardCharsets.UTF_8));
                    assertEquals("secret", new String(password, StandardCharsets.UTF_8));
                    s.getOutputStream().write(new byte[]{1, 0});
                    byte[] connect = new byte[4];
                    in.readFully(connect);
                    assertArrayEquals(new byte[]{5, 1, 0, 3}, connect);
                    byte[] hostname = new byte[in.readUnsignedByte()];
                    in.readFully(hostname);
                    assertEquals("example.com", new String(hostname, StandardCharsets.US_ASCII));
                    assertEquals(443, in.readUnsignedShort());
                    s.getOutputStream().write(new byte[]{5, 0, 0, 1, 127, 0, 0, 1, 0, 1});
                } catch (IOException error) { throw new AssertionError(error); }
            });
            try (Socket client = new Socket(InetAddress.getLoopbackAddress(), server.getLocalPort())) {
                client.setSoTimeout(2000);
                GfVpnSocksProbe.connect(client, "user", "secret", "example.com", 443);
            }
            peer.get(3, TimeUnit.SECONDS);
        } finally { worker.shutdownNow(); }
    }

    @Test void refusesUnauthenticatedProxy() throws Exception {
        ExecutorService worker = Executors.newSingleThreadExecutor();
        try (ServerSocket server = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            Future<?> peer = worker.submit(() -> {
                try (Socket s = server.accept()) {
                    s.setSoTimeout(2000);
                    new DataInputStream(s.getInputStream()).readFully(new byte[3]);
                    s.getOutputStream().write(new byte[]{5, 0});
                } catch (IOException error) { throw new AssertionError(error); }
            });
            try (Socket client = new Socket(InetAddress.getLoopbackAddress(), server.getLocalPort())) {
                client.setSoTimeout(2000);
                assertThrows(IOException.class, () -> GfVpnSocksProbe.connect(client, "user", "secret", "example.com", 443));
            }
            peer.get(3, TimeUnit.SECONDS);
        } finally { worker.shutdownNow(); }
    }
}
