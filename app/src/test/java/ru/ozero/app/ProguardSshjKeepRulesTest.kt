package ru.ozero.app

import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertTrue

class ProguardSshjKeepRulesTest {

    private val rules by lazy {
        val moduleRoot = File(System.getProperty("user.dir") ?: ".")
        val f = File(moduleRoot, "proguard-rules.pro")
        assertTrue(f.exists(), "proguard-rules.pro не найден: $f")
        f.readText()
    }

    @Test
    fun `eddsa keep + dontwarn — R8 minify падает на sunSecurityX509Key без него`() {
        assertTrue(
            rules.contains("net.i2p.crypto.eddsa.**"),
            "Без net.i2p.crypto.eddsa keep рули R8 ругается Missing class sun.security.x509.X509Key " +
                "(referenced from EdDSAEngine). Sentinel против релизного R8 fail.",
        )
        assertTrue(rules.contains("-dontwarn sun.security.**"))
    }

    @Test
    fun `sshj keep rule присутствует — иначе MasterDNS deploy NoClassDef в release`() {
        assertTrue(rules.contains("net.schmizz.sshj.**"))
        assertTrue(rules.contains("com.hierynomus.**"))
    }
}
