package ru.ozero.corebackup

import org.json.JSONArray
import org.json.JSONObject

object AppBackupSerializer {

    private const val MAX_BACKUP_BYTES = 10 * 1024 * 1024

    class BackupParseException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

    fun serialize(data: AppBackupData): String {
        val root = JSONObject()
        root.put("version", data.version)
        root.put("exportedAt", data.exportedAt)
        root.put("settings", BackupSettingsSerializer.serialize(data.settings))

        val urn = JSONObject()
        data.urnetwork.byJwt?.let { urn.put("byJwt", it) }
        data.urnetwork.windowType?.let { urn.put("windowType", it) }
        data.urnetwork.fixedIpSize?.let { urn.put("fixedIpSize", it) }
        data.urnetwork.allowDirect?.let { urn.put("allowDirect", it) }
        data.urnetwork.provideEnabled?.let { urn.put("provideEnabled", it) }
        data.urnetwork.provideControlMode?.let { urn.put("provideControlMode", it) }
        data.urnetwork.provideNetworkMode?.let { urn.put("provideNetworkMode", it) }
        data.urnetwork.selectedLocation?.let { loc ->
            val locObj = JSONObject()
            loc.countryCode?.let { locObj.put("countryCode", it) }
            loc.region?.let { locObj.put("region", it) }
            loc.city?.let { locObj.put("city", it) }
            urn.put("selectedLocation", locObj)
        }
        root.put("urnetwork", urn)

        val slotsArr = JSONArray()
        for (slot in data.warpSlots) slotsArr.put(BackupWarpSerializer.toJson(slot))
        root.put("warpSlots", slotsArr)

        val rulesArr = JSONArray()
        for (rule in data.splitRules) {
            val obj = JSONObject()
            obj.put("packageName", rule.packageName)
            obj.put("isExcluded", rule.isExcluded)
            rulesArr.put(obj)
        }
        root.put("splitRules", rulesArr)

        data.strategy?.let { root.put("strategy", BackupStrategySerializer.serialize(it)) }

        data.telegram?.let { tg ->
            val tgObj = JSONObject()
            tg.enabled?.let { tgObj.put("enabled", it) }
            tg.port?.let { tgObj.put("port", it) }
            tg.domain?.let { tgObj.put("domain", it) }
            tg.secret?.let { tgObj.put("secret", it) }
            root.put("telegram", tgObj)
        }

        return root.toString(2)
    }

    fun serializeEncrypted(data: AppBackupData): ByteArray =
        BackupCipher.encrypt(serialize(data).toByteArray(Charsets.UTF_8))

    fun deserializeAuto(bytes: ByteArray): AppBackupData {
        if (bytes.size > MAX_BACKUP_BYTES) {
            throw BackupParseException("Backup file too large: ${bytes.size} > $MAX_BACKUP_BYTES bytes")
        }
        val json = runCatching {
            if (BackupCipher.isEncrypted(bytes)) {
                BackupCipher.decrypt(bytes).toString(Charsets.UTF_8)
            } else {
                bytes.toString(Charsets.UTF_8)
            }
        }.getOrElse { throw BackupParseException("Failed to read backup payload", it) }
        return deserialize(json)
    }

    fun deserialize(json: String): AppBackupData = runCatching {
        val root = JSONObject(json)
        val version = root.getInt("version")
        if (version < 1 || version > AppBackupData.CURRENT_VERSION) {
            throw BackupParseException(
                "Unsupported backup version $version (expected 1..${AppBackupData.CURRENT_VERSION})",
            )
        }
        val exportedAt = root.optString("exportedAt", "")
        val settings = BackupSettingsSerializer.deserialize(root.optJSONObject("settings") ?: JSONObject())

        val u = root.optJSONObject("urnetwork") ?: JSONObject()
        val urnetwork = BackupUrnetwork(
            byJwt = u.optString("byJwt").takeIf { it.isNotEmpty() },
            windowType = u.optString("windowType").takeIf { it.isNotEmpty() },
            fixedIpSize = u.booleanOrNull("fixedIpSize"),
            allowDirect = u.booleanOrNull("allowDirect"),
            provideEnabled = u.booleanOrNull("provideEnabled"),
            provideControlMode = u.optString("provideControlMode").takeIf { it.isNotEmpty() },
            provideNetworkMode = u.optString("provideNetworkMode").takeIf { it.isNotEmpty() },
            selectedLocation = u.optJSONObject("selectedLocation")?.let { loc ->
                BackupUrnetworkLocation(
                    countryCode = loc.optString("countryCode").takeIf { it.isNotEmpty() },
                    region = loc.optString("region").takeIf { it.isNotEmpty() },
                    city = loc.optString("city").takeIf { it.isNotEmpty() },
                )
            },
        )

        val slotsArr = root.optJSONArray("warpSlots") ?: JSONArray()
        val warpSlots = (0 until slotsArr.length()).map { i ->
            BackupWarpSerializer.fromJson(slotsArr.getJSONObject(i))
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
            BackupStrategySerializer.deserialize(root.getJSONObject("strategy"))
        } else {
            null
        }

        val telegram = root.optJSONObject("telegram")?.let { tg ->
            BackupTelegram(
                enabled = tg.booleanOrNull("enabled"),
                port = tg.intOrNull("port"),
                domain = tg.optString("domain").takeIf { it.isNotEmpty() },
                secret = tg.optString("secret").takeIf { it.isNotEmpty() },
            )
        }

        AppBackupData(
            version = version,
            exportedAt = exportedAt,
            settings = settings,
            urnetwork = urnetwork,
            warpSlots = warpSlots,
            splitRules = splitRules,
            strategy = strategy,
            telegram = telegram,
        )
    }.getOrElse { e ->
        if (e is BackupParseException) throw e
        throw BackupParseException("Malformed backup JSON: ${e.javaClass.simpleName}", e)
    }
}
