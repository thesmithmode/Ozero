package ru.ozero.app.ui.settings.engines

import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertTrue

class UrnetworkCountryPersistenceTest {

    private val viewModelSource by lazy {
        val moduleRoot = File(System.getProperty("user.dir") ?: ".")
        val f = File(
            moduleRoot,
            "src/main/java/ru/ozero/app/ui/settings/engines/UrnetworkEngineSettingsViewModel.kt",
        )
        assertTrue(f.exists(), "UrnetworkEngineSettingsViewModel.kt не найден: $f")
        f.readText()
    }

    @Test
    fun `UrnetworkEngineSettingsViewModel инжектит SettingsRepository`() {
        assertTrue(
            viewModelSource.contains("settingsRepository: SettingsRepository"),
            "ViewModel обязан инжектить SettingsRepository — иначе выбранная страна не сохраняется в DataStore.",
        )
    }

    @Test
    fun `selectLocation persists countryCode в SettingsRepository`() {
        val body = viewModelSource
            .substringAfter("fun selectLocation(location: UrnetworkSdkBridge.LocationToken?)")
            .substringBefore("fun setSearchQuery")
        assertTrue(
            body.contains("settingsRepository.setUrnetworkCountryCode("),
            "selectLocation обязан звать settingsRepository.setUrnetworkCountryCode для " +
                "персистенса страны между запусками. Body:\n$body",
        )
    }

    @Test
    fun `selectLocation также пингует bridge setPreferredCountry`() {
        val body = viewModelSource
            .substringAfter("fun selectLocation(location: UrnetworkSdkBridge.LocationToken?)")
            .substringBefore("fun setSearchQuery")
        assertTrue(
            body.contains("bridge.setPreferredCountry("),
            "selectLocation обязан установить preferredCountry в bridge — для restore при " +
                "следующем VPN start.",
        )
    }
}
