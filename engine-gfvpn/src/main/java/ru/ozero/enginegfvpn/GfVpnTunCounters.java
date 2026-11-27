package ru.ozero.enginegfvpn;

import java.io.IOException;

/** HEV's documented JNI order is TX packets, TX bytes, RX packets, RX bytes. */
final class GfVpnTunCounters {
    private GfVpnTunCounters() { }

    static long[] validate(long[] stats) throws IOException {
        if (stats == null || stats.length < 4) throw new IOException("Invalid HEV statistics");
        for (int i = 0; i < 4; i++) {
            if (stats[i] < 0) throw new IOException("Invalid HEV counter");
        }
        return stats;
    }
}
