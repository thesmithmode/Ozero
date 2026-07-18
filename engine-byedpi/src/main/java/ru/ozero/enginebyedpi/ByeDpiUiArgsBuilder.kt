package ru.ozero.enginebyedpi

import ru.ozero.enginescore.settings.ByeDpiUiSettings
import ru.ozero.enginescore.settings.ByeDpiUiSettings.DesyncMethod

internal object ByeDpiUiArgsBuilder {

    @Suppress("LongMethod", "ComplexMethod", "NestedBlockDepth")
    fun build(settings: ByeDpiUiSettings, socksPort: Int): Array<String> {
        val prefix = listOf("--ip", "127.0.0.1", "-p", socksPort.toString())
        return (prefix + buildArgsOnly(settings)).toTypedArray()
    }

    @Suppress("LongMethod", "ComplexMethod", "NestedBlockDepth", "CognitiveComplexMethod")
    fun buildArgsOnly(settings: ByeDpiUiSettings): List<String> {
        val args = mutableListOf<String>()

        if (settings.maxConnections > 0) args.add("-c${settings.maxConnections}")
        if (settings.bufferSize > 0) args.add("-b${settings.bufferSize}")

        val protocols = buildString {
            if (settings.desyncHttps) append("t")
            if (settings.desyncHttp) {
                if (isNotEmpty()) append(",")
                append("h")
            }
        }
        if (protocols.isNotEmpty()) args.add("-K$protocols")

        if (settings.defaultTtl > 0) args.add("-g${settings.defaultTtl}")
        if (settings.noDomain) args.add("-N")

        if (settings.splitPosition != 0) {
            val pos = settings.splitPosition.toString() + if (settings.splitAtHost) "+h" else ""
            val option = when (settings.desyncMethod) {
                DesyncMethod.SPLIT -> "-s"
                DesyncMethod.DISORDER -> "-d"
                DesyncMethod.OOB -> "-o"
                DesyncMethod.DISOOB -> "-q"
                DesyncMethod.FAKE -> "-f"
                DesyncMethod.NONE -> ""
            }
            if (option.isNotEmpty()) args.add("$option$pos")
        }

        if (settings.desyncMethod == DesyncMethod.FAKE) {
            if (settings.fakeTtl > 0) args.add("-t${settings.fakeTtl}")
            val fakeSni = settings.fakeSni.trim()
            if (fakeSni.isNotEmpty() && isValidFakeSni(fakeSni)) args.add("-n$fakeSni")
            if (settings.fakeOffset != 0) args.add("-O${settings.fakeOffset}")
        }

        if (settings.desyncMethod == DesyncMethod.OOB || settings.desyncMethod == DesyncMethod.DISOOB) {
            val ch = settings.oobChar.firstOrNull() ?: 'a'
            args.add("-e${ch.code.toByte().toInt()}")
        }

        val modFlags = buildString {
            if (settings.hostMixedCase) append("h")
            if (settings.domainMixedCase) {
                if (isNotEmpty()) append(",")
                append("d")
            }
            if (settings.hostRemoveSpaces) {
                if (isNotEmpty()) append(",")
                append("r")
            }
        }
        if (modFlags.isNotEmpty()) args.add("-M$modFlags")

        if (settings.tlsRecordSplit && settings.tlsRecordSplitPosition != 0) {
            val tls = settings.tlsRecordSplitPosition.toString() +
                if (settings.tlsRecordSplitAtSni) "+s" else ""
            args.add("-r$tls")
        }

        if (settings.tcpFastOpen) args.add("-F")
        if (settings.dropSack) args.add("-Y")

        args.add("-An")

        if (settings.desyncUdp) {
            args.add("-Ku")
            if (settings.udpFakeCount > 0) args.add("-a${settings.udpFakeCount}")
            args.add("-An")
        }

        return args
    }

    private fun isValidFakeSni(value: String): Boolean {
        if (value.length > MAX_FAKE_SNI_LENGTH) return false
        if (value.any(Char::isWhitespace)) return false
        if (value.startsWith(".") || value.endsWith(".")) return false
        val labels = value.split('.')
        return labels.size >= MIN_FAKE_SNI_LABELS && labels.all(::isValidFakeSniLabel)
    }

    private fun isValidFakeSniLabel(label: String): Boolean {
        if (label.isEmpty() || label.length > MAX_FAKE_SNI_LABEL_LENGTH) return false
        if (label.startsWith("-") || label.endsWith("-")) return false
        return label.all { it in 'a'..'z' || it in 'A'..'Z' || it in '0'..'9' || it == '-' }
    }

    private const val MIN_FAKE_SNI_LABELS = 2
    private const val MAX_FAKE_SNI_LENGTH = 253
    private const val MAX_FAKE_SNI_LABEL_LENGTH = 63
}
