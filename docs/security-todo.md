# Security TODO

## Critical (блокирует prod release)

### [KS-1] Kill-switch: OzeroVpnService не подписан на TunnelController
**Статус:** TODO, приоритет критичный.
**Проблема:** При падении движка TUN закрывается вместо удержания, трафик уходит в обход через физический интерфейс.
**Фикс:** `OzeroVpnService` подписывается на `TunnelController.state`. При `TunnelState.Dead`:
- TUN fd **не закрывается**
- Добавляется pass-through dropper (перечитывает пакеты из TUN и отбрасывает)
- Либо отключается маршрут 0.0.0.0/0 через `Builder().establish()` без роутов — ОС не найдёт путь наружу.
**Блок:** E3 (после интеграции hev-socks5-tunnel реального). Сейчас E1 — только скелет.

### [KS-2] Подписка на изменения TunnelState в MainActivity / Orchestrator
Должно быть явное ребалансирование: Orchestrator.Failed → TunnelController.onEngineDied, а не прямо закрытие TUN.

## Important (до merge в main)

### [SV-1] Ed25519 verify не constant-time
BouncyCastle `Ed25519Signer` делает early-exit. Таймингова side-channel.
**Фикс:** Заменить на Google Tink `Ed25519Verify` (гарантированно constant-time) или обернуть в фиксированный deadline.

### [SV-2] ServerEntity.uri — plaintext секреты в Room
Полный VLESS/Trojan URI с паролем/UUID хранится незашифрованным. Защищено только ФС + `android:allowBackup="false"` в манифесте.
**Фикс:** SQLCipher + master key из Android Keystore.

## Minor

- HealthMonitor.consecutiveFails не атомарен
- OzeroVpnService.buildTunBuilder использует setBlocking(true) — не оптимально
- Orchestrator CAS-loop без yield — busy-spin теоретически возможен
