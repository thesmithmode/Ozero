package ru.ozero.commonvpn

import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertTrue

class JniContractTest {

    private val moduleRoot = File(System.getProperty("user.dir") ?: ".")

    @Test
    fun `class TProxyService существует в пакете hev`() {
        val expected = File(moduleRoot, "src/main/java/hev/TProxyService.kt")
        assertTrue(
            expected.exists(),
            "Файл $expected отсутствует. Upstream JNI вызывает FindClass('hev/TProxyService') — " +
                "путь и имя жёсткие, переименование = NoSuchMethodError при первом вызове VPN.",
        )
        val source = expected.readText()
        assertTrue(
            source.contains("package hev") && source.contains("object TProxyService"),
            "Класс должен быть `package hev` + `object TProxyService`. " +
                "Иначе RegisterNatives в hev-jni.c не найдёт класс.",
        )
    }

    @Test
    fun `external fun TProxyStartService имеет сигнатуру (String, Int) Unit`() {
        val source = File(moduleRoot, "src/main/java/hev/TProxyService.kt").readText()
                        val pattern = "external\\s+fun\\s+TProxyStartService\\s*\\(" +
            "\\s*\\w+\\s*:\\s*String\\s*,\\s*\\w+\\s*:\\s*Int\\s*\\)(?!\\s*:)"
        val ok = Regex(pattern).containsMatchIn(source)
        assertTrue(
            ok,
            "TProxyStartService должна быть `external fun TProxyStartService(path: String, fd: Int)` " +
                "без return type — upstream возвращает void. Синхронизируй с heiher/hev-socks5-tunnel src/hev-jni.c.",
        )
    }

    @Test
    fun `external fun TProxyStopService имеет сигнатуру () Unit`() {
        val source = File(moduleRoot, "src/main/java/hev/TProxyService.kt").readText()
        val ok = Regex("external\\s+fun\\s+TProxyStopService\\s*\\(\\s*\\)(?!\\s*:)").containsMatchIn(source)
        assertTrue(
            ok,
            "TProxyStopService должна быть `external fun TProxyStopService()` без параметров и без return type.",
        )
    }

    @Test
    fun `loadLibrary имя совпадает с именем so-файла из release pipeline`() {
                        val source = File(moduleRoot, "src/main/java/hev/TProxyService.kt").readText()
        assertTrue(
            source.contains("System.loadLibrary(\"hev-socks5-tunnel\")"),
            "loadLibrary должен принимать ровно \"hev-socks5-tunnel\" — release.yml собирает upstream под этим именем.",
        )
    }

    @Test
    fun `NativeHevTunnelGateway адаптирует upstream void return в Int rc`() {
                                val source = File(moduleRoot, "src/main/java/ru/ozero/commonvpn/HevTunnelGateway.kt").readText()
        assertTrue(
            source.contains("hev.TProxyService.TProxyStartService"),
            "NativeHevTunnelGateway должен делегировать в hev.TProxyService.TProxyStartService.",
        )
    }
}
