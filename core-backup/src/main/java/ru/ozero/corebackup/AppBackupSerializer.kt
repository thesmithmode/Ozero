package ru.ozero.corebackup

import org.json.JSONArray
import org.json.JSONObject

object AppBackupSerializer {

    fun serialize(data: AppBackupData): String {
        val root = JSONObject()
        root.put("version", data.version)
        root.put("exportedAt", data.exportedAt)

        val s = data.settings
        val settings = JSONObject()
        s.splitMode?.let { settings.put("splitMode", it) }
        s.ipv6Enabled?.let { settings.put("ipv6Enabled", it) }
        s.autoStart?.let { settings.put("autoStart", it) }
        s.manualEngine?.let { settings.put("manualEngine", it) }
        s.bydpiWinningArgs?.let { settings.put("bydpiWinningArgs", it) }
        s.urnetworkEnabled?.let { settings.put("urnetworkEnabled", it) }
        s.urnetworkJwt?.let { settings.put("urnetworkJwt", it) }
        s.customDnsServers?.let { settings.put("customDnsServers", it) }
        s.hostsMode?.let { settings.put("hostsMode", it) }
        s.hostsList?.let { settings.put("hostsList", it) }
        s.uiLocaleTag?.let { settings.put("uiLocaleTag", it) }
        s.appMode?.let { settings.put("appMode", it) }
        root.put("settings", settings)

        val urn = JSONObject()
        data.urnetwork.walletOverride?.let { urn.put("walletOverride", it) }
        data.urnetwork.byJwt?.let { urn.put("byJwt", it) }
        root.put("urnetwork", urn)

        val slotsArr = JSONArray()
        for (slot in data.warpSlots) {
            slotsArr.put(slotToJson(slot))
        }
        root.put("warpSlots", slotsArr)

        val rulesArr = JSONArray()
        for (rule in data.splitRules) {
            val obj = JSONObject()
            obj.put("packageName", rule.packageName)
            obj.put("isExcluded", rule.isExcluded)
            rulesArr.put(obj)
        }
        root.put("splitRules", rulesArr)

        return root.toString(2)
    }

    fun serializeEncrypted(data: AppBackupData): ByteArray =
        BackupCipher.encrypt(serialize(data).toByteArray(Charsets.UTF_8))

    fun deserializeAuto(bytes: ByteArray): AppBackupData {
        val json = if (BackupCipher.isEncrypted(bytes)) {
            BackupCipher.decrypt(bytes).toString(Charsets.UTF_8)
        } else {
            bytes.toString(Charsets.UTF_8)
        }
        return deserialize(json)
    }

    fun deserialize(json: String): AppBackupData {
        val root = JSONObject(json)
        val version = root.getInt("version")
        if (version > AppBackupData.CURRENT_VERSION) {
            error("Unsupported backup version $version (max ${AppBackupData.CURRENT_VERSION})")
        }
        val exportedAt = root.optString("exportedAt", "")

        val s = root.optJSONObject("settings") ?: JSONObject()
        val settings = BackupSettings(
            splitMode = s.optString("splitMode").takeIf { it.isNotEmpty() },
            ipv6Enabled = if (s.has("ipv6Enabled")) s.getBoolean("ipv6Enabled") else null,
            autoStart = if (s.has("autoStart")) s.getBoolean("autoStart") else null,
            manualEngine = s.optString("manualEngine").takeIf { it.isNotEmpty() },
            bydpiWinningArgs = s.optString("bydpiWinningArgs").takeIf { it.isNotEmpty() },
            urnetworkEnabled = if (s.has("urnetworkEnabled")) s.getBoolean("urnetworkEnabled") else null,
            urnetworkJwt = s.optString("urnetworkJwt").takeIf { it.isNotEmpty() },
            customDnsServers = s.optString("customDnsServers").takeIf { it.isNotEmpty() },
            hostsMode = s.optString("hostsMode").takeIf { it.isNotEmpty() },
            hostsList = s.optString("hostsList").takeIf { it.isNotEmpty() },
            uiLocaleTag = s.optString("uiLocaleTag").takeIf { it.isNotEmpty() },
            appMode = s.optString("appMode").takeIf { it.isNotEmpty() },
        )

        val u = root.optJSONObject("urnetwork") ?: JSONObject()
        val urnetwork = BackupUrnetwork(
            walletOverride = u.optString("walletOverride").takeIf { it.isNotEmpty() },
            byJwt = u.optString("byJwt").takeIf { it.isNotEmpty() },
        )

        val slotsArr = root.optJSONArray("warpSlots") ?: JSONArray()
        val warpSlots = (0 until slotsArr.length()).map { i ->
            slotFromJson(slotsArr.getJSONObject(i))
        }

        val rulesArr = root.optJSONArray("splitRules") ?: JSONArray()
        val splitRules = (0 until rulesArr.length()).map { i ->
            val obj = rulesArr.getJSONObject(i)
            BackupSplitRule(
                packageName = obj.getString("packageName"),
                isExcluded = obj.optBoolean("isExcluded", false),
            )
        }

        return AppBackupData(
            version = version,
            exportedAt = exportedAt,
            settings = settings,
            urnetwork = urnetwork,
            warpSlots = warpSlots,
            splitRules = splitRules,
        )
    }

