package ru.ozero.enginegfvpn;

import java.io.IOException;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GfVpnTunCountersTest {
    @Test void acceptsIdleAndActiveNativeCounters() throws Exception {
        assertArrayEquals(new long[] {0, 0, 0, 0}, GfVpnTunCounters.validate(new long[4]));
        assertArrayEquals(new long[] {1, 200, 3, 400}, GfVpnTunCounters.validate(new long[] {1, 200, 3, 400}));
    }

    @Test void rejectsMissingTruncatedAndNegativeCounters() {
        assertThrows(IOException.class, () -> GfVpnTunCounters.validate(null));
        assertThrows(IOException.class, () -> GfVpnTunCounters.validate(new long[3]));
        assertThrows(IOException.class, () -> GfVpnTunCounters.validate(new long[] {1, -1, 3, 4}));
    }
}
