# Telegram WSS

Standalone local SOCKS5 proxy for Telegram MTProto over TLS WebSocket.
Select Proxy traffic mode and Telegram WSS manually in Ozero, start the
connection, and use the engine settings screen to open Telegram's proxy
configuration. The listener is bound to 127.0.0.1:10812, with no user credentials.

The transport accepts only explicitly mapped Telegram IPv4 DC addresses and
selected TCP ports. Unknown destinations, UDP and unsupported MTProto headers
are rejected. No direct TCP fallback, third-party proxy, or certificate bypass.
The WebSocket targets Telegram's own web.telegram.org endpoints with standard TLS.

The engine is intentionally excluded from TUN mode, automatic selection, and
multi-engine chains. There is no general internet exit or UDP call support.
Because no TUN is established in this mode, outgoing sockets use normal OS
routing; they do not bypass other VPNs. Readiness verifies the local SOCKS5
listener and a TLS WebSocket handshake with an approved Telegram endpoint. It
does not prove end-to-end MTProto message delivery. Settings backup uses the existing engine
selection and traffic mode fields.

The TCP stream is framed into encrypted MTProto packets with a bounded packet
size. For media DCs, the alternate Telegram WSS hostname is tried only if
the initial MTProto header has not been queued for transmission. Live Android interoperability with Telegram DCs, media, and reconnects
must be verified before release. See NOTICE for pinned protocol references.
