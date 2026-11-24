# Telegram WSS (experimental)

Isolated Kotlin transport prototype for Telegram MTProto over TLS WebSocket.
The local SOCKS5 listener accepts only explicitly mapped Telegram IPv4 DC addresses
and selected TCP ports. Unknown destinations, UDP and unsupported MTProto
headers are rejected. No direct TCP fallback, no third-party proxy, no custom
trust manager or certificate bypass. The WebSocket targets Telegram's own
`web.telegram.org` endpoints with normal OkHttp TLS validation.

The module is included in Gradle but deliberately not registered as an
EnginePlugin or exposed in the app yet. Before enabling it, verify MTProto
packet framing and live DC connectivity, integrate protected outbound sockets,
restrict the engine to Telegram (not system-wide auto mode or chains), add
settings/backup/UI and lifecycle tests, and validate CI. No production
compatibility or coverage claim is made.

Protocol reference: Yornich/tg-ws-proxy-android at
`cd9c08b10758783c9a6455e399ede01b78a3201b` (MIT; see NOTICE).
