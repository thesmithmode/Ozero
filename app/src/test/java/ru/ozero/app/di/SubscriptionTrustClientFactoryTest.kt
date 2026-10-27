package ru.ozero.app.di

import org.junit.jupiter.api.Test
import java.security.KeyStore
import java.security.cert.CertificateFactory
import java.util.Base64
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SubscriptionTrustClientFactoryTest {
    @Test
    fun `system trust store keeps system CAs and drops user CAs`() {
        val cert = CertificateFactory.getInstance("X.509").generateCertificate(
            Base64.getDecoder().decode(CERT_DER_B64).inputStream(),
        )
        val source = KeyStore.getInstance(KeyStore.getDefaultType()).apply { load(null, null) }
        source.setCertificateEntry("system:0", cert)
        source.setCertificateEntry("user:0", cert)

        val systemOnly = SubscriptionTrustClientFactory.systemOnlyCaKeyStore(source)

        assertEquals(cert, systemOnly.getCertificate("system:0"))
        assertNull(systemOnly.getCertificate("user:0"))
        assertEquals(listOf("system:0"), systemOnly.aliases().toList())
    }

    private companion object {
        const val CERT_DER_B64 =
            "MIIDGTCCAgGgAwIBAgIUQzIDZJaxUA5Sb6thb1W5ueuZB6kwDQYJKoZIhvcNAQEL" +
                "BQAwHDEaMBgGA1UEAwwRb3plcm8tc3lzdGVtLXRlc3QwHhcNMjYxMDA0MTMyMTM3" +
                "WhcNMzYxMDAxMTMyMTM3WjAcMRowGAYDVQQDDBFvemVyby1zeXN0ZW0tdGVzdDCC" +
                "ASIwDQYJKoZIhvcNAQEBBQADggEPADCCAQoCggEBALaoJv9UtFIa4DLW6uIrfHme" +
                "UT4nfFBDTZIex7SWWfueF2QrxrWVX56bFU+vyIYVgcn25zfWZn4/ZowB3kRD6sl" +
                "FjP6Y70CIOpo81SgwnZekp3KaNVmLLu74uu1nm5zyvYua8ZS/5ttR4NBN5fg/40M" +
                "Z59ljAniHQRiSuVPz0ZXztg/Tc+gG80XI623lRXf1SJJq80y9aD2pnhT34bzkc2" +
                "aHO5wL5OZ8QUfCaWyi369JhrzUD1h7TdDptBlsCfSeUS2iWz9K4psVQuZKKpTRfP" +
                "V/Da6w1c7JuNTofh9h32rOwkAvHokJArOVRGVMyvEcWDgpchahNEJRnqFNiwJKd/" +
                "ECAwEAAaNTMFEwHQYDVR0OBBYEFO9wlCV2ES8UHCz6DMgQ+sEvTxf4MB8GA1UdIw" +
                "QYMBaAFO9wlCV2ES8UHCz6DMgQ+sEvTxf4MA8GA1UdEwEB/wQFMAMBAf8wDQYJKo" +
                "ZIhvcNAQELBQADggEBAEDpR4VNdXAd+kYfFGU7LZRH/5ZTQqnXcEpXlKTawVxBv" +
                "gfmEhcy1Y5IUSFFqDlILrUVUlODF7dOccQtQJtvfugao9jPlOBQ6VMZ/Z0KVsKNG" +
                "uKQZiEYbcxPB56aCSTGrRBWMBn0eC79B1Gs1ETstmqce+UWOkiP/Pzkqvvqpbi5F" +
                "3WMO1DsecYLCk90sDPDegRcucUS+66Czkuau1vOC15oQ052ZwU1fEs9PnozedBE0" +
                "QZXya79PBvCjUn4TgINLJG7ECA4bTOb6kPQOopR8JWsfp2VGKzGfZmsQPOycCiow" +
                "i0d7vqT4Azo5XW9rTQwhygNXPkwql2t4Dr0HrjBrgI="
    }
}
