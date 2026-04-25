package ru.ozero.app.ui.permission

import android.os.Build
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NotificationPermissionTest {

    @Test
    fun `isApplicable false on api below 33`() {
        assertFalse(NotificationPermission.isApplicable(Build.VERSION_CODES.S))
        assertFalse(NotificationPermission.isApplicable(Build.VERSION_CODES.S_V2))
    }

    @Test
    fun `isApplicable true on api 33 and above`() {
        assertTrue(NotificationPermission.isApplicable(Build.VERSION_CODES.TIRAMISU))
        assertTrue(NotificationPermission.isApplicable(Build.VERSION_CODES.UPSIDE_DOWN_CAKE))
        assertTrue(NotificationPermission.isApplicable(Build.VERSION_CODES.VANILLA_ICE_CREAM))
    }

    @Test
    fun `resolve returns NotApplicable for sub-33 regardless of grant or asked`() {
        val sdk = Build.VERSION_CODES.S
        assertEquals(
            NotificationPermission.State.NotApplicable,
            NotificationPermission.resolve(sdk, hasGrant = false, asked = false),
        )
        assertEquals(
            NotificationPermission.State.NotApplicable,
            NotificationPermission.resolve(sdk, hasGrant = true, asked = true),
        )
    }

    @Test
    fun `resolve returns Granted on api 33+ when grant present`() {
        val sdk = Build.VERSION_CODES.TIRAMISU
        assertEquals(
            NotificationPermission.State.Granted,
            NotificationPermission.resolve(sdk, hasGrant = true, asked = false),
        )
        assertEquals(
            NotificationPermission.State.Granted,
            NotificationPermission.resolve(sdk, hasGrant = true, asked = true),
        )
    }

    @Test
    fun `resolve returns NeedsRequest on api 33+ when not granted and not yet asked`() {
        val sdk = Build.VERSION_CODES.TIRAMISU
        assertEquals(
            NotificationPermission.State.NeedsRequest,
            NotificationPermission.resolve(sdk, hasGrant = false, asked = false),
        )
    }

    @Test
    fun `resolve returns Denied on api 33+ when not granted but already asked`() {
        val sdk = Build.VERSION_CODES.TIRAMISU
        assertEquals(
            NotificationPermission.State.Denied,
            NotificationPermission.resolve(sdk, hasGrant = false, asked = true),
        )
    }
}
