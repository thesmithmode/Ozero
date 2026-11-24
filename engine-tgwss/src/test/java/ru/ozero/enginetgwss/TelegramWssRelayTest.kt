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
                output.write(byteArrayOf(5, 1, 0, 5, 1, 0, 1, 149.toByte(), 154.toByte(), 167.toByte(), 41, 1, 187.toByte()))
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

    private fun initAndPacket(): Pair<ByteArray, ByteArray> {
        val header = ByteArray(64) { it.toByte() }
        val cipher = Cipher.getInstance("AES/CTR/NoPadding")
        cipher.init(
            Cipher.ENCRYPT_MODE,
            SecretKeySpec(header.copyOfRange(8, 40), "AES"),
            IvParameterSpec(header.copyOfRange(40, 56)),
        )
        val stream = cipher.update(ByteArray(64))
        for (index in 56..59) header[index] = (stream[index].toInt() xor 0xef).toByte()
        header[60] = (stream[60].toInt() xor 2).toByte()
        header[61] = stream[61]
        return header to cipher.update(byteArrayOf(2) + ByteArray(8))
    }
}
