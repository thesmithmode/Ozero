package ru.ozero.app.di.stubs

import android.util.Log
import ru.ozero.engineamnezia.LibAwgDelegate
import ru.ozero.enginehysteria2.LibHy2Delegate
import ru.ozero.enginenaive.LibNaiveDelegate
import ru.ozero.enginetor.LibTorDelegate
import ru.ozero.enginetor.dynamicmod.DynamicTorInstaller
import ru.ozero.enginetor.dynamicmod.InstallResult
import ru.ozero.enginexray.LibXrayDelegate

private const val NOT_IMPLEMENTED = -1
private const val TAG = "StubLibDelegate"

class StubLibXrayDelegate : LibXrayDelegate {
    override fun startXray(configJson: String): Int {
        Log.w(TAG, "xray stub: start не реализован (RT.6 заменит на JNI)")
        return NOT_IMPLEMENTED
    }
    override fun stopXray(): Int = 0
    override fun version(): String = "stub"
    override fun queryStats(tag: String, direction: String): Long = 0L
}

class StubLibAwgDelegate : LibAwgDelegate {
    override fun startAwg(configIni: String): Int {
        Log.w(TAG, "awg stub: start не реализован (RT.6 заменит на JNI)")
        return NOT_IMPLEMENTED
    }
    override fun stopAwg(): Int = 0
    override fun isUp(): Boolean = false
    override fun version(): String = "stub"
    override fun queryStats(direction: String): Long = 0L
}

class StubLibHy2Delegate : LibHy2Delegate {
    override fun startHy2(configJson: String): Int {
        Log.w(TAG, "hy2 stub: start не реализован (RT.6 заменит на JNI)")
        return NOT_IMPLEMENTED
    }
    override fun stopHy2(): Int = 0
    override fun version(): String = "stub"
    override fun queryStats(direction: String): Long = 0L
}

class StubLibNaiveDelegate : LibNaiveDelegate {
    override fun startNaive(configJson: String): Int {
        Log.w(TAG, "naive stub: start не реализован (RT.6 заменит на JNI)")
        return NOT_IMPLEMENTED
    }
    override fun stopNaive(): Int = 0
    override fun isAlive(): Boolean = false
    override fun version(): String = "stub"
}

class StubLibTorDelegate : LibTorDelegate {
    override fun startTor(torrc: String): Int {
        Log.w(TAG, "tor stub: start не реализован (RT.6 заменит на JNI)")
        return NOT_IMPLEMENTED
    }
    override fun stopTor(): Int = 0
    override fun isBootstrapped(): Boolean = false
    override fun bootstrapPercent(): Int = 0
    override fun version(): String = "stub"
}

class StubDynamicTorInstaller : DynamicTorInstaller {
    override suspend fun ensureInstalled(): InstallResult = InstallResult.AlreadyInstalled
}
