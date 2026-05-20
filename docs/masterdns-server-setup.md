# MasterDNS — поднять свой сервер за 15 минут

DNS-туннельный движок Ozero (`MasterDNS`) — обёртка над [MasterDnsVPN](https://github.com/masterking32/MasterDnsVPN). Туннелирует трафик через DNS-запросы к собственному authoritative-серверу. Подходит как экстренный режим, когда обычные VPN-протоколы полностью заблокированы. Скорость ~1–3 Мбит/с.

Шифр для движка: AES-256-GCM или ChaCha20-Poly1305. Свой сервер обязателен — публичных нет.

## Что понадобится

- VPS у любого провайдера: 1 vCPU, 512 МБ RAM. Бюджет 3–5 USD/мес.
- Доменное имя с возможностью настроить NS-делегирование (например `v.example.com`).
- 15 минут.

## Шаги

### 1. Делегирование домена

В DNS-настройках основного домена создать NS-запись для поддомена и A-запись на IP сервера:

```
ns1.example.com.  A     <IP_SERVER>
v.example.com.    NS    ns1.example.com.
```

После делегирования все запросы к `*.v.example.com` идут на ваш сервер.

### 2. Установка сервера

На VPS (Debian/Ubuntu):

```bash
curl -fsSL https://raw.githubusercontent.com/masterking32/MasterDnsVPN/main/server_linux_install.sh | bash
```

Скрипт:
- установит Go и зависимости;
- скомпилирует сервер;
- зарегистрирует systemd-юнит `masterdnsvpn-server.service`.

### 3. Конфигурация сервера

Отредактировать `/etc/masterdnsvpn/server_config.toml`:

```toml
DOMAIN = ["v.example.com"]
PROTOCOL_TYPE = "SOCKS5"
UDP_PORT = 53
DATA_ENCRYPTION_METHOD = 5
ENCRYPTION_KEY_FILE = "/etc/masterdnsvpn/encrypt_key.txt"
```

Сгенерировать ключ:

```bash
openssl rand -hex 32 > /etc/masterdnsvpn/encrypt_key.txt
chmod 600 /etc/masterdnsvpn/encrypt_key.txt
```

Запустить:

```bash
systemctl enable --now masterdnsvpn-server
systemctl status masterdnsvpn-server
```

UDP порт 53 на сервере должен быть открыт во внешний интернет.

### 4. Настройка клиента в Ozero

Открыть Ozero → Настройки → MasterDNS.

В поле `Конфигурация client_config.toml` вставить:

```toml
DOMAINS = ["v.example.com"]
DATA_ENCRYPTION_METHOD = 5
ENCRYPTION_KEY = "<содержимое encrypt_key.txt>"
PROTOCOL_TYPE = "SOCKS5"
RESOLVER_BALANCING_STRATEGY = 3
```

В поле `DNS-резолверы` (по одному на строку):

```
8.8.8.8
1.1.1.1
9.9.9.9
8.8.4.4
```

Включить переключатель `Включить MasterDNS`, затем — подключить VPN из главного экрана. В качестве движка выбрать MasterDNS либо оставить auto-режим (тогда MasterDNS будет использоваться как fallback).

## Диагностика

- `journalctl -u masterdnsvpn-server -f` — логи сервера.
- В Ozero: Настройки → Логи (фильтр `MasterDnsService`).
- Локальный SOCKS5 поднимается на `127.0.0.1:18000` (или ближайший свободный из 18000–18999).

## Безопасность

- Шифрование AES-256-GCM (`DATA_ENCRYPTION_METHOD = 5`) — рекомендуемое.
- `encrypt_key.txt` не выкладывать в публичные репозитории.
- Не открывать TCP-порты MasterDNS-сервера наружу — только UDP/53.
- Регулярно ротировать ключ при подозрении на компрометацию.

## Ограничения

- Реальная пропускная способность 1–3 Мбит/с — DNS не предназначен для bulk-трафика.
- Высокая нагрузка на CPU сервера при многих параллельных подключениях.
- Заметная задержка (200–500 мс) поверх сетевой латентности.
- Бэндвидс резко падает при потерях пакетов >1%.

Если эти ограничения критичны — использовать MasterDNS только как emergency-fallback в auto-режиме Ozero.
