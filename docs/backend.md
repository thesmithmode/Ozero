# Ozero subscription backend

## 1. Назначение
Единый источник актуальных рабочих серверов для клиентов Ozero. Клиент приложения скачивает `servers.json` + `servers.json.sig`, верифицирует Ed25519, использует серверы из пула.

## 2. Стек
- Go 1.22+
- systemd (сервис `ozero-sub.service`)
- Caddy (reverse-proxy + HTTPS + HSTS)
- cron: обновление `servers.json` раз в час из `servers.yaml`
- Ed25519 через `crypto/ed25519` stdlib
- YAML parser `gopkg.in/yaml.v3`

## 3. Архитектура
```
[admin push via git] → servers.yaml (приватный repo)
                             ↓
                     cron (1h): fetch → validate → sign → publish
                             ↓
                  /var/www/ozero/servers.json (+ .sig)
                             ↓
                        Caddy (HTTPS)
                             ↓
          https://sub.ozero.app/v1/servers.json[.sig]
                             ↓
                    Ozero Android клиент
```

## 4. Формат servers.yaml
```yaml
version: 12
updated: 2026-04-24T12:00:00Z
servers:
  - id: "ru-entry-01"
    country: "RU"
    role: "entry"           # entry | exit | single
    protocol: "vless"       # vless|hysteria2|amnezia|naive|trojan
    uri: "vless://UUID@..."
    port: 443
    tls_fingerprint: "chrome"
    reality_pub: "..."
    alive_check_url: "https://..."
    priority: 10
```

## 5. Подпись
- Canonical JSON (RFC 8785 JCS) `servers.json`
- `servers.json.sig` — 64 байта Ed25519 в чистом бинарном файле
- Приватный ключ SK — на backend-сервере (HSM/TPM если возможно, иначе encrypted at rest, unlock при systemd start через systemd-credentials)
- Публичный ключ хардкоден в APK

## 6. Deploy

### Требования
- Debian 12 / Ubuntu 22.04 LTS
- Домен `sub.ozero.app` с валидным DNS A/AAAA
- Порт 443 открыт

### Шаги
```bash
# 1. Caddy install
sudo apt install caddy

# 2. Go service
sudo useradd -r -s /bin/false ozero-sub
sudo mkdir -p /opt/ozero-sub /var/www/ozero /etc/ozero-sub
sudo cp ozero-sub /opt/ozero-sub/
sudo cp ozero-sub.service /etc/systemd/system/
sudo systemctl enable --now ozero-sub
sudo systemctl enable --now ozero-sub-cron.timer
```

### Caddyfile
```
sub.ozero.app {
    encode gzip zstd
    header {
        Strict-Transport-Security "max-age=63072000; includeSubDomains; preload"
        X-Content-Type-Options "nosniff"
        Referrer-Policy "no-referrer"
    }
    file_server {
        root /var/www/ozero
    }
}
```

### systemd service
`/etc/systemd/system/ozero-sub.service`:
```ini
[Unit]
Description=Ozero subscription refresher
After=network.target

[Service]
Type=oneshot
User=ozero-sub
ExecStart=/opt/ozero-sub/ozero-sub refresh
LoadCredentialEncrypted=sk:/etc/ozero-sub/sk.enc
```

`/etc/systemd/system/ozero-sub-cron.timer`:
```ini
[Unit]
Description=Ozero subscription refresh hourly

[Timer]
OnCalendar=hourly
Persistent=true

[Install]
WantedBy=timers.target
```

## 7. Rate limits
- Caddy: 100 req/min/IP на `/v1/*`
- fail2ban: ban IP после 1000 req/min
- Cloudflare в front (опционально)

## 8. Monitoring
- Prometheus node_exporter + blackbox_exporter
- Alert: `servers.json` возраст > 3h → WARNING
- Alert: `sub.ozero.app` недоступен > 5 мин → CRITICAL
- Uptime target: 99.5%

## 9. Backup
- `servers.yaml` — git history (истина в git)
- Приватный SK — see `docs/key-rotation.md`
- nginx/caddy logs → `/var/log/caddy/` (rotate 30 дней)

## 10. Disaster recovery
- Новый VPS → clone этой doc → apt install → copy `servers.yaml` из git → rotate DNS → 10 мин downtime
- RTO: 30 мин. RPO: 1 час (если только что прошёл refresh cycle)

## 11. Hardening
- SSH only via wireguard jumphost
- Firewall (ufw): только 443/tcp публично
- Unattended-upgrades для критических патчей
- Fail2ban на sshd
- Root disabled, sudoer через pubkey
- Audit log → journald with persistent storage

## 12. Roadmap backend
- v1: текущее (YAML + cron + подпись)
- v2: мультирегиональная раздача (anycast / 3 точки)
- v3: клиент-подписанные feedback (работают ли серверы) для адаптивного пула
- v4: P2P-gossip через DHT (см. PRD §5.5)
