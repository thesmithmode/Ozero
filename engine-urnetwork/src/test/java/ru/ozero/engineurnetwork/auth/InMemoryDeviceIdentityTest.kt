package ru.ozero.engineurnetwork.auth

import kotlinx.coroutines.test.runTest
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters
import org.bouncycastle.crypto.signers.Ed25519Signer
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class InMemoryDeviceIdentityTest {

    @Test
    fun `sign производит подпись 64 байта - Ed25519 контракт`() = runTest {
        val identity = InMemoryUrnetworkDeviceIdentity()
        val sig = identity.sign("hello".toByteArray(Charsets.UTF_8))
        assertEquals(64, sig.size, "Ed25519 signature всегда 64 байта")
    }

    @Test
    fun `pubkey стабилен между вызовами — seed детерминирован`() = runTest {
        val seed = ByteArray(32) { (it * 7).toByte() }
        val a = InMemoryUrnetworkDeviceIdentity(seed)
        val b = InMemoryUrnetworkDeviceIdentity(seed)
        assertEquals(a.pubkeyBase58(), b.pubkeyBase58(), "тот же seed → тот же pubkey")
    }

    @Test
    fun `разные seed дают разные pubkey - регрессия против fixed key`() = runTest {
        val a = InMemoryUrnetworkDeviceIdentity(ByteArray(32) { 1 })
        val b = InMemoryUrnetworkDeviceIdentity(ByteArray(32) { 2 })
        assertNotEquals(a.pubkeyBase58(), b.pubkeyBase58())
    }

    @Test
    fun `signature валидна по Ed25519 verify - server примет`() = runTest {
        val seed = ByteArray(32) { (it + 3).toByte() }
        val identity = InMemoryUrnetworkDeviceIdentity(seed)
        val pubkeyB58 = identity.pubkeyBase58()
        val pubkeyBytes = Base58.decode(pubkeyB58)
        val message = "ozero-auth-v1:$pubkeyB58".toByteArray(Charsets.UTF_8)
        val sig = identity.sign(message)
        val verifier = Ed25519Signer().apply {
            init(false, Ed25519PublicKeyParameters(pubkeyBytes, 0))
            update(message, 0, message.size)
        }
        assertTrue(verifier.verifySignature(sig), "Ed25519 verify обязан принять подпись")
    }

    @Test
    fun `неправильный seed_size бросает исключение`() {
        val ex = runCatching { InMemoryUrnetworkDeviceIdentity(ByteArray(31)) }.exceptionOrNull()
        assertTrue(ex != null, "31-byte seed должен упасть — Ed25519 требует ровно 32")
    }

    @Test
    fun `pubkey закодирован в Base58 без 0OIl символов`() = runTest {
        val identity = InMemoryUrnetworkDeviceIdentity()
        val pk = identity.pubkeyBase58()
        val forbidden = setOf('0', 'O', 'I', 'l')
        for (c in pk) {
            assertTrue(c !in forbidden, "Base58 не должен содержать $c в $pk")
        }
    }

    @Test
    fun `external seed mutation после ctor не влияет на pubkey - defensive copy ownership`() = runTest {
        val seed = ByteArray(32) { (it + 5).toByte() }
        val identity = InMemoryUrnetworkDeviceIdentity(seed)
        val pubkeyBefore = identity.pubkeyBase58()
        for (i in seed.indices) {
            seed[i] = 0xff.toByte()
        }
        val pubkeyAfter = identity.pubkeyBase58()
        assertEquals(
            pubkeyBefore,
            pubkeyAfter,
            "InMemoryUrnetworkDeviceIdentity обязан брать defensive copy seed — иначе caller мутирует " +
                "и identity ломается. seed.copyOf() в init().",
        )
    }

    @Test
    fun `export and import seed restores same pubkey`() = runTest {
        val source = InMemoryUrnetworkDeviceIdentity(ByteArray(32) { (it + 11).toByte() })
        val target = InMemoryUrnetworkDeviceIdentity(ByteArray(32) { (it + 22).toByte() })
        val seed = source.exportSeedForBackup()

        assertTrue(target.importSeedFromBackup(seed))
        assertEquals(source.pubkeyBase58(), target.pubkeyBase58())
    }

    @Test
    fun `import rejects invalid seed size and keeps current identity`() = runTest {
        val identity = InMemoryUrnetworkDeviceIdentity(ByteArray(32) { (it + 9).toByte() })
        val before = identity.pubkeyBase58()

        assertEquals(false, identity.importSeedFromBackup(ByteArray(31)))

        assertEquals(before, identity.pubkeyBase58())
    }

    @Test
    fun `exportSeedForBackup returns defensive copy`() = runTest {
        val identity = InMemoryUrnetworkDeviceIdentity(ByteArray(32) { (it + 13).toByte() })
        val before = identity.pubkeyBase58()
        val exported = identity.exportSeedForBackup()

        exported.fill(0)

        assertEquals(before, identity.pubkeyBase58())
    }

    @Test
    fun `importSeedFromBackup copies input seed defensively`() = runTest {
        val seed = ByteArray(32) { (it + 17).toByte() }
        val expected = InMemoryUrnetworkDeviceIdentity(seed).pubkeyBase58()
        val identity = InMemoryUrnetworkDeviceIdentity(ByteArray(32) { 1 })

        assertTrue(identity.importSeedFromBackup(seed))
        seed.fill(0)

        assertEquals(expected, identity.pubkeyBase58())
    }

    @Test
    fun `sign empty message produces verifiable Ed25519 signature`() = runTest {
        val seed = ByteArray(32) { (it + 21).toByte() }
        val identity = InMemoryUrnetworkDeviceIdentity(seed)
        val pubkeyBytes = Base58.decode(identity.pubkeyBase58())
        val message = ByteArray(0)
        val sig = identity.sign(message)
        val verifier = Ed25519Signer().apply {
            init(false, Ed25519PublicKeyParameters(pubkeyBytes, 0))
            update(message, 0, message.size)
        }

        assertTrue(verifier.verifySignature(sig))
    }

    @Test
    fun `signature fails verification for different message`() = runTest {
        val seed = ByteArray(32) { (it + 25).toByte() }
        val identity = InMemoryUrnetworkDeviceIdentity(seed)
        val pubkeyBytes = Base58.decode(identity.pubkeyBase58())
        val sig = identity.sign("first".toByteArray())
        val verifier = Ed25519Signer().apply {
            init(false, Ed25519PublicKeyParameters(pubkeyBytes, 0))
            val other = "second".toByteArray()
            update(other, 0, other.size)
        }

        assertEquals(false, verifier.verifySignature(sig))
    }

    @Test
    fun `import valid seed changes identity`() = runTest {
        val identity = InMemoryUrnetworkDeviceIdentity(ByteArray(32) { 1 })
        val before = identity.pubkeyBase58()

        assertTrue(identity.importSeedFromBackup(ByteArray(32) { 2 }))

        assertNotEquals(before, identity.pubkeyBase58())
    }
}
