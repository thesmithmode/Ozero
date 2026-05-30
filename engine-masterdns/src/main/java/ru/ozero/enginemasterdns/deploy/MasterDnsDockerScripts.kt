package ru.ozero.enginemasterdns.deploy

internal object MasterDnsDockerScripts {

    const val checkSudoNoPassword =
        "sudo -K 2>/dev/null;" +
            " if [ \"\$(whoami)\" = \"root\" ]; then echo SUDO_OK; exit 0; fi;" +
            " if ! command -v sudo >/dev/null 2>&1; then echo ERR_SUDO_NOT_INSTALLED; exit 0; fi;" +
            " out=\$(sudo -n true 2>&1);" +
            " case \"\$out\" in" +
            " *\"password is required\"*) echo ERR_SUDO_PWD_REQUIRED;;" +
            " *\"not allowed\"*|*\"sudoers\"*) echo ERR_SUDO_NOT_ALLOWED;;" +
            " *\"can't cd\"*|*\"Permission denied\"*) echo ERR_SUDO_NO_HOME;;" +
            " \"\") if id -nG \"\$(whoami)\" | grep -qE '\\b(sudo|wheel)\\b';" +
            " then echo SUDO_OK; else echo ERR_SUDO_NOT_IN_GROUP; fi;;" +
            " *) echo ERR_SUDO_NOT_ALLOWED;;" +
            " esac"

    val checkPort53: String =
        """
        if sudo docker ps --format '{{.Names}}' 2>/dev/null | grep -q '^masterdns-ozero${'$'}'; then echo PORT_FREE; exit 0; fi
        docker_conflict() { sudo docker ps --format '{{.Names}}|{{.Ports}}' 2>/dev/null | awk -F'|' '${'$'}1 != "masterdns-ozero" { split(${'$'}2, ports, ","); for (i in ports) { p=ports[i]; gsub(/^ +| +${'$'}/, "", p); if (p ~ /->53\/(udp|tcp)/) { proto=p; sub(/^.*->53\//, "", proto); sub(/ .*/, "", proto); host=p; sub(/->.*${'$'}/, "", host); addr=host; sub(/:53${'$'}/, "", addr); gsub(/^\[/, "", addr); gsub(/\]${'$'}/, "", addr); if (addr == "") addr="0.0.0.0"; print "PORT_BUSY|proto=" proto "|addr=" addr "|name=" ${'$'}1; exit } } } }'; }
        ss_conflict() { proto="${'$'}1"; flags="${'$'}2"; sudo ss -H "${'$'}flags" 2>/dev/null | awk -v proto="${'$'}proto" 'function clean(addr) { gsub(/^\[/, "", addr); gsub(/\]${'$'}/, "", addr); sub(/%.*${'$'}/, "", addr); return addr } function ignored(addr) { return addr == "127.0.0.53" || addr == "127.0.0.54" || addr == "127.0.0.1" || addr == "::1" } { local=""; for (i = 1; i <= NF; i++) if (${'$'}i ~ /:53${'$'}/ || ${'$'}i ~ /\]:53${'$'}/) { local=${'$'}i; break } if (local == "") next; addr=local; sub(/:53${'$'}/, "", addr); addr=clean(addr); if (!ignored(addr)) { name="unknown"; if (match(${'$'}0, /"[^"]+"/)) name=substr(${'$'}0, RSTART + 1, RLENGTH - 2); print "PORT_BUSY|proto=" proto "|addr=" addr "|name=" name; exit } }'; }
        bind_probe() { proto="${'$'}1"; py="${'$'}(command -v python3 2>/dev/null || command -v python 2>/dev/null)"; [ -n "${'$'}py" ] || return 0; sudo "${'$'}py" - "${'$'}proto" <<'PY'
        import errno
        import socket
        import sys
        proto = sys.argv[1]
        sock_type = socket.SOCK_DGRAM if proto == "udp" else socket.SOCK_STREAM
        sock = socket.socket(socket.AF_INET, sock_type)
        try:
            sock.bind(("0.0.0.0", 53))
        except OSError as exc:
            sys.exit(98 if exc.errno in (errno.EADDRINUSE, errno.EACCES) else 1)
        finally:
            sock.close()
        PY
        }
        bind_probe udp; bind_rc=${'$'}?
        if [ "${'$'}bind_rc" != "0" ]; then busy="${'$'}(docker_conflict)"; [ -n "${'$'}busy" ] && { echo "${'$'}busy"; exit 0; }; busy="${'$'}(ss_conflict udp -lunp)"; [ -n "${'$'}busy" ] && { echo "${'$'}busy"; exit 0; }; fi
        busy="${'$'}(docker_conflict)"; [ -n "${'$'}busy" ] && { echo "${'$'}busy"; exit 0; }
        busy="${'$'}(ss_conflict udp -lunp)"; [ -n "${'$'}busy" ] && { echo "${'$'}busy"; exit 0; }
        echo PORT_FREE
        """.trimIndent()

