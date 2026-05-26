package ru.ozero.desktop.vpn

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import ru.ozero.desktop.model.AppMode
import ru.ozero.desktop.model.EngineId
import ru.ozero.desktop.model.SettingsModel
import java.io.File
import java.util.Properties

class DesktopSettingsStore {

    private val file = File(System.getProperty("user.home"), ".ozero/settings.properties")
    private val _settings = MutableStateFlow(SettingsModel.DEFAULT)
    val settings: StateFlow<SettingsModel> = _settings.asStateFlow()

    init {
        load()
    }

    fun update(transform: SettingsModel.() -> SettingsModel) {
        _settings.value = _settings.value.transform()
        save()
    }

    private fun load() {
        if (!file.exists()) return
        runCatching {
            val props = Properties()
            file.inputStream().use { props.load(it) }
            _settings.value = SettingsModel(
                ipv6Enabled = props.getProperty("ipv6", "false").toBoolean(),
                autoStart = props.getProperty("autoStart", "false").toBoolean(),
                manualEngine = props.getProperty("manualEngine")?.let { runCatching { EngineId.valueOf(it) }.getOrNull() },
                appMode = props.getProperty("appMode")?.let { runCatching { AppMode.valueOf(it) }.getOrNull() } ?: AppMode.EXPERT,
                killswitchEnabled = props.getProperty("killswitch", "false").toBoolean(),
            )
        }
    }

    private fun save() {
        runCatching {
            file.parentFile?.mkdirs()
            val props = Properties()
            val s = _settings.value
            props.setProperty("ipv6", s.ipv6Enabled.toString())
            props.setProperty("autoStart", s.autoStart.toString())
            s.manualEngine?.let { props.setProperty("manualEngine", it.name) }
            props.setProperty("appMode", s.appMode.name)
            props.setProperty("killswitch", s.killswitchEnabled.toString())
            file.outputStream().use { props.store(it, "Ozero Desktop Settings") }
        }
    }
}
