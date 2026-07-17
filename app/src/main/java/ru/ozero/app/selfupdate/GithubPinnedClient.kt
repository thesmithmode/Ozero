package ru.ozero.app.selfupdate

import okhttp3.CertificatePinner
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

object GithubPinnedClient {

    private const val HOST = "api.github.com"

    private val PINS = listOf(
        "sha256/tt9RksdSBGiieTiyWkU8g3MOmCrfMcvXDGC4ZALs9rg=",
        "sha256/ZSagvDzjltLkewXEBuDxIzpW/dpVw1Juvvmd0hhkzdY=",
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

    fun pins(): List<String> = PINS.toList()

    fun host(): String = HOST
}
