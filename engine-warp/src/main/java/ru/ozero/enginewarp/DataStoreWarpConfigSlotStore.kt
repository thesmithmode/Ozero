package ru.ozero.enginewarp

import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import ru.ozero.enginescore.PersistentLoggers
import java.util.UUID

class DataStoreWarpConfigSlotStore(
    private val dataStore: DataStore<Preferences>,
    private val legacyStore: DataStoreWarpConfigStore,
) : WarpConfigSlotStore {

    private val mutex = Mutex()

    override fun slots(): Flow<List<WarpConfigSlot>> = dataStore.data.map { prefs ->
        parseSlots(prefs[KEY_SLOTS] ?: "[]")
    }

    override fun activeSlot(): Flow<WarpConfigSlot?> = slots().map { list ->
        list.firstOrNull { it.isActive }
    }

    override fun activeConfig(): Flow<WarpConfig?> = slots().map { list ->
        list.firstOrNull { it.isActive }?.config
    }

    override suspend fun addSlot(name: String, config: WarpConfig, rawIni: String?): String = mutex.withLock {
        val id = UUID.randomUUID().toString()
        val fingerprint = config.dedupFingerprint()
        var duplicate: WarpConfigSlot? = null
        dataStore.edit { prefs ->
            val current = parseSlots(prefs[KEY_SLOTS] ?: "[]")
            val existing = current.firstOrNull { it.config.dedupFingerprint() == fingerprint }
            if (existing != null) {
                duplicate = existing
                return@edit
            }
            val makeActive = current.isEmpty()
            val updated = current + WarpConfigSlot(
                id = id,
                name = name,
                config = config,
                isActive = makeActive,
                rawIniOverride = rawIni,
            )
            prefs[KEY_SLOTS] = serializeSlots(updated)
        }
        duplicate?.let { throw WarpConfigDuplicateException(it.id, it.name) }
        id
    }

    override suspend fun setActive(id: String): Unit = mutex.withLock {
        dataStore.edit { prefs ->
            val current = parseSlots(prefs[KEY_SLOTS] ?: "[]")
            if (current.none { it.id == id }) return@edit
            val updated = current.map { slot -> slot.copy(isActive = slot.id == id) }
            prefs[KEY_SLOTS] = serializeSlots(updated)
        }
    }

    override suspend fun rename(id: String, name: String): Unit = mutex.withLock {
        dataStore.edit { prefs ->
            val current = parseSlots(prefs[KEY_SLOTS] ?: "[]")
            val updated = current.map { slot -> if (slot.id == id) slot.copy(name = name) else slot }
            prefs[KEY_SLOTS] = serializeSlots(updated)
        }
    }

    override suspend fun updateSlot(id: String, name: String, config: WarpConfig, rawIni: String?): Unit =
        mutex.withLock {
            dataStore.edit { prefs ->
                val current = parseSlots(prefs[KEY_SLOTS] ?: "[]")
                val updated = current.map { slot ->
                    if (slot.id == id) {
                        slot.copy(name = name, config = config, rawIniOverride = rawIni)
                    } else {
                        slot
                    }
                }
                prefs[KEY_SLOTS] = serializeSlots(updated)
            }
        }

    override suspend fun delete(id: String): Unit = mutex.withLock {
        dataStore.edit { prefs ->
            val current = parseSlots(prefs[KEY_SLOTS] ?: "[]")
            val filtered = current.filter { it.id != id }
            val needsNewActive = filtered.isNotEmpty() && filtered.none { it.isActive }
            val updated = if (needsNewActive) {
                filtered.mapIndexed { i, slot -> if (i == 0) slot.copy(isActive = true) else slot }
            } else {
                filtered
            }
            prefs[KEY_SLOTS] = serializeSlots(updated)
        }
    }

    override suspend fun replaceAll(slots: List<WarpConfigSlot>): Unit = mutex.withLock {
        dataStore.edit { prefs ->
            prefs[KEY_SLOTS] = serializeSlots(slots)
        }
    }

    override suspend fun clear(): Unit = mutex.withLock {
        dataStore.edit { prefs ->
            prefs.remove(KEY_SLOTS)
            prefs.remove(KEY_MIGRATION_DONE)
        }
    }

    override suspend fun migrateIfNeeded(): Unit = mutex.withLock {
        val alreadyDone = dataStore.data.map { it[KEY_MIGRATION_DONE] ?: false }.first()
        if (alreadyDone) return
        val legacyConfig = legacyStore.current().first()
        dataStore.edit { prefs ->
            if (prefs[KEY_MIGRATION_DONE] == true) return@edit
            prefs[KEY_MIGRATION_DONE] = true
            if (legacyConfig == null) return@edit
            val existing = parseSlots(prefs[KEY_SLOTS] ?: "[]")
            if (existing.isNotEmpty()) return@edit
            val id = UUID.randomUUID().toString()
            val slot = WarpConfigSlot(id = id, name = "Migrated", config = legacyConfig, isActive = true)
            prefs[KEY_SLOTS] = serializeSlots(listOf(slot))
            Log.i(TAG, "migrated legacy WARP config to slot $id")
        }
    }

    private fun parseSlots(json: String): List<WarpConfigSlot> {
        val arr = try {
            JSONArray(json)
        } catch (e: Exception) {
            PersistentLoggers.warn(TAG, "slot list JSON invalid: ${e.message}")
            return emptyList()
        }
        val result = mutableListOf<WarpConfigSlot>()
        for (i in 0 until arr.length()) {
            try {
                result += slotFromJson(arr.getJSONObject(i))
            } catch (e: Exception) {
                PersistentLoggers.warn(TAG, "slot[$i] parse failed, skipping: ${e.message}")
            }
        }
        return result
    }

    private fun slotFromJson(obj: JSONObject): WarpConfigSlot {
        val configObj = obj.getJSONObject("config")
        val awgObj = configObj.optJSONObject("awgParams") ?: JSONObject()
        val dnsArr = configObj.optJSONArray("dnsServers")
        val dns = if (dnsArr != null) {
            (0 until dnsArr.length()).map { dnsArr.getString(it) }
        } else {
            WarpConfig.DEFAULT_DNS
        }
        val awg = AwgParams(
            junkPacketCount = awgObj.optInt("jc", AwgParams.DEFAULT_JC),
            junkPacketMinSize = awgObj.optInt("jmin", AwgParams.DEFAULT_JMIN),
            junkPacketMaxSize = awgObj.optInt("jmax", AwgParams.DEFAULT_JMAX),
            initPacketJunkSize = awgObj.optInt("s1", AwgParams.DEFAULT_S1),
            responsePacketJunkSize = awgObj.optInt("s2", AwgParams.DEFAULT_S2),
            underloadPacketJunkSize = awgObj.optInt("s3", AwgParams.DEFAULT_S3),
            payloadPacketJunkSize = awgObj.optInt("s4", AwgParams.DEFAULT_S4),
            initPacketMagicHeader = awgObj.optLong("h1", AwgParams.DEFAULT_H1),
            responsePacketMagicHeader = awgObj.optLong("h2", AwgParams.DEFAULT_H2),
            cookieReplyMagicHeader = awgObj.optLong("h3", AwgParams.DEFAULT_H3),
            transportMagicHeader = awgObj.optLong("h4", AwgParams.DEFAULT_H4),
            payloadPacketSizeCount1 = awgObj.optInt("i1", AwgParams.DEFAULT_I1),
            payloadPacketSizeCount2 = awgObj.optInt("i2", AwgParams.DEFAULT_I2),
            specialJunk3 = awgObj.optInt("i3", AwgParams.DEFAULT_I3),
            specialJunk4 = awgObj.optInt("i4", AwgParams.DEFAULT_I4),
            payloadPacketSizeCount3 = awgObj.optInt("i5", AwgParams.DEFAULT_I5),
            payloadHexI1 = awgObj.optString("i1Hex", "").takeIf { it.isNotEmpty() },
            payloadHexI2 = awgObj.optString("i2Hex", "").takeIf { it.isNotEmpty() },
            payloadHexI3 = awgObj.optString("i3Hex", "").takeIf { it.isNotEmpty() },
            payloadHexI4 = awgObj.optString("i4Hex", "").takeIf { it.isNotEmpty() },
            payloadHexI5 = awgObj.optString("i5Hex", "").takeIf { it.isNotEmpty() },
        )
        val config = WarpConfig(
            privateKey = configObj.getString("priv"),
            publicKey = configObj.optString("pub", ""),
            peerPublicKey = configObj.getString("peerPub"),
            peerEndpoint = configObj.getString("peerEndpoint"),
            interfaceAddressV4 = configObj.getString("ifaceV4"),
            interfaceAddressV6 = configObj.getString("ifaceV6"),
            accountLicense = configObj.optString("license", ""),
            mtu = configObj.optInt("mtu", WarpConfig.DEFAULT_MTU),
            dnsServers = dns,
            keepaliveSeconds = configObj.optInt("keepalive", WarpConfig.DEFAULT_KEEPALIVE),
            awgParams = awg,
            doHProvider = configObj.optString("doHProvider", "").let { name ->
                DoHProvider.entries.firstOrNull { it.name == name } ?: DoHProvider.SYSTEM
            },
        )
        val rawIni = obj.optString("rawIni", "").takeIf { it.isNotEmpty() }
        return WarpConfigSlot(
            id = obj.getString("id"),
            name = obj.getString("name"),
            config = config.copy(awgParams = migrateAwgParams(awg)),
            isActive = obj.optBoolean("isActive", false),
            rawIniOverride = rawIni,
        )
    }

    private fun migrateAwgParams(awg: AwgParams): AwgParams {
        val isOldInjected = awg.underloadPacketJunkSize == 19 &&
            awg.payloadPacketJunkSize == 20 &&
            awg.payloadPacketSizeCount1 == 28 &&
            awg.payloadHexI1 == null &&
            awg.payloadPacketSizeCount2 == 29 &&
            awg.payloadHexI2 == null &&
            awg.payloadPacketSizeCount3 == 10 &&
            awg.payloadHexI5 == null
        if (!isOldInjected) return awg
        return awg.copy(
            underloadPacketJunkSize = 0,
            payloadPacketJunkSize = 0,
            payloadPacketSizeCount1 = 0,
            payloadPacketSizeCount2 = 0,
            payloadPacketSizeCount3 = 0,
        )
    }

    private fun serializeSlots(slots: List<WarpConfigSlot>): String {
        val arr = JSONArray()
        for (slot in slots) {
            val obj = JSONObject()
            obj.put("id", slot.id)
            obj.put("name", slot.name)
            obj.put("isActive", slot.isActive)
            slot.rawIniOverride?.takeIf { it.isNotEmpty() }?.let { obj.put("rawIni", it) }
            val cfg = slot.config
            val configObj = JSONObject()
            configObj.put("priv", cfg.privateKey)
            configObj.put("pub", cfg.publicKey)
            configObj.put("peerPub", cfg.peerPublicKey)
            configObj.put("peerEndpoint", cfg.peerEndpoint)
            configObj.put("ifaceV4", cfg.interfaceAddressV4)
            configObj.put("ifaceV6", cfg.interfaceAddressV6)
            configObj.put("license", cfg.accountLicense)
            configObj.put("mtu", cfg.mtu)
            val dnsArr = JSONArray()
            cfg.dnsServers.forEach { dnsArr.put(it) }
            configObj.put("dnsServers", dnsArr)
            configObj.put("keepalive", cfg.keepaliveSeconds)
            val awg = cfg.awgParams
            val awgObj = JSONObject()
            awgObj.put("jc", awg.junkPacketCount)
            awgObj.put("jmin", awg.junkPacketMinSize)
            awgObj.put("jmax", awg.junkPacketMaxSize)
            awgObj.put("s1", awg.initPacketJunkSize)
            awgObj.put("s2", awg.responsePacketJunkSize)
            awgObj.put("s3", awg.underloadPacketJunkSize)
            awgObj.put("s4", awg.payloadPacketJunkSize)
            awgObj.put("h1", awg.initPacketMagicHeader)
            awgObj.put("h2", awg.responsePacketMagicHeader)
            awgObj.put("h3", awg.cookieReplyMagicHeader)
            awgObj.put("h4", awg.transportMagicHeader)
            awgObj.put("i1", awg.payloadPacketSizeCount1)
            awgObj.put("i2", awg.payloadPacketSizeCount2)
            awgObj.put("i3", awg.specialJunk3)
            awgObj.put("i4", awg.specialJunk4)
            awgObj.put("i5", awg.payloadPacketSizeCount3)
            awg.payloadHexI1?.let { awgObj.put("i1Hex", it) }
            awg.payloadHexI2?.let { awgObj.put("i2Hex", it) }
            awg.payloadHexI3?.let { awgObj.put("i3Hex", it) }
            awg.payloadHexI4?.let { awgObj.put("i4Hex", it) }
            awg.payloadHexI5?.let { awgObj.put("i5Hex", it) }
            configObj.put("awgParams", awgObj)
            configObj.put("doHProvider", cfg.doHProvider.name)
            obj.put("config", configObj)
            arr.put(obj)
        }
        return arr.toString()
    }

    private companion object {
        const val TAG = "DataStoreWarpConfigSlotStore"
        val KEY_SLOTS = stringPreferencesKey("warp_slots_json")
        val KEY_MIGRATION_DONE = booleanPreferencesKey("warp_slots_migration_done")
    }
}
