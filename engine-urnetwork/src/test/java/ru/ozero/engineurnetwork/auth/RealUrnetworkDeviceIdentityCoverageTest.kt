package ru.ozero.engineurnetwork.auth

import android.app.Application
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class RealUrnetworkDeviceIdentityCoverageTest {

    @Test
    fun `importSeedFromBackup rejects wrong seed size before touching keystore`() = runTest {
        val identity = RealUrnetworkDeviceIdentity(Application())

        val imported = identity.importSeedFromBackup(ByteArray(31))

        assertEquals(false, imported)
    }
}
