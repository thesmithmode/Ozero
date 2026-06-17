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
        val cmd = MasterDnsDockerScripts.runContainer(TEST_SERVER_IPV4)
        assertTrue(cmd.contains("docker volume inspect masterdns-key"), "must check volume existence")
        assertTrue(cmd.contains("docker volume create masterdns-key"), "must create volume if absent")
        assertTrue(
            cmd.contains("-v masterdns-key:/etc/masterdnsvpn"),
            "must mount named volume into container — иначе key теряется при container rm",
        )
    }

    @Test
    fun `Dockerfile exposes root server config path expected by upstream binary`() {
        val cmd = MasterDnsDockerScripts.deployMasterDns
        assertTrue(cmd.contains("ln -sf /etc/masterdnsvpn/server_config.toml /server_config.toml"))
        assertFalse(cmd.contains("COPY server_config.toml"))
    }

    @Test
    fun `deployMasterDns fails build with bin missing marker when upstream installer produces no server binary`() {
        val cmd = MasterDnsDockerScripts.deployMasterDns
        assertFalse(cmd.contains("| bash 2>&1 || true"))
        assertTrue(cmd.contains("ERR_BUILD_BIN_MISSING"))
        assertTrue(cmd.contains("ERR_BUILD|reason=bin_missing"))
        assertTrue(cmd.contains("grep -q ERR_BUILD_BIN_MISSING"))
        assertTrue(cmd.contains("candidates="))
        assertTrue(cmd.contains("tail -80"))
        assertTrue(cmd.contains("head -40"))
    }

    @Test
    fun `deployMasterDns uses pinned release assets before installer fallback`() {
        val cmd = MasterDnsDockerScripts.deployMasterDns
        assertTrue(cmd.contains("ARG MASTERDNS_RELEASE_TAG=v2026.05.10.180256-27c7e11"))
        assertTrue(cmd.contains("api.github.com/repos/masterking32/MasterDnsVPN/releases/tags"))
        assertTrue(cmd.contains("browser_download_url"))
        assertTrue(cmd.contains("MasterDnsVPN_Server_Linux"))
        assertTrue(cmd.contains("server_linux_install.sh"))
    }

    @Test
    fun `deployMasterDns installs discovered server binary into expected executable path`() {
        val cmd = MasterDnsDockerScripts.deployMasterDns
        assertTrue(cmd.contains("MasterDnsVPN_Server_Linux*_v*"))
        assertTrue(cmd.contains("MasterDnsVPN_Server_Linux*"))
        assertTrue(cmd.contains("masterdnsvpn-server"))
        assertTrue(cmd.contains("install -m755"))
        assertTrue(cmd.contains("/usr/local/bin/masterdnsvpn-server"))
        assertTrue(cmd.contains("test -x /usr/local/bin/masterdnsvpn-server"))
        assertTrue(cmd.contains("CMD [\"/usr/local/bin/masterdnsvpn-server\"]"))
    }

    @Test
    fun `deployMasterDns cleans only temp build dir and stale image metadata before build`() {
        val cmd = MasterDnsDockerScripts.deployMasterDns
        assertTrue(cmd.contains("sudo rm -rf /tmp/mdns_build"))
        assertTrue(cmd.contains("sudo docker rmi masterdns-ozero"))
        assertFalse(cmd.contains("masterdns-key"))
        assertFalse(cmd.contains("docker volume rm"))
        assertFalse(cmd.contains("docker system prune"))
    }

    @Test
    fun `runContainer removes stale masterdns container before run without touching key volume`() {
        val cmd = MasterDnsDockerScripts.runContainer(TEST_SERVER_IPV4)
        assertTrue(cmd.contains("docker inspect -f '{{.State.Status}}' masterdns-ozero"))
        assertTrue(cmd.contains("created|exited|dead"))
        assertTrue(cmd.contains("sudo docker rm -f masterdns-ozero"))
        assertFalse(cmd.contains("docker volume rm"))
    }

    @Test
    fun `runContainer returns structured run diagnostics with inspect state error and log tail`() {
        val cmd = MasterDnsDockerScripts.runContainer(TEST_SERVER_IPV4)
        assertTrue(cmd.contains("ERR_RUN|phase=%s|exit=%s|%s|error=%s|logs=%s"))
        assertTrue(cmd.contains("state={{.State.Status}}|exit={{.State.ExitCode}}|error={{.State.Error}}"))
        assertTrue(cmd.contains("docker logs --tail 20 masterdns-ozero"))
        assertTrue(cmd.contains("run_out=\$"))
        assertFalse(cmd.contains("{ echo ERR_RUN; exit 0; }"))
    }

    @Test
    fun `runContainer generates encrypt key only if missing - idempotent redeploy preserves key`() {
        val cmd = MasterDnsDockerScripts.runContainer(TEST_SERVER_IPV4)
        assertTrue(
            cmd.contains("[ ! -s /etc/masterdnsvpn/encrypt_key.txt ]"),
            "must check key existence before generating — иначе каждый deploy перезаписывает key",
        )
        assertTrue(cmd.contains("openssl rand -hex 32"), "must have openssl rand fallback для первой генерации")
        assertTrue(
            cmd.contains("chmod 600"),
            "encrypt_key.txt должен быть 600 — секрет не должен быть world-readable",
        )
    }

    @Test
    fun `runContainer writes server config before starting masterdns container`() {
        val cmd = MasterDnsDockerScripts.runContainer(TEST_SERVER_IPV4)
        val configIndex = cmd.indexOf("server_config.toml")
        val runIndex = cmd.indexOf("docker run -d --name masterdns-ozero")

        assertTrue(configIndex >= 0)
        assertTrue(runIndex > configIndex)
        assertTrue(cmd.contains("DOMAIN = [\"${MasterDnsDockerScripts.DEFAULT_DOMAIN}\"]"))
        assertFalse(cmd.contains("DOMAIN = []"))
        assertTrue(cmd.contains("PROTOCOL_TYPE = \"SOCKS5\""))
        assertTrue(cmd.contains("UDP_PORT = 53"))
        assertTrue(cmd.contains("DATA_ENCRYPTION_METHOD = 5"))
        assertTrue(cmd.contains("ENCRYPTION_KEY_FILE = \"/etc/masterdnsvpn/encrypt_key.txt\""))
        assertTrue(cmd.contains("run_diag config_init"))
    }

    @Test
    fun `runContainer migrates existing empty server domain config`() {
        val cmd = MasterDnsDockerScripts.runContainer(TEST_SERVER_IPV4)

        assertTrue(cmd.contains("grep -Eq \"^[[:space:]]*DOMAIN[[:space:]]*=[[:space:]]*\\[[[:space:]]*\\]"))
        assertFalse(cmd.contains("grep -Eq '^DOMAIN"))
        assertTrue(cmd.contains("sed \"s/^[[:space:]]*DOMAIN[[:space:]]*=[[:space:]]*\\[[[:space:]]*\\]"))
        assertFalse(cmd.contains("sed 's/^DOMAIN[[:space:]]*=[[:space:]]*\\[[[:space:]]*\\]"))
        assertTrue(cmd.contains("grep -Ev \"^[[:space:]]*DOMAIN[[:space:]]*=\""))
        assertTrue(cmd.contains("printf \"DOMAIN = [\\\"${MasterDnsDockerScripts.DEFAULT_DOMAIN}\\\"]\\n\""))
        assertTrue(cmd.contains("DOMAIN = [\"${MasterDnsDockerScripts.DEFAULT_DOMAIN}\"]"))
    }

    @Test
    fun `runContainer preserves indented or literal quoted non-empty domain config`() {
        val cmd = MasterDnsDockerScripts.runContainer(TEST_SERVER_IPV4)

        assertTrue(cmd.contains("^[[:space:]]*DOMAIN[[:space:]]*="))
        assertTrue(cmd.contains("[[:space:]]*['\"'\"'\\\"]?[A-Za-z0-9]"))
        assertFalse(cmd.contains("^DOMAIN[[:space:]]*=\\[[[:space:]]*\\\"?[A-Za-z0-9]"))
        assertFalse(cmd.contains("grep -Ev \"^DOMAIN[[:space:]]*=\""))
    }

    @Test
    fun `runContainer config init shell keeps nested sh-c single quote balanced`() {
        val cmd = MasterDnsDockerScripts.runContainer(TEST_SERVER_IPV4)
        val configInit = cmd.substringAfter("masterdns-ozero sh -c '").substringBefore("' 2>&1")
        val escapedSingleQuotes = configInit
            .replace("'\\''", "")
            .replace("'\"'\"'", "")

        assertFalse(escapedSingleQuotes.contains("'"))
        assertFalse(escapedSingleQuotes.contains("sed '"))
        assertTrue(configInit.contains("sed \""))
        assertTrue(configInit.contains("elif grep -Eq"))
        assertTrue(configInit.contains("fi"))
    }

    @Test
    fun `removeAmneziaDnsOnly does not prune or remove volumes networks images`() {
        val cmd = MasterDnsDockerScripts.removeAmneziaDnsOnly
        assertTrue(cmd.contains("sudo docker inspect amnezia-dns"))
        assertTrue(cmd.contains("/amnezia-dns"))
        assertTrue(cmd.contains("sudo docker stop amnezia-dns"))
        assertTrue(cmd.contains("sudo docker rm amnezia-dns"))
        assertFalse(cmd.contains("docker system prune"))
        assertFalse(cmd.contains("docker volume rm"))
        assertFalse(cmd.contains("docker network rm"))
        assertFalse(cmd.contains("docker rmi"))
        assertFalse(cmd.contains("amnezia-awg"))
        assertFalse(cmd.contains("amnezia-awg2"))
        assertFalse(cmd.contains("adguardhome"))
    }

    @Test
    fun `removeAmneziaDnsOnly reports failed stop or rm instead of unconditional success`() {
        val cmd = MasterDnsDockerScripts.removeAmneziaDnsOnly
        assertTrue(cmd.contains(MasterDnsDockerScripts.MARKER_AMNEZIA_DNS_REMOVE_FAILED))
        assertFalse(cmd.contains("docker stop amnezia-dns 2>/dev/null || true"))
        assertFalse(cmd.contains("docker rm amnezia-dns 2>/dev/null || true"))
    }

    @Test
    fun `checkAmneziaDns53 inspects exact amnezia-dns name and emits conflict marker`() {
        val cmd = MasterDnsDockerScripts.checkAmneziaDns53
        assertTrue(cmd.contains("sudo docker ps --format '{{.Names}}'"))
        assertTrue(cmd.contains("grep -qx 'amnezia-dns'"))
        assertTrue(cmd.contains("sudo docker inspect amnezia-dns"))
        assertTrue(cmd.contains("/amnezia-dns"))
        assertTrue(cmd.contains("53/udp"))
        assertTrue(cmd.contains("AMNEZIA_DNS_CONFLICT|proto="))
    }

    @Test
    fun `checkAmneziaDns53 checks only udp published on amnezia-dns`() {
        val cmd = MasterDnsDockerScripts.checkAmneziaDns53
        assertFalse(cmd.contains("53/tcp"))
    }

    @Test
    fun `cleanupLegacyMasterDns only targets legacy MasterDNS service and process names`() {
        val cmd = MasterDnsDockerScripts.cleanupLegacyMasterDns
        assertTrue(cmd.contains("LEGACY_MASTERDNS_CLEANUP_OK"))
        assertTrue(cmd.contains("masterdns|masterdnsvpn"))
        assertTrue(cmd.contains("MasterDnsVPN_Server_Linux|masterdnsvpn-server|MasterDnsVPN"))
        assertTrue(cmd.contains("systemctl stop"))
        assertTrue(cmd.contains("pkill -f"))
        assertFalse(cmd.contains("docker volume rm"))
        assertFalse(cmd.contains("docker system prune"))
        assertFalse(cmd.contains("systemctl stop systemd-resolved"))
        assertFalse(cmd.contains("pkill -f 'dnsmasq"))
        assertFalse(cmd.contains("pkill -f 'named"))
    }

    @Test
    fun `checkPort53 inspects UDP local address without peer-column false positive`() {
        val cmd = MasterDnsDockerScripts.checkPort53(TEST_SERVER_IPV4)
        assertTrue(cmd.contains("ss_conflict udp -lunp"))
        assertTrue(cmd.contains("for (i = 1; i <= NF; i++)"))
        assertTrue(cmd.contains("local=\"\""))
        assertFalse(cmd.contains("awk '$5 ~ /(^|:)53$/ {print $5"))
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
    fun `runContainer does not need restart after pre-start config generation`() {
        val cmd = MasterDnsDockerScripts.runContainer(TEST_SERVER_IPV4)
        assertFalse(
            cmd.contains("docker restart masterdns-ozero"),
            "config and key are initialized before docker run, so restart must not be needed",
        )
        assertTrue(cmd.indexOf("server_config.toml") < cmd.indexOf("docker run -d --name masterdns-ozero"))
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
    fun `installDocker uses official get-docker-com installer as primary path`() {
        val cmd = MasterDnsDockerScripts.installDocker
        assertTrue(
            cmd.contains("https://get.docker.com"),
            "installDocker обязан использовать https://get.docker.com primary — " +
                "distro docker.io пакет фейлит 'Не удалось получить информацию о пакете' на " +
                "минимальных Debian/Ubuntu без universe репа. Official installer Docker Inc " +
                "сам подключает docker-ce репо на любом distro.",
        )
        assertTrue(
            cmd.contains("curl -fsSL") || cmd.contains("wget -qO"),
            "Должен быть curl или wget fetcher для get.docker.com installer.",
        )
    }

    @Test
    fun `installDocker captures install log to surface real failure reason`() {
        val cmd = MasterDnsDockerScripts.installDocker
        assertTrue(
            cmd.contains("/tmp/docker-install.log"),
            "При ERR_DOCKER юзер должен видеть конкретную причину (apt fail / no universe / network) — " +
                "лог tail обязателен для диагностики без угадывания.",
        )
        assertTrue(
            cmd.contains("tail -30 /tmp/docker-install.log") || cmd.contains("tail -n 30"),
            "Tail последних строк лога должен попадать в stdout response для UI/PersistentLoggers.",
        )
    }

    @Test
    fun `installDocker is idempotent — skip install if docker binary already present`() {
        val cmd = MasterDnsDockerScripts.installDocker
        assertTrue(
            cmd.contains("docker_already") && cmd.contains("docker --version"),
            "Re-deploy на сервер с уже стоящим docker должен пропускать install — " +
                "иначе мы перезаписываем рабочую установку.",
        )
    }

    @Test
    fun `checkPort53 emits structured owner address protocol when busy`() {
        val script = MasterDnsDockerScripts.checkPort53(TEST_SERVER_IPV4)
        assertTrue(script.contains("PORT_BUSY|proto="))
        assertTrue(script.contains("|addr="))
        assertTrue(script.contains("|owner="))
        assertTrue(script.contains("ss_conflict udp -lunp"))
        assertTrue(script.contains("docker_conflict"))
    }

    @Test
    fun `checkPort53 inspects docker containers and removes stale own container before bind verdict`() {
        val script = MasterDnsDockerScripts.checkPort53(TEST_SERVER_IPV4)
        assertTrue(script.contains("docker ps --format '{{.Names}}|{{.Ports}}'"))
        assertTrue(script.contains("docker ps -a --filter status=created --format '{{.Names}}|{{.Ports}}'"))
        assertTrue(script.contains("masterdns_state="))
        assertTrue(script.contains("created|exited|dead|restarting|paused"))
        assertTrue(script.contains("sudo docker rm -f masterdns-ozero"))
        assertTrue(script.contains("running) echo PORT_FREE; exit 0"))
    }

    @Test
    fun `checkPort53 never reports free after bind probe failure without owner details`() {
        val script = MasterDnsDockerScripts.checkPort53(TEST_SERVER_IPV4)
        assertTrue(script.contains("bind_probe udp \"\$publish_addr\"; bind_rc="))
        assertTrue(script.contains("owner=bind_probe:exit_"))
        assertTrue(script.contains("addr=\$publish_addr:53"))
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
    fun `checkPort53 loopback-only systemd-resolved does not produce busy contract`() {
        val script = MasterDnsDockerScripts.checkPort53(TEST_SERVER_IPV4)
        assertTrue(script.contains("127.0.0.53"))
        assertTrue(script.contains("127.0.0.54"))
        assertTrue(script.contains("127.0.0.1"))
        assertTrue(script.contains("::1"))
        assertFalse(
            script.contains("grep -q ':53 '") && script.contains("&& echo PORT_BUSY"),
            "Raw ss :53 match treats loopback-only systemd-resolved as conflict.",
        )
    }

    @Test
    fun `checkPort53 test-binds UDP publish address used by runContainer`() {
        val script = MasterDnsDockerScripts.checkPort53(TEST_SERVER_IPV4)
        assertTrue(script.contains("publish_host_ip"))
        assertTrue(script.contains("ip route get 1.1.1.1"))
        assertTrue(script.contains("bind_probe udp"))
        assertTrue(script.contains("socket.SOCK_DGRAM"))
        assertTrue(script.contains("sock.bind((addr, 53))"))
        assertTrue(MasterDnsDockerScripts.runContainer(TEST_SERVER_IPV4).contains("publish_host_ip"))
        assertTrue(MasterDnsDockerScripts.runContainer(TEST_SERVER_IPV4).contains("-p \"\$publish_addr:53:53/udp\""))
    }

    @Test
    fun `checkPort53 reports zero IPv4 listener as busy with machine marker`() {
        val script = MasterDnsDockerScripts.checkPort53(TEST_SERVER_IPV4)
        assertTrue(script.contains("PORT_BUSY|proto="))
        assertTrue(script.contains("|addr="))
        assertTrue(script.contains("|owner="))
        assertTrue(script.contains("!ignored(addr) && scoped(addr)"))
        assertTrue(script.contains("addr == \"0.0.0.0\""))
    }

    @Test
    fun `checkPort53 scopes docker and ss conflicts to publish address or wildcard`() {
        val script = MasterDnsDockerScripts.checkPort53(TEST_SERVER_IPV4)

        assertTrue(script.contains("-v publish_addr=\"\$publish_addr\""))
        assertTrue(script.contains("addr == publish_addr"))
        assertTrue(script.contains("addr == \"0.0.0.0\""))
        assertTrue(script.contains("addr == \"*\""))
        assertTrue(script.contains("addr == \"::\""))
        assertTrue(script.contains("if (scoped(addr))"))
        assertTrue(script.contains("!ignored(addr) && scoped(addr)"))
    }

    @Test
    fun `checkPort53 reports wildcard IPv6 listener as busy`() {
        val script = MasterDnsDockerScripts.checkPort53(TEST_SERVER_IPV4)
        val ignoredBody = Regex("""function ignored\(addr\) \{ return ([^}]+) }""")
            .find(script)
            ?.groupValues
            ?.get(1)
            ?: ""
        assertTrue(script.contains("gsub(/^\\[/"))
        assertTrue(script.contains("gsub(/\\]$/"))
        assertTrue(ignoredBody.contains("addr == \"::1\""))
        assertFalse(ignoredBody.contains("addr == \"::\""), "[::]:53 must remain an external conflict, not ignored.")
    }

    @Test
    fun `checkPort53 reports external server IPv4 listener as busy`() {
        val script = MasterDnsDockerScripts.checkPort53(TEST_SERVER_IPV4)
        assertTrue(script.contains("ss_conflict udp -lunp"))
        assertTrue(script.contains("sub(/%.*$/"))
        assertTrue(script.contains("!ignored(addr) && scoped(addr)"))
    }

    @Test
    fun `checkPort53 reports Docker UDP publish as busy`() {
        val script = MasterDnsDockerScripts.checkPort53(TEST_SERVER_IPV4)
        assertTrue(script.contains("docker_conflict"))
        assertTrue(script.contains("->53\\/udp"))
        assertTrue(script.contains("proto="))
        assertTrue(script.contains("owner=docker:"))
    }

    @Test
    fun `checkPort53 ignores Docker TCP publish because runContainer binds only UDP`() {
        val script = MasterDnsDockerScripts.checkPort53(TEST_SERVER_IPV4)
        assertFalse(script.contains("->53\\/(udp|tcp)"))
        assertFalse(script.contains("->53\\/tcp"))
        assertTrue(script.contains("sub(/^.*->53\\//"))
    }

    @Test
    fun `runContainer uses retry loop instead of fixed sleep for readiness probe`() {
        val cmd = MasterDnsDockerScripts.runContainer(TEST_SERVER_IPV4)
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
    fun `readEncryptKey retries after container start before reporting empty key`() {
        val cmd = MasterDnsDockerScripts.readEncryptKey
        assertTrue(cmd.contains("seq 1 10"))
        assertTrue(cmd.contains("docker exec masterdns-ozero cat /etc/masterdnsvpn/encrypt_key.txt"))
        assertTrue(cmd.contains("sleep 1"))
        assertTrue(cmd.contains("exit 0"))
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
    fun `removeAll closes firewall port 53 only if we opened it (marker file gate)`() {
        val cmd = MasterDnsDockerScripts.removeAll
        assertTrue(
            cmd.contains("/var/lib/masterdns-ozero/fw_opened"),
            "Marker file gate обязателен — без него removeAll удалит юзерское правило ufw allow 53/udp " +
                "которое существовало до нашего deploy.",
        )
        assertTrue(cmd.contains("ufw delete allow 53/udp"))
        assertTrue(cmd.contains("--remove-port=53/udp"))
        assertTrue(cmd.contains("iptables -D INPUT"))
    }

    @Test
    fun `openFirewallPort53 writes marker file to track ownership`() {
        val script = MasterDnsDockerScripts.openFirewallPort53
        val hasMarker = script.contains("/var/lib/masterdns-ozero/fw_opened")
        val hasMkdir = script.contains("mkdir -p /var/lib/masterdns-ozero") ||
            script.contains("install -d /var/lib/masterdns-ozero")
        assertTrue(
            hasMarker && hasMkdir,
            "openFirewallPort53 должен писать marker — нужен для removeAll cleanup gating.",
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

    @Test
    fun `publish address is derived from configured server host and constrained to IPv4`() {
        val checkScript = MasterDnsDockerScripts.checkPort53("dns.example.test")
        val runScript = MasterDnsDockerScripts.runContainer("dns.example.test")

        for (script in listOf(checkScript, runScript)) {
            assertTrue(script.contains("server_host='dns.example.test'"))
            assertTrue(script.contains("local_ipv4()"))
            assertTrue(script.contains("ip -4 addr show"))
            assertTrue(script.contains("local_ipv4 \"\$literal_ipv4\""))
            assertTrue(script.contains("local_ipv4 \"\$resolved_ipv4\""))
            assertTrue(script.contains("getent ahostsv4"))
            assertTrue(script.contains("hostname -I 2>/dev/null | awk '{ for (i = 1; i <= NF; i++)"))
            assertTrue(script.contains("^[0-9]+[.][0-9]+[.][0-9]+[.][0-9]+$"))
            assertFalse(script.contains("hostname -I 2>/dev/null | awk '{print $1}'"))
        }
    }

    private companion object {
        const val TEST_SERVER_IPV4 = "203.0.113.10"
    }
}
