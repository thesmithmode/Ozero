package hev

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class TProxyServiceCoverageTest {

    @AfterEach
    fun reset() {
        TProxyService.resetForTest()
    }

    @Test
    fun loadOnceRecordsNativeLoadFailureAndDoesNotThrow() {
        TProxyService.resetForTest()

        TProxyService.loadOnce()

        assertFalse(TProxyService.libraryLoaded)
        assertNotNull(TProxyService.loadError)
    }

    @Test
    fun loadOnceIsIdempotentAfterFirstAttempt() {
        TProxyService.resetForTest()

        TProxyService.loadOnce()
        val firstError = TProxyService.loadError
        TProxyService.loadOnce()

        assertFalse(TProxyService.libraryLoaded)
        assertTrue(firstError === TProxyService.loadError || firstError == TProxyService.loadError)
    }

    private fun TProxyService.resetForTest() {
        setField("libraryLoaded", false)
        setField("loadError", null)
        setField("loadAttempted", false)
    }

    private fun setField(name: String, value: Any?) {
        val field = TProxyService::class.java.getDeclaredField(name)
        field.isAccessible = true
        field.set(TProxyService, value)
    }
}
