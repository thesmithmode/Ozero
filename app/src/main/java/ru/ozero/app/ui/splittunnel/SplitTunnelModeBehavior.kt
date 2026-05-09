package ru.ozero.app.ui.splittunnel

import ru.ozero.enginescore.settings.SplitTunnelMode

internal fun SplitTunnelMode.requiresAppList(): Boolean =
    this == SplitTunnelMode.ALLOWLIST || this == SplitTunnelMode.BLOCKLIST
