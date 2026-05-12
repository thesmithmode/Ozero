package ru.ozero.corebackup

import org.json.JSONArray
import org.json.JSONObject

object AppBackupSerializer {

    fun serialize(data: AppBackupData): String {
        val root = JSONObject()
        root.put("version", data.version)
        root.put("exportedAt", data.exportedAt)
        root.put("settings", BackupSettingsSerializer.serialize(data.settings))

        val urn = JSONObject()
        data.urnetwork.walletOverride?.let { urn.put("walletOverride", it) }
        data.urnetwork.byJwt?.let { urn.put("byJwt", it) }
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
        val settings = BackupSettingsSerializer.deserialize(root.optJSONObject("settings") ?: JSONObject())

        val u = root.optJSONObject("urnetwork") ?: JSONObject()
        val urnetwork = BackupUrnetwork(
            walletOverride = u.optString("walletOverride").takeIf { it.isNotEmpty() },
            byJwt = u.optString("byJwt").takeIf { it.isNotEmpty() },
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
}
