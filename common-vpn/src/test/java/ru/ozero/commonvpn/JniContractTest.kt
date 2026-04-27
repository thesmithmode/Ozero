package ru.ozero.commonvpn

import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertTrue

/**
 * Static contract test: проверяет соответствие Kotlin-side JNI declarations
 * upstream `heiher/hev-socks5-tunnel` requirements. Прошлый релиз (v1.0.0
 * первый выпуск) крашился именно потому что класс лежал в `ru.ozero.commonvpn`,
 * методы назывались `startNative(yaml):Int` — и unit-тесты этого не ловили
 * (всё проходило через FakeHevTunnelGateway).
 *
 * Тест парсит исходник в src/main, не загружает класс — иначе init блок дёрнет
 * System.loadLibrary("hev-socks5-tunnel") на JVM-runner и упадёт.
 */
class JniContractTest {

    private val moduleRoot = File(System.getProperty("user.dir") ?: ".")

    @Test
    fun `class TProxyService существует в пакете hev`() {
        val expected = File(moduleRoot, "src/main/java/hev/TProxyService.kt")
        assertTrue(expected.exists(), "Файл $expected отсутствует. Upstream JNI вызывает FindClass('hev/TProxyService') — путь и имя жёсткие, переименование = NoSuchMethodError при первом вызове VPN.")
        val source = expected.readText()
        assertTrue(
            source.contains("package hev") && source.contains("object TProxyService"),
            "Класс должен быть `package hev` + `object TProxyService`. Иначе RegisterNatives в hev-jni.c не найдёт класс.",
        )
    }

    @Test
    fun `external fun TProxyStartService имеет сигнатуру (String, Int) Unit`() {
        val source = File(moduleRoot, "src/main/java/hev/TProxyService.kt").readText()
        // Upstream registers:  TProxyStartService(Ljava/lang/String;I)V
        // Любая другая сигнатура (return Int, разные параметры) = UnsatisfiedLinkError в runtime.
        val ok = Regex(
            "external\\s+fun\\s+TProxyStartService\\s*\\(\\s*\\w+\\s*:\\s*String\\s*,\\s*\\w+\\s*:\\s*Int\\s*\\)(?!\\s*:)",
        ).containsMatchIn(source)
        assertTrue(
            ok,
            "TProxyStartService должна быть `external fun TProxyStartService(path: String, fd: Int)` без return type — upstream возвращает void. " +
                "Если меняешь — синхронизируй с upstream src/hev-jni.c в heiher/hev-socks5-tunnel.",
        )
    }

    @Test
    fun `external fun TProxyStopService имеет сигнатуру () Unit`() {
        val source = File(moduleRoot, "src/main/java/hev/TProxyService.kt").readText()
        val ok = Regex("external\\s+fun\\s+TProxyStopService\\s*\\(\\s*\\)(?!\\s*:)").containsMatchIn(source)
        assertTrue(ok, "TProxyStopService должна быть `external fun TProxyStopService()` без параметров и без return type.")
    }

    @Test
    fun `loadLibrary имя совпадает с именем .so из release pipeline`() {
        // Если поменяешь имя в loadLibrary — добавь соответствующий шаг в release.yml
        // который собирает/копирует .so с этим именем в jniLibs/<abi>/.
        val source = File(moduleRoot, "src/main/java/hev/TProxyService.kt").readText()
        assertTrue(
            source.contains("System.loadLibrary(\"hev-socks5-tunnel\")"),
            "loadLibrary должен принимать ровно \"hev-socks5-tunnel\" — release.yml собирает upstream под этим именем.",
        )
    }

    @Test
    fun `NativeHevTunnelGateway адаптирует upstream void return в Int rc`() {
        // Upstream возвращает void, наш HevTunnelGateway interface — Int rc.
        // Контракт: при отсутствии исключения считаем 0 (OK), иначе -1.
        // Если изменить семантику — обновить VpnEnginePipeline.bringTunnelUp.
        val source = File(moduleRoot, "src/main/java/ru/ozero/commonvpn/HevTunnelGateway.kt").readText()
        assertTrue(
            source.contains("hev.TProxyService.TProxyStartService"),
            "NativeHevTunnelGateway должен делегировать в hev.TProxyService.TProxyStartService.",
        )
    }
}
