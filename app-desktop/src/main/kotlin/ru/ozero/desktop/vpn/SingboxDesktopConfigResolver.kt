package ru.ozero.desktop.vpn

import ru.ozero.desktop.engine.SingboxDesktopEngine
import ru.ozero.desktop.model.SettingsModel
import ru.ozero.desktop.model.VpnMode
import java.io.File

object SingboxDesktopConfigResolver {
    val defaultConfigFile: File
        get() = File(System.getProperty("user.home"), ".ozero/singbox-custom.json")

    fun resolve(
        settings: SettingsModel,
        mode: VpnMode,
        importedConfigFile: File = defaultConfigFile,
    ): Result<String> {
        if (importedConfigFile.isFile) {
            return runCatching { importedConfigFile.readText().trim() }
                .mapCatching { json ->
                    if (json.isBlank()) throw IllegalStateException("Sing-box imported JSON config is empty")
                    json
                }
        }

        val outbound = settings.singboxCustomLink.trim().takeIf { it.startsWith("{") }
        if (outbound != null) {
            return Result.success(
                when (mode) {
                    VpnMode.TUN -> SingboxDesktopEngine.buildFullTunConfig(outbound)
                    VpnMode.PROXY -> SingboxDesktopEngine.buildFullProxyConfig(outbound)
                },
            )
        }

        val hasRuntimeConfig = settings.singboxCustomLink.isNotBlank() ||
            settings.singboxSubscriptionUrl.isNotBlank()
        if (hasRuntimeConfig) {
            return Result.failure(
                IllegalStateException("Sing-box desktop requires an imported JSON config before connecting"),
            )
        }

        return Result.success(
            when (mode) {
                VpnMode.TUN -> SingboxDesktopEngine.buildTunConfig()
                VpnMode.PROXY -> SingboxDesktopEngine.buildProxyConfig()
            },
        )
    }
}
