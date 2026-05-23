package ru.ozero.enginemasterdns.deploy

internal object MasterDnsDockerScripts {

    const val checkPort53 =
        "if sudo docker ps --format '{{.Names}}' 2>/dev/null | grep -q '^masterdns-ozero$';" +
            " then echo PORT_FREE;" +
            " else ss -uln 2>/dev/null | grep -q ':53 ' && echo PORT_BUSY || echo PORT_FREE; fi"

    const val checkResources =
        "echo \$(free -m 2>/dev/null | awk 'NR==2{print \$7}')" +
            " \$(df -m / 2>/dev/null | awk 'NR==2{print \$4}')"

    const val installDocker: String =
        "if which apt-get > /dev/null 2>&1; then pm=\$(which apt-get);" +
            " si=\"-yq install\"; su=\"-yq update\"; dp=\"docker.io\"; is_apt=1;" +
            " elif which dnf > /dev/null 2>&1; then pm=\$(which dnf);" +
            " si=\"-yq install\"; su=\"-yq check-update\"; dp=\"docker\"; is_apt=0;" +
            " elif which yum > /dev/null 2>&1; then pm=\$(which yum);" +
            " si=\"-y -q install\"; su=\"-y -q check-update\"; dp=\"docker\"; is_apt=0;" +
            " elif which zypper > /dev/null 2>&1; then pm=\$(which zypper);" +
            " si=\"-nq install\"; su=\"-nq refresh\"; dp=\"docker\"; is_apt=0;" +
            " elif which pacman > /dev/null 2>&1; then pm=\$(which pacman);" +
            " si=\"-S --noconfirm --quiet\"; su=\"-Sup\"; dp=\"docker\"; is_apt=0;" +
            " else echo ERR_NO_PM; exit 1; fi;" +
            " if [ \"\$is_apt\" = \"1\" ]; then export DEBIAN_FRONTEND=noninteractive;" +
            " locked=0; for i in \$(seq 1 30);" +
            " do sudo fuser /var/lib/dpkg/lock-frontend >/dev/null 2>&1 || { locked=0; break; };" +
            " locked=1; sleep 10; done;" +
            " if [ \"\$locked\" = \"1\" ]; then echo ERR_DPKG_LOCKED; exit 0; fi; fi;" +
            " if ! command -v docker > /dev/null 2>&1;" +
            " then sudo \$pm \$su; sudo \$pm \$si \$dp;" +
            " sleep 3; sudo systemctl enable --now docker; sleep 3; fi;" +
            " if [ \"\$(sudo systemctl is-active docker 2>/dev/null)\" != \"active\" ];" +
            " then sudo systemctl start docker; sleep 3; fi;" +
            " docker --version > /dev/null 2>&1 && echo DOCKER_OK || echo ERR_DOCKER"

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
            "sleep 2; " +
            "sudo docker exec masterdns-ozero sh -c " +
            "'test -f /etc/masterdnsvpn/encrypt_key.txt || " +
            "(openssl rand -hex 32 > /etc/masterdnsvpn/encrypt_key.txt && " +
            "chmod 600 /etc/masterdnsvpn/encrypt_key.txt && exit 42)'; " +
            "rc=\$?; " +
            "if [ \$rc -eq 42 ]; then sudo docker restart masterdns-ozero >/dev/null 2>&1; fi; " +
            "echo RUN_OK"

    const val readEncryptKey =
        "sudo docker exec masterdns-ozero cat /etc/masterdnsvpn/encrypt_key.txt 2>/dev/null"

    const val removeAll =
        "sudo docker stop masterdns-ozero 2>/dev/null || true;" +
            " sudo docker rm -f masterdns-ozero 2>/dev/null || true;" +
            " sudo docker rmi masterdns-ozero 2>/dev/null || true;" +
            " sudo rm -rf /tmp/mdns_build 2>/dev/null || true;" +
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

    const val MIN_FREE_RAM_MB = 256
    const val MIN_FREE_DISK_MB = 500
}
