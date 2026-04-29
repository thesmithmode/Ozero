package ru.ozero.commonvpn

import android.os.ParcelFileDescriptor
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.assertEquals

class HevTunnelGatewayShutdownOrderTest {

    @BeforeEach
    fun setUp() {
        mockkObject(hev.TProxyService)
        every { hev.TProxyService.loadOnce() } answers {}
        every { hev.TProxyService.libraryLoaded } returns true
        every { hev.TProxyService.loadError } returns null
    }

    @AfterEach
    fun tearDown() {
        unmockkObject(hev.TProxyService)
    }

    @Test
    fun `stop closes duped pfd ДО nativeStop — иначе pthread_join зависает на Nubia`(
        @TempDir tmp: File,
    ) {
        val events = mutableListOf<String>()
        val duped: ParcelFileDescriptor = mockk(relaxed = true) {
            every { fd } returns 4242
            every { close() } answers {
                events.add("closeDuped")
            }
        }
        val original: ParcelFileDescriptor = mockk {
            every { fd } returns 42
            every { dup() } returns duped
        }
        val gateway = NativeHevTunnelGateway(
            cacheDir = tmp,
            nativeStart = { _, _ -> 0 },
            nativeStop = { events.add("nativeStop") },
        )
        gateway.start(HevTunnelConfig(tunPfd = original, socksAddress = "127.0.0.1", socksPort = 1080))

        gateway.stop()

        assertEquals(
            listOf("closeDuped", "nativeStop"),
            events,
            "closeDuped обязан вызываться ДО nativeStop. Иначе upstream hev v2.7.0 native_stop_service " +
                "делает pthread_join(work_thread), worker всё ещё читает TUN fd → EOF не приходит → " +
                "join зависает (полевой тест Nubia/RedMagic: ~59 сек до FORCE STOP).",
        )
    }

    @Test
    fun `stop без предшествующего start не ломается и не зовёт closeDuped дважды`(
        @TempDir tmp: File,
    ) {
        var stopCalled = 0
        val gateway = NativeHevTunnelGateway(
            cacheDir = tmp,
            nativeStart = { _, _ -> 0 },
            nativeStop = { stopCalled++ },
        )

        gateway.stop()

        assertEquals(1, stopCalled, "nativeStop вызывается даже без duped — гарантия teardown")
    }

    @Test
    fun `stop вызывается дважды подряд — closeDuped не падает на повторе`(
        @TempDir tmp: File,
    ) {
        val duped: ParcelFileDescriptor = mockk(relaxed = true) {
            every { fd } returns 1
        }
        val original: ParcelFileDescriptor = mockk {
            every { fd } returns 2
            every { dup() } returns duped
        }
        var nativeStops = 0
        val gateway = NativeHevTunnelGateway(
            cacheDir = tmp,
            nativeStart = { _, _ -> 0 },
            nativeStop = { nativeStops++ },
        )
        gateway.start(HevTunnelConfig(tunPfd = original, socksAddress = "127.0.0.1", socksPort = 1080))

        gateway.stop()
        gateway.stop()

        verify(exactly = 1) { duped.close() }
        assertEquals(2, nativeStops, "nativeStop зовётся каждый раз — идемпотентность ниже на стороне upstream")
    }
}
