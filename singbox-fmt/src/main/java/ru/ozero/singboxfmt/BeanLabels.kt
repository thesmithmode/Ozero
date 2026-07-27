package ru.ozero.singboxfmt

fun AbstractBean.protocolLabel(): String = when (this) {
    is VLESSBean -> "VLESS"
    is VMessBean -> "VMESS"
    is TrojanBean -> "TROJAN"
    is ShadowsocksBean -> "SHADOWSOCKS"
    else -> javaClass.simpleName.ifBlank { "UNKNOWN" }
}
