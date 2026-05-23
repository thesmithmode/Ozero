package ru.ozero.enginemasterdns.deploy

internal object MasterDnsDockerScripts {

    val checkPort53 =
        "ss -uln 2>/dev/null | grep -q ':53 ' && echo PORT_BUSY || echo PORT_FREE"

    val checkResources =
        "echo \$(free -m 2>/dev/null | awk 'NR==2{print \$7}')" +
            " \$(df -m / 2>/dev/null | awk 'NR==2{print \$4}')"

    val installDocker: String =
        "if which apt-get > /dev/null 2>&1; then pm=\$(which apt-get);" +
            " si=\"-yq install\"; su=\"-yq update\"; dp=\"docker.io\";" +
            " elif which dnf > /dev/null 2>&1; then pm=\$(which dnf);" +
            " si=\"-yq install\"; su=\"-yq check-update\"; dp=\"docker\";" +
            " elif which yum > /dev/null 2>&1; then pm=\$(which yum);" +
            " si=\"-y -q install\"; su=\"-y -q check-update\"; dp=\"docker\";" +
            " elif which zypper > /dev/null 2>&1; then pm=\$(which zypper);" +
            " si=\"-nq install\"; su=\"-nq refresh\"; dp=\"docker\";" +
            " elif which pacman > /dev/null 2>&1; then pm=\$(which pacman);" +
            " si=\"-S --noconfirm --quiet\"; su=\"-Sup\"; dp=\"docker\";" +
            " else echo ERR_NO_PM; exit 1; fi;" +
            " if [ \"\$(which apt-get 2>/dev/null)\" != \"\" ];" +
            " then export DEBIAN_FRONTEND=noninteractive; fi;" +
            " if ! command -v docker > /dev/null 2>&1;" +
            " then sudo \$pm \$su; sudo \$pm \$si \$dp;" +
            " sleep 3; sudo systemctl enable --now docker; sleep 3; fi;" +
            " if [ \"\$(sudo systemctl is-active docker 2>/dev/null)\" != \"active\" ];" +
            " then sudo systemctl start docker; sleep 3; fi;" +
            " docker --version > /dev/null 2>&1 && echo DOCKER_OK || echo ERR_DOCKER"

    val deployMasterDns: String =
        "mkdir -p /tmp/mdns_build && cat > /tmp/mdns_build/Dockerfile << 'EODF'\n" +
            "FROM ubuntu:22.04\n" +
            "ARG DEBIAN_FRONTEND=noninteractive\n" +
            "RUN apt-get update -yq && apt-get install -yq curl openssl ca-certificates\n" +
            "RUN curl -fsSL https://raw.githubusercontent.com/masterking32/MasterDnsVPN" +
            "/main/server_linux_install.sh | bash 2>&1 || true\n" +
            "RUN [ -f /usr/local/bin/masterdnsvpn-server ] || " +
            "(find / -maxdepth 6 -name 'masterdnsvpn-server' -type f 2>/dev/null" +
            " | head -1 | xargs -I{} install -m755 {} /usr/local/bin/ 2>/dev/null)\n" +
            "RUN mkdir -p /etc/masterdnsvpn && openssl rand -hex 32" +
            " > /etc/masterdnsvpn/encrypt_key.txt" +
            " && chmod 600 /etc/masterdnsvpn/encrypt_key.txt\n" +
            "EXPOSE 53/udp\n" +
            "CMD [\"/usr/local/bin/masterdnsvpn-server\"]\n" +
            "EODF\n" +
            "sudo docker build --no-cache -t masterdns-ozero /tmp/mdns_build 2>&1" +
            " | tail -3 && echo BUILD_OK || echo ERR_BUILD"

    val runContainer =
        "sudo docker rm -f masterdns-ozero 2>/dev/null; " +
            "sudo docker run -d --name masterdns-ozero --restart always" +
            " -p 53:53/udp masterdns-ozero && echo RUN_OK || echo ERR_RUN"

    val readEncryptKey =
        "sudo docker exec masterdns-ozero cat /etc/masterdnsvpn/encrypt_key.txt 2>/dev/null"

    const val MARKER_PORT_BUSY = "PORT_BUSY"
    const val MARKER_PORT_FREE = "PORT_FREE"
    const val MARKER_DOCKER_OK = "DOCKER_OK"
    const val MARKER_BUILD_OK = "BUILD_OK"
    const val MARKER_RUN_OK = "RUN_OK"
    const val MARKER_ERR_NO_PM = "ERR_NO_PM"
    const val MARKER_ERR_DOCKER = "ERR_DOCKER"
    const val MARKER_ERR_BUILD = "ERR_BUILD"
    const val MARKER_ERR_RUN = "ERR_RUN"

    const val MIN_FREE_RAM_MB = 256
    const val MIN_FREE_DISK_MB = 500
}
