package ru.ozero.engineurnetwork

object UrnetworkDefaults {
    // PAYOUT-ONLY. НЕ identity. Каждый Ozero-юзер имеет СВОЙ device-Ed25519 keypair как identity
    // (UrnetworkDeviceIdentity → walletAuth networkCreate). PRESET_WALLET — Solana адрес куда
    // backend отправляет выплаты со ВСЕХ юзерских network через walletVc.updatePayoutWallet.
    // Identity ≠ payout. Не путать. Не использовать PRESET_WALLET для authLogin/networkCreate.
    const val PRESET_WALLET: String = "27wAThHKVpd8c4r4GXNzMAev8byGUozA6RVayeZ7vHMM"
    const val DEFAULT_API_URL: String = "https://api.bringyour.com"
    const val DEFAULT_CONNECT_URL: String = "wss://connect.bringyour.com"

    const val DEVICE_DESCRIPTION: String = "Ozero VPN Android"
    const val DEVICE_SPEC: String = "android"

    const val SOLANA_BASE58_REGEX: String = "^[1-9A-HJ-NP-Za-km-z]{32,44}$"
}
