package ru.ozero.enginewarp

enum class DoHProvider(val displayName: String, val url: String) {
    SYSTEM("System (системный DNS)", ""),
    CLOUDFLARE_1111("Cloudflare (1.1.1.1)", "https://1.1.1.1/dns-query"),
    CLOUDFLARE_1001("Cloudflare (1.0.0.1)", "https://1.0.0.1/dns-query"),
    GOOGLE_8888("Google (8.8.8.8)", "https://8.8.8.8/dns-query"),
    GOOGLE_8844("Google (8.8.4.4)", "https://8.8.4.4/dns-query"),
    ADGUARD("AdGuard", "https://94.140.14.14/dns-query"),
    MALW("dns.malw.link", "https://dns.malw.link/dns-query"),
    GEOHIDE("geohide", "https://dns.geohide.ru:444/dns-query"),
    ;

    val isSystem: Boolean get() = this == SYSTEM
}
