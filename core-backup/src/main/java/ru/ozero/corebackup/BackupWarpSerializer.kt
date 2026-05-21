package ru.ozero.corebackup

import org.json.JSONArray
import org.json.JSONObject

internal object BackupWarpSerializer {

    fun toJson(slot: BackupWarpSlot): JSONObject {
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
        slot.awgS3?.let { obj.put("awgS3", it) }
        slot.awgS4?.let { obj.put("awgS4", it) }
        slot.awgI1?.let { obj.put("awgI1", it) }
        slot.awgI2?.let { obj.put("awgI2", it) }
        slot.awgI3?.let { obj.put("awgI3", it) }
        slot.awgI4?.let { obj.put("awgI4", it) }
        slot.awgI5?.let { obj.put("awgI5", it) }
        slot.awgI1Hex?.let { obj.put("awgI1Hex", it) }
        slot.awgI2Hex?.let { obj.put("awgI2Hex", it) }
        slot.awgI3Hex?.let { obj.put("awgI3Hex", it) }
        slot.awgI4Hex?.let { obj.put("awgI4Hex", it) }
        slot.awgI5Hex?.let { obj.put("awgI5Hex", it) }
        return obj
    }

    fun fromJson(obj: JSONObject): BackupWarpSlot {
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
            awgS3 = if (obj.has("awgS3")) obj.optInt("awgS3", 0) else null,
            awgS4 = if (obj.has("awgS4")) obj.optInt("awgS4", 0) else null,
            awgI1 = if (obj.has("awgI1")) obj.optInt("awgI1", 0) else null,
            awgI2 = if (obj.has("awgI2")) obj.optInt("awgI2", 0) else null,
            awgI3 = if (obj.has("awgI3")) obj.optInt("awgI3", 0) else null,
            awgI4 = if (obj.has("awgI4")) obj.optInt("awgI4", 0) else null,
            awgI5 = if (obj.has("awgI5")) obj.optInt("awgI5", 0) else null,
            awgI1Hex = obj.optString("awgI1Hex", "").takeIf { it.isNotEmpty() },
            awgI2Hex = obj.optString("awgI2Hex", "").takeIf { it.isNotEmpty() },
            awgI3Hex = obj.optString("awgI3Hex", "").takeIf { it.isNotEmpty() },
            awgI4Hex = obj.optString("awgI4Hex", "").takeIf { it.isNotEmpty() },
            awgI5Hex = obj.optString("awgI5Hex", "").takeIf { it.isNotEmpty() },
        )
    }
}
