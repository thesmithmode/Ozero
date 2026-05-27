package ru.ozero.app.logging

import android.content.Context
import ru.ozero.enginescore.PersistentLogger
import java.io.File

object BootFileLogger : PersistentLogger {

    fun init(context: Context) = UnifiedLogger.init(context)

    fun file(): File? = UnifiedLogger.file()

    override fun trace(tag: String, msg: String) = UnifiedLogger.trace(tag, msg)
    override fun debug(tag: String, msg: String) = UnifiedLogger.debug(tag, msg)
    override fun info(tag: String, msg: String) = UnifiedLogger.info(tag, msg)
    override fun warn(tag: String, msg: String, t: Throwable?) = UnifiedLogger.warn(tag, msg, t)
    override fun error(tag: String, msg: String, t: Throwable?) = UnifiedLogger.error(tag, msg, t)

    fun clear() = UnifiedLogger.clear()

    fun read(): String = UnifiedLogger.read()
}
