package ru.ozero.commonnet

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import kotlin.test.assertNull

class AndroidNetworkDiagnosticsTest {
    private val context = mockk<Context>()
    private val manager = mockk<ConnectivityManager>()
    private val network = mockk<Network>()
    private val capabilities = mockk<NetworkCapabilities>()
    private val probes = mockk<NetworkDiagnostics>()

    @Test
    fun `rejects unavailable connectivity manager`() = runBlocking {
        every { context.getSystemService(Context.CONNECTIVITY_SERVICE) } returns null

        assertNull(AndroidNetworkDiagnostics(context, probes).run { true })
        coVerify(exactly = 0) { probes.run(any(), any()) }
    }

    @Test
    fun `rejects when application policy denies diagnostics`() = runBlocking {
        every { context.getSystemService(Context.CONNECTIVITY_SERVICE) } returns manager

        assertNull(AndroidNetworkDiagnostics(context, probes).run { false })
        coVerify(exactly = 0) { probes.run(any(), any()) }
    }

    @Test
    fun `rejects missing active network`() = runBlocking {
        every { context.getSystemService(Context.CONNECTIVITY_SERVICE) } returns manager
        every { manager.activeNetwork } returns null

        assertNull(AndroidNetworkDiagnostics(context, probes).run { true })
        coVerify(exactly = 0) { probes.run(any(), any()) }
    }

    @Test
    fun `rejects vpn transport`() = runBlocking {
        every { context.getSystemService(Context.CONNECTIVITY_SERVICE) } returns manager
        every { manager.activeNetwork } returns network
        every { manager.getNetworkCapabilities(network) } returns capabilities
        every { capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN) } returns true

        assertNull(AndroidNetworkDiagnostics(context, probes).run { true })
        coVerify(exactly = 0) { probes.run(any(), any()) }
    }

    @Test
    fun `rejects unknown network capabilities`() = runBlocking {
        every { context.getSystemService(Context.CONNECTIVITY_SERVICE) } returns manager
        every { manager.activeNetwork } returns network
        every { manager.getNetworkCapabilities(network) } returns null

        assertNull(AndroidNetworkDiagnostics(context, probes).run { true })
        coVerify(exactly = 0) { probes.run(any(), any()) }
    }
}
