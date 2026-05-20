package ru.ozero.corebackup

import org.json.JSONObject

internal object BackupSettingsSerializer {

    fun serialize(s: BackupSettings): JSONObject {
        val obj = JSONObject()
        s.splitMode?.let { obj.put("splitMode", it) }
        s.ipv6Enabled?.let { obj.put("ipv6Enabled", it) }
        s.autoStart?.let { obj.put("autoStart", it) }
        s.manualEngine?.let { obj.put("manualEngine", it) }
        s.bydpiWinningArgs?.let { obj.put("bydpiWinningArgs", it) }
        s.urnetworkEnabled?.let { obj.put("urnetworkEnabled", it) }
        s.urnetworkJwt?.let { obj.put("urnetworkJwt", it) }
        s.customDnsServers?.let { obj.put("customDnsServers", it) }
        s.hostsMode?.let { obj.put("hostsMode", it) }
        s.hostsList?.let { obj.put("hostsList", it) }
        s.uiLocaleTag?.let { obj.put("uiLocaleTag", it) }
        s.appMode?.let { obj.put("appMode", it) }
        s.engineAutoPriority?.let { obj.put("engineAutoPriority", it) }
        s.bydpiUseUiMode?.let { obj.put("bydpiUseUiMode", it) }
        s.bydpiUiSettingsJson?.let { obj.put("bydpiUiSettingsJson", it) }
        s.bydpiDefaultAccepted?.let { obj.put("bydpiDefaultAccepted", it) }
        s.urnetworkCountryCode?.let { obj.put("urnetworkCountryCode", it) }
        return obj
    }

    fun deserialize(s: JSONObject): BackupSettings =
        BackupSettings(
            splitMode = s.optString("splitMode").takeIf { it.isNotEmpty() },
            ipv6Enabled = s.booleanOrNull("ipv6Enabled"),
            autoStart = s.booleanOrNull("autoStart"),
            manualEngine = s.optString("manualEngine").takeIf { it.isNotEmpty() },
            bydpiWinningArgs = s.optString("bydpiWinningArgs").takeIf { it.isNotEmpty() },
            urnetworkEnabled = s.booleanOrNull("urnetworkEnabled"),
            urnetworkJwt = s.optString("urnetworkJwt").takeIf { it.isNotEmpty() },
            customDnsServers = s.optString("customDnsServers").takeIf { it.isNotEmpty() },
            hostsMode = s.optString("hostsMode").takeIf { it.isNotEmpty() },
            hostsList = s.optString("hostsList").takeIf { it.isNotEmpty() },
            uiLocaleTag = s.optString("uiLocaleTag").takeIf { it.isNotEmpty() },
            appMode = s.optString("appMode").takeIf { it.isNotEmpty() },
            engineAutoPriority = s.optString("engineAutoPriority").takeIf { it.isNotEmpty() },
            bydpiUseUiMode = s.booleanOrNull("bydpiUseUiMode"),
            bydpiUiSettingsJson = s.optString("bydpiUiSettingsJson").takeIf { it.isNotEmpty() },
            bydpiDefaultAccepted = s.booleanOrNull("bydpiDefaultAccepted"),
            urnetworkCountryCode = s.optString("urnetworkCountryCode").takeIf { it.isNotEmpty() },
        )
}
