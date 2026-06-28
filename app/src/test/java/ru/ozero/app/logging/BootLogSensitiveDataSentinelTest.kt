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
    fun `UrnetworkPayoutWalletSetup использует нейтральный TAG UrnAccountSync`() {
        val src = readSource("engine-urnetwork/src/main/java/ru/ozero/engineurnetwork/UrnetworkPayoutWalletSetup.kt")
        assertTrue(
            src.contains("const val TAG = \"UrnAccountSync\""),
            "UrnetworkPayoutWalletSetup TAG обязан быть нейтральный 'UrnAccountSync' — " +
                "TAG 'UrnetworkPayoutWallet' раскрывает payout-механику в публичных логах",
        )
        assertNoForbidden(
            src,
            "UrnetworkPayoutWalletSetup.kt",
            listOf(
                "payout wallet set",
                "payout wallet setup",
                "walletId not found",
                "setupPayoutWallet",
            ),
            reason = "log-строки с 'payout wallet'/'walletId' раскрывают монетизационную архитектуру — " +
                "юзер из логов поймёт что трафик раздаётся в чей-то кошелёк. Использовать disguise: " +
                "'endpoint sync', 'registry id'",
        )
    }

    @Test
    fun `UrnetworkRelayCoordinator использует нейтральный TAG MeshSession`() {
        val src = readSource("app/src/main/java/ru/ozero/app/relay/UrnetworkRelayCoordinator.kt")
        assertTrue(
            src.contains("const val TAG = \"MeshSession\""),
            "UrnetworkRelayCoordinator TAG обязан быть 'MeshSession' — " +
                "TAG 'UrnetworkRelayCoord' прямо называет relay-функцию в публичных логах",
        )
        assertNoForbidden(
            src,
            "UrnetworkRelayCoordinator.kt",
            listOf(
                "relay started alongside",
                "relay stop:",
                "relay bridge.start failed",
            ),
            reason = "log-строки с 'relay' раскрывают что трафик пользователя проксируется через mesh — " +
                "использовать disguise: 'mesh session: worker started'",
        )
    }

    @Test
    fun `FptnEngine не пишет connection metadata в PersistentLoggers`() {
        val src = readSource("engine-fptn/src/main/java/ru/ozero/enginefptn/FptnEngine.kt")
        assertTrue(
            src.contains("authenticate: POST \$API_LOGIN_PATH timeout="),
            "FptnEngine.authenticate должен оставлять persistent checkpoint без server, port, sni и bypass",
        )
        assertNoForbidden(
            src,
            "FptnEngine.kt",
            listOf(
                "PersistentLoggers.debug(\n            TAG,\n            \"start: server=",
                "PersistentLoggers.debug(\n                TAG,\n                \"authenticate: POST /api/v1/login server=",
                "PersistentLoggers.debug(TAG, \"authenticate: success server=",
                "server=\${server.name}:\${server.port} sni=",
                "server=\${firstServer.name} port=",
                "selected=\${fptn.selectedServerName",
            ),
            reason = "FPTN provider/server name, port, SNI and bypass strategy must not be persisted to shareable logs",
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
