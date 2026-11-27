package ru.ozero.enginegfvpn;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GfVpnTunProbeTest {
    @Test void checksTunBeforeAndAfterRouteAndAllowsIdleTunnel() throws Exception {
        List<String> order = new ArrayList<>();
        GfVpnTunProbe.Result result = GfVpnTunProbe.run(
            () -> { order.add("tun"); return new long[4]; },
            () -> { order.add("route"); return 0; }
        );
        assertEquals(List.of("tun", "route", "tun"), order);
        assertEquals(0, result.latencyMs);
        assertEquals(0, result.bytesOut);
        assertEquals(0, result.bytesIn);
    }

    @Test void refusesHealthySocksWhenTunWasNeverAttached() {
        AtomicInteger calls = new AtomicInteger();
        assertThrows(IOException.class, () -> GfVpnTunProbe.run(
            () -> { throw new IOException("not attached"); },
            () -> { calls.incrementAndGet(); return 1; }
        ));
        assertEquals(0, calls.get());
    }

    @Test void refusesNativeFailureDuringSuccessfulSocksProbe() {
        AtomicInteger reads = new AtomicInteger();
        assertThrows(IOException.class, () -> GfVpnTunProbe.run(
            () -> reads.incrementAndGet() == 1 ? new long[4] : null,
            () -> 5
        ));
        assertEquals(2, reads.get());
    }

    @Test void refusesRouteFailureEvenWithHealthyTun() {
        assertThrows(IOException.class, () -> GfVpnTunProbe.run(
            () -> new long[4], () -> { throw new IOException("route"); }
        ));
        assertThrows(IOException.class, () -> GfVpnTunProbe.run(() -> new long[4], () -> -1));
    }

    @Test void reportsPostProbeCounters() throws Exception {
        AtomicInteger reads = new AtomicInteger();
        GfVpnTunProbe.Result result = GfVpnTunProbe.run(
            () -> reads.incrementAndGet() == 1 ? new long[4] : new long[]{1, 200, 3, 400}, () -> 7
        );
        assertEquals(7, result.latencyMs);
        assertEquals(200, result.bytesOut);
        assertEquals(400, result.bytesIn);
    }
}
