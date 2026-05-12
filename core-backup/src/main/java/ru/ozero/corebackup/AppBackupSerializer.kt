package ru.ozero.corebackup

import org.json.JSONArray
import org.json.JSONObject

object AppBackupSerializer {

    fun serialize(data: AppBackupData): String {
        val root = JSONObject()
        root.put("version", data.version)
        root.put("exportedAt", data.exportedAt)
        root.put("settings", serializeAppSettings(data.settings))

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

        data.strategy?.let { root.put("strategy", serializeStrategy(it)) }

        return root.toString(2)
    }

    private fun serializeAppSettings(s: BackupSettings): JSONObject {
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
        return settings
    }

    private fun serializeStrategySettings(ss: BackupStrategySettings): JSONObject {
        val sObj = JSONObject()
        ss.requestsPerDomain?.let { sObj.put("requestsPerDomain", it) }
        ss.concurrentLimit?.let { sObj.put("concurrentLimit", it) }
        ss.timeoutSeconds?.let { sObj.put("timeoutSeconds", it) }
        ss.delayBetweenMs?.let { sObj.put("delayBetweenMs", it) }
        ss.useCustomStrategies?.let { sObj.put("useCustomStrategies", it) }
        ss.customStrategies?.let { sObj.put("customStrategies", it) }
        ss.evolutionMode?.let { sObj.put("evolutionMode", it) }
        ss.evolutionPopulationSize?.let { sObj.put("evolutionPopulationSize", it) }
        ss.evolutionMaxGenerations?.let { sObj.put("evolutionMaxGenerations", it) }
        ss.evolutionMutationRate?.let { sObj.put("evolutionMutationRate", it) }
        ss.evolutionEliteCount?.let { sObj.put("evolutionEliteCount", it) }
        return sObj
    }

    private fun serializeStrategy(strat: BackupStrategy): JSONObject {
        val stratObj = JSONObject()
        strat.settings?.let { stratObj.put("settings", serializeStrategySettings(it)) }

        val dlArr = JSONArray()
        for (dl in strat.domainLists) {
            val obj = JSONObject()
            obj.put("id", dl.id)
            obj.put("name", dl.name)
            obj.put("isActive", dl.isActive)
            obj.put("isBuiltIn", dl.isBuiltIn)
            val domains = JSONArray()
            dl.domains.forEach { domains.put(it) }
            obj.put("domains", domains)
            dlArr.put(obj)
        }
        stratObj.put("domainLists", dlArr)

        val ssArr = JSONArray()
        for (ss in strat.savedStrategies) {
            val obj = JSONObject()
            obj.put("id", ss.id)
            obj.put("command", ss.command)
            obj.put("isPinned", ss.isPinned)
            ss.name?.let { obj.put("name", it) }
            ssArr.put(obj)
        }
        stratObj.put("savedStrategies", ssArr)

        strat.evolutionMemory?.let { stratObj.put("evolutionMemory", it) }
        return stratObj
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
        val settings = deserializeAppSettings(s)

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

        val strategy = if (root.has("strategy")) {
            deserializeStrategy(root.getJSONObject("strategy"))
        } else {
            null
        }

        return AppBackupData(
            version = version,
            exportedAt = exportedAt,
            settings = settings,
            urnetwork = urnetwork,
            warpSlots = warpSlots,
            splitRules = splitRules,
            strategy = strategy,
        )
    }

    private fun deserializeAppSettings(s: JSONObject): BackupSettings =
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
        )

    private fun deserializeStrategySettings(ss: JSONObject): BackupStrategySettings =
        BackupStrategySettings(
            requestsPerDomain = ss.intOrNull("requestsPerDomain"),
            concurrentLimit = ss.intOrNull("concurrentLimit"),
            timeoutSeconds = ss.intOrNull("timeoutSeconds"),
            delayBetweenMs = if (ss.has("delayBetweenMs")) {
                ss.getLong("delayBetweenMs")
            } else {
                null
            },
            useCustomStrategies = ss.booleanOrNull("useCustomStrategies"),
            customStrategies = ss.optString("customStrategies").takeIf { it.isNotEmpty() },
            evolutionMode = ss.booleanOrNull("evolutionMode"),
            evolutionPopulationSize = ss.intOrNull("evolutionPopulationSize"),
            evolutionMaxGenerations = ss.intOrNull("evolutionMaxGenerations"),
            evolutionMutationRate = ss.floatOrNull("evolutionMutationRate"),
            evolutionEliteCount = ss.intOrNull("evolutionEliteCount"),
        )

    private fun deserializeStrategy(stratObj: JSONObject): BackupStrategy {
        val ss = stratObj.optJSONObject("settings")
        val backupSettings = if (ss != null) {
            deserializeStrategySettings(ss)
        } else {
            null
        }

        val dlArr = stratObj.optJSONArray("domainLists") ?: JSONArray()
        val domainLists = (0 until dlArr.length()).map { i ->
            val obj = dlArr.getJSONObject(i)
            val domainsArr = obj.optJSONArray("domains") ?: JSONArray()
            BackupDomainList(
                id = obj.getString("id"),
                name = obj.getString("name"),
                isActive = obj.optBoolean("isActive", true),
                isBuiltIn = obj.optBoolean("isBuiltIn", false),
                domains = (0 until domainsArr.length()).map { domainsArr.getString(it) },
            )
        }

        val savedArr = stratObj.optJSONArray("savedStrategies") ?: JSONArray()
        val savedStrategies = (0 until savedArr.length()).map { i ->
            val obj = savedArr.getJSONObject(i)
            BackupSavedStrategy(
                id = obj.getString("id"),
                command = obj.getString("command"),
                isPinned = obj.optBoolean("isPinned", false),
                name = obj.optString("name").takeIf { it.isNotEmpty() },
            )
        }

        return BackupStrategy(
            settings = backupSettings,
            domainLists = domainLists,
            savedStrategies = savedStrategies,
            evolutionMemory = stratObj.optString("evolutionMemory").takeIf {
                it.isNotEmpty()
            },
        )
    }

    private fun JSONObject.intOrNull(key: String): Int? = if (has(key)) getInt(key) else null

    private fun JSONObject.floatOrNull(key: String): Float? =
        if (has(key)) getDouble(key).toFloat() else null

    private fun JSONObject.booleanOrNull(key: String): Boolean? = if (has(key)) {
        getBoolean(key)
    } else {
        null
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
