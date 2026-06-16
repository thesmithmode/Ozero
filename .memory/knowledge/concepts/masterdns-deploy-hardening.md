---
title: "MasterDNS SSH Deploy Hardening: Phase A-F Patterns"
aliases: [masterdns-hardening, masterdns-deploy-phases, ssh-deploy-hardening]
tags: [masterdns, ssh, deploy, docker, security, hardening]
sources:
  - "daily/2026-05-23.md"
created: 2026-05-23
updated: 2026-06-12
---

# MasterDNS SSH Deploy Hardening: Phase A-F Patterns

After initial MasterDNS SSH+Docker deploy was working, a structured hardening pass (Phases A-F) was applied based on analysis of Amnezia's reference SSH deploy implementation (`AmneziaVPN` remote server setup). Six classes of problems were identified and fixed or planned, covering idempotency, security, reliability, and process management.

## Key Points

- `checkPort53` bug: `ss -uln | grep ':53'` returns BUSY when OUR OWN container is already running → re-deploy blocked. Fix: check `docker ps | grep '^masterdns-ozero$'` first; if running → skip port check (PORT_FREE)
- **Firewall marker file** pattern: write `/var/lib/masterdns-ozero/fw_opened` with firewall kind after opening port 53/udp. `removeAll` reads marker and closes only what was opened (no collateral damage to user's existing firewall rules)
- `dpkg-lock` polling: up to 30 × 10s = 5 min wait for unattended-upgrades before `apt-get install`. Prevents `E: Could not get lock /var/lib/dpkg/lock-frontend` failures on stock Ubuntu servers
- **Container readiness**: poll `docker exec <container> true` up to 15s × 1s instead of fixed `sleep 2` — race-free key extraction on slow VPS
- **Sudo error codes**: 5 codes (sudo_not_installed, sudo_requires_password, docker_not_installed, docker_permission_denied, command_not_found) matching Amnezia's 205-210 numeric codes in spirit
- **sshj R8/proguard**: `net.i2p.crypto.eddsa.EdDSAEngine` (sshj dependency) references `sun.security.x509.X509Key` — desktop-only JDK class. Android R8 minify crashes at startup. Fix: `dontwarn sun.security.x509.**` in proguard-rules.pro

## Details

### Re-deploy Idempotency (Phase A)

The original `checkPort53` ran `ss -uln | grep ':53'` to detect if port 53 was occupied before starting the container. This correctly detected OTHER processes using port 53 (dnsmasq, systemd-resolved). But when our own MasterDNS container was already running and using port 53, the check also returned BUSY → the re-deploy flow returned `Port53Busy` error instead of proceeding.

Fix: prefix the check with a Docker container presence query:
```bash
if docker ps --format '{{.Names}}' | grep -q '^masterdns-ozero$'; then
    echo "CONTAINER_RUNNING"  # skip port check, already ours
else
    # ... ss -uln check ...
fi
```

This makes re-deploy idempotent: if our container is running, proceed as if port is free (we'll replace the container).

### Firewall Marker Pattern (Phase B)

The deploy opens UDP port 53 via `ufw allow 53/udp` or `iptables -A INPUT -p udp --dport 53 -j ACCEPT` depending on what's available. Without tracking what was opened:
- `removeAll` would close the rule even if the user had manually added it before deploy
- `removeAll` would fail if a different firewall tool was used

Solution: write a marker file `/var/lib/masterdns-ozero/fw_opened` containing the firewall kind (`ufw` or `iptables`) after a successful rule insertion. `removeAll` script reads the marker → applies the matching close command → deletes the marker. If marker absent, skip firewall cleanup.

### Amnezia Reference Analysis

Key observations from Amnezia's SSH deploy:
- **7-phase deploy sequence**: cleanup → build → copy secrets → start → configure → verify → notify
- `chmod 600` on all secrets before copy — Amnezia enforces this; Ozero adopted the pattern
- **5 sudo error codes** (205-210): `SUDO_NOT_FOUND`, `SUDO_NO_PASS`, `DOCKER_NOT_FOUND`, `DOCKER_NO_PERM`, `UNKNOWN_ERROR`
- Amnezia does NOT handle SSH reconnect — each command is a fresh channel (same as Ozero's `Flow { emit(exec(cmd)) }` pattern)
- Amnezia firewall is NOT idempotent: `iptables` rules are lost after reboot (no `iptables-save` or `ufw` integration). Ozero's marker pattern is better.
- Root-user skip: Amnezia skips sudo check if `uid == 0` — copied pattern (deploy works without sudo when connected as root)

### R8/Proguard for sshj (Phase C)

Adding `com.hierynomus:sshj` to the Android build introduced a transitive dependency chain:
```
sshj → net.i2p.crypto:eddsa → EdDSAEngine.java → sun.security.x509.X509Key (import)
```

`sun.security.x509.X509Key` is a JDK internal class (not on Android). R8 minify found the reference and crashed at startup with `ClassNotFoundException`. The class is only needed on desktop JDK (where `X509Key` is available); Android's Bouncy Castle does not need it.

Fix in `proguard-rules.pro`:
```
-dontwarn sun.security.x509.**
-dontwarn sun.security.**
```

Pattern: any new dependency with EdDSA, JCE, or JDK cryptography → check for `sun.*` references before merge with R8 enabled. This is the same class of problem as the Proguard release drift feedback.

### Undeploy Feature (Phase A extension)

`MasterDnsServerDeployer` gained an `undeploy()` method that runs the inverse of deploy:

1. Connect + authenticate with stored credentials
2. Emit `MasterDnsDeployState.Removing`
3. Execute `removeAll` script: `docker stop masterdns-ozero && docker rm masterdns-ozero && docker rmi masterdns-ozero && rm -rf /tmp/mdns_build`
4. Emit `Removed`; clear credentials from memory

The `removeAll` script does NOT touch `/etc/docker`, system packages, or firewall rules beyond the marker-tracked rule. `ViewModel.onUndeployClick` uses the same `deployJob` guard to prevent concurrent deploy+undeploy races.

UI consequence: `DeployDoneRow` gains [Переустановить] + [Удалить сервер] buttons. `DeployRemovedRow` confirms removal. `isDeploying` predicate expanded to include `Removing` state.

### Phase D (Deferred)

SSH key file authentication (instead of password) requires multi-line input in settings UI. Deferred to v0.3.x due to UI scope.

### Phase E (TOFU — Planned)

`PromiscuousVerifier` in sshj accepts any host key — MITM vulnerable. Fix: Trust-On-First-Use (TOFU) via `SharedPreferences` storing `host:port → Base64(publicKey)`. On second connect, verify stored key matches. Planned but not implemented as of 2026-05-23.

### Phase F (exec stderr logging)

Non-zero SSH exec exit codes previously threw exceptions silently swallowed by the `runCatching` wrapper. Fix: non-zero exit → `PersistentLoggers.warn` with command + exit code + first 200 chars of stderr output. Silent failures now visible in ozero.log without crashing the deploy flow.

### Auto-Setup After Deploy

`MasterDnsSettingsViewModel.onDeployClick` auto-configures the engine after a successful deploy. After emitting `Done`:
1. Calls `setResolvers(["${host}:53"])` — fills resolvers field with the deployed server
2. Calls `setEnabled(true)` — activates the engine

Zero manual configuration required post-deploy. Sentinel-tested in `MasterDnsSettingsViewModelTest`.

### Amnezia Patterns Adopted vs Not Adopted

Adopted from Amnezia SSH deploy reference:
- Root-user skip: skip sudo-check entirely when SSH user is `root` (uid==0)
- `chmod 600` on all secrets before copy to server
- Cleanup script runs before build (not after) — cleans stale artifacts from prior deploy
- 5 sudo error codes (205-210 in spirit): `sudo_not_installed`, `auth_failed`, `no_permission`, `command_failed`, `unknown_error`

NOT adopted (Amnezia bugs):
- `iptables` rules without `iptables-save` — lose rules after reboot. Ozero uses `ufw` (persistent) + firewall marker file pattern instead.
- SSH reconnect on failure — Amnezia opens a new channel per command. Ozero cancels the Flow on disconnect (cleaner resource management).

## Related Concepts

- [[concepts/engine-masterdns]] — Base subprocess engine architecture; hardening applies on top of the deploy flow
- [[concepts/proguard-release-drift]] — R8 minify catching JDK-only dependencies; sshj/EdDSA follows the same pattern
- [[concepts/persistent-logger-accumulation-trap]] — Deploy errors use `PersistentLoggers.warn` for SSH exec non-zero exits; stdout stays as `Log.w`
- [[concepts/ci-workflow-discipline]] — CI regression from deploy code adding `Log.w` without whitelist in `LoggingContractTest`

## Sources

- [[daily/2026-05-23.md]] — Session 17:25: Amnezia SSH deploy analysis (7-phase, chmod 600, 5 sudo codes, no reconnect, firewall not idempotent); R8 crash traced to sshj→EdDSA→sun.security.x509.X509Key; proguard fix. Session 15:00: checkPort53 idempotency bug (our container = false BUSY); firewall marker file pattern; dpkg-lock polling 30×10s; container readiness polling; MasterDNS undeploy full implementation (Removing/Removed states, removeAll script, ViewModel guard). Session "MasterDNS Hardening Phase A-F": commits include 3071d131; Phase D keyfile auth deferred to v0.3.x; Phase E TOFU planned
