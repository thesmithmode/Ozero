package ru.ozero.coresubscriptions.uri

sealed class ParsedServer {
    data class Vless(val server: VlessServer) : ParsedServer()
    data class Hysteria2(val server: Hysteria2Server) : ParsedServer()
    data class Trojan(val server: TrojanServer) : ParsedServer()
    data class Shadowsocks(val server: ShadowsocksServer) : ParsedServer()
    data class AmneziaWg(val server: AmneziaWgServer) : ParsedServer()
    data class Naive(val server: NaiveServer) : ParsedServer()
    data class Error(val reason: String) : ParsedServer()
}

class SubscriptionUriParser(
    private val vless: VlessUriParser = VlessUriParser(),
    private val hysteria2: Hysteria2UriParser = Hysteria2UriParser(),
    private val trojan: TrojanUriParser = TrojanUriParser(),
    private val shadowsocks: ShadowsocksUriParser = ShadowsocksUriParser(),
    private val amneziaWg: AmneziaWgUriParser = AmneziaWgUriParser(),
    private val naive: NaiveUriParser = NaiveUriParser(),
) {

    fun parse(uri: String): ParsedServer =
        when {
            uri.startsWith("vless://") -> toVless(vless.parse(uri))
            uri.startsWith("hysteria2://") || uri.startsWith("hy2://") -> toHy2(hysteria2.parse(uri))
            uri.startsWith("trojan://") -> toTrojan(trojan.parse(uri))
            uri.startsWith("ss://") -> toSs(shadowsocks.parse(uri))
            uri.startsWith("awg://") -> toAwg(amneziaWg.parse(uri))
            uri.startsWith("naive+") -> toNaive(naive.parse(uri))
            else -> ParsedServer.Error("неизвестный scheme")
        }

    private fun toNaive(r: UriParseResult<NaiveServer>): ParsedServer =
        when (r) {
            is UriParseResult.Ok -> ParsedServer.Naive(r.server)
            is UriParseResult.Error -> ParsedServer.Error(r.reason)
        }

    private fun toVless(r: UriParseResult<VlessServer>): ParsedServer =
        when (r) {
            is UriParseResult.Ok -> ParsedServer.Vless(r.server)
            is UriParseResult.Error -> ParsedServer.Error(r.reason)
        }

    private fun toHy2(r: UriParseResult<Hysteria2Server>): ParsedServer =
        when (r) {
            is UriParseResult.Ok -> ParsedServer.Hysteria2(r.server)
            is UriParseResult.Error -> ParsedServer.Error(r.reason)
        }

    private fun toTrojan(r: UriParseResult<TrojanServer>): ParsedServer =
        when (r) {
            is UriParseResult.Ok -> ParsedServer.Trojan(r.server)
            is UriParseResult.Error -> ParsedServer.Error(r.reason)
        }

    private fun toSs(r: UriParseResult<ShadowsocksServer>): ParsedServer =
        when (r) {
            is UriParseResult.Ok -> ParsedServer.Shadowsocks(r.server)
            is UriParseResult.Error -> ParsedServer.Error(r.reason)
        }

    private fun toAwg(r: UriParseResult<AmneziaWgServer>): ParsedServer =
        when (r) {
            is UriParseResult.Ok -> ParsedServer.AmneziaWg(r.server)
            is UriParseResult.Error -> ParsedServer.Error(r.reason)
        }
}
