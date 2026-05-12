package ru.ozero.corebackup

import org.json.JSONArray
import org.json.JSONObject

internal object BackupStrategySerializer {

    fun serialize(strat: BackupStrategy): JSONObject {
        val stratObj = JSONObject()
        strat.settings?.let { stratObj.put("settings", serializeSettings(it)) }

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

        strat.evolutionMemory?.let { raw ->
            val parsed = runCatching { JSONObject(raw) }.getOrNull()
            if (parsed != null) stratObj.put("evolutionMemory", parsed)
        }
        return stratObj
    }

    fun deserialize(stratObj: JSONObject): BackupStrategy {
        val ss = stratObj.optJSONObject("settings")
        val settings = if (ss != null) deserializeSettings(ss) else null

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
            settings = settings,
            domainLists = domainLists,
            savedStrategies = savedStrategies,
            evolutionMemory = stratObj.optJSONObject("evolutionMemory")?.toString()
                ?: stratObj.optString("evolutionMemory").takeIf { it.isNotEmpty() },
        )
    }

    private fun serializeSettings(ss: BackupStrategySettings): JSONObject {
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

    private fun deserializeSettings(ss: JSONObject): BackupStrategySettings =
        BackupStrategySettings(
            requestsPerDomain = ss.intOrNull("requestsPerDomain"),
            concurrentLimit = ss.intOrNull("concurrentLimit"),
            timeoutSeconds = ss.intOrNull("timeoutSeconds"),
            delayBetweenMs = if (ss.has("delayBetweenMs")) ss.getLong("delayBetweenMs") else null,
            useCustomStrategies = ss.booleanOrNull("useCustomStrategies"),
            customStrategies = ss.optString("customStrategies").takeIf { it.isNotEmpty() },
            evolutionMode = ss.booleanOrNull("evolutionMode"),
            evolutionPopulationSize = ss.intOrNull("evolutionPopulationSize"),
            evolutionMaxGenerations = ss.intOrNull("evolutionMaxGenerations"),
            evolutionMutationRate = ss.floatOrNull("evolutionMutationRate"),
            evolutionEliteCount = ss.intOrNull("evolutionEliteCount"),
        )
}
