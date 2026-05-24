package ru.ozero.singboxfmt

import com.esotericsoftware.kryo.Kryo
import com.esotericsoftware.kryo.io.ByteBufferInput
import com.esotericsoftware.kryo.io.ByteBufferOutput
import com.esotericsoftware.kryo.util.Pool
import java.io.ByteArrayOutputStream

object KryoSerializer {
    private val pool = object : Pool<Kryo>(true, false, 4) {
        override fun create(): Kryo = Kryo().apply {
            isRegistrationRequired = false
            register(VLESSBean::class.java)
            register(StandardV2RayBean::class.java)
            register(AbstractBean::class.java)
        }
    }

    fun serialize(bean: AbstractBean): ByteArray {
        val kryo = pool.obtain()
        return try {
            val baos = ByteArrayOutputStream(256)
            ByteBufferOutput(baos).use { out ->
                kryo.writeClassAndObject(out, bean)
                out.flush()
            }
            baos.toByteArray()
        } finally {
            pool.free(kryo)
        }
    }

    @Suppress("UNCHECKED_CAST")
    fun <T : AbstractBean> deserialize(bytes: ByteArray): T {
        val kryo = pool.obtain()
        return try {
            ByteBufferInput(bytes).use { inp ->
                kryo.readClassAndObject(inp) as T
            }
        } finally {
            pool.free(kryo)
        }
    }
}
