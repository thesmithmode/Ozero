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

    private val runtimeSource by lazy {
        val moduleRoot = File(System.getProperty("user.dir") ?: ".")
        val f = File(moduleRoot, "src/main/java/ru/ozero/engineurnetwork/UrnetworkRuntime.kt")
        assertTrue(f.exists(), "UrnetworkRuntime.kt не найден: $f")
        f.readText()
    }

    @Test
    fun `acquireGuestJwt использует guestMode=true и terms=true`() {
        assertTrue(
            source.contains("guestMode = true"),
            "guestMode=true обязателен — без него API требует email верификацию",
        )
        assertTrue(
            source.contains("terms = true"),
            "terms=true обязателен — иначе server отклоняет networkCreate с ошибкой ToS",
        )
    }

    @Test
    fun `все cgo вызовы строго на Main thread (Dispatchers Main immediate)`() {
        assertTrue(
            source.contains("Dispatchers.Main.immediate"),
            "URnetwork SDK cgo не thread-safe относительно Dispatchers.IO worker — SIGABRT на Nubia",
        )
        assertTrue(
            !source.contains("Dispatchers.IO"),
            "Dispatchers.IO для cgo калов запрещён — только Main",
        )
    }

    @Test
    fun `использует UrnetworkRuntime singleton (один NetworkSpaceManager на процесс)`() {
        assertTrue(
            source.contains("UrnetworkRuntime.ensure(app)"),
            "auth-service обязан использовать UrnetworkRuntime.ensure — один manager на процесс",
        )
        assertTrue(
            !source.contains("Sdk.newNetworkSpaceManager"),
            "auth-service НЕ должен создавать свой NetworkSpaceManager — два manager-а = SIGABRT",
        )
    }

    @Test
    fun `Application context (не Activity Context) — setLogDir global`() {
        assertTrue(
            source.contains("private val app: Application"),
            "Application — глобальный, не Activity (иначе утечка Activity через setLogDir)",
        )
    }

    @Test
    fun `Runtime использует null envSecret и null store (не пустые строки)`() {
        assertTrue(
            runtimeSource.contains("v.envSecret = null"),
            "envSecret = null (не пустая строка) — SDK различает null vs blank в validation path",
        )
        assertTrue(
            runtimeSource.contains("v.store = null"),
            "store = null (не пустая строка) — нестора режим",
        )
    }

    @Test
    fun `Runtime вызывает setLogDir и setMemoryLimit ДО newNetworkSpaceManager`() {
        val setLogIdx = runtimeSource.indexOf("Sdk.setLogDir")
        val newMgrIdx = runtimeSource.indexOf("Sdk.newNetworkSpaceManager")
        assertTrue(setLogIdx >= 0, "Sdk.setLogDir обязан в Runtime")
        assertTrue(newMgrIdx >= 0, "Sdk.newNetworkSpaceManager обязан в Runtime")
        assertTrue(setLogIdx < newMgrIdx, "setLogDir обязан ДО newNetworkSpaceManager")
        assertTrue(runtimeSource.contains("Sdk.setMemoryLimit"))
    }

    @Test
    fun `Runtime вызывает newLoginViewController после updateNetworkSpace`() {
        assertTrue(
            runtimeSource.contains("Sdk.newLoginViewController"),
            "newLoginViewController инициализирует internal SDK state перед networkCreate",
        )
    }

    @Test
    fun `callback покрывает 5 ветвей`() {
        val callbackBlock = source.substringAfter("NetworkCreateCallback").substringBefore("try {")
        assertTrue(callbackBlock.contains("err != null"))
        assertTrue(callbackBlock.contains("result == null"))
        assertTrue(callbackBlock.contains("result.error != null"))
        assertTrue(callbackBlock.contains("result.network != null"))
        assertTrue(callbackBlock.contains("isNullOrBlank()"))
        assertTrue(callbackBlock.contains("GuestJwtResult.Success(byJwt = jwt)"))
    }

    @Test
    fun `acquireClientJwt существует и зовёт authNetworkClient`() {
        assertTrue(source.contains("override suspend fun acquireClientJwt"))
        assertTrue(source.contains("api.authNetworkClient(args, callback)"))
        assertTrue(
            source.contains("api.byJwt = byJwt"),
            "api.byJwt должен быть установлен перед authNetworkClient",
        )
    }

    @Test
    fun `acquireClientJwt blank byJwt → Error без вызова SDK`() {
        assertTrue(source.contains("byJwt.isBlank()"))
    }

    @Test
    fun `используется suspendCancellableCoroutine`() {
        assertTrue(source.contains("suspendCancellableCoroutine"))
    }
}
