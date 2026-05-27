---
title: "Upstream Parity Verification: line-by-line source comparison"
aliases: [upstream-parity, source-comparison]
tags: [methodology, urnetwork, debugging]
sources:
  - "daily/2026-05-27.md"
created: 2026-05-27
updated: 2026-05-27
---

# Upstream Parity Verification Pattern

При интеграции стороннего SDK (URnetwork, singbox) — кодовое ревью своего кода недостаточно. Нужно line-by-line сравнение с upstream reference app.

## Метод

1. Найти upstream source на GitHub (search code API + raw file fetch)
2. Построить таблицу: upstream call → наш call → совпадает?
3. Проверить: порядок вызовов, параметры, lifecycle, fd modes, listeners
4. Особое внимание на "implicit contracts" — вещи которые upstream делает но не документирует

## Пример: URnetwork provide zero-traffic

**9 багов найдено** за 3 сессии, включая:
- Missing `setProvideControlMode`/`setProvideNetworkMode` после start
- Missing `WakeLock`/`WifiLock` → CPU sleep kills provide
- Missing IoLoop → upstream ВСЕГДА создаёт даже в offline mode
- Missing `O_NONBLOCK` на fd → upstream `setBlocking(false)`

Каждый из этих багов был бы найден за минуты при line-by-line upstream comparison. Без comparison потребовалось 10 дней от первого report до полного fix.

## Правило

При `unpaidBytes=0` или другом "работает но результат ноль" — первым делом upstream parity check, не гипотезы.