    const val checkResources =
        "echo \$(free -m 2>/dev/null | awk 'NR==2{print \$7}')" +
            " \$(df -m / 2>/dev/null | awk 'NR==2{print \$4}')"

    const val installDocker: String =
        "set +e;" +
            " if command -v docker >/dev/null 2>&1 && sudo docker --version >/dev/null 2>&1;" +
            " then docker_already=1; else docker_already=0; fi;" +
            " if which apt-get >/dev/null 2>&1; then is_apt=1; else is_apt=0; fi;" +
            " if [ \"\$is_apt\" = \"1\" ] && [ \"\$docker_already\" = \"0\" ];" +
            " then export DEBIAN_FRONTEND=noninteractive;" +
            " locked=0; for i in \$(seq 1 30);" +
            " do sudo fuser /var/lib/dpkg/lock-frontend >/dev/null 2>&1 || { locked=0; break; };" +
            " locked=1; sleep 10; done;" +
            " if [ \"\$locked\" = \"1\" ]; then echo ERR_DPKG_LOCKED; exit 0; fi; fi;" +
            " if [ \"\$docker_already\" = \"0\" ];" +
            " then if command -v curl >/dev/null 2>&1;" +
            " then curl -fsSL https://get.docker.com -o /tmp/get-docker.sh 2>/dev/null" +
            " && sudo sh /tmp/get-docker.sh >/tmp/docker-install.log 2>&1;" +
            " elif command -v wget >/dev/null 2>&1;" +
            " then wget -qO /tmp/get-docker.sh https://get.docker.com 2>/dev/null" +
            " && sudo sh /tmp/get-docker.sh >/tmp/docker-install.log 2>&1;" +
            " else echo INSTALLER_NO_FETCH >/tmp/docker-install.log; fi;" +
            " if ! command -v docker >/dev/null 2>&1;" +
            " then if which apt-get >/dev/null 2>&1;" +
            " then pm=apt-get; si='-yq install'; su='-yq update'; dp='docker.io';" +
            " elif which dnf >/dev/null 2>&1;" +
            " then pm=dnf; si='-yq install'; su='-yq check-update'; dp='docker';" +
            " elif which yum >/dev/null 2>&1;" +
            " then pm=yum; si='-y -q install'; su='-y -q check-update'; dp='docker';" +
            " elif which zypper >/dev/null 2>&1;" +
            " then pm=zypper; si='-nq install'; su='-nq refresh'; dp='docker';" +
            " elif which pacman >/dev/null 2>&1;" +
            " then pm=pacman; si='-S --noconfirm --quiet'; su='-Sup'; dp='docker';" +
            " else echo ERR_NO_PM; exit 0; fi;" +
            " sudo \$pm \$su >>/tmp/docker-install.log 2>&1;" +
            " sudo \$pm \$si \$dp >>/tmp/docker-install.log 2>&1; fi; fi;" +
            " if command -v systemctl >/dev/null 2>&1;" +
            " then sudo systemctl enable --now docker >/dev/null 2>&1; sleep 3;" +
            " if [ \"\$(sudo systemctl is-active docker 2>/dev/null)\" != \"active\" ];" +
            " then sudo systemctl start docker >/dev/null 2>&1; sleep 3; fi; fi;" +
            " if sudo docker --version >/dev/null 2>&1; then echo DOCKER_OK;" +
            " else echo \"--- /tmp/docker-install.log (tail -30) ---\";" +
            " sudo tail -30 /tmp/docker-install.log 2>/dev/null || true;" +
            " echo ERR_DOCKER; fi"

