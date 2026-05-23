package ru.ozero.enginemasterdns.deploy

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MasterDnsDockerScriptsContractTest {

    @Test
    fun `Dockerfile does not embed openssl encrypt_key generation - per-build key would break clients`() {
        assertFalse(
            MasterDnsDockerScripts.deployMasterDns.contains("openssl rand"),
            "Dockerfile RUN openssl rand генерит new key каждый --no-cache build → юзеры со старым toml " +
                "ломаются. Key обязан persist в named volume, не baked в image layer.",
        )
    }

    @Test
    fun `runContainer ensures named volume masterdns-key for encrypt key persistence`() {
        val cmd = MasterDnsDockerScripts.runContainer
        assertTrue(cmd.contains("docker volume inspect masterdns-key"), "must check volume existence")
        assertTrue(cmd.contains("docker volume create masterdns-key"), "must create volume if absent")
        assertTrue(
            cmd.contains("-v masterdns-key:/etc/masterdnsvpn"),
            "must mount named volume into container — иначе key теряется при container rm",
        )
    }

    @Test
    fun `runContainer generates encrypt key only if missing - idempotent redeploy preserves key`() {
        val cmd = MasterDnsDockerScripts.runContainer
        assertTrue(
            cmd.contains("test -f /etc/masterdnsvpn/encrypt_key.txt"),
            "must check key existence before generating — иначе каждый deploy перезаписывает key",
        )
        assertTrue(cmd.contains("openssl rand -hex 32"), "must have openssl rand fallback для первой генерации")
        assertTrue(
            cmd.contains("chmod 600"),
            "encrypt_key.txt должен быть 600 — секрет не должен быть world-readable",
        )
    }

    @Test
    fun `removeAll does NOT delete named volume - key persist for redeploy`() {
        assertFalse(
            MasterDnsDockerScripts.removeAll.contains("docker volume rm"),
            "removeAll НЕ должен удалять masterdns-key volume — иначе redeploy = новый key = " +
                "клиенты со старым toml ломаются. Полный wipe — отдельный flow если нужен.",
        )
    }

    @Test
    fun `runContainer restarts container after first-time key generation`() {
        val cmd = MasterDnsDockerScripts.runContainer
        assertTrue(
            cmd.contains("docker restart masterdns-ozero"),
            "После создания key файла нужно restart container — masterdnsvpn-server читает key на старте, " +
                "до restart он работает без key",
        )
    }

    @Test
    fun `installDocker polls dpkg lock before apt-get to survive background apt update`() {
        val cmd = MasterDnsDockerScripts.installDocker
        assertTrue(
            cmd.contains("fuser /var/lib/dpkg/lock-frontend"),
            "Без dpkg-lock polling apt-get падает E: Could not get lock на серверах " +
                "с unattended-upgrades. Polling обязателен перед sudo apt-get update/install.",
        )
        assertTrue(
            cmd.contains("seq 1 30"),
            "Polling loop должен быть min 30 итераций (≈5 мин при sleep 10) — типичная длительность apt update.",
        )
        assertTrue(
            cmd.contains("ERR_DPKG_LOCKED"),
            "Marker ERR_DPKG_LOCKED обязателен — UI должен показать distinct error при истечённом polling.",
        )
    }

    @Test
    fun `MARKER_ERR_DPKG_LOCKED constant exposed for deployer mapping`() {
        assertTrue(
            MasterDnsDockerScripts.MARKER_ERR_DPKG_LOCKED == "ERR_DPKG_LOCKED",
            "Constant marker для consumer-mapping (MasterDnsDeployerImpl парсит этот marker → DeployFailure).",
        )
    }
}
