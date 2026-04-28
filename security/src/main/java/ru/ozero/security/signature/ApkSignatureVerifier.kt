package ru.ozero.security.signature

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import java.security.MessageDigest

class ApkSignatureVerifier(
    private val context: Context,
    private val expectedSha256Hex: String,
) {

    fun isSelfSignatureValid(): Boolean {
        val sigs = loadSignatures() ?: return false
        return sigs.any { sha256(it).equals(expectedSha256Hex, ignoreCase = true) }
    }

    fun currentSignatureSha256(): String? = loadSignatures()?.firstOrNull()?.let(::sha256)

    @Suppress("SwallowedException")
    private fun loadSignatures(): List<ByteArray>? {
        val pm = context.packageManager
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                @Suppress("DEPRECATION")
                val info = pm.getPackageInfo(
                    context.packageName,
                    PackageManager.GET_SIGNING_CERTIFICATES,
                )
                val sigInfo = info.signingInfo ?: return null
                val sigs = sigInfo.apkContentsSigners.map { it.toByteArray() }
                if (sigs.size != 1) return null
                sigs
            } else {
                @Suppress("DEPRECATION")
                val info = pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES)

                @Suppress("DEPRECATION")
                val sigs = info.signatures
                if (sigs == null || sigs.size != 1) null else sigs.map { it.toByteArray() }
            }
        } catch (e: PackageManager.NameNotFoundException) {
            null
        }
    }

    private fun sha256(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return buildString(digest.size * 2) {
            for (b in digest) append("%02x".format(b))
        }
    }
}
