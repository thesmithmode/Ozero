package ru.ozero.enginebyedpi

import org.junit.jupiter.api.Test
import ru.ozero.enginescore.settings.ByeDpiUiSettings
import ru.ozero.enginescore.settings.ByeDpiUiSettings.DesyncMethod
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ByeDpiUiArgsBuilderExtraCoverageTest {

    @Test
    fun `detailed desync modes cover disoob fake and disabled split branches`() {
        val disoob = ByeDpiUiArgsBuilder.buildArgsOnly(
            ByeDpiUiSettings.DEFAULT.copy(
                desyncMethod = DesyncMethod.DISOOB,
                splitPosition = 4,
                oobChar = "!",
            ),
        )
        assertTrue("-q4" in disoob)
        assertTrue("-e33" in disoob)

        val fakeMinimal = ByeDpiUiArgsBuilder.buildArgsOnly(
            ByeDpiUiSettings.DEFAULT.copy(
                desyncMethod = DesyncMethod.FAKE,
                splitPosition = 2,
                fakeTtl = 0,
                fakeSni = "",
                fakeOffset = 0,
            ),
        )
        assertTrue("-f2" in fakeMinimal)
        assertTrue(fakeMinimal.none { it.startsWith("-t") })
        assertTrue(fakeMinimal.none { it.startsWith("-n") })
        assertTrue(fakeMinimal.none { it.startsWith("-O") })

        val noSplit = ByeDpiUiArgsBuilder.buildArgsOnly(
            ByeDpiUiSettings.DEFAULT.copy(splitPosition = 0),
        )
        assertTrue(noSplit.none { it.startsWith("-o") || it.startsWith("-s") || it.startsWith("-d") })
    }

    @Test
    fun `empty protocol numeric and optional settings are omitted`() {
        val args = ByeDpiUiArgsBuilder.buildArgsOnly(
            ByeDpiUiSettings.DEFAULT.copy(
                maxConnections = 0,
                bufferSize = 0,
                desyncHttps = false,
                desyncHttp = false,
                defaultTtl = 0,
                noDomain = false,
                hostMixedCase = false,
                domainMixedCase = false,
                hostRemoveSpaces = false,
                tlsRecordSplit = false,
                tcpFastOpen = false,
                dropSack = false,
            ),
        )

        assertTrue(args.none { it.startsWith("-c") })
        assertTrue(args.none { it.startsWith("-b") })
        assertTrue(args.none { it == "-Kt" || it == "-Kh" || it == "-Kt,h" })
        assertTrue(args.none { it.startsWith("-g") })
        assertTrue("-N" !in args)
        assertTrue(args.none { it.startsWith("-M") })
        assertTrue(args.none { it.startsWith("-r") })
        assertTrue("-F" !in args)
        assertTrue("-Y" !in args)
    }

    @Test
    fun `empty oob char and udp zero fake count use fallback branches`() {
        val oob = ByeDpiUiArgsBuilder.buildArgsOnly(
            ByeDpiUiSettings.DEFAULT.copy(oobChar = ""),
        )
        assertTrue("-e97" in oob)

        val udp = ByeDpiUiArgsBuilder.buildArgsOnly(
            ByeDpiUiSettings.DEFAULT.copy(desyncUdp = true, udpFakeCount = 0),
        )
        assertTrue("-Ku" in udp)
        assertTrue(udp.none { it.startsWith("-a") })
        assertEquals("-An", udp.last())
    }

    @Test
    fun `split disorder oob and none methods map split options`() {
        val split = ByeDpiUiArgsBuilder.buildArgsOnly(
            ByeDpiUiSettings.DEFAULT.copy(desyncMethod = DesyncMethod.SPLIT, splitPosition = 3),
        )
        val disorder = ByeDpiUiArgsBuilder.buildArgsOnly(
            ByeDpiUiSettings.DEFAULT.copy(desyncMethod = DesyncMethod.DISORDER, splitPosition = 4),
        )
        val oob = ByeDpiUiArgsBuilder.buildArgsOnly(
            ByeDpiUiSettings.DEFAULT.copy(desyncMethod = DesyncMethod.OOB, splitPosition = 5, oobChar = "Z"),
        )
        val none = ByeDpiUiArgsBuilder.buildArgsOnly(
            ByeDpiUiSettings.DEFAULT.copy(desyncMethod = DesyncMethod.NONE, splitPosition = 6),
        )

        assertTrue("-s3" in split)
        assertTrue("-d4" in disorder)
        assertTrue("-o5" in oob)
        assertTrue("-e90" in oob)
        assertTrue(none.none { it == "-s6" || it == "-d6" || it == "-o6" || it == "-q6" || it == "-f6" })
    }

    @Test
    fun `host split tls split and protocol combinations are rendered`() {
        val args = ByeDpiUiArgsBuilder.buildArgsOnly(
            ByeDpiUiSettings.DEFAULT.copy(
                desyncHttps = true,
                desyncHttp = true,
                splitPosition = 7,
                splitAtHost = true,
                tlsRecordSplit = true,
                tlsRecordSplitPosition = 8,
                tlsRecordSplitAtSni = true,
                hostMixedCase = true,
                domainMixedCase = true,
                hostRemoveSpaces = true,
            ),
        )

        assertTrue("-Kt,h" in args)
        assertTrue(args.any { it.endsWith("7+h") })
        assertTrue("-r8+s" in args)
        assertTrue("-Mh,d,r" in args)
    }

    @Test
    fun `fake method renders positive ttl sni and offset`() {
        val args = ByeDpiUiArgsBuilder.buildArgsOnly(
            ByeDpiUiSettings.DEFAULT.copy(
                desyncMethod = DesyncMethod.FAKE,
                splitPosition = 9,
                fakeTtl = 10,
                fakeSni = "front.example",
                fakeOffset = -2,
            ),
        )

        assertTrue("-f9" in args)
        assertTrue("-t10" in args)
        assertTrue("-nfront.example" in args)
        assertTrue("-O-2" in args)
    }

    @Test
    fun `udp fake count and build prefix are rendered`() {
        val args = ByeDpiUiArgsBuilder.build(
            ByeDpiUiSettings.DEFAULT.copy(desyncUdp = true, udpFakeCount = 3),
            socksPort = 2090,
        ).toList()

        assertEquals(listOf("--ip", "127.0.0.1", "-p", "2090"), args.take(4))
        assertTrue("-Ku" in args)
        assertTrue("-a3" in args)
        assertEquals(2, args.count { it == "-An" })
    }
}
