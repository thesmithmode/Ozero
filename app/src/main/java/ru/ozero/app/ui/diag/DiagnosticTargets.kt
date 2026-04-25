package ru.ozero.app.ui.diag

/**
 * 20 целей для теста "работает ли реально". Подобраны по принципу:
 * - 5 западных (доказательство выхода за границу): cloudflare, google, microsoft, github, wikipedia
 * - 5 социальных (типичные блокировки РКН): instagram, facebook, twitter, telegram, discord
 * - 5 видео/стриминг (потеря бытовой ценности при блокировке): youtube, twitch, vimeo, netflix, spotify
 * - 5 dev-tools (актуально для разработчика-владельца): npmjs, pypi, dockerhub, stackoverflow, archlinux
 */
object DiagnosticTargets {
    val URLS: List<String> = listOf(
        "https://cloudflare.com",
        "https://www.google.com",
        "https://www.microsoft.com",
        "https://github.com",
        "https://en.wikipedia.org",
        "https://www.instagram.com",
        "https://www.facebook.com",
        "https://twitter.com",
        "https://web.telegram.org",
        "https://discord.com",
        "https://www.youtube.com",
        "https://www.twitch.tv",
        "https://vimeo.com",
        "https://www.netflix.com",
        "https://open.spotify.com",
        "https://www.npmjs.com",
        "https://pypi.org",
        "https://hub.docker.com",
        "https://stackoverflow.com",
        "https://archlinux.org",
    )
}
