package ru.ozero.engineurnetwork.auth

import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertTrue

class RealUrnetworkAuthServiceContractTest {

    private val source by lazy {
        val moduleRoot = File(System.getProperty("user.dir") ?: ".")
        val f = File(moduleRoot, "src/main/java/ru/ozero/engineurnetwork/auth/RealUrnetworkAuthService.kt")
        assertTrue(f.exists(), "RealUrnetworkAuthService.kt не найден: $f")
        f.readText()
    }

    @Test
    fun `acquireGuestJwt использует guestMode=true и terms=true (PORTAL TOR контракт)`() {
        assertTrue(
            source.contains("guestMode = true"),
            "guestMode=true обязателен — без него API требует email верификацию (антипаттерн UI флоу). " +
                "PORTAL TOR референс показал что guest auto-mode единственный путь без UI.",
        )
        assertTrue(
            source.contains("terms = true"),
            "terms=true обязателен — иначе server отклоняет networkCreate с ошибкой ToS.",
        )
    }

    @Test
    fun `callback покрывает все 5 ветвей (err, result null, result_error, network jwt blank, success)`() {
        val callbackBlock = source.substringAfter("NetworkCreateCallback").substringBefore("try {")
        assertTrue(
            callbackBlock.contains("err != null"),
            "Branch 1: err != null — обязан resume Error, иначе coroutine висит навсегда.",
        )
        assertTrue(
            callbackBlock.contains("result == null"),
            "Branch 2: result == null — обязан resume Error, иначе NPE при доступе к result.error.",
        )
        assertTrue(
            callbackBlock.contains("result.error != null"),
            "Branch 3: result.error != null — server вернул business-level error, обязан resume Error.",
        )
        assertTrue(
            callbackBlock.contains("result.network != null"),
            "Branch 4: network != null — happy path, проверка jwt.",
        )
        assertTrue(
            callbackBlock.contains("isNullOrBlank()"),
            "Branch 5: jwt.isNullOrBlank() — server вернул network но без JWT (edge case), обязан resume Error.",
        )
        assertTrue(
            callbackBlock.contains("GuestJwtResult.Success(byJwt = jwt)"),
            "Happy path: GuestJwtResult.Success(byJwt = jwt) — без этого engine не получает токен.",
        )
    }

    @Test
    fun `networkCreate exception обработан через try catch + resume`() {
        val callBlock = source.substringAfter("try {").substringBefore("private fun ensureApi")
        assertTrue(
            callBlock.contains("api.networkCreate(args, callback)"),
            "Должен вызывать api.networkCreate(args, callback).",
        )
        assertTrue(
            callBlock.contains("catch (t: Throwable)") &&
                callBlock.contains("cont.resume(GuestJwtResult.Error"),
            "Throwable обязан catch + resume Error — иначе при SDK throw coroutine висит навсегда " +
                "и engine.start блокируется навечно.",
        )
    }

    @Test
    fun `ensureApi обрабатывает null space fallback через importNetworkSpaceFromJson`() {
        val ensureBlock = source.substringAfter("private fun ensureApi")
        assertTrue(
            ensureBlock.contains("importNetworkSpaceFromJson"),
            "ensureApi обязан fallback к importNetworkSpaceFromJson если getNetworkSpace вернул null — " +
                "первый запуск не имеет сохранённых пространств, необходимо создать.",
        )
        assertTrue(
            ensureBlock.contains("catch (t: Throwable)"),
            "ensureApi обязан catch Throwable — Sdk.newNetworkSpaceManager может throw на init " +
                "(file system, native), иначе crash на главном пути.",
        )
    }

    @Test
    fun `используется suspendCancellableCoroutine не suspendCoroutine`() {
        assertTrue(
            source.contains("suspendCancellableCoroutine"),
            "suspendCancellableCoroutine обязателен — позволяет cancel при VPN stop. " +
                "suspendCoroutine не отменяется → utечка callback после disconnect.",
        )
    }
}
