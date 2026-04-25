package ru.ozero.enginehysteria2.cgnat

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class CgnatDetectorTest {

    private fun detect(vararg ips: String): NatStatus =
        CgnatDetector(provider = { ips.toList() }).detect()

    // ---- CGNAT (RFC 6598: 100.64.0.0/10 = 100.64.0.0 .. 100.127.255.255) ----

    @Test fun cgnatLowerBoundary() = assertEquals(NatStatus.CGNAT, detect("100.64.0.0"))

    @Test fun cgnatUpperBoundary() = assertEquals(NatStatus.CGNAT, detect("100.127.255.255"))

    @Test fun cgnatTypicalMobileIsp() = assertEquals(NatStatus.CGNAT, detect("100.96.42.7"))

    @Test
    fun belowCgnatRangeIsNotCgnat() {
        assertEquals(NatStatus.OPEN, detect("100.63.255.255"))
    }

    @Test
    fun aboveCgnatRangeIsNotCgnat() {
        assertEquals(NatStatus.OPEN, detect("100.128.0.0"))
    }

    // ---- private RFC1918 → UNKNOWN (нужен STUN, отложен на E9) ----

    @Test fun privateClassA() = assertEquals(NatStatus.UNKNOWN, detect("10.0.0.5"))

    @Test fun privateClassB() = assertEquals(NatStatus.UNKNOWN, detect("172.16.0.10"))

    @Test fun privateClassC() = assertEquals(NatStatus.UNKNOWN, detect("192.168.1.100"))

    @Test fun linkLocal() = assertEquals(NatStatus.UNKNOWN, detect("169.254.1.1"))

    // ---- public → OPEN ----

    @Test fun publicIpv4() = assertEquals(NatStatus.OPEN, detect("8.8.8.8"))

    // ---- IPv6 (нет понятия CGNAT) ----

    @Test fun ipv6UlaTreatedAsUnknown() = assertEquals(NatStatus.UNKNOWN, detect("fc00::1"))

    @Test fun ipv6PublicIsOpen() = assertEquals(NatStatus.OPEN, detect("2001:db8::1"))

    // ---- комбинации ----

    @Test
    fun cgnatBeatsPrivate() {
        assertEquals(NatStatus.CGNAT, detect("192.168.1.10", "100.64.5.5"))
    }

    @Test
    fun publicBeatsPrivate() {
        assertEquals(NatStatus.OPEN, detect("192.168.1.10", "203.0.113.5"))
    }

    @Test
    fun cgnatBeatsPublic() {
        // На мобиле публичного нет, есть только CGNAT — детектор должен ответить CGNAT
        // даже если в списке вдруг оказался публичный (например VPN-туннель уже поднят).
        assertEquals(NatStatus.CGNAT, detect("100.96.0.1", "8.8.8.8"))
    }

    @Test fun emptyListUnknown() = assertEquals(NatStatus.UNKNOWN, detect())

    @Test
    fun malformedAddressIgnored() {
        assertEquals(NatStatus.UNKNOWN, detect("not-an-ip"))
    }

    @Test
    fun loopbackIgnored() {
        assertEquals(NatStatus.UNKNOWN, detect("127.0.0.1", "::1"))
    }
}
