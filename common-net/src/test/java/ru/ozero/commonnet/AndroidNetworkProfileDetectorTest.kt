package ru.ozero.commonnet

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.RouteInfo
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class AndroidNetworkProfileDetectorTest {

    @Test
    fun `returns NONE when connectivity manager missing`() {
        val context = mockk<Context>()
        every { context.getSystemService(Context.CONNECTIVITY_SERVICE) } returns null

        val detector = AndroidNetworkProfileDetector(context)

        assertEquals(NetworkProfile.NONE, detector.current())
    }

    @Test
    fun `returns NONE when active network missing`() {
        val context = mockk<Context>()
        val cm = mockk<ConnectivityManager>()
        every { context.getSystemService(Context.CONNECTIVITY_SERVICE) } returns cm
        every { cm.activeNetwork } returns null

        val detector = AndroidNetworkProfileDetector(context)

        assertEquals(NetworkProfile.NONE, detector.current())
    }

    @Test
    fun `wifi profile hashes gateway and interface`() {
        val context = mockk<Context>()
        val cm = mockk<ConnectivityManager>()
        val network = mockk<Network>()
        val caps = mockk<NetworkCapabilities>()
        val link = mockk<LinkProperties>()
        val route = mockk<RouteInfo>()

        every { context.getSystemService(Context.CONNECTIVITY_SERVICE) } returns cm
        every { cm.activeNetwork } returns network
        every { cm.getNetworkCapabilities(network) } returns caps
        every { cm.getLinkProperties(network) } returns link
        every { caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) } returns false
        every { caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) } returns true
        every { caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) } returns false
        every { caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) } returns false
        every { link.routes } returns listOf(route)
        every { route.isDefaultRoute } returns true
        every { route.gateway?.hostAddress } returns "192.0.2.1"
        every { link.interfaceName } returns "wlan0"

        val detector = AndroidNetworkProfileDetector(context)
        val profile = detector.current()

        assertEquals(NetworkProfile.Transport.WIFI, profile.transport)
        assertEquals(16, profile.id.length)
    }

    @Test
    fun `other transport is used when no known transport flags are set`() {
        val context = mockk<Context>()
        val cm = mockk<ConnectivityManager>()
        val network = mockk<Network>()
        val caps = mockk<NetworkCapabilities>()

        every { context.getSystemService(Context.CONNECTIVITY_SERVICE) } returns cm
        every { cm.activeNetwork } returns network
        every { cm.getNetworkCapabilities(network) } returns caps
        every { cm.getLinkProperties(network) } returns null
        every { caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) } returns false
        every { caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) } returns false
        every { caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) } returns false
        every { caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) } returns false

        val detector = AndroidNetworkProfileDetector(context)

        assertEquals(NetworkProfile.Transport.OTHER, detector.current().transport)
    }
}
