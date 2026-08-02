package ru.ozero.singboxfmt

internal abstract class LegacyV1AbstractBean {
    var serverAddress: String = "127.0.0.1"
    var serverPort: Int = 1080
    var name: String = ""
}

internal abstract class LegacyV1StandardV2RayBean : LegacyV1AbstractBean() {
    var uuid: String = ""
    var encryption: String = ""
    var type: String = "tcp"
    var host: String = ""
    var path: String = ""
    var headerType: String = "none"
    var mKcpSeed: String = ""
    var quicSecurity: String = "none"
    var quicKey: String = ""
    var grpcServiceName: String = ""
    var grpcServiceNameCompat: Boolean = false
    var grpcMultiMode: Boolean = false
    var maxEarlyData: Int = 0
    var earlyDataHeaderName: String = ""
    var wsUseBrowserForwarder: Boolean = false
    var shUseBrowserForwarder: Boolean = false
    var splithttpMode: String = "auto"
    var splithttpExtra: String = ""
    var meekUrl: String = ""
    var mekyaKcpSeed: String = ""
    var mekyaKcpHeaderType: String = "none"
    var mekyaUrl: String = ""
    var security: String = "none"
    var sni: String = ""
    var alpn: String = ""
    var certificates: String = ""
    var pinnedPeerCertificateChainSha256: String = ""
    var pinnedPeerCertificatePublicKeySha256: String = ""
    var pinnedPeerCertificateSha256: String = ""
    var allowInsecure: Boolean = false
    var utlsFingerprint: String = ""
    var echEnabled: Boolean = false
    var echConfig: String = ""
    var mtlsCertificate: String = ""
    var mtlsCertificatePrivateKey: String = ""
    var realityPublicKey: String = ""
    var realityShortId: String = ""
    var realityFingerprint: String = "chrome"
    var realityDisableX25519Mlkem768: Boolean = false
    var hy2DownMbps: Long = 0L
    var hy2UpMbps: Long = 0L
    var hy2Password: String = ""
    var packetEncoding: String = "none"
    var mux: Boolean = false
    var muxConcurrency: Int = 8
    var muxPacketEncoding: String = "none"
    var singMux: Boolean = false
    var singMuxProtocol: String = "h2mux"
    var singMuxMaxConnections: Int = 0
    var singMuxMinStreams: Int = 0
    var singMuxMaxStreams: Int = 0
    var singMuxPadding: Boolean = false
}

internal class LegacyV1VLESSBean : LegacyV1StandardV2RayBean() {
    var flow: String = ""
}

internal class LegacyV1VMessBean : LegacyV1StandardV2RayBean() {
    var alterId: Int = 0
}

internal class LegacyV1TrojanBean : LegacyV1StandardV2RayBean() {
    var password: String = ""
}

internal class LegacyV1ShadowsocksBean : LegacyV1AbstractBean() {
    var method: String = "aes-128-gcm"
    var password: String = ""
    var plugin: String = ""
    var pluginOpts: String = ""
}

internal fun LegacyV1AbstractBean.toCurrentBean(): AbstractBean {
    val target = when (this) {
        is LegacyV1VLESSBean -> VLESSBean().also {
            copyStandardFields(it)
            it.flow = flow
        }
        is LegacyV1VMessBean -> VMessBean().also {
            copyStandardFields(it)
            it.alterId = alterId
        }
        is LegacyV1TrojanBean -> TrojanBean().also {
            copyStandardFields(it)
            it.password = password
        }
        is LegacyV1ShadowsocksBean -> ShadowsocksBean().also {
            copyBaseFields(it)
            it.method = method
            it.password = password
            it.plugin = plugin
            it.pluginOpts = pluginOpts
        }
        else -> error("Unsupported legacy bean type")
    }
    return target.applyCanonicalDefaults()
}

private fun LegacyV1AbstractBean.copyBaseFields(target: AbstractBean) {
    target.serverAddress = serverAddress
    target.serverPort = serverPort
    target.name = name
}

private fun LegacyV1StandardV2RayBean.copyStandardFields(target: StandardV2RayBean) {
    copyBaseFields(target)
    target.uuid = uuid
    target.encryption = encryption
    target.type = type
    target.rawTransportType = ""
    target.host = host
    target.path = path
    target.headerType = headerType
    target.mKcpSeed = mKcpSeed
    target.quicSecurity = quicSecurity
    target.quicKey = quicKey
    target.grpcServiceName = grpcServiceName
    target.grpcServiceNameCompat = grpcServiceNameCompat
    target.grpcMultiMode = grpcMultiMode
    target.maxEarlyData = maxEarlyData
    target.earlyDataHeaderName = earlyDataHeaderName
    target.wsUseBrowserForwarder = wsUseBrowserForwarder
    target.shUseBrowserForwarder = shUseBrowserForwarder
    target.splithttpMode = splithttpMode
    target.splithttpExtra = splithttpExtra
    target.meekUrl = meekUrl
    target.mekyaKcpSeed = mekyaKcpSeed
    target.mekyaKcpHeaderType = mekyaKcpHeaderType
    target.mekyaUrl = mekyaUrl
    target.security = security
    target.sni = sni
    target.alpn = alpn
    target.certificates = certificates
    target.pinnedPeerCertificateChainSha256 = pinnedPeerCertificateChainSha256
    target.pinnedPeerCertificatePublicKeySha256 = pinnedPeerCertificatePublicKeySha256
    target.pinnedPeerCertificateSha256 = pinnedPeerCertificateSha256
    target.allowInsecure = allowInsecure
    target.utlsFingerprint = utlsFingerprint
    target.echEnabled = echEnabled
    target.echConfig = echConfig
    target.mtlsCertificate = mtlsCertificate
    target.mtlsCertificatePrivateKey = mtlsCertificatePrivateKey
    target.realityPublicKey = realityPublicKey
    target.realityShortId = realityShortId
    target.realityFingerprint = realityFingerprint
    target.realityDisableX25519Mlkem768 = realityDisableX25519Mlkem768
    target.hy2DownMbps = hy2DownMbps
    target.hy2UpMbps = hy2UpMbps
    target.hy2Password = hy2Password
    target.packetEncoding = packetEncoding
    target.mux = mux
    target.muxConcurrency = muxConcurrency
    target.muxPacketEncoding = muxPacketEncoding
    target.singMux = singMux
    target.singMuxProtocol = singMuxProtocol
    target.singMuxMaxConnections = singMuxMaxConnections
    target.singMuxMinStreams = singMuxMinStreams
    target.singMuxMaxStreams = singMuxMaxStreams
    target.singMuxPadding = singMuxPadding
}
