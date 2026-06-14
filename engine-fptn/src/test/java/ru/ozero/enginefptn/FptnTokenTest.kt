package ru.ozero.enginefptn

import android.util.Base64
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FptnTokenTest {

    @BeforeEach
    fun setUp() {
        mockkStatic(Base64::class)
        every { Base64.decode(any<String>(), any<Int>()) } answers {
            java.util.Base64.getDecoder().decode(firstArg<String>().trim())
        }
    }

    @AfterEach
    fun tearDown() {
        unmockkStatic(Base64::class)
    }

    @Test
    fun `should return null for empty string`() {
        assertNull(FptnToken.parse(""))
    }

    @Test
    fun `should return null for unknown prefix`() {
        assertNull(FptnToken.parse("http:abc"))
        assertNull(FptnToken.parse("xfptn:abc"))
    }

    @Test
    fun `should return null when base64 decode throws`() {
        every { Base64.decode(any<String>(), any<Int>()) } throws IllegalArgumentException("bad base64")
        assertNull(FptnToken.parse("fptn:!!!not-base64!!!"))
    }

    @Test
    fun `should return null when servers array is empty`() {
        val json = """{"version":1,"username":"user","password":"pass","servers":[]}"""
        assertNull(FptnToken.parse("fptn:${encode(json)}"))
    }

    @Test
    fun `should return null when required field username is missing`() {
        val json = """{"version":1,"password":"pass",
            "servers":[{"name":"S","host":"1.2.3.4","port":443}]}"""
        assertNull(FptnToken.parse("fptn:${encode(json)}"))
    }

    @Test
    fun `should return null when required field password is missing`() {
        val json = """{"version":1,"username":"u",
            "servers":[{"name":"S","host":"1.2.3.4","port":443}]}"""
        assertNull(FptnToken.parse("fptn:${encode(json)}"))
    }

    @Test
    fun `should return null when servers key is absent`() {
        val json = """{"version":1,"username":"u","password":"p"}"""
        assertNull(FptnToken.parse("fptn:${encode(json)}"))
    }

    @Test
    fun `should parse valid fptn token with single server`() {
        val json = """{"version":2,"username":"alice","password":"s3cr3t",
            "servers":[{"name":"DE-1","host":"1.2.3.4","port":443,
                         "md5_fingerprint":"aabb","country_code":"de"}]}"""
        val result = FptnToken.parse("fptn:${encode(json)}")
        assertNotNull(result)
        assertEquals(2, result.version)
        assertEquals("alice", result.username)
        assertEquals("s3cr3t", result.password)
        assertEquals(1, result.servers.size)
        val server = result.servers[0]
        assertEquals("DE-1", server.name)
        assertEquals("1.2.3.4", server.host)
        assertEquals(443, server.port)
        assertEquals("aabb", server.md5Fingerprint)
        assertEquals("DE", server.countryCode)
    }

    @Test
    fun `should parse token with multiple servers`() {
        val json = """{"version":1,"username":"u","password":"p",
            "servers":[
                {"name":"RU-1","host":"10.0.0.1","port":443,"country_code":"ru"},
                {"name":"US-1","host":"10.0.0.2","port":443,"country_code":"us"}
            ]}"""
        val result = FptnToken.parse("fptn:${encode(json)}")
        assertNotNull(result)
        assertEquals(2, result.servers.size)
        assertEquals("RU", result.servers[0].countryCode)
        assertEquals("US", result.servers[1].countryCode)
    }

    @Test
    fun `should default version to 1 when absent`() {
        val json = """{"username":"u","password":"p",
            "servers":[{"name":"S","host":"h","port":443}]}"""
        val result = FptnToken.parse("fptn:${encode(json)}")
        assertNotNull(result)
        assertEquals(1, result.version)
    }

    @Test
    fun `should uppercase country code`() {
        val json = """{"username":"u","password":"p",
            "servers":[{"name":"S","host":"h","port":1,"country_code":"gb"}]}"""
        val result = FptnToken.parse("fptn:${encode(json)}")
        assertNotNull(result)
        assertEquals("GB", result.servers[0].countryCode)
    }

    @Test
    fun `should parse camel case countryCode`() {
        val json = """{"username":"u","password":"p",
            "servers":[{"name":"S","host":"h","port":1,"countryCode":"br"}]}"""
        val result = FptnToken.parse("fptn:${encode(json)}")
        assertNotNull(result)
        assertEquals("BR", result.servers[0].countryCode)
    }

    @Test
    fun `should keep country code null when token has no country metadata`() {
        val json = """{"username":"u","password":"p",
            "servers":[{"name":"S","host":"h","port":1}]}"""
        val result = FptnToken.parse("fptn:${encode(json)}")
        assertNotNull(result)
        assertNull(result.servers[0].countryCode)
    }

    @Test
    fun `should not keep invalid country code as question marks`() {
        val json = """{"username":"u","password":"p",
            "servers":[{"name":"S","host":"h","port":1,"country_code":"??"}]}"""
        val result = FptnToken.parse("fptn:${encode(json)}")
        assertNotNull(result)
        assertNull(result.servers[0].countryCode)
    }

    @Test
    fun `should ignore blank and null country metadata`() {
        val json = """{"username":"u","password":"p",
            "servers":[
                {"name":"blank","host":"h1","port":1,"country_code":"   "},
                {"name":"null","host":"h2","port":2,"countryCode":null}
            ]}"""
        val result = FptnToken.parse("fptn:${encode(json)}")
        assertNotNull(result)
        assertNull(result.servers[0].countryCode)
        assertNull(result.servers[1].countryCode)
    }

    @Test
    fun `should reject long and partially numeric country codes`() {
        val json = """{"username":"u","password":"p",
            "servers":[
                {"name":"long","host":"h1","port":1,"country_code":"rus"},
                {"name":"numeric","host":"h2","port":2,"country_code":"r1"}
            ]}"""
        val result = FptnToken.parse("fptn:${encode(json)}")
        assertNotNull(result)
        assertNull(result.servers[0].countryCode)
        assertNull(result.servers[1].countryCode)
    }

    @Test
    fun `country_code takes precedence over camel case countryCode`() {
        val json = """{"username":"u","password":"p",
            "servers":[{"name":"S","host":"h","port":1,"country_code":"de","countryCode":"br"}]}"""

        val result = FptnToken.parse("fptn:${encode(json)}")

        assertNotNull(result)
        assertEquals("DE", result.servers.single().countryCode)
    }

    @Test
    fun `blank country_code falls back to camel case countryCode`() {
        val json = """{"username":"u","password":"p",
            "servers":[{"name":"S","host":"h","port":1,"country_code":" ","countryCode":"br"}]}"""

        val result = FptnToken.parse("fptn:${encode(json)}")

        assertNotNull(result)
        assertEquals("BR", result.servers.single().countryCode)
    }

    @Test
    fun `should return null when server item is missing required host`() {
        val json = """{"username":"u","password":"p",
            "servers":[{"name":"S","port":1,"country_code":"de"}]}"""

        assertNull(FptnToken.parse("fptn:${encode(json)}"))
    }

    @Test
    fun `toString should not expose username or password`() {
        val json = """{"username":"sensitive_user","password":"secret_pass",
            "servers":[{"name":"S","host":"h","port":1}]}"""
        val result = FptnToken.parse("fptn:${encode(json)}")
        assertNotNull(result)
        val str = result.toString()
        assertTrue(!str.contains("sensitive_user"), "username must not appear in toString")
        assertTrue(!str.contains("secret_pass"), "password must not appear in toString")
        assertTrue(str.contains("***"), "toString should mask credentials")
    }

    @Test
    fun `should return null for fptnb when base64 payload is not valid brotli`() {
        val notBrotli = java.util.Base64.getEncoder().encodeToString("plain-text-not-brotli".toByteArray())
        assertNull(FptnToken.parse("fptnb:$notBrotli"))
    }

    @Test
    fun `should return null for fptnb when base64 itself is invalid`() {
        assertNull(FptnToken.parse("fptnb:!!!not-base64!!!"))
    }

    @Test
    fun `readBounded returns content when within limit`() {
        val data = ByteArray(100) { it.toByte() }
        val result = FptnToken.readBounded(ByteArrayInputStream(data), 1024)
        assertContentEquals(data, result)
    }

    @Test
    fun `readBounded allows payload exactly at limit`() {
        val data = ByteArray(128) { it.toByte() }

        val result = FptnToken.readBounded(ByteArrayInputStream(data), 128)

        assertContentEquals(data, result)
    }

    @Test
    fun `readBounded throws when payload exceeds limit`() {
        val oversized = ByteArray(200) { it.toByte() }
        assertFailsWith<IllegalStateException> {
            FptnToken.readBounded(ByteArrayInputStream(oversized), 100)
        }
    }

    @Test
    fun `parse returns null for fptnb with oversized payload via readBounded`() {
        val oversized = ByteArray(2 * 1024 * 1024 + 1) { 0 }
        assertFailsWith<IllegalStateException> {
            FptnToken.readBounded(ByteArrayInputStream(oversized), 1 * 1024 * 1024)
        }
    }

    @Test
    fun `sentinel brotli decompression имеет hard size limit против OOM DoS`() {
        val sourceFile = locateFptnTokenSource()
        val source = sourceFile.readText(Charsets.UTF_8)
        assertTrue(
            source.contains("MAX_DECOMPRESSED_BYTES"),
            "FptnToken.brotliDecompress должен иметь MAX_DECOMPRESSED_BYTES лимит. " +
                "Без него malicious fptnb: с brotli expansion 100KB→1GB крашит heap.",
        )
        assertTrue(
            !Regex("""BrotliInputStream\([^)]+\)\.use\s*\{\s*it\.readBytes\(\)\s*\}""").containsMatchIn(source),
            "FptnToken.brotliDecompress не должен использовать readBytes() — " +
                "unbounded чтение в heap = OOM DoS. Использовать size-bounded цикл.",
        )
    }

    @Test
    fun `fptnb prefix must be parsed not unconditionally rejected`() {
        val sourceFile = locateFptnTokenSource()
        val source = sourceFile.readText(Charsets.UTF_8)
        assertTrue(
            !source.contains("""startsWith("fptnb:") -> return null"""),
            "FptnToken.kt безусловно отвергает fptnb: префикс. " +
                "User-feedback 2026-05-23: валидные fptnb: токены (brotli-compressed) " +
                "должны парситься. Регрессия — добавь brotli decompression.",
        )
    }

    private fun locateFptnTokenSource(): java.io.File {
        var dir = java.io.File(System.getProperty("user.dir") ?: ".").absoluteFile
        repeat(5) {
            val candidate = java.io.File(
                dir,
                "engine-fptn/src/main/java/ru/ozero/enginefptn/FptnToken.kt",
            )
            if (candidate.isFile) return candidate
            dir = dir.parentFile ?: return@repeat
        }
        error("FptnToken.kt не найден от ${System.getProperty("user.dir")}")
    }

    private fun encode(json: String): String =
        java.util.Base64.getEncoder().encodeToString(json.toByteArray())
}
