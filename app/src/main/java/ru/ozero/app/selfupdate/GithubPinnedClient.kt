package ru.ozero.app.selfupdate

import okhttp3.CertificatePinner
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * OkHttpClient с certificate pinning для api.github.com.
 *
 * Без pinning любой публично доверенный CA мог бы выпустить mitm-сертификат на
 * api.github.com, и self-update скачал бы вредоносный APK URL. Pinning ловит
 * подмену даже при компрометации CA.
 *
 * Стратегия: pin'ятся SPKI трёх уровней цепочки (leaf + intermediate + root)
 * чтобы пережить ротацию любого одного. Если GitHub сменит CA целиком — pins
 * нужно обновить через OTA (нельзя жёстко закладываться на один CA).
 *
 * Источник pins: `openssl s_client -servername api.github.com -connect api.github.com:443
 * -showcerts | openssl x509 -pubkey -noout | openssl pkey -pubin -outform DER |
 * openssl dgst -sha256 -binary | base64` для каждого cert в chain.
 */
object GithubPinnedClient {

    private const val HOST = "api.github.com"

    /**
     * Pins SPKI sha256 trí уровней цепочки api.github.com.
     * Получены 2026-04-25. При смене CA GitHub обновлять через release.
     */
    private val PINS = listOf(
        // Leaf: *.github.com (Sectigo Public Server Authentication CA DV E36)
        "sha256/tt9RksdSBGiieTiyWkU8g3MOmCrfMcvXDGC4ZALs9rg=",
        // Intermediate: Sectigo Public Server Authentication CA DV E36
        "sha256/ZSagvDzjltLkewXEBuDxIzpW/dpVw1Juvvmd0hhkzdY=",
        // Intermediate root: Sectigo Public Server Authentication Root E46
        "sha256/sLVjNUaFYfW7n6EtgBeEpjOlcnBdNPMrZDRF36iwBdE=",
    )

    fun create(): OkHttpClient {
        val pinner = CertificatePinner.Builder().apply {
            for (pin in PINS) add(HOST, pin)
        }.build()

        return OkHttpClient.Builder()
            .certificatePinner(pinner)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    /** Список ожидаемых pins — для проверки в тестах и аудитах. */
    fun pins(): List<String> = PINS.toList()

    fun host(): String = HOST
}
