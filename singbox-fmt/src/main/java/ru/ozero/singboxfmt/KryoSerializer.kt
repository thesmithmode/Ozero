package ru.ozero.singboxfmt

import com.esotericsoftware.kryo.Kryo
import com.esotericsoftware.kryo.KryoException
import com.esotericsoftware.kryo.io.ByteBufferInput
import com.esotericsoftware.kryo.io.ByteBufferOutput
import com.esotericsoftware.kryo.serializers.FieldSerializer
import com.esotericsoftware.kryo.util.Pool
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer

object KryoSerializer {
    private const val MAGIC = 0x4f5a424e
    private const val FORMAT_VERSION = 1
    private const val HEADER_SIZE = 6

    private val pool = object : Pool<Kryo>(true, false, 4) {
        override fun create(): Kryo = Kryo().apply {
            isRegistrationRequired = false
            register(VLESSBean::class.java)
            register(VMessBean::class.java)
            register(TrojanBean::class.java)
            register(ShadowsocksBean::class.java)
            register(StandardV2RayBean::class.java)
            register(AbstractBean::class.java)
        }
    }

    fun serialize(bean: AbstractBean): ByteArray {
        val kryo = pool.obtain()
        return try {
            val baos = ByteArrayOutputStream(256)
            ByteBufferOutput(baos).use { out ->
                out.writeInt(MAGIC)
                out.writeByte(FORMAT_VERSION)
                out.writeByte(protocolType(bean))
                kryo.writeClassAndObject(out, bean)
                out.flush()
            }
            baos.toByteArray()
        } finally {
            pool.free(kryo)
        }
    }

    @Suppress("UNCHECKED_CAST")
    fun <T : AbstractBean> copy(bean: T): T {
        val kryo = pool.obtain()
        return try {
            val baos = ByteArrayOutputStream(256)
            ByteBufferOutput(baos).use { out ->
                kryo.writeClassAndObject(out, bean)
                out.flush()
            }
            ByteBufferInput(baos.toByteArray()).use { inp ->
                kryo.readClassAndObject(inp) as T
            }
        } finally {
            pool.free(kryo)
        }
    }

    @Suppress("UNCHECKED_CAST")
    fun <T : AbstractBean> deserialize(bytes: ByteArray): T {
        return deserializeWithMigration(bytes).bean as T
    }

    fun deserializeWithMigration(bytes: ByteArray): DecodedBean {
        if (isVersioned(bytes)) return DecodedBean(readVersioned(bytes), migratedBlob = null)
        val legacyV1 = runCatching { readLegacy(bytes, legacyV1Pool) }
        val bean = legacyV1.getOrElse { readLegacy(bytes, pool) }
        return DecodedBean(bean, migratedBlob = serialize(bean))
    }

    data class DecodedBean(
        val bean: AbstractBean,
        val migratedBlob: ByteArray?,
    )

    private val legacyV1Pool = object : Pool<Kryo>(true, false, 4) {
        override fun create(): Kryo = Kryo().apply {
            isRegistrationRequired = false
            registerLegacyStandard(VLESSBean::class.java)
            registerLegacyStandard(VMessBean::class.java)
            registerLegacyStandard(TrojanBean::class.java)
            register(ShadowsocksBean::class.java)
            registerLegacyStandard(StandardV2RayBean::class.java)
            register(AbstractBean::class.java)
        }
    }

    private fun <T : StandardV2RayBean> Kryo.registerLegacyStandard(type: Class<T>) {
        val serializer = FieldSerializer<T>(this, type)
        serializer.removeField("rawTransportType")
        register(type, serializer)
    }

    private fun isVersioned(bytes: ByteArray): Boolean =
        bytes.size >= HEADER_SIZE && ByteBuffer.wrap(bytes, 0, Int.SIZE_BYTES).int == MAGIC

    private fun readVersioned(bytes: ByteArray): AbstractBean {
        if (bytes.size < HEADER_SIZE) throw KryoException("Bean blob header is truncated")
        val header = ByteBuffer.wrap(bytes, 0, HEADER_SIZE)
        check(header.int == MAGIC) { "Bean blob magic mismatch" }
        check(header.get().toInt() == FORMAT_VERSION) { "Unsupported bean blob version" }
        val protocol = header.get().toInt() and 0xff
        val bean = readLegacy(bytes.copyOfRange(HEADER_SIZE, bytes.size), pool)
        check(protocolType(bean) == protocol) { "Bean blob protocol mismatch" }
        return bean
    }

    private fun readLegacy(bytes: ByteArray, sourcePool: Pool<Kryo>): AbstractBean {
        val kryo = sourcePool.obtain()
        return try {
            ByteBufferInput(bytes).use { input ->
                (kryo.readClassAndObject(input) as AbstractBean).applyCanonicalDefaults()
            }
        } finally {
            sourcePool.free(kryo)
        }
    }

    private fun protocolType(bean: AbstractBean): Int = when (bean) {
        is VLESSBean -> 0
        is VMessBean -> 1
        is TrojanBean -> 2
        is ShadowsocksBean -> 3
        else -> throw KryoException("Unsupported bean type")
    }
}
