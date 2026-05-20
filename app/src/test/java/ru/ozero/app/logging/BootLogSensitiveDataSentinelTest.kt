package ru.ozero.app.logging

import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BootLogSensitiveDataSentinelTest {

    private val repoRoot: File by lazy { locateRepoRoot() }

    @Test
    fun `EngineUrnetwork не логирует payout wallet даже в truncated form`() {
        val src = readSource("engine-urnetwork/src/main/java/ru/ozero/engineurnetwork/EngineUrnetwork.kt")
        assertNoForbidden(
            src,
            "EngineUrnetwork.kt",
            listOf("wallet.take(", "wallet.take ("),
            reason = "wallet address не должен попадать в boot.log даже как take(N) prefix — " +
                "репо публичный, индексируется навсегда (см. CLAUDE.md \"Чувствительные данные\")",
        )
    }

    @Test
    fun `UrnetworkPayoutWalletSetup не логирует payout wallet адрес`() {
        val src = readSource("engine-urnetwork/src/main/java/ru/ozero/engineurnetwork/UrnetworkPayoutWalletSetup.kt")
        assertNoForbidden(
            src,
            "UrnetworkPayoutWalletSetup.kt",
            listOf("walletAddress.take(", "walletAddress.take ("),
            reason = "PRESET_WALLET — общий payout адрес, не должен попадать в логи даже truncated " +
                "(commit 644004f8). Регрессия = утечка инфраструктуры в boot.log",
        )
    }

    @Test
    fun `ProxyWarpAutoConfig использует mirrorTag и НЕ логирует mirror URL plaintext`() {
        val rel = "engine-warp/src/main/java/ru/ozero/enginewarp/ProxyWarpAutoConfig.kt"
        val src = readSource(rel)
        assertTrue(
            src.contains("private fun mirrorTag(") && src.contains("\"m%08x\".format(url.hashCode())"),
            "$rel обязан содержать helper mirrorTag(url) = m%08x формат — " +
                "без него ошибки логируются с plaintext URL",
        )
        assertNoForbidden(
            src,
            "ProxyWarpAutoConfig.kt",
            listOf(
                "mirror timeout: \$",
                "HTTP failure on \$url",
                "mirror parse failed on \$url",
                "mirror parse failed on \$",
            ),
            reason = "mirror URL — внутренняя инфра-деталь Cloudflare WARP, не должна попадать в публичный " +
                "boot.log. Логировать только через mirrorTag (см. commit 644004f8)",
        )
    }

    @Test
    fun `UrnetworkRuntime не упоминает libgojni и tombstone в логах`() {
        val src = readSource("engine-urnetwork/src/main/java/ru/ozero/engineurnetwork/UrnetworkRuntime.kt")
        assertNoForbidden(
            src,
            "UrnetworkRuntime.kt",
            listOf("libgojni", "tombstone"),
            reason = "dev/diagnostic шум (libgojni, tombstone, OBB sandbox hints) — " +
                "удалён в 644004f8 как утечка деталей реализации. Регрессия = шум в публичном boot.log",
        )
    }

    private fun readSource(relativePath: String): String {
        val f = File(repoRoot, relativePath)
        assertTrue(f.isFile, "Source файл не найден: $f — путь устарел?")
        return f.readText()
    }

    private fun assertNoForbidden(
        source: String,
        label: String,
        forbidden: List<String>,
        reason: String,
    ) {
        val hits = forbidden.filter { source.contains(it) }
        assertFalse(
            hits.isNotEmpty(),
            "$label содержит запрещённые подстроки $hits — $reason",
        )
    }

    private fun locateRepoRoot(): File {
        var dir = File(System.getProperty("user.dir") ?: ".").absoluteFile
        repeat(5) {
            if (File(dir, "settings.gradle.kts").isFile) return dir
            dir = dir.parentFile ?: return@repeat
        }
        error("repo root (settings.gradle.kts) не найден от ${System.getProperty("user.dir")}")
    }
}
