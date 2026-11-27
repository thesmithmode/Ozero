package ru.ozero.enginescore.settings

/** Connection options supported by the reference gfVPN binding. Empty strings select SDK defaults. */
data class GfVpnOptions(
    val serverSelection: String = "auto",
    val transport: String = "",
    val simultaneousConnections: Int = 0,
    val maxConcurrentAttempts: Int = 0,
    val rotation: String = "",
    val autoUpdate: Boolean = true,
    val cachedNodes: Boolean = true,
    val bypassLocalServices: Boolean = false,
) {
    init {
        require(serverSelection in SERVER_MODES || GfVpnServerCatalog.countryFor(serverSelection) != null)
        require(transport in TRANSPORTS)
        require(simultaneousConnections in 0..4)
        require(maxConcurrentAttempts in 0..10)
        require(rotation in ROTATION_MODES)
    }

    val managedConcurrency: Boolean
        get() = serverSelection == "whitelist-ru" || transport == "VKT"

    fun effective(): GfVpnOptions = if (managedConcurrency) {
        copy(
            transport = if (serverSelection == "whitelist-ru") "" else transport,
            simultaneousConnections = 0,
            maxConcurrentAttempts = 0,
            rotation = "",
        )
    } else {
        this
    }

    fun encode(): String = listOf(
        serverSelection, transport, simultaneousConnections.toString(), maxConcurrentAttempts.toString(),
        rotation, autoUpdate.toString(), cachedNodes.toString(), bypassLocalServices.toString(),
    ).joinToString("|")

    companion object {
        val SERVER_MODES = listOf("auto", "performance", "reliability", "whitelist-ru")
        val TRANSPORTS = listOf("", "DNSTT", "H2", "MK", "V2H", "V2W", "VKT", "WT", "XRXH")
        val ROTATION_MODES = listOf("", "true", "false")

        @JvmField
        val DEFAULT = GfVpnOptions()

        @JvmStatic
        fun decode(raw: String?): GfVpnOptions {
            val parts = raw?.split('|') ?: return DEFAULT
            if (parts.size != 8) return DEFAULT
            return runCatching {
                GfVpnOptions(
                    serverSelection = parts[0],
                    transport = parts[1],
                    simultaneousConnections = parts[2].toInt(),
                    maxConcurrentAttempts = parts[3].toInt(),
                    rotation = parts[4],
                    autoUpdate = parts[5].toBooleanStrict(),
                    cachedNodes = parts[6].toBooleanStrict(),
                    bypassLocalServices = parts[7].toBooleanStrict(),
                )
            }.getOrDefault(DEFAULT)
        }
    }
}
