package ru.ozero.engineurnetwork

import android.app.ActivityManager
import android.content.Context
import com.bringyour.sdk.Sdk
import ru.ozero.enginescore.PersistentLoggers
import java.util.concurrent.atomic.AtomicBoolean

object UrnetworkSdkBootstrap {

    private const val TAG = "UrnetworkSdkBootstrap"
    private const val DEFAULT_MEM_MIB = 32L
    private const val MAX_SDK_MEM_MIB = 64L
    private const val MIB = 1024L * 1024L

    private val initialized = AtomicBoolean(false)

    fun initOnce(context: Context) {
        if (!initialized.compareAndSet(false, true)) return
        runCatching {
            val path = context.applicationContext.filesDir.absolutePath
            Sdk.setLogDir(path)
            val am = context.applicationContext.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            val maxMib = am?.memoryClass?.toLong() ?: DEFAULT_MEM_MIB
            val sdkMib = minOf((3L * maxMib) / 4L, MAX_SDK_MEM_MIB)
            Sdk.setMemoryLimit(sdkMib * MIB)
            PersistentLoggers.info(TAG, "URnetwork SDK init: logDir=$path memMiB=$sdkMib")
        }.onFailure {
            initialized.set(false)
            PersistentLoggers.error(TAG, "URnetwork SDK init failed: ${it.message}", it)
        }
    }
}
