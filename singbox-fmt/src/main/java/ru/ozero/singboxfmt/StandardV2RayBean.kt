package ru.ozero.singboxfmt

abstract class StandardV2RayBean : AbstractBean() {
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

    override fun initializeDefaultValues() {
        super.initializeDefaultValues()
    }
}