    const val deployMasterDns: String =
        "mkdir -p /tmp/mdns_build && cat > /tmp/mdns_build/Dockerfile << 'EODF'\n" +
            "FROM ubuntu:22.04\n" +
            "ARG DEBIAN_FRONTEND=noninteractive\n" +
            "RUN apt-get update -yq && apt-get install -yq curl openssl ca-certificates\n" +
            "RUN curl -fsSL https://raw.githubusercontent.com/masterking32/MasterDnsVPN" +
            "/main/server_linux_install.sh | bash 2>&1 || true\n" +
            "RUN [ -f /usr/local/bin/masterdnsvpn-server ] || " +
            "(find / -maxdepth 6 -name 'masterdnsvpn-server' -type f 2>/dev/null" +
            " | head -1 | xargs -I{} install -m755 {} /usr/local/bin/ 2>/dev/null)\n" +
            "RUN mkdir -p /etc/masterdnsvpn\n" +
            "EXPOSE 53/udp\n" +
            "CMD [\"/usr/local/bin/masterdnsvpn-server\"]\n" +
            "EODF\n" +
            "sudo docker build --no-cache -t masterdns-ozero /tmp/mdns_build 2>&1" +
            " | tail -3; build_rc=\${PIPESTATUS[0]}; [ \$build_rc -eq 0 ] && echo BUILD_OK || echo ERR_BUILD"

    const val runContainer =
        "sudo docker rm -f masterdns-ozero 2>/dev/null; " +
            "sudo docker volume inspect masterdns-key >/dev/null 2>&1 || " +
            "sudo docker volume create masterdns-key >/dev/null; " +
            "sudo docker run -d --name masterdns-ozero --restart always" +
            " -v masterdns-key:/etc/masterdnsvpn" +
            " -p 53:53/udp masterdns-ozero || { echo ERR_RUN; exit 0; }; " +
            "for i in \$(seq 1 15);" +
            " do sudo docker exec masterdns-ozero true >/dev/null 2>&1 && break;" +
            " sleep 1; done; " +
            "sudo docker exec masterdns-ozero sh -c " +
            "'test -f /etc/masterdnsvpn/encrypt_key.txt || " +
            "(openssl rand -hex 32 > /etc/masterdnsvpn/encrypt_key.txt && " +
            "chmod 600 /etc/masterdnsvpn/encrypt_key.txt && exit 42)'; " +
            "rc=\$?; " +
            "if [ \$rc -eq 42 ]; then sudo docker restart masterdns-ozero >/dev/null 2>&1; fi; " +
            "echo RUN_OK"

    const val openFirewallPort53 =
        "sudo mkdir -p /var/lib/masterdns-ozero 2>/dev/null;" +
            " fw_kind=none;" +
            " if command -v ufw >/dev/null 2>&1 && sudo ufw status 2>/dev/null | grep -q 'Status: active';" +
            " then if ! sudo ufw status numbered 2>/dev/null | grep -qE '\\b53/udp\\b';" +
            " then sudo ufw allow 53/udp >/dev/null 2>&1; fw_kind=ufw; fi; echo FW_UFW_OK;" +
            " elif command -v firewall-cmd >/dev/null 2>&1 && sudo firewall-cmd --state >/dev/null 2>&1;" +
            " then if ! sudo firewall-cmd --query-port=53/udp >/dev/null 2>&1;" +
            " then sudo firewall-cmd --permanent --add-port=53/udp >/dev/null 2>&1" +
            " && sudo firewall-cmd --reload >/dev/null 2>&1; fw_kind=firewalld; fi; echo FW_FIREWALLD_OK;" +
            " elif command -v iptables >/dev/null 2>&1;" +
            " then if ! sudo iptables -C INPUT -p udp --dport 53 -j ACCEPT 2>/dev/null;" +
            " then sudo iptables -A INPUT -p udp --dport 53 -j ACCEPT; fw_kind=iptables;" +
            " if command -v iptables-save >/dev/null 2>&1 && [ -d /etc/iptables ];" +
            " then sudo iptables-save | sudo tee /etc/iptables/rules.v4 >/dev/null 2>&1; fi; fi;" +
            " echo FW_IPTABLES_OK;" +
            " else echo FW_NONE_OK; fi;" +
            " if [ \"\$fw_kind\" != \"none\" ];" +
            " then echo \"\$fw_kind\" | sudo tee /var/lib/masterdns-ozero/fw_opened >/dev/null 2>&1; fi"

