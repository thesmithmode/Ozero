package ru.ozero.engineurnetwork

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class UrnetworkConfigModelCoverageTest {

    @Test
    fun `wallet override flow filters blanks and falls back to preset wallet`() = runTest {
        val store = InMemoryUrnetworkConfigStore()

        assertEquals(UrnetworkDefaults.PRESET_WALLET, store.walletAddress().first())
        assertNull(store.walletOverride().first())

        store.setWalletOverride("  ")
        assertEquals(UrnetworkDefaults.PRESET_WALLET, store.walletAddress().first())
        assertNull(store.walletOverride().first())

        store.setWalletOverride(" wallet-address ")
        assertEquals(" wallet-address ", store.walletAddress().first())
        assertEquals(" wallet-address ", store.walletOverride().first())
    }

    @Test
    fun `jwt pubkey and device network flows filter blank values`() = runTest {
        val store = InMemoryUrnetworkConfigStore()

        store.setByJwt("")
        store.setByClientJwt(" ")
        store.setDevicePubkey("")
        store.setDeviceNetworkName(" ")

        assertNull(store.byJwt().first())
        assertNull(store.byClientJwt().first())
        assertNull(store.devicePubkey().first())
        assertNull(store.deviceNetworkName().first())

        store.setByJwt("by")
        store.setByClientJwt("client")
        store.setDevicePubkey("pub")
        store.setDeviceNetworkName("network")

        assertEquals("by", store.byJwt().first())
        assertEquals("client", store.byClientJwt().first())
        assertEquals("pub", store.devicePubkey().first())
        assertEquals("network", store.deviceNetworkName().first())
    }

    @Test
    fun `boolean and enum setters update independent config fields`() = runTest {
        val store = InMemoryUrnetworkConfigStore()

        store.setWindowType(UrnetworkWindowType.QUALITY)
        store.setFixedIpSize(true)
        store.setAllowDirect(false)
        store.setProvideEnabled(false)
        store.setProvideControlMode(UrnetworkProvideControlMode.AUTO)
        store.setProvideNetworkMode(UrnetworkProvideNetworkMode.ALL)

        assertEquals(UrnetworkWindowType.QUALITY, store.windowType().first())
        assertTrue(store.fixedIpSize().first())
        assertFalse(store.allowDirect().first())
        assertTrue(store.provideEnabled().first())
        assertEquals(UrnetworkProvideControlMode.ALWAYS, store.provideControlMode().first())
        assertEquals(UrnetworkProvideNetworkMode.ALL, store.provideNetworkMode().first())
    }

    @Test
    fun `selected location setter trims country region and city independently`() = runTest {
        val store = InMemoryUrnetworkConfigStore()

        store.setSelectedLocation(UrnetworkLocationSelection(" de ", " Bavaria ", " Munich "))

        assertEquals(
            UrnetworkLocationSelection("DE", "Bavaria", "Munich"),
            store.selectedLocation().first(),
        )
    }

    @Test
    fun `selected location setter keeps non blank region and city without country`() = runTest {
        val store = InMemoryUrnetworkConfigStore()

        store.setSelectedLocation(UrnetworkLocationSelection(" ", " Region ", " City "))

        assertEquals(
            UrnetworkLocationSelection(null, "Region", "City"),
            store.selectedLocation().first(),
        )
        assertEquals("??/Region/City", store.selectedLocation().first().summary())
    }

    @Test
    fun `location normalization rejects invalid country and normalizes valid letters`() {
        assertNull(UrnetworkLocationSelection(null, null, null).normalized())
        assertNull(UrnetworkLocationSelection("1A", null, null).normalized())
        assertNull(UrnetworkLocationSelection("USA", null, null).normalized())
        assertEquals(
            UrnetworkLocationSelection("BR", null, "Sao Paulo"),
            UrnetworkLocationSelection(" br ", " ", " Sao Paulo ").normalized(),
        )
        assertEquals(
            UrnetworkLocationSelection(null, "Bavaria", null),
            UrnetworkLocationSelection(null, " Bavaria ", " ").normalized(),
        )
    }

    @Test
    fun `cached location implements location token and preserves provider flags`() = runTest {
        val location = UrnetworkCachedLocation(
            name = "Munich",
            countryCode = "DE",
            region = "Bavaria",
            city = "Munich",
            providerCount = 7,
            isStable = false,
            isStrongPrivacy = true,
        )
        val store = InMemoryUrnetworkConfigStore()

        store.setCachedLocations(
            countries = listOf(location.copy(name = "Germany", region = null, city = null)),
            regions = listOf(location.copy(name = "Bavaria", city = null)),
            cities = listOf(location),
            bestMatches = listOf(location.copy(name = "Best Munich")),
        )

        val snap = store.config().first()
        assertEquals("Germany", snap.cachedCountries.single().name)
        assertEquals("Bavaria", snap.cachedRegions.single().region)
        assertEquals("Munich", snap.cachedCities.single().city)
        assertEquals(7, snap.cachedCities.single().providerCount)
        assertFalse(snap.cachedCities.single().isStable)
        assertTrue(snap.cachedCities.single().isStrongPrivacy)
        assertSame(location.countryCode, snap.cachedCities.single().countryCode)
    }

    @Test
    fun `in memory store inject mutates snapshot without replacing store`() {
        val store = InMemoryUrnetworkConfigStore()

        store.inject { it.copy(byJwt = "injected", allowDirect = false) }

        assertEquals("injected", store.snapshot.byJwt)
        assertFalse(store.snapshot.allowDirect)
    }

    @Test
    fun `location normalization accepts country-only and trims all location parts`() {
        assertEquals(
            UrnetworkLocationSelection("US", null, null),
            UrnetworkLocationSelection(" us ", null, null).normalized(),
        )
        assertEquals(
            UrnetworkLocationSelection("NL", "North Holland", "Amsterdam"),
            UrnetworkLocationSelection(" nl ", " North Holland ", " Amsterdam ").normalized(),
        )
    }

    @Test
    fun `location normalization rejects non letter country but keeps valid region city combinations`() {
        assertEquals(
            UrnetworkLocationSelection(null, "Region", "City"),
            UrnetworkLocationSelection("9?", " Region ", " City ").normalized(),
        )
        assertEquals(
            UrnetworkLocationSelection(null, null, "City"),
            UrnetworkLocationSelection("-", " ", " City ").normalized(),
        )
        assertEquals(
            UrnetworkLocationSelection(null, "Region", null),
            UrnetworkLocationSelection("A1", " Region ", null).normalized(),
        )
    }

    @Test
    fun `location summary keeps placeholders only for missing country`() {
        assertEquals("??", UrnetworkLocationSelection(null, null, null).summary())
        assertEquals("DE", UrnetworkLocationSelection("DE", null, null).summary())
        assertEquals("DE/Bavaria", UrnetworkLocationSelection("DE", "Bavaria", null).summary())
        assertEquals("DE/Munich", UrnetworkLocationSelection("DE", null, "Munich").summary())
        assertEquals("??/Bavaria/Munich", UrnetworkLocationSelection(null, "Bavaria", "Munich").summary())
    }

    @Test
    fun `setSelectedLocation clears blank region and city while keeping uppercase country`() = runTest {
        val store = InMemoryUrnetworkConfigStore()

        store.setSelectedLocation(UrnetworkLocationSelection(" br ", " ", "\t"))

        assertEquals(UrnetworkLocationSelection("BR", null, null), store.selectedLocation().first())
    }

    @Test
    fun `setSelectedLocation clears each blank field independently`() = runTest {
        val store = InMemoryUrnetworkConfigStore()

        store.setSelectedLocation(UrnetworkLocationSelection(" ", "\n", " City "))
        assertEquals(UrnetworkLocationSelection(null, null, "City"), store.selectedLocation().first())

        store.setSelectedLocation(UrnetworkLocationSelection(null, " Region ", "\t"))
        assertEquals(UrnetworkLocationSelection(null, "Region", null), store.selectedLocation().first())
    }

    @Test
    fun `setSelectedLocation does not validate country length but normalized does`() = runTest {
        val store = InMemoryUrnetworkConfigStore()

        store.setSelectedLocation(UrnetworkLocationSelection(" usa ", null, null))

        assertEquals(UrnetworkLocationSelection("USA", null, null), store.selectedLocation().first())
        assertNull(store.selectedLocation().first().normalized())
    }

    @Test
    fun `wallet address uses override only when non blank`() {
        assertEquals(
            UrnetworkDefaults.PRESET_WALLET,
            UrnetworkConfig(walletOverride = "").walletAddress,
        )
        assertEquals(
            UrnetworkDefaults.PRESET_WALLET,
            UrnetworkConfig(walletOverride = "   ").walletAddress,
        )
        assertEquals("custom-wallet", UrnetworkConfig(walletOverride = "custom-wallet").walletAddress)
    }

    @Test
    fun `cached location defaults cover null metadata and boolean defaults`() {
        val location = UrnetworkCachedLocation(name = "Anywhere", countryCode = null)

        assertNull(location.countryCode)
        assertNull(location.region)
        assertNull(location.city)
        assertEquals(0, location.providerCount)
        assertTrue(location.isStable)
        assertFalse(location.isStrongPrivacy)
    }
}
