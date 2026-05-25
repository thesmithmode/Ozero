package ru.ozero.singboxcore

class TunConfig {
    internal val native = libcore.TunConfig()

    var fileDescriptor: Int
        get() = native.fileDescriptor
        set(v) {
            native.fileDescriptor = v
        }

    var mtu: Int
        get() = native.mtu
        set(v) {
            native.mtu = v
        }

    var v2Ray: V2RayInstance? = null
        set(v) {
            field = v
            native.v2Ray = v?.native
        }

    var protect: Boolean
        get() = native.protect
        set(v) {
            native.protect = v
        }

    var protector: Protector? = null
        set(v) {
            field = v
            native.protector = v?.let { p -> libcore.Protector { fd -> p.protect(fd) } }
        }

    var addr4: String?
        get() = native.addr4
        set(v) {
            native.addr4 = v
        }

    var addr6: String?
        get() = native.addr6
        set(v) {
            native.addr6 = v
        }

    var dns4: String?
        get() = native.dns4
        set(v) {
            native.dns4 = v
        }

    var dns6: String?
        get() = native.dns6
        set(v) {
            native.dns6 = v
        }

    var enableIPv6: Boolean
        get() = native.enableIPv6
        set(v) {
            native.enableIPv6 = v
        }

    var implementation: Int
        get() = native.implementation
        set(v) {
            native.implementation = v
        }

    var fakeDNS: Boolean
        get() = native.fakeDNS
        set(v) {
            native.fakeDNS = v
        }

    var sniffing: Boolean
        get() = native.sniffing
        set(v) {
            native.sniffing = v
        }

    var overrideDestination: Boolean
        get() = native.overrideDestination
        set(v) {
            native.overrideDestination = v
        }

    var debug: Boolean
        get() = native.debug
        set(v) {
            native.debug = v
        }

    var dumpUID: Boolean
        get() = native.dumpUID
        set(v) {
            native.dumpUID = v
        }

    var trafficStats: Boolean
        get() = native.trafficStats
        set(v) {
            native.trafficStats = v
        }

    var pcap: Boolean
        get() = native.pcap
        set(v) {
            native.pcap = v
        }

    var protectPath: String?
        get() = native.protectPath
        set(v) {
            native.protectPath = v
        }

    var discardICMP: Boolean
        get() = native.discardICMP
        set(v) {
            native.discardICMP = v
        }

    var discardIPv6BasedOnNetwork: Boolean
        get() = native.discardIPv6BasedOnNetwork
        set(v) {
            native.discardIPv6BasedOnNetwork = v
        }
}
