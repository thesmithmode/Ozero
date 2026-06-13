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
        masterdns_state=${'$'}(sudo docker inspect -f '{{.State.Status}}' masterdns-ozero 2>/dev/null || true)
        case "${'$'}masterdns_state" in
            created|exited|dead|restarting|paused) sudo docker rm -f masterdns-ozero 2>/dev/null || true ;;
            running) echo PORT_FREE; exit 0 ;;
        esac
        docker_conflict() { { sudo docker ps --format '{{.Names}}|{{.Ports}}' 2>/dev/null; sudo docker ps -a --filter status=created --format '{{.Names}}|{{.Ports}}' 2>/dev/null; } | awk -F'|' '${'$'}1 != "masterdns-ozero" { split(${'$'}2, ports, ","); for (i in ports) { p=ports[i]; gsub(/^ +| +${'$'}/, "", p); if (p ~ /->53\/udp/) { proto=p; sub(/^.*->53\//, "", proto); sub(/ .*/, "", proto); host=p; sub(/->.*${'$'}/, "", host); addr=host; sub(/:53${'$'}/, "", addr); gsub(/^\[/, "", addr); gsub(/\]${'$'}/, "", addr); if (addr == "") addr="0.0.0.0"; print "PORT_BUSY|proto=" proto "|addr=" addr "|owner=docker:" ${'$'}1; exit } } } }'; }
        ss_conflict() { proto="${'$'}1"; flags="${'$'}2"; sudo ss -H "${'$'}flags" 2>/dev/null | awk -v proto="${'$'}proto" 'function clean(addr) { gsub(/^\[/, "", addr); gsub(/\]${'$'}/, "", addr); sub(/%.*${'$'}/, "", addr); return addr } function ignored(addr) { return addr == "127.0.0.53" || addr == "127.0.0.54" || addr == "127.0.0.1" || addr == "::1" } { local=""; for (i = 1; i <= NF; i++) if (${'$'}i ~ /:53${'$'}/ || ${'$'}i ~ /\]:53${'$'}/) { local=${'$'}i; break } if (local == "") next; addr=local; sub(/:53${'$'}/, "", addr); addr=clean(addr); if (!ignored(addr)) { name="unknown"; if (match(${'$'}0, /"[^"]+"/)) name=substr(${'$'}0, RSTART + 1, RLENGTH - 2); print "PORT_BUSY|proto=" proto "|addr=" addr "|owner=" name; exit } }'; }
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
        if [ "${'$'}bind_rc" != "0" ]; then busy="${'$'}(docker_conflict)"; [ -n "${'$'}busy" ] && { echo "${'$'}busy"; exit 0; }; busy="${'$'}(ss_conflict udp -lunp)"; [ -n "${'$'}busy" ] && { echo "${'$'}busy"; exit 0; }; echo "PORT_BUSY|proto=udp|addr=0.0.0.0:53|owner=bind_probe:exit_${'$'}bind_rc"; exit 0; fi
        busy="${'$'}(docker_conflict)"; [ -n "${'$'}busy" ] && { echo "${'$'}busy"; exit 0; }
        busy="${'$'}(ss_conflict udp -lunp)"; [ -n "${'$'}busy" ] && { echo "${'$'}busy"; exit 0; }
        echo PORT_FREE
        """.trimIndent()

    val checkAmneziaDns53: String =
        """
        if ! sudo docker ps --format '{{.Names}}' 2>/dev/null | grep -qx 'amnezia-dns'; then echo AMNEZIA_DNS_NOT_FOUND; exit 0; fi
        inspect=${'$'}(sudo docker inspect amnezia-dns 2>/dev/null); rc=${'$'}?
        if [ ${'$'}rc -ne 0 ]; then echo AMNEZIA_DNS_NOT_FOUND; exit 0; fi
        name=${'$'}(printf '%s' "${'$'}inspect" | awk -F'\"' '/\"Name\"/ {print ${'$'}4; exit}')
        if [ "${'$'}name" != "/amnezia-dns" ]; then echo AMNEZIA_DNS_NOT_FOUND; exit 0; fi
        block=${'$'}(printf '%s' "${'$'}inspect" | awk -v proto='"53/udp"' 'index(${'$'}0,proto){seen=1} seen{print} seen && index(${'$'}0,"]"){exit}')
        if printf '%s' "${'$'}block" | grep -q '"HostPort": *"53"'; then
            addr=${'$'}(printf '%s' "${'$'}block" | awk -F'\"' '/HostIp/ {print ${'$'}4; exit}')
            [ -n "${'$'}addr" ] || addr=0.0.0.0
            echo "AMNEZIA_DNS_CONFLICT|proto=udp|addr=${'$'}addr"
            exit 0
        fi
        echo AMNEZIA_DNS_NOT_FOUND
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

    val deployMasterDns: String =
        """
        sudo rm -rf /tmp/mdns_build 2>/dev/null || true
        sudo docker rmi masterdns-ozero 2>/dev/null || true
        mkdir -p /tmp/mdns_build && cat > /tmp/mdns_build/Dockerfile << 'EODF'
        FROM ubuntu:22.04
        ARG MASTERDNS_RELEASE_TAG=v2026.05.10.180256-27c7e11
        ARG DEBIAN_FRONTEND=noninteractive
        RUN apt-get update -yq && apt-get install -yq curl openssl ca-certificates findutils tar gzip unzip
        RUN set -eu; \
            mkdir -p /tmp/masterdns-release-dir; \
            arch=${'$'}(uname -m); \
            asset_arch=""; \
            case "${'$'}arch" in \
                x86_64|amd64) asset_arch=AMD64;; \
                aarch64|arm64) asset_arch=ARM64;; \
                armv7l|armv7*) asset_arch=ARMv7;; \
            esac; \
            release_api="https://api.github.com/repos/masterking32/MasterDnsVPN/releases/tags/${'$'}{MASTERDNS_RELEASE_TAG}"; \
            urls=${'$'}(curl -fsSL "${'$'}release_api" 2>/tmp/masterdns-release-api.err \
                | tr ',' '\n' \
                | sed -n 's/.*"browser_download_url": *"\([^"]*\)".*/\1/p' \
                | grep -i 'MasterDnsVPN_Server_Linux' || true); \
            url=""; \
            if [ -n "${'$'}asset_arch" ]; then \
                url=${'$'}(printf '%s\n' "${'$'}urls" | grep -Ei "MasterDnsVPN_Server_Linux.*${'$'}asset_arch" | head -1 || true); \
            fi; \
            if [ -z "${'$'}url" ]; then \
                url=${'$'}(printf '%s\n' "${'$'}urls" | head -1 || true); \
            fi; \
            if [ -n "${'$'}url" ]; then \
                asset_name=${'$'}(basename "${'$'}url" | sed 's/[?].*${'$'}//'); \
                if curl -fL "${'$'}url" -o "/tmp/${'$'}asset_name"; then \
                    case "${'$'}asset_name" in \
                        *.tar.gz|*.tgz) tar -xzf "/tmp/${'$'}asset_name" -C /tmp/masterdns-release-dir || true;; \
                        *.zip) unzip -q "/tmp/${'$'}asset_name" -d /tmp/masterdns-release-dir || true;; \
                        *) cp "/tmp/${'$'}asset_name" "/tmp/masterdns-release-dir/${'$'}asset_name" || true;; \
                    esac; \
                fi; \
            fi; \
            candidate=""; \
            candidate=${'$'}(find /tmp/masterdns-release-dir -maxdepth 8 -type f \
                \( -name 'MasterDnsVPN_Server_Linux*_v*' -o -name 'MasterDnsVPN_Server_Linux*' -o -name 'masterdnsvpn-server' \) \
                2>/dev/null | head -1 || true); \
            if [ -z "${'$'}candidate" ]; then \
                if curl -fsSL https://raw.githubusercontent.com/masterking32/MasterDnsVPN/main/server_linux_install.sh \
                    -o /tmp/masterdns-install.sh; \
                then \
                    install_rc=0; \
                    bash /tmp/masterdns-install.sh 2>&1 || install_rc=${'$'}?; \
                else \
                    install_rc=127; \
                fi; \
                candidate=${'$'}(find / -maxdepth 8 -type f \
                    \( -name 'MasterDnsVPN_Server_Linux*_v*' -o -name 'MasterDnsVPN_Server_Linux*' -o -name 'masterdnsvpn-server' \) \
                    2>/dev/null | head -1 || true); \
            fi; \
            if [ -z "${'$'}candidate" ]; then \
                api_error=${'$'}(cat /tmp/masterdns-release-api.err 2>/dev/null | tr '\n\r' '  ' | cut -c1-300 || true); \
                missing_candidates=${'$'}(find /tmp/masterdns-release-dir /usr/local/bin /usr/bin /opt /tmp -maxdepth 8 -type f \
                    \( -name 'MasterDnsVPN*' -o -name '*masterdns*' \) \
                    2>/dev/null | head -20 | tr '\n' ',' | sed 's/,${'$'}//' || true); \
                [ -n "${'$'}missing_candidates" ] || missing_candidates=none; \
                echo "ERR_BUILD_BIN_MISSING|release_tag=${'$'}{MASTERDNS_RELEASE_TAG}|release_url=${'$'}{url:-none}|api_error=${'$'}api_error|candidates=${'$'}missing_candidates"; \
                exit 42; \
            fi; \
            if [ "${'$'}candidate" != "/usr/local/bin/masterdnsvpn-server" ]; then \
                install -m755 "${'$'}candidate" /usr/local/bin/masterdnsvpn-server; \
            else \
                chmod 755 /usr/local/bin/masterdnsvpn-server; \
            fi; \
            test -x /usr/local/bin/masterdnsvpn-server; \
            if [ "${'$'}{install_rc:-0}" -ne 0 ]; then \
                echo "INSTALL_NONZERO_BUT_BINARY_FOUND|exit=${'$'}{install_rc:-0}"; \
            fi
        RUN mkdir -p /etc/masterdnsvpn
        EXPOSE 53/udp
        CMD ["/usr/local/bin/masterdnsvpn-server"]
        EODF
        build_log=/tmp/mdns_build/docker-build.log
        sudo docker build --no-cache -t masterdns-ozero /tmp/mdns_build > "${'$'}build_log" 2>&1
        build_rc=${'$'}?
        echo "--- docker-build.log head -40 ---"
        head -40 "${'$'}build_log" 2>/dev/null || true
        echo "--- docker-build.log tail -80 ---"
        tail -80 "${'$'}build_log" 2>/dev/null || true
        if [ ${'$'}build_rc -eq 0 ]; then
            echo BUILD_OK
        elif grep -q ERR_BUILD_BIN_MISSING "${'$'}build_log" 2>/dev/null; then
            diag=${'$'}(
                grep ERR_BUILD_BIN_MISSING "${'$'}build_log" 2>/dev/null |
                    tail -1 |
                    sed 's/^.*ERR_BUILD_BIN_MISSING/ERR_BUILD_BIN_MISSING/' |
                    tr '\n\r' '  ' |
                    cut -c1-1000
            )
            echo "ERR_BUILD|reason=bin_missing|${'$'}diag"
        else
            echo ERR_BUILD
        fi
        """.trimIndent()

    val runContainer: String =
        """
        run_diag() {
            phase="${'$'}1"
            rc="${'$'}2"
            err="${'$'}3"
            state="${'$'}(
                sudo docker inspect -f 'state={{.State.Status}}|exit={{.State.ExitCode}}|error={{.State.Error}}' \
                    masterdns-ozero 2>/dev/null || true
            )"
            logs="${'$'}(
                sudo docker logs --tail 20 masterdns-ozero 2>&1 |
                    tr '\n\r' '  ' |
                    cut -c1-1000 || true
            )"
            err_line="${'$'}(printf '%s' "${'$'}err" | tr '\n\r' '  ' | cut -c1-1000)"
            printf 'ERR_RUN|phase=%s|exit=%s|%s|error=%s|logs=%s\n' \
                "${'$'}phase" "${'$'}rc" "${'$'}state" "${'$'}err_line" "${'$'}logs"
        }
        state=${'$'}(sudo docker inspect -f '{{.State.Status}}' masterdns-ozero 2>/dev/null || true)
        case "${'$'}state" in
            created|exited|dead|running|restarting|paused)
                sudo docker rm -f masterdns-ozero 2>/dev/null || true
                ;;
        esac
        sudo docker volume inspect masterdns-key >/dev/null 2>&1 ||
            sudo docker volume create masterdns-key >/dev/null
        run_out=${'$'}(
            sudo docker run -d --name masterdns-ozero --restart always \
                -v masterdns-key:/etc/masterdnsvpn \
                -p 53:53/udp masterdns-ozero 2>&1
        )
        run_rc=${'$'}?
        if [ ${'$'}run_rc -ne 0 ]; then run_diag docker_run ${'$'}run_rc "${'$'}run_out"; exit 0; fi
        ready=0
        for i in ${'$'}(seq 1 15); do
            sudo docker exec masterdns-ozero true >/dev/null 2>&1 && { ready=1; break; }
            sleep 1
        done
        if [ ${'$'}ready -ne 1 ]; then run_diag readiness 1 "container did not become ready"; exit 0; fi
        key_out=${'$'}(
            sudo docker exec masterdns-ozero sh -c \
                'test -f /etc/masterdnsvpn/encrypt_key.txt || \
                (openssl rand -hex 32 > /etc/masterdnsvpn/encrypt_key.txt && \
                chmod 600 /etc/masterdnsvpn/encrypt_key.txt && exit 42)' 2>&1
        )
        rc=${'$'}?
        if [ ${'$'}rc -ne 0 ] && [ ${'$'}rc -ne 42 ]; then run_diag key_init ${'$'}rc "${'$'}key_out"; exit 0; fi
        if [ ${'$'}rc -eq 42 ]; then
            sudo docker restart masterdns-ozero >/dev/null 2>&1 || {
                restart_rc=${'$'}?
                run_diag restart ${'$'}restart_rc "docker restart failed"
                exit 0
            }
        fi
        echo RUN_OK
        """.trimIndent()

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
        "for i in \$(seq 1 10); do" +
            " key=\$(sudo docker exec masterdns-ozero cat /etc/masterdnsvpn/encrypt_key.txt 2>/dev/null || true);" +
            " if [ -n \"\$key\" ]; then printf '%s\\n' \"\$key\"; exit 0; fi;" +
            " sleep 1;" +
            " done"

    const val removeAmneziaDnsOnly =
        "inspect=\$(sudo docker inspect amnezia-dns 2>/dev/null); rc=\$?;" +
            " if [ \$rc -ne 0 ]; then echo AMNEZIA_DNS_NOT_FOUND; exit 0; fi;" +
            " name=\$(printf '%s' \"\$inspect\" | awk -F'\\\"' '/\\\"Name\\\"/ {print \$4; exit}');" +
            " if [ \"\$name\" != \"/amnezia-dns\" ]; then echo AMNEZIA_DNS_NOT_FOUND; exit 0; fi;" +
            " sudo docker stop amnezia-dns 2>/dev/null || { echo AMNEZIA_DNS_REMOVE_FAILED; exit 0; };" +
            " sudo docker rm amnezia-dns 2>/dev/null || { echo AMNEZIA_DNS_REMOVE_FAILED; exit 0; };" +
            " echo AMNEZIA_DNS_REMOVED"

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
    const val MARKER_AMNEZIA_DNS_CONFLICT = "AMNEZIA_DNS_CONFLICT"
    const val MARKER_AMNEZIA_DNS_NOT_FOUND = "AMNEZIA_DNS_NOT_FOUND"
    const val MARKER_AMNEZIA_DNS_REMOVED = "AMNEZIA_DNS_REMOVED"
    const val MARKER_AMNEZIA_DNS_REMOVE_FAILED = "AMNEZIA_DNS_REMOVE_FAILED"
    const val MARKER_PORT_BUSY = "PORT_BUSY"
    const val MARKER_PORT_FREE = "PORT_FREE"
    const val MARKER_DOCKER_OK = "DOCKER_OK"
    const val MARKER_BUILD_OK = "BUILD_OK"
    const val MARKER_RUN_OK = "RUN_OK"
    const val MARKER_ERR_NO_PM = "ERR_NO_PM"
    const val MARKER_ERR_DOCKER = "ERR_DOCKER"
    const val MARKER_ERR_BUILD = "ERR_BUILD"
    const val MARKER_ERR_BUILD_BIN_MISSING = "ERR_BUILD_BIN_MISSING"
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
