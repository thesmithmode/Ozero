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
    fun `external fun TProxyGetStats имеет сигнатуру () LongArray`() {
        val source = File(moduleRoot, "src/main/java/hev/TProxyService.kt").readText()
        val pattern = "external\\s+fun\\s+TProxyGetStats\\s*\\(\\s*\\)\\s*:\\s*LongArray"
        val ok = Regex(pattern).containsMatchIn(source)
        assertTrue(
            ok,
            "TProxyGetStats обязан быть `external fun TProxyGetStats(): LongArray` — " +
                "upstream src/hev-jni.c регистрирует 3 native метода (StartService, StopService, GetStats). " +
                "Если Kotlin-объявление отсутствует, RegisterNatives бросает NoSuchMethodError при " +
                "System.loadLibrary, libhev не грузится, VPN тоннель не поднимается.",
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

    @Test
    fun `release_yml собирает libhev с APP_CFLAGS=-DPKGNAME=hev`() {
        val candidates = sequenceOf(
            File(moduleRoot, ".github/workflows/release.yml"),
            File(moduleRoot.parentFile, ".github/workflows/release.yml"),
        )
        val releaseYml = candidates.firstOrNull { it.exists() }
        assertTrue(
            releaseYml != null,
            "release.yml не найден в ${candidates.toList()}. " +
                "Тест должен запускаться из repo root или module root.",
        )
        val text = releaseYml!!.readText()
        assertTrue(
            text.contains("APP_CFLAGS=\"-DPKGNAME=hev\"") ||
                text.contains("APP_CFLAGS='-DPKGNAME=hev'") ||
                text.contains("APP_CFLAGS=-DPKGNAME=hev"),
            "release.yml должен передавать APP_CFLAGS=-DPKGNAME=hev в ndk-build. " +
                "Без этого upstream src/hev-jni.c использует default PKGNAME=hev/htproxy и " +
                "ищет класс hev/htproxy/TProxyService — FindClass=NULL → " +
                "RegisterNatives(NULL,...) → ART JniAbort при первом старте VPN. " +
                "См. heiher/hev-socks5-tunnel src/hev-jni.c L24-26.",
        )
    }
}
