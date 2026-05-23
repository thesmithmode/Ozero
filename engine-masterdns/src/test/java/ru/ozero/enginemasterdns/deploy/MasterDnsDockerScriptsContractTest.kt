package ru.ozero.enginemasterdns.deploy

import org.junit.jupiter.api.Assertions.assertEquals
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

    @Test
    fun `checkSudoNoPassword script clears sudo cache before probe`() {
        val script = MasterDnsDockerScripts.checkSudoNoPassword
        assertTrue(
            script.contains("sudo -K"),
            "sudo -K обязателен перед probe — иначе cached credentials дадут false SUDO_OK " +
                "на серверах где пароль требуется но кэш ещё не истёк.",
        )
    }

    @Test
    fun `checkSudoNoPassword emits SUDO_OK for root user without running sudo`() {
        val script = MasterDnsDockerScripts.checkSudoNoPassword
        assertTrue(
            script.contains("whoami") && script.contains("root"),
            "Root user должен получать SUDO_OK без sudo probe — sudo для root всегда available.",
        )
    }

    @Test
    fun `checkSudoNoPassword emits all 5 distinct error markers`() {
        val script = MasterDnsDockerScripts.checkSudoNoPassword
        assertTrue(script.contains("ERR_SUDO_NOT_INSTALLED"), "marker для отсутствия sudo binary")
        assertTrue(script.contains("ERR_SUDO_PWD_REQUIRED"), "marker когда пароль требуется")
        assertTrue(script.contains("ERR_SUDO_NOT_ALLOWED"), "marker когда запрещён в sudoers")
        assertTrue(script.contains("ERR_SUDO_NO_HOME"), "marker когда home dir недоступен")
        assertTrue(script.contains("ERR_SUDO_NOT_IN_GROUP"), "marker когда не в sudo/wheel группе")
    }

    @Test
    fun `runContainer uses retry loop instead of fixed sleep for readiness probe`() {
        val cmd = MasterDnsDockerScripts.runContainer
        assertFalse(
            cmd.contains("sleep 2;"),
            "Fixed sleep 2 race condition — readEncryptKey может вернуть пусто если контейнер ещё не готов. " +
                "Заменить retry loop docker exec true.",
        )
        assertTrue(
            cmd.contains("docker exec masterdns-ozero true"),
            "readiness probe должен проверять что контейнер отвечает (docker exec true).",
        )
        assertTrue(
            cmd.contains("seq 1 15"),
            "Retry loop ≥15 итераций (15s при sleep 1) — достаточно для запуска контейнера на slow VPS.",
        )
    }

    @Test
    fun `openFirewallPort53 detects ufw firewalld iptables and emits distinct markers`() {
        val script = MasterDnsDockerScripts.openFirewallPort53
        assertTrue(script.contains("ufw") && script.contains("ufw allow 53/udp"))
        assertTrue(script.contains("firewall-cmd") && script.contains("--add-port=53/udp"))
        assertTrue(script.contains("iptables") && script.contains("--dport 53"))
        assertTrue(script.contains("FW_UFW_OK"))
        assertTrue(script.contains("FW_FIREWALLD_OK"))
        assertTrue(script.contains("FW_IPTABLES_OK"))
        assertTrue(script.contains("FW_NONE_OK"))
    }

    @Test
    fun `openFirewallPort53 persists iptables rule via iptables-save not just runtime allow`() {
        val script = MasterDnsDockerScripts.openFirewallPort53
        assertTrue(
            script.contains("iptables-save"),
            "Без iptables-save правило теряется после reboot — это Amnezia bug, у нас persistence обязательно.",
        )
    }

    @Test
    fun `openFirewallPort53 idempotent — check existing iptables rule before adding`() {
        val script = MasterDnsDockerScripts.openFirewallPort53
        assertTrue(
            script.contains("iptables -C") || script.contains("iptables --check"),
            "iptables -C проверка дубля — без неё каждый redeploy добавляет дублирующее правило.",
        )
    }

    @Test
    fun `firewall markers exposed as constants for deployer mapping`() {
        assertEquals("FW_UFW_OK", MasterDnsDockerScripts.MARKER_FW_UFW_OK)
        assertEquals("FW_FIREWALLD_OK", MasterDnsDockerScripts.MARKER_FW_FIREWALLD_OK)
        assertEquals("FW_IPTABLES_OK", MasterDnsDockerScripts.MARKER_FW_IPTABLES_OK)
        assertEquals("FW_NONE_OK", MasterDnsDockerScripts.MARKER_FW_NONE_OK)
    }

    @Test
    fun `sudo markers are exposed as constants for deployer mapping`() {
        assertEquals("ERR_SUDO_NOT_INSTALLED", MasterDnsDockerScripts.MARKER_ERR_SUDO_NOT_INSTALLED)
        assertEquals("ERR_SUDO_PWD_REQUIRED", MasterDnsDockerScripts.MARKER_ERR_SUDO_PWD_REQUIRED)
        assertEquals("ERR_SUDO_NOT_ALLOWED", MasterDnsDockerScripts.MARKER_ERR_SUDO_NOT_ALLOWED)
        assertEquals("ERR_SUDO_NO_HOME", MasterDnsDockerScripts.MARKER_ERR_SUDO_NO_HOME)
        assertEquals("ERR_SUDO_NOT_IN_GROUP", MasterDnsDockerScripts.MARKER_ERR_SUDO_NOT_IN_GROUP)
        assertEquals("SUDO_OK", MasterDnsDockerScripts.MARKER_SUDO_OK)
    }
}
