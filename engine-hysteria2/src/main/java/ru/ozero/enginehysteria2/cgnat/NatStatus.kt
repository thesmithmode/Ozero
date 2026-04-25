package ru.ozero.enginehysteria2.cgnat

/**
 * Результат пассивной NAT-детекции.
 *
 * - [OPEN]    — есть публичный IP; UDP/QUIC должен ходить, Hy2 порт-хоппинг работоспособен.
 * - [CGNAT]   — IP в 100.64.0.0/10 (RFC 6598): провайдер использует CGNAT (типично для RU mobile).
 *               UDP может фильтроваться → нужен fallback на TCP-движок (VLESS).
 * - [UNKNOWN] — есть только приватные/loopback/link-local адреса; для точного ответа нужен STUN
 *               (E9). По принципу безопасности трактуется как «возможно CGNAT» в StrategyEngine.
 */
enum class NatStatus { OPEN, CGNAT, UNKNOWN }