    private fun slotToJson(slot: BackupWarpSlot): JSONObject {
        val obj = JSONObject()
        obj.put("id", slot.id)
        obj.put("name", slot.name)
        obj.put("isActive", slot.isActive)
        obj.put("priv", slot.privateKey)
        obj.put("pub", slot.publicKey)
        obj.put("peerPub", slot.peerPublicKey)
        obj.put("peerEndpoint", slot.peerEndpoint)
        obj.put("ifaceV4", slot.interfaceAddressV4)
        obj.put("ifaceV6", slot.interfaceAddressV6)
        obj.put("license", slot.accountLicense)
        obj.put("mtu", slot.mtu)
        val dns = JSONArray()
        slot.dnsServers.forEach { dns.put(it) }
        obj.put("dnsServers", dns)
        obj.put("keepalive", slot.keepaliveSeconds)
        obj.put("awgJc", slot.awgJc)
        obj.put("awgJmin", slot.awgJmin)
        obj.put("awgJmax", slot.awgJmax)
        obj.put("awgS1", slot.awgS1)
        obj.put("awgS2", slot.awgS2)
        obj.put("awgH1", slot.awgH1)
        obj.put("awgH2", slot.awgH2)
        obj.put("awgH3", slot.awgH3)
        obj.put("awgH4", slot.awgH4)
        return obj
    }

    private fun slotFromJson(obj: JSONObject): BackupWarpSlot {
        val dnsArr = obj.optJSONArray("dnsServers")
        val dns = if (dnsArr != null) {
            (0 until dnsArr.length()).map { dnsArr.getString(it) }
        } else {
            listOf("1.1.1.1", "1.0.0.1")
        }
        return BackupWarpSlot(
            id = obj.getString("id"),
            name = obj.getString("name"),
            isActive = obj.optBoolean("isActive", false),
            privateKey = obj.getString("priv"),
            publicKey = obj.optString("pub", ""),
            peerPublicKey = obj.getString("peerPub"),
            peerEndpoint = obj.getString("peerEndpoint"),
            interfaceAddressV4 = obj.getString("ifaceV4"),
            interfaceAddressV6 = obj.getString("ifaceV6"),
            accountLicense = obj.optString("license", ""),
            mtu = obj.optInt("mtu", 1280),
            dnsServers = dns,
            keepaliveSeconds = obj.optInt("keepalive", 25),
            awgJc = obj.optInt("awgJc", 0),
            awgJmin = obj.optInt("awgJmin", 0),
            awgJmax = obj.optInt("awgJmax", 0),
            awgS1 = obj.optInt("awgS1", 0),
            awgS2 = obj.optInt("awgS2", 0),
            awgH1 = obj.optLong("awgH1", 0L),
            awgH2 = obj.optLong("awgH2", 0L),
            awgH3 = obj.optLong("awgH3", 0L),
            awgH4 = obj.optLong("awgH4", 0L),
        )
    }
}
