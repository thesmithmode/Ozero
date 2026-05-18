package ru.ozero.engineurnetwork.auth

import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertTrue

class DeviceWalletJwtSentinelTest {

    private val authSource: String by lazy {
        val moduleRoot = File(System.getProperty("user.dir") ?: ".")
        File(moduleRoot, "src/main/java/ru/ozero/engineurnetwork/auth/RealUrnetworkAuthService.kt")
            .readText()
    }

    private val identitySource: String by lazy {
        val moduleRoot = File(System.getProperty("user.dir") ?: ".")
        File(moduleRoot, "src/main/java/ru/ozero/engineurnetwork/auth/RealUrnetworkDeviceIdentity.kt")
            .readText()
    }

    private val engineSource: String by lazy {
        val moduleRoot = File(System.getProperty("user.dir") ?: ".")
        File(moduleRoot, "src/main/java/ru/ozero/engineurnetwork/EngineUrnetwork.kt").readText()
    }

    @Test
    fun `acquireDeviceWalletJwt подписывает префиксованное сообщение - replay-safe для конкретного pubkey`() {
        assertTrue(
            authSource.contains("WALLET_MESSAGE_PREFIX") &&
                authSource.contains("\"ozero-auth-v1:\""),
            "сообщение для подписи обязано начинаться с ozero-auth-v1: — иначе captured signature от другой " +
                "версии Ozero replay-able. Не менять prefix без миграции keypairs.",
        )
    }

    @Test
    fun `blockchain обязан быть solana - сервер URnetwork validates только это значение`() {
        assertTrue(
            authSource.contains("WALLET_BLOCKCHAIN_SOLANA") &&
                authSource.contains("\"solana\""),
            "blockchain в WalletAuthArgs обязан быть 'solana' — сервер reject других значений.",
        )
    }

    @Test
    fun `authLogin вызывается перед networkCreate - returning user получает существующий byJwt`() {
        val loginIdx = authSource.indexOf("api.authLogin")
        val createIdx = authSource.indexOf("api.networkCreate(args, callback)")
        assertTrue(loginIdx > 0, "api.authLogin отсутствует — flow не реализован")
        assertTrue(createIdx > loginIdx, "networkCreate обязан идти ПОСЛЕ authLogin")
    }

    @Test
    fun `networkCreate с walletAuth передаёт terms=true - сервер требует ToS acceptance`() {
        val createBlock = authSource.substringAfter("networkCreateWithWallet")
            .substringBefore("private fun mapCreateOutcome")
        assertTrue(
            createBlock.contains("terms = true"),
            "networkCreate(walletAuth) обязан terms=true — иначе сервер вернёт error и регистрация провалится.",
        )
    }

    @Test
    fun `signature кодируется Base64 NO_WRAP - сервер не принимает с переносами строк`() {
        assertTrue(
            authSource.contains("Base64.NO_WRAP"),
            "Ed25519 signature обязан Base64.NO_WRAP — иначе \\n ломает server-side parse.",
        )
    }

    @Test
    fun `RealUrnetworkDeviceIdentity не логирует raw seed - утечка privkey запрещена`() {
        val forbiddenLogPatterns = listOf(
            "Log.i(TAG, seed",
            "Log.d(TAG, seed",
            "Log.v(TAG, seed",
            "Log.w(TAG, seed",
            "Log.e(TAG, seed",
            "Log.i(TAG, \"$seed",
            "Log.i(TAG, \"\${seed",
        )
        for (pattern in forbiddenLogPatterns) {
            assertTrue(
                !identitySource.contains(pattern),
                "RealUrnetworkDeviceIdentity содержит подозрительный log с raw seed: $pattern",
            )
        }
    }

    @Test
    fun `RealUrnetworkDeviceIdentity использует AndroidKeyStore - privkey wrap, не plaintext на диске`() {
        assertTrue(
            identitySource.contains("AndroidKeyStore") &&
                identitySource.contains("AES/GCM/NoPadding"),
            "privkey обязан encryption через AndroidKeyStore AES-GCM — иначе rooted device читает напрямую.",
        )
    }

    @Test
    fun `EngineUrnetwork tryAcquireDeviceJwt вызывается перед guest fallback - device приоритетнее`() {
        val ensureBlock = engineSource.substringAfter("private suspend fun ensureGuestJwt()")
            .substringBefore("private suspend fun ensureClientJwt")
        val tryDeviceIdx = ensureBlock.indexOf("tryAcquireDeviceJwt")
        val guestFallbackIdx = ensureBlock.indexOf("authService.acquireGuestJwt")
        assertTrue(tryDeviceIdx >= 0, "tryAcquireDeviceJwt не вызывается в ensureGuestJwt")
        assertTrue(guestFallbackIdx > tryDeviceIdx, "guest fallback должен идти ПОСЛЕ device walletAuth попытки")
    }

    @Test
    fun `EngineUrnetwork сохраняет byJwt и devicePubkey атомарно — без race в crash recovery`() {
        val tryBlock = engineSource.substringAfter("private suspend fun tryAcquireDeviceJwt")
            .substringBefore("private companion object")
        val updateIdx = tryBlock.indexOf("configStore.update")
        val byJwtIdx = tryBlock.indexOf("byJwt = r.byJwt")
        val pubkeyIdx = tryBlock.indexOf("devicePubkey = pubkey")
        assertTrue(updateIdx >= 0, "tryAcquireDeviceJwt обязан использовать configStore.update — atomic transaction")
        assertTrue(byJwtIdx > updateIdx, "byJwt обязан set'иться внутри update {} блока")
        assertTrue(pubkeyIdx > updateIdx, "devicePubkey обязан set'иться внутри того же update {} блока")
    }

    @Test
    fun `networkName кешируется между запусками - не создавать новую network на каждом start`() {
        val tryBlock = engineSource.substringAfter("private suspend fun tryAcquireDeviceJwt")
            .substringBefore("private companion object")
        assertTrue(
            tryBlock.contains("configStore.deviceNetworkName().first()"),
            "tryAcquireDeviceJwt обязан читать stored networkName — иначе каждый start = новая регистрация",
        )
        assertTrue(
            tryBlock.contains("deviceNetworkName = networkName"),
            "networkName обязан сохраняться после первой успешной регистрации",
        )
    }

    @Test
    fun `legacy guest byJwt без devicePubkey мигрирует на walletAuth - existing users тоже получают per-device`() {
        val ensureBlock = engineSource.substringAfter("private suspend fun ensureGuestJwt()")
            .substringBefore("private suspend fun tryAcquireDeviceJwt")
        assertTrue(
            ensureBlock.contains("devicePubkey") && ensureBlock.contains("isNullOrBlank"),
            "ensureGuestJwt должен проверять devicePubkey != null — без этого existing guests останутся " +
                "на shared PRESET_WALLET и не получат свой traffic limit",
        )
    }

    @Test
    fun `migration инвалидирует старый byClientJwt - он привязан к старому byJwt`() {
        val tryBlock = engineSource.substringAfter("private suspend fun tryAcquireDeviceJwt")
            .substringBefore("private companion object")
        assertTrue(
            tryBlock.contains("byClientJwt = if (legacyMigration) null"),
            "при migration старый byClientJwt должен сбрасываться — иначе SDK использует stale client jwt " +
                "от guest network против device byJwt",
        )
    }
}
