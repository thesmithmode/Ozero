package ru.ozero.enginebyedpi

import org.junit.jupiter.api.Test
import ru.ozero.enginescore.settings.ByeDpiUiSettings
import ru.ozero.enginescore.settings.ByeDpiUiSettings.DesyncMethod
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ByeDpiUiArgsBuilderTest {

    @Test
    fun `defaults дают TCP OOB strategy без UDP секции`() {
        val args = ByeDpiUiArgsBuilder.build(ByeDpiUiSettings.DEFAULT, socksPort = 1080).toList()

        val expected = listOf(
            "--ip", "127.0.0.1", "-p", "1080",
            "-c512", "-b16384",
            "-Kt,h",
            "-o1",
            "-e97",
            "-An",
        )
        assertEquals(expected, args)
    }

    @Test
    fun `desyncMethod=SPLIT использует -s flag`() {
        val s = ByeDpiUiSettings.DEFAULT.copy(desyncMethod = DesyncMethod.SPLIT, splitPosition = 5)
        val args = ByeDpiUiArgsBuilder.build(s, 1080).toList()
        assertTrue("-s5" in args, "expected -s5 for SPLIT method, got $args")
    }

    @Test
    fun `desyncMethod=DISORDER использует -d flag`() {
        val s = ByeDpiUiSettings.DEFAULT.copy(desyncMethod = DesyncMethod.DISORDER, splitPosition = 3)
        val args = ByeDpiUiArgsBuilder.build(s, 1080).toList()
        assertTrue("-d3" in args)
    }

    @Test
    fun `desyncMethod=FAKE добавляет -t -n -O`() {
        val s = ByeDpiUiSettings.DEFAULT.copy(
            desyncMethod = DesyncMethod.FAKE,
            splitPosition = 1,
            fakeTtl = 4,
            fakeSni = "example.com",
            fakeOffset = 2,
        )
        val args = ByeDpiUiArgsBuilder.build(s, 1080).toList()
        assertTrue("-f1" in args)
        assertTrue("-t4" in args)
        assertTrue("-nexample.com" in args)
        assertTrue("-O2" in args)
    }

    @Test
    fun `fakeSni with whitespace is omitted`() {
        val s = ByeDpiUiSettings.DEFAULT.copy(
            desyncMethod = DesyncMethod.FAKE,
            fakeSni = "example.com --ip 0.0.0.0",
        )

        val args = ByeDpiUiArgsBuilder.build(s, 1080).toList()

        assertTrue(args.none { it.startsWith("-n") })
        assertTrue("--ip" !in args)
        assertTrue("0.0.0.0" !in args)
    }

    @Test
    fun `fakeSni accepts trimmed hostname`() {
        val s = ByeDpiUiSettings.DEFAULT.copy(
            desyncMethod = DesyncMethod.FAKE,
            fakeSni = " front.example.com ",
        )

        val args = ByeDpiUiArgsBuilder.build(s, 1080).toList()

        assertTrue("-nfront.example.com" in args)
    }

    @Test
    fun `desyncMethod=NONE не добавляет split-flag`() {
        val s = ByeDpiUiSettings.DEFAULT.copy(desyncMethod = DesyncMethod.NONE, splitPosition = 1)
        val args = ByeDpiUiArgsBuilder.build(s, 1080).toList()
        listOf("-s1", "-d1", "-o1", "-q1", "-f1").forEach { flag ->
            assertTrue(flag !in args, "expected no $flag for NONE, got $args")
        }
    }

    @Test
    fun `splitAtHost добавляет +h к pos`() {
        val s = ByeDpiUiSettings.DEFAULT.copy(splitPosition = 7, splitAtHost = true)
        val args = ByeDpiUiArgsBuilder.build(s, 1080).toList()
        assertTrue("-o7+h" in args)
    }

    @Test
    fun `oobChar парсится в byte code`() {
        val s = ByeDpiUiSettings.DEFAULT.copy(oobChar = "z")
        val args = ByeDpiUiArgsBuilder.build(s, 1080).toList()
        assertTrue("-e122" in args, "'z' = 122, got $args")
    }

    @Test
    fun `tlsRecordSplit добавляет -r с position+s`() {
        val s = ByeDpiUiSettings.DEFAULT.copy(
            tlsRecordSplit = true,
            tlsRecordSplitPosition = 3,
            tlsRecordSplitAtSni = true,
        )
        val args = ByeDpiUiArgsBuilder.build(s, 1080).toList()
        assertTrue("-r3+s" in args)
    }

    @Test
    fun `tlsRecordSplit without SNI marker emits plain position`() {
        val s = ByeDpiUiSettings.DEFAULT.copy(
            tlsRecordSplit = true,
            tlsRecordSplitPosition = 3,
            tlsRecordSplitAtSni = false,
        )
        val args = ByeDpiUiArgsBuilder.build(s, 1080).toList()
        assertTrue("-r3" in args)
    }

    @Test
    fun `tlsRecordSplit без position не добавляет -r`() {
        val s = ByeDpiUiSettings.DEFAULT.copy(tlsRecordSplit = true, tlsRecordSplitPosition = 0)
        val args = ByeDpiUiArgsBuilder.build(s, 1080).toList()
        assertTrue(args.none { it.startsWith("-r") })
    }

    @Test
    fun `modHttpFlags комбинирует hostMixed+domainMixed+removeSpaces`() {
        val s = ByeDpiUiSettings.DEFAULT.copy(
            hostMixedCase = true,
            domainMixedCase = true,
            hostRemoveSpaces = true,
        )
        val args = ByeDpiUiArgsBuilder.build(s, 1080).toList()
        assertTrue("-Mh,d,r" in args)
    }

    @Test
    fun `mod flags can be emitted independently`() {
        val domainOnly = ByeDpiUiSettings.DEFAULT.copy(
            hostMixedCase = false,
            domainMixedCase = true,
            hostRemoveSpaces = false,
        )
        val removeOnly = ByeDpiUiSettings.DEFAULT.copy(
            hostMixedCase = false,
            domainMixedCase = false,
            hostRemoveSpaces = true,
        )

        assertTrue("-Md" in ByeDpiUiArgsBuilder.build(domainOnly, 1080).toList())
        assertTrue("-Mr" in ByeDpiUiArgsBuilder.build(removeOnly, 1080).toList())
    }

    @Test
    fun `desyncUdp=false убирает -Ku`() {
        val s = ByeDpiUiSettings.DEFAULT.copy(desyncUdp = false)
        val args = ByeDpiUiArgsBuilder.build(s, 1080).toList()
        assertTrue("-Ku" !in args)
    }

    @Test
    fun `desyncUdp=true добавляет отдельную UDP секцию`() {
        val s = ByeDpiUiSettings.DEFAULT.copy(desyncUdp = true)
        val args = ByeDpiUiArgsBuilder.build(s, 1080).toList()
        assertTrue(args.takeLast(3) == listOf("-Ku", "-a1", "-An"), "expected UDP section at tail, got $args")
    }

    @Test
    fun `legacy default UI settings migrate to TCP fallback default`() {
        val legacy = "{" +
            "\"maxConnections\":512," +
            "\"bufferSize\":16384," +
            "\"defaultTtl\":0," +
            "\"noDomain\":false," +
            "\"desyncHttp\":true," +
            "\"desyncHttps\":true," +
            "\"desyncUdp\":true," +
            "\"desyncMethod\":\"OOB\"," +
            "\"splitPosition\":1," +
            "\"splitAtHost\":false," +
            "\"fakeTtl\":8," +
            "\"fakeSni\":\"www.iana.org\"," +
            "\"fakeOffset\":0," +
            "\"oobChar\":\"a\"," +
            "\"hostMixedCase\":false," +
            "\"domainMixedCase\":false," +
            "\"hostRemoveSpaces\":false," +
            "\"tlsRecordSplit\":false," +
            "\"tlsRecordSplitPosition\":0," +
            "\"tlsRecordSplitAtSni\":false," +
            "\"tcpFastOpen\":false," +
            "\"udpFakeCount\":1," +
            "\"dropSack\":false" +
            "}"
        val migrated = ByeDpiUiSettings.fromJson(legacy)
        assertTrue(!migrated.desyncUdp)
    }

    @Test
    fun `new explicit desyncUdp only setting round-trips with schema marker`() {
        val saved = ByeDpiUiSettings.DEFAULT.copy(desyncUdp = true).toJson()
        val restored = ByeDpiUiSettings.fromJson(saved)
        assertTrue(restored.desyncUdp)
    }

    @Test
    fun `tcpFastOpen добавляет -F, dropSack добавляет -Y`() {
        val s = ByeDpiUiSettings.DEFAULT.copy(tcpFastOpen = true, dropSack = true)
        val args = ByeDpiUiArgsBuilder.build(s, 1080).toList()
        assertTrue("-F" in args)
        assertTrue("-Y" in args)
    }

    @Test
    fun `socksPort прокинут в -p`() {
        val args = ByeDpiUiArgsBuilder.build(ByeDpiUiSettings.DEFAULT, socksPort = 49152).toList()
        assertEquals("49152", args[args.indexOf("-p") + 1])
    }

    @Test
    fun `desyncHttps=false desyncHttp=true даёт -Kh`() {
        val s = ByeDpiUiSettings.DEFAULT.copy(desyncHttps = false, desyncHttp = true)
        val args = ByeDpiUiArgsBuilder.build(s, 1080).toList()
        assertTrue("-Kh" in args)
        assertTrue("-Kt,h" !in args)
    }

    @Test
    fun `noDomain=true добавляет -N`() {
        val s = ByeDpiUiSettings.DEFAULT.copy(noDomain = true)
        val args = ByeDpiUiArgsBuilder.build(s, 1080).toList()
        assertTrue("-N" in args)
    }

    @Test
    fun `defaultTtl=64 добавляет -g64`() {
        val s = ByeDpiUiSettings.DEFAULT.copy(defaultTtl = 64)
        val args = ByeDpiUiArgsBuilder.build(s, 1080).toList()
        assertTrue("-g64" in args)
    }
}
