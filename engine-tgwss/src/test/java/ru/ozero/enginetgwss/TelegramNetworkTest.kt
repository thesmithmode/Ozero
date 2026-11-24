package ru.ozero.enginetgwss

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import ru.ozero.enginescore.VpnSocketProtector
import ru.ozero.enginescore.VpnSocketProtectorHolder
import java.net.InetAddress
import java.net.Socket
import java.net.UnknownHostException
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TelegramNetworkTest {
    @Test
    fun `DNS skips VPN and networks without internet`() {
        val context = mockk<Context>()
        val manager = mockk<ConnectivityManager>()
        val vpn = mockk<Network>()
        val offline = mockk<Network>()
        val physical = mockk<Network>()
        val address = InetAddress.getByAddress(byteArrayOf(192.toByte(), 0, 2, 1))
        every { context.getSystemService(ConnectivityManager::class.java) } returns manager
        every { manager.activeNetwork } returns vpn
        every { manager.allNetworks } returns arrayOf(vpn, offline, physical)
        every { manager.getNetworkCapabilities(vpn) } returns capabilities(internet = true, physical = false)
        every { manager.getNetworkCapabilities(offline) } returns null
        every { manager.getNetworkCapabilities(physical) } returns capabilities(internet = true, physical = true)
        every { physical.getAllByName("kws2.web.telegram.org") } returns arrayOf(address)
        val network = TelegramNetwork(context)
        assertEquals(listOf(address), network.dns.lookup("kws2.web.telegram.org"))
        verify(exactly = 0) { vpn.getAllByName(any()) }
        every { manager.getNetworkCapabilities(physical) } returns capabilities(internet = false, physical = true)
        assertFailsWith<UnknownHostException> { network.dns.lookup("kws2.web.telegram.org") }
    }

    @Test
    fun `missing connectivity and socket protector fail closed`() {
        val context = mockk<Context>()
        every { context.getSystemService(ConnectivityManager::class.java) } returns null
        val network = TelegramNetwork(context)
        assertFailsWith<UnknownHostException> { network.dns.lookup("kws2.web.telegram.org") }
        val binding = VpnSocketProtector { true }
        Socket().use { socket ->
            assertFalse(network.protect(socket))
            try {
                VpnSocketProtectorHolder.bind(binding) { it === socket }
                assertTrue(network.protect(socket))
            } finally {
                VpnSocketProtectorHolder.unbind(binding)
            }
        }
    }

    private fun capabilities(internet: Boolean, physical: Boolean): NetworkCapabilities =
        mockk<NetworkCapabilities>().apply {
            every { hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) } returns internet
            every { hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN) } returns physical
        }
}
