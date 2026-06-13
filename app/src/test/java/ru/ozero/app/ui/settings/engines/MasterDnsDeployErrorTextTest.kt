package ru.ozero.app.ui.settings.engines

import org.junit.jupiter.api.Test
import ru.ozero.app.R
import ru.ozero.enginemasterdns.deploy.MasterDnsDeployState
import kotlin.test.assertEquals

class MasterDnsDeployErrorTextTest {

    @Test
    fun `structured port busy maps owner address protocol to formatted message`() {
        val text = masterDnsDeployErrorText(
            MasterDnsDeployState.PortBusy(
                protocol = "udp",
                address = "0.0.0.0:53",
                owner = "docker:adguardhome",
            ),
        )

        assertEquals(R.string.masterdns_deploy_error_port_busy_structured, text.resId)
        assertEquals(listOf("docker:adguardhome", "0.0.0.0:53", "UDP"), text.args)
    }

    @Test
    fun `legacy port busy code keeps fallback message`() {
        val text = masterDnsDeployErrorText(MasterDnsDeployState.Error("port_53_busy"))

        assertEquals(R.string.masterdns_deploy_error_port_busy, text.resId)
        assertEquals(emptyList(), text.args)
    }

    @Test
    fun `bin missing build failure maps to actionable localized message`() {
        val text = masterDnsDeployErrorText(MasterDnsDeployState.Error("build_failed/bin_missing"))

        assertEquals(R.string.masterdns_deploy_error_build_bin_missing, text.resId)
        assertEquals(emptyList(), text.args)
    }

    @Test
    fun `bin missing build failure with diagnostics keeps actionable localized message`() {
        val text = masterDnsDeployErrorText(
            MasterDnsDeployState.Error("build_failed/bin_missing|candidates=none"),
        )

        assertEquals(R.string.masterdns_deploy_error_build_bin_missing, text.resId)
        assertEquals(emptyList(), text.args)
    }
}
