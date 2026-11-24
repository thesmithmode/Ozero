package ru.ozero.commonnet

import org.junit.jupiter.api.Test
import java.net.InetAddress
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StunBindingTest {
    private val transaction = ByteArray(12) { it.toByte() }

    @Test
    fun `request includes cookie and transaction`() {
        val request = StunBinding.request(transaction)

        assertEquals(20, request.size)
        assertEquals(1, ByteBuffer.wrap(request).short.toInt())
        assertEquals(0x2112A442, ByteBuffer.wrap(request).getInt(4))
        assertTrue(transaction.contentEquals(request.copyOfRange(8, 20)))
    }

    @Test
    fun `decodes ipv4 and ipv6 mapping after padded attribute`() {
        for (host in listOf("192.0.2.1", "2001:db8::1")) {
            val address = InetAddress.getByName(host)
            val response = stunResponse(transaction, address, 54321, paddedAttribute = true)

            assertEquals(InetSocketAddress(address, 54321), StunBinding.mappedAddress(response, transaction))
        }
    }

    @Test
    fun `rejects malformed or unrelated responses`() {
        val valid = stunResponse(transaction, InetAddress.getByName("192.0.2.1"), 1234)
        val invalid = listOf(
            valid.copyOf(19),
            valid.copyOf(valid.size - 1),
            valid.copyOf().apply { this[0] = 0 },
            valid.copyOf().apply { this[4] = 0 },
            valid.copyOf().apply { this[8] = 42 },
            valid.copyOf().apply { this[23] = 127 },
            valid.copyOf().apply { this[25] = 3 },
            valid.copyOf().apply { this[24] = 1 },
            valid.copyOf().apply { this[26] = 0x21; this[27] = 0x12 },
            valid.copyOf().apply { this[21] = 1 },
        )

        invalid.forEach { assertNull(StunBinding.mappedAddress(it, transaction)) }
        assertNull(StunBinding.mappedAddress(valid, ByteArray(11)))
    }
}

internal fun stunResponse(
    transaction: ByteArray,
    address: InetAddress,
    port: Int,
    paddedAttribute: Boolean = false,
): ByteArray {
    val raw = address.address
    val prefixSize = if (paddedAttribute) 8 else 0
    val bodySize = 8 + raw.size + prefixSize
    val mask = ByteBuffer.allocate(16).putInt(0x2112A442).put(transaction).array()
    val response = ByteBuffer.allocate(20 + bodySize)
        .putShort(0x0101)
        .putShort(bodySize.toShort())
        .putInt(0x2112A442)
        .put(transaction)
    if (paddedAttribute) response.putShort(0x8022.toShort()).putShort(1).putInt(0)
    response.putShort(0x0020).putShort((4 + raw.size).toShort())
        .put(0).put(if (raw.size == 4) 1.toByte() else 2.toByte())
        .putShort((port xor 0x2112).toShort())
    raw.indices.forEach { index -> response.put((raw[index].toInt() xor mask[index].toInt()).toByte()) }
    return response.array()
}
