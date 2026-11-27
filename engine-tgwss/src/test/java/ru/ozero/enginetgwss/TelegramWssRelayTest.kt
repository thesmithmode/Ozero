package ru.ozero.enginetgwss

import okio.ByteString.Companion.toByteString
import org.junit.jupiter.api.Test
import java.io.DataInputStream
import java.net.Socket
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TelegramWssRelayTest {
    @Test
    fun `relay transfers encrypted packets without changing boundaries and receives binary replies`() {
        val webSockets = FakeTelegramWebSockets()
        TelegramWssProxy(webSockets) {}.use { proxy ->
            Socket("127.0.0.1", proxy.start()).use { socket ->
                socket.soTimeout = 2000
                val input = DataInputStream(socket.getInputStream())
                val output = socket.getOutputStream()
                output.write(socksRequest(41))
                output.flush()
                assertContentEquals(
                    byteArrayOf(5, 0, 5, 0, 0, 1, 0, 0, 0, 0, 0, 0),
                    ByteArray(12).also { input.readFully(it) },
                )
                val (header, packet) = initAndPacket()
                output.write(header)
                output.write(packet, 0, 2)
                output.flush()
                output.write(packet, 2, packet.size - 2)
                output.flush()

                val session = assertNotNull(webSockets.sessions.poll(2, TimeUnit.SECONDS))
                assertEquals("kws2.web.telegram.org", session.request().url.host)
                assertEquals("https", session.request().url.scheme)
                assertContentEquals(header, assertNotNull(session.sent.poll(2, TimeUnit.SECONDS)).toByteArray())
                assertContentEquals(packet, assertNotNull(session.sent.poll(2, TimeUnit.SECONDS)).toByteArray())
                val response = byteArrayOf(9, 8, 7)
                session.listener.onMessage(session, response.toByteString())
                assertContentEquals(response, ByteArray(3).also { input.readFully(it) })
                assertEquals(3L, proxy.bytesIn)
                assertTrue(proxy.activeConnections > 0)
                session.listener.onMessage(session, "invalid text")
                assertEquals(-1, input.read())
                assertTrue(session.cancelled.get())
            }
        }
    }

    @Test
    fun `remote probe requires negotiated binary protocol and stops on close`() {
        val webSockets = FakeTelegramWebSockets()
        TelegramWssProxy(webSockets) {}.use { proxy ->
            assertNotNull(proxy.probeRemote(100))
            assertTrue(assertNotNull(webSockets.sessions.poll()).cancelled.get())
            proxy.close()
            assertNull(proxy.probeRemote(100))
        }
        TelegramWssProxy(FakeTelegramWebSockets(protocol = null)) {}.use {
            assertNull(it.probeRemote(10))
        }
        TelegramWssProxy(FakeTelegramWebSockets(connectFails = true)) {}.use {
            assertNull(it.probeRemote(10))
        }
    }

    @Test
    fun `media endpoint falls back only before init is sent`() {
        val webSockets = FakeTelegramWebSockets(rejectedHosts = setOf("kws2-1.web.telegram.org"))
        TelegramWssProxy(webSockets) {}.use { proxy ->
            Socket("127.0.0.1", proxy.start()).use { socket ->
                socket.soTimeout = 2000
                val input = DataInputStream(socket.getInputStream())
                socket.getOutputStream().apply {
                    write(socksRequest(151))
                    flush()
                }
                input.readFully(ByteArray(12))
                val (header, packet) = initAndPacket(-2)
                socket.getOutputStream().apply {
                    write(header)
                    write(packet)
                    flush()
                }
                val first = assertNotNull(webSockets.sessions.poll(2, TimeUnit.SECONDS))
                val second = assertNotNull(webSockets.sessions.poll(2, TimeUnit.SECONDS))
                assertEquals("kws2-1.web.telegram.org", first.request().url.host)
                assertEquals("kws2.web.telegram.org", second.request().url.host)
                assertTrue(first.sent.isEmpty())
                assertContentEquals(header, assertNotNull(second.sent.poll(2, TimeUnit.SECONDS)).toByteArray())
                assertContentEquals(packet, assertNotNull(second.sent.poll(2, TimeUnit.SECONDS)).toByteArray())
                second.listener.onFailure(second, java.io.IOException("closed"), null)
                assertEquals(-1, input.read())
                assertNull(webSockets.sessions.poll(100, TimeUnit.MILLISECONDS))
            }
        }
    }

    @Test
    fun `blocked direct handshake cannot delay WARP or duplicate MTProto init`() {
        val direct = FakeTelegramWebSockets(deferOpen = true)
        val fallback = FakeTelegramWebSockets()
        TelegramWssProxy(direct, fallbackWebSockets = fallback) {}.use { proxy ->
            Socket("127.0.0.1", proxy.start()).use { socket ->
                socket.soTimeout = 2000
                val input = DataInputStream(socket.getInputStream())
                socket.getOutputStream().apply {
                    write(socksRequest(41))
                    flush()
                }
                input.readFully(ByteArray(12))
                val (header, packet) = initAndPacket()
                socket.getOutputStream().apply {
                    write(header)
                    write(packet)
                    flush()
                }

                val slow = assertNotNull(direct.sessions.poll(2, TimeUnit.SECONDS))
                val winner = assertNotNull(fallback.sessions.poll(2, TimeUnit.SECONDS))
                assertContentEquals(header, assertNotNull(winner.sent.poll(2, TimeUnit.SECONDS)).toByteArray())
                assertContentEquals(packet, assertNotNull(winner.sent.poll(2, TimeUnit.SECONDS)).toByteArray())
                slow.open()
                assertTrue(slow.cancelled.get())
                assertTrue(slow.sent.isEmpty())
                slow.listener.onFailure(slow, java.io.IOException("late failure"), null)
                winner.listener.onMessage(winner, byteArrayOf(42).toByteString())
                assertEquals(42, input.read())
            }
        }
    }

    @Test
    fun `documented TLS endpoint works when Web K host is unavailable`() {
        val webSockets = FakeTelegramWebSockets(rejectedHosts = setOf("kws2.web.telegram.org"))
        TelegramWssProxy(webSockets) {}.use { proxy ->
            assertNotNull(proxy.probeRemote(100))
            assertEquals("kws2.web.telegram.org", assertNotNull(webSockets.sessions.poll()).request().url.host)
            assertEquals("venus.web.telegram.org", assertNotNull(webSockets.sessions.poll()).request().url.host)
            Socket("127.0.0.1", proxy.start()).use { socket ->
                socket.soTimeout = 2000
                val input = DataInputStream(socket.getInputStream())
                socket.getOutputStream().apply {
                    write(socksRequest(41))
                    flush()
                }
                input.readFully(ByteArray(12))
                val (header, packet) = initAndPacket()
                socket.getOutputStream().apply {
                    write(header)
                    write(packet)
                    flush()
                }
                val rejected = assertNotNull(webSockets.sessions.poll(2, TimeUnit.SECONDS))
                val accepted = assertNotNull(webSockets.sessions.poll(2, TimeUnit.SECONDS))
                assertEquals("kws2.web.telegram.org", rejected.request().url.host)
                assertEquals("venus.web.telegram.org", accepted.request().url.host)
                assertTrue(rejected.sent.isEmpty())
                assertContentEquals(header, assertNotNull(accepted.sent.poll(2, TimeUnit.SECONDS)).toByteArray())
                assertContentEquals(packet, assertNotNull(accepted.sent.poll(2, TimeUnit.SECONDS)).toByteArray())
            }
        }
    }

    private fun socksRequest(lastOctet: Int): ByteArray =
        byteArrayOf(
            5, 1, 0, 5, 1, 0, 1, 149.toByte(), 154.toByte(), 167.toByte(), lastOctet.toByte(), 1, 187.toByte(),
        )

    private fun initAndPacket(dc: Int = 2): Pair<ByteArray, ByteArray> {
        val header = ByteArray(64) { it.toByte() }
        val cipher = Cipher.getInstance("AES/CTR/NoPadding")
        cipher.init(
            Cipher.ENCRYPT_MODE,
            SecretKeySpec(header.copyOfRange(8, 40), "AES"),
            IvParameterSpec(header.copyOfRange(40, 56)),
        )
        val stream = cipher.update(ByteArray(64))
        for (index in 56..59) header[index] = (stream[index].toInt() xor 0xef).toByte()
        header[60] = (stream[60].toInt() xor (dc and 0xff)).toByte()
        header[61] = (stream[61].toInt() xor ((dc shr 8) and 0xff)).toByte()
        return header to cipher.update(byteArrayOf(2) + ByteArray(8))
    }
}
