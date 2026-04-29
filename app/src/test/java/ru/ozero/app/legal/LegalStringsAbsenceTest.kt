package ru.ozero.app.legal

import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertFalse

class LegalStringsAbsenceTest {

    private val moduleRoot = File(System.getProperty("user.dir") ?: ".")
    private val ru by lazy { File(moduleRoot, "src/main/res/values/strings.xml").readText() }
    private val en by lazy { File(moduleRoot, "src/main/res/values-en/strings.xml").readText() }

    @Test
    fun `settings_about_summary удалён из strings_xml RU`() {
        assertFalse(
            ru.contains("name=\"settings_about_summary\""),
            "settings_about_summary должен быть удалён из RU strings.xml — юзер требует убрать legal-текст " +
                "«Open-source / Privacy / Threat model».",
        )
    }

    @Test
    fun `settings_about_summary удалён из strings_xml EN`() {
        assertFalse(
            en.contains("name=\"settings_about_summary\""),
            "settings_about_summary должен быть удалён из EN strings.xml.",
        )
    }

    @Test
    fun `about_privacy удалён из strings_xml RU`() {
        assertFalse(
            ru.contains("name=\"about_privacy\""),
            "about_privacy ссылка на Privacy policy удалена — никакой юридической самодеятельности.",
        )
    }

    @Test
    fun `about_threat_model удалён из strings_xml RU`() {
        assertFalse(
            ru.contains("name=\"about_threat_model\""),
            "about_threat_model ссылка на Threat model удалена.",
        )
    }

    @Test
    fun `about_privacy удалён из strings_xml EN`() {
        assertFalse(
            en.contains("name=\"about_privacy\""),
            "about_privacy в EN strings.xml удалён.",
        )
    }

    @Test
    fun `about_threat_model удалён из strings_xml EN`() {
        assertFalse(
            en.contains("name=\"about_threat_model\""),
            "about_threat_model в EN strings.xml удалён.",
        )
    }

    @Test
    fun `Open-source mention удалён из RU strings_xml`() {
        assertFalse(
            ru.contains("Open-source"),
            "Слово Open-source не должно фигурировать в RU strings.xml — юзер требует убрать.",
        )
    }
}