    const val readEncryptKey =
        "sudo docker exec masterdns-ozero cat /etc/masterdnsvpn/encrypt_key.txt 2>/dev/null"

    const val removeAll =
        "sudo docker stop masterdns-ozero 2>/dev/null || true;" +
            " sudo docker rm -f masterdns-ozero 2>/dev/null || true;" +
            " sudo docker rmi masterdns-ozero 2>/dev/null || true;" +
            " sudo rm -rf /tmp/mdns_build 2>/dev/null || true;" +
            " if [ -f /var/lib/masterdns-ozero/fw_opened ];" +
            " then fw=\$(sudo cat /var/lib/masterdns-ozero/fw_opened 2>/dev/null);" +
            " case \"\$fw\" in" +
            " ufw) sudo ufw delete allow 53/udp >/dev/null 2>&1 || true;;" +
            " firewalld) sudo firewall-cmd --permanent --remove-port=53/udp >/dev/null 2>&1 || true;" +
            " sudo firewall-cmd --reload >/dev/null 2>&1 || true;;" +
            " iptables) sudo iptables -D INPUT -p udp --dport 53 -j ACCEPT 2>/dev/null || true;" +
            " if command -v iptables-save >/dev/null 2>&1 && [ -d /etc/iptables ];" +
            " then sudo iptables-save | sudo tee /etc/iptables/rules.v4 >/dev/null 2>&1 || true; fi;;" +
            " esac;" +
            " sudo rm -f /var/lib/masterdns-ozero/fw_opened 2>/dev/null || true; fi;" +
            " echo REMOVE_OK"

    const val MARKER_REMOVE_OK = "REMOVE_OK"
    const val MARKER_PORT_BUSY = "PORT_BUSY"
    const val MARKER_PORT_FREE = "PORT_FREE"
    const val MARKER_DOCKER_OK = "DOCKER_OK"
    const val MARKER_BUILD_OK = "BUILD_OK"
    const val MARKER_RUN_OK = "RUN_OK"
    const val MARKER_ERR_NO_PM = "ERR_NO_PM"
    const val MARKER_ERR_DOCKER = "ERR_DOCKER"
    const val MARKER_ERR_BUILD = "ERR_BUILD"
    const val MARKER_ERR_RUN = "ERR_RUN"
    const val MARKER_ERR_DPKG_LOCKED = "ERR_DPKG_LOCKED"
    const val MARKER_SUDO_OK = "SUDO_OK"
    const val MARKER_ERR_SUDO_NOT_INSTALLED = "ERR_SUDO_NOT_INSTALLED"
    const val MARKER_ERR_SUDO_PWD_REQUIRED = "ERR_SUDO_PWD_REQUIRED"
    const val MARKER_ERR_SUDO_NOT_ALLOWED = "ERR_SUDO_NOT_ALLOWED"
    const val MARKER_ERR_SUDO_NO_HOME = "ERR_SUDO_NO_HOME"
    const val MARKER_ERR_SUDO_NOT_IN_GROUP = "ERR_SUDO_NOT_IN_GROUP"
    const val MARKER_FW_UFW_OK = "FW_UFW_OK"
    const val MARKER_FW_FIREWALLD_OK = "FW_FIREWALLD_OK"
    const val MARKER_FW_IPTABLES_OK = "FW_IPTABLES_OK"
    const val MARKER_FW_NONE_OK = "FW_NONE_OK"

    const val MIN_FREE_RAM_MB = 256
    const val MIN_FREE_DISK_MB = 500
}
