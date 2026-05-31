package ru.ozero.enginewarp

data class WarpEditDraft(
    val slotId: String,
    val name: String,
    val endpoint: String,
    val privateKey: String,
    val publicKey: String,
    val peerPublicKey: String,
    val addressV4: String,
    val addressV6: String,
    val dns: String,
    val mtu: String,
    val keepalive: String,
    val jc: String,
    val jmin: String,
    val jmax: String,
    val s1: String,
    val s2: String,
    val h1: String,
    val h2: String,
    val h3: String,
    val h4: String,
    val doHProvider: DoHProvider = WarpConfig.DEFAULT_DOH_PROVIDER,
)

data class WarpSettingsUiState(
    val slots: List<WarpConfigSlot> = emptyList(),
    val activeSlotId: String? = null,
    val isRegistering: Boolean = false,
    val errorMessage: String? = null,
    val errorMessageRes: Int? = null,
    val progressMirror: String? = null,
    val progressCurrent: Int = 0,
    val progressTotal: Int = 0,
    val importSuccessCount: Int = 0,
    val editDraft: WarpEditDraft? = null,
    val isProvingEndpoints: Boolean = false,
    val provingProgress: String = "",
)

fun buildNextWarpSlotName(existing: List<WarpConfigSlot>): String {
    val used = existing.mapNotNull { it.name.removePrefix("Ozero-").toIntOrNull() }.toSet()
    var n = 1
    while (n in used) n++
    return "Ozero-$n"
}

fun draftFromSlot(slot: WarpConfigSlot): WarpEditDraft =
    draftFromConfig(slot.config, slot.id, slot.name)

fun draftFromConfig(
    config: WarpConfig,
    slotId: String = "",
    name: String = "WARP",
): WarpEditDraft {
    val awg = config.awgParams
    return WarpEditDraft(
        slotId = slotId,
        name = name,
        endpoint = config.peerEndpoint,
        privateKey = config.privateKey,
        publicKey = config.publicKey,
        peerPublicKey = config.peerPublicKey,
        addressV4 = config.interfaceAddressV4,
        addressV6 = config.interfaceAddressV6,
        dns = config.dnsServers.joinToString(", "),
        mtu = config.mtu.toString(),
        keepalive = config.keepaliveSeconds.toString(),
        jc = awg.junkPacketCount.toString(),
        jmin = awg.junkPacketMinSize.toString(),
        jmax = awg.junkPacketMaxSize.toString(),
        s1 = awg.initPacketJunkSize.toString(),
        s2 = awg.responsePacketJunkSize.toString(),
        h1 = awg.initPacketMagicHeader.toString(),
        h2 = awg.responsePacketMagicHeader.toString(),
        h3 = awg.cookieReplyMagicHeader.toString(),
        h4 = awg.transportMagicHeader.toString(),
        doHProvider = config.doHProvider,
    )
}

fun emptyWarpDraft(): WarpEditDraft =
    draftFromConfig(
        WarpConfig(
            privateKey = "",
            publicKey = "",
            peerPublicKey = "",
            peerEndpoint = "",
            interfaceAddressV4 = "",
            interfaceAddressV6 = "",
        ),
    )

fun WarpEditDraft.toWarpConfig(
    fallbackAwgParams: AwgParams = AwgParams(),
): WarpConfig {
    val mtu = mtu.toIntOrNull() ?: WarpConfig.DEFAULT_MTU
    val keepalive = keepalive.toIntOrNull() ?: WarpConfig.DEFAULT_KEEPALIVE
    val dns = parseDnsServers(dns)
    val jcValue = jc.toIntOrNull() ?: AwgParams.DEFAULT_JC
    val jminValue = jmin.toIntOrNull() ?: AwgParams.DEFAULT_JMIN
    val jmaxValue = jmax.toIntOrNull() ?: AwgParams.DEFAULT_JMAX
    val s1Value = s1.toIntOrNull() ?: AwgParams.DEFAULT_S1
    val s2Value = s2.toIntOrNull() ?: AwgParams.DEFAULT_S2
    val h1Value = h1.toLongOrNull() ?: AwgParams.DEFAULT_H1
    val h2Value = h2.toLongOrNull() ?: AwgParams.DEFAULT_H2
    val h3Value = h3.toLongOrNull() ?: AwgParams.DEFAULT_H3
    val h4Value = h4.toLongOrNull() ?: AwgParams.DEFAULT_H4
    return WarpConfig(
        privateKey = privateKey.trim(),
        publicKey = publicKey.trim(),
        peerPublicKey = peerPublicKey.trim(),
        peerEndpoint = endpoint.trim(),
        interfaceAddressV4 = addressV4.trim(),
        interfaceAddressV6 = addressV6.trim(),
        mtu = mtu,
        dnsServers = dns,
        keepaliveSeconds = keepalive,
        awgParams = fallbackAwgParams.copy(
            junkPacketCount = jcValue,
            junkPacketMinSize = minOf(jminValue, jmaxValue),
            junkPacketMaxSize = maxOf(jminValue, jmaxValue),
            initPacketJunkSize = s1Value,
            responsePacketJunkSize = s2Value,
            initPacketMagicHeader = h1Value,
            responsePacketMagicHeader = h2Value,
            cookieReplyMagicHeader = h3Value,
            transportMagicHeader = h4Value,
        ),
        doHProvider = doHProvider,
    )
}

fun hasRequiredFields(draft: WarpEditDraft): Boolean =
    listOf(draft.privateKey, draft.peerPublicKey, draft.endpoint, draft.addressV4).none { it.isBlank() }

internal fun parseDnsServers(raw: String): List<String> {
    val resolved = raw.split(",").map { it.trim() }.filter { it.isNotBlank() }
    return resolved.ifEmpty { WarpConfig.DEFAULT_DNS }
}
