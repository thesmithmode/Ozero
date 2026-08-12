package ru.ozero.singboxfmt

import com.esotericsoftware.kryo.Kryo
import com.esotericsoftware.kryo.KryoException
import com.esotericsoftware.kryo.io.ByteBufferInput
import com.esotericsoftware.kryo.io.ByteBufferOutput
import com.esotericsoftware.kryo.util.Pool
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

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
                val header = ByteBuffer.allocate(HEADER_SIZE)
                    .order(ByteOrder.LITTLE_ENDIAN)
                    .putInt(MAGIC)
                    .put(FORMAT_VERSION.toByte())
                    .put(protocolType(bean).toByte())
                    .array()
                out.writeBytes(header)
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
        val candidates = decodeCandidates(bytes)
        val semanticCandidates = candidates.distinctBy { serialize(it.bean).toList() }
        val bean = when (semanticCandidates.size) {
            0 -> throw KryoException("Unsupported bean blob")
            1 -> semanticCandidates.single().bean
            else -> throw KryoException("Ambiguous bean blob schema")
        }
        return DecodedBean(bean, migratedBlob = if (isVersioned(bytes)) null else serialize(bean))
    }

    fun decodeCandidates(bytes: ByteArray): List<DecodedCandidate> {
        if (bytes.isEmpty()) return emptyList()
        if (isVersioned(bytes)) {
            return decodeCandidate(BeanBlobSchema.VERSIONED_V1, bytes) { readVersioned(bytes) }
                ?.let(::listOf)
                .orEmpty()
        }
        return listOfNotNull(
            decodeCandidate(BeanBlobSchema.CURRENT_RAW_V2, bytes) { readCurrentRaw(bytes) },
            decodeCandidate(BeanBlobSchema.LEGACY_V1, bytes) { readLegacyV1(bytes) },
        )
    }

    private inline fun decodeCandidate(
        schema: BeanBlobSchema,
        bytes: ByteArray,
        decode: () -> AbstractBean,
    ): DecodedCandidate? = runCatching {
        val bean = decode()
        requireAllowedPersistedBean(bean)
        DecodedCandidate(schema, bean, bytes.size, bytes.size)
    }.getOrNull()

    private fun requireAllowedPersistedBean(bean: AbstractBean) =
        require(bean is VLESSBean || bean is VMessBean || bean is TrojanBean || bean is ShadowsocksBean)

    data class DecodedBean(
        val bean: AbstractBean,
        val migratedBlob: ByteArray?,
    )

    private val legacyV1Pool = object : Pool<Kryo>(true, false, 4) {
        override fun create(): Kryo = Kryo().apply {
            isRegistrationRequired = false
            register(LegacyV1VLESSBean::class.java)
            register(LegacyV1VMessBean::class.java)
            register(LegacyV1TrojanBean::class.java)
            register(LegacyV1ShadowsocksBean::class.java)
            register(LegacyV1StandardV2RayBean::class.java)
            register(LegacyV1AbstractBean::class.java)
        }
    }

    private fun isVersioned(bytes: ByteArray): Boolean =
        bytes.size >= HEADER_SIZE &&
            ByteBuffer.wrap(bytes, 0, Int.SIZE_BYTES)
                .order(ByteOrder.LITTLE_ENDIAN)
                .int == MAGIC

    private fun readVersioned(bytes: ByteArray): AbstractBean {
        if (bytes.size < HEADER_SIZE) throw KryoException("Bean blob header is truncated")
        val header = ByteBuffer.wrap(bytes, 0, HEADER_SIZE).order(ByteOrder.LITTLE_ENDIAN)
        check(header.int == MAGIC) { "Bean blob magic mismatch" }
        check(header.get().toInt() == FORMAT_VERSION) { "Unsupported bean blob version" }
        val protocol = header.get().toInt() and 0xff
        val bean = readCurrentRaw(bytes.copyOfRange(HEADER_SIZE, bytes.size))
        check(protocolType(bean) == protocol) { "Bean blob protocol mismatch" }
        return bean
    }

    private fun readCurrentRaw(bytes: ByteArray): AbstractBean =
        (readRaw(bytes, pool) as? AbstractBean)
            ?.applyCanonicalDefaults()
            ?: throw KryoException("Unsupported current bean type")

    private fun readLegacyV1(bytes: ByteArray): AbstractBean =
        (readRaw(bytes, legacyV1Pool) as? LegacyV1AbstractBean)
            ?.toCurrentBean()
            ?: throw KryoException("Unsupported legacy bean type")

    private fun readRaw(bytes: ByteArray, sourcePool: Pool<Kryo>): Any {
        val kryo = sourcePool.obtain()
        return try {
            ByteBufferInput(bytes).use { input ->
                val value = kryo.readClassAndObject(input) ?: throw KryoException("Bean blob is null")
                if (input.position() != bytes.size) throw KryoException("Bean blob has trailing payload")
                value
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
        else -> throw KryoException("Unsupported persisted bean protocol")
    }
}

enum class BeanBlobSchema {
    LEGACY_V1,
    CURRENT_RAW_V2,
    VERSIONED_V1,
}

data class DecodedCandidate(
    val schema: BeanBlobSchema,
    val bean: AbstractBean,
    val bytesConsumed: Int,
    val blobSize: Int,
)
