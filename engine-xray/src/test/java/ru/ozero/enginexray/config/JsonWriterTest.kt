package ru.ozero.enginexray.config

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class JsonWriterTest {

    @Test
    fun nullValue() {
        assertEquals("null", JsonWriter.write(null))
    }

    @Test
    fun primitives() {
        assertEquals("true", JsonWriter.write(true))
        assertEquals("false", JsonWriter.write(false))
        assertEquals("42", JsonWriter.write(42))
        assertEquals("3.14", JsonWriter.write(3.14))
        assertEquals("\"abc\"", JsonWriter.write("abc"))
    }

    @Test
    fun escapes() {
        assertEquals("\"\\\"\"", JsonWriter.write("\""))
        assertEquals("\"\\\\\"", JsonWriter.write("\\"))
        assertEquals("\"\\n\"", JsonWriter.write("\n"))
        assertEquals("\"\\t\"", JsonWriter.write("\t"))
        assertEquals("\"\\u0001\"", JsonWriter.write("\u0001"))
    }

    @Test
    fun objectPreservesKeyOrder() {
        val m = linkedMapOf<String, Any?>("b" to 1, "a" to 2, "c" to 3)
        assertEquals("""{"b":1,"a":2,"c":3}""", JsonWriter.write(m))
    }

    @Test
    fun emptyContainers() {
        assertEquals("[]", JsonWriter.write(emptyList<Any>()))
        assertEquals("{}", JsonWriter.write(emptyMap<String, Any>()))
    }

    @Test
    fun nestedStructure() {
        val m = linkedMapOf<String, Any?>(
            "a" to listOf(1, 2, linkedMapOf("k" to null)),
        )
        assertEquals("""{"a":[1,2,{"k":null}]}""", JsonWriter.write(m))
    }

    @Test
    fun rejectsUnsupportedType() {
        val ex = runCatching { JsonWriter.write(Object()) }.exceptionOrNull()
        assertEquals(IllegalStateException::class, ex!!::class)
    }
}
