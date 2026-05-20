package ru.ozero.app.di

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

class MasterDnsModuleSourceTest {

    private val source: File = listOf(
        File("src/main/java/ru/ozero/app/di/MasterDnsModule.kt"),
        File("app/src/main/java/ru/ozero/app/di/MasterDnsModule.kt"),
    ).first { it.exists() }

    @Test
    fun `module declares IntoSet EnginePlugin provider for MasterDns`() {
        val text = source.readText()
        assertTrue(text.contains("@IntoSet")) { "@IntoSet missing" }
        assertTrue(text.contains("MasterDnsEngine(")) { "MasterDnsEngine constructor call missing" }
        assertTrue(text.contains(": EnginePlugin")) { "return type EnginePlugin missing" }
    }

    @Test
    fun `module exposes MasterDnsConfigStore`() {
        val text = source.readText()
        assertTrue(text.contains("MasterDnsConfigStore"))
        assertTrue(text.contains("DataStoreMasterDnsConfigStore"))
    }

    @Test
    fun `dedicated DataStore qualifier present`() {
        val text = source.readText()
        assertTrue(text.contains("MasterDnsPrefs")) { "qualifier MasterDnsPrefs missing" }
        assertTrue(text.contains("masterdns_prefs")) { "preferences file name missing" }
    }
}
