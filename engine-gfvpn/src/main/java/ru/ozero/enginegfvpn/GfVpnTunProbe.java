package ru.ozero.enginegfvpn;

import java.io.IOException;

/** Check the attached native TUN on both sides of the authenticated route probe. */
final class GfVpnTunProbe {
    interface Counters {
        long[] read() throws Exception;
    }

    interface Route {
        long probe() throws Exception;
    }

    static final class Result {
        final long latencyMs;
        final long bytesOut;
        final long bytesIn;

        Result(long latencyMs, long[] counters) {
            this.latencyMs = latencyMs;
            bytesOut = counters[1];
            bytesIn = counters[3];
        }
    }

    private GfVpnTunProbe() { }

    static Result run(Counters counters, Route route) throws Exception {
        GfVpnTunCounters.validate(counters.read());
        long latency = route.probe();
        if (latency < 0) throw new IOException("Invalid route latency");
        return new Result(latency, GfVpnTunCounters.validate(counters.read()));
    }
}
