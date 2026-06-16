package ru.ozero.app.urnetwork

import android.content.Context
import android.content.SharedPreferences
import io.mockk.every
import io.mockk.mockk

internal fun mockUrnetworkContext(sharedPreferences: SharedPreferences): Context {
    val context = mockk<Context>(relaxed = true)
    every { context.applicationContext } returns context
    every { context.getSharedPreferences(any(), any()) } returns sharedPreferences
    return context
}

internal class InMemorySharedPreferences : SharedPreferences {

    private val data = linkedMapOf<String, Any?>()

    fun putRaw(key: String, value: String?) {
        if (value == null) {
            data.remove(key)
        } else {
            data[key] = value
        }
    }

    fun rawString(key: String): String? = data[key] as? String

    override fun getAll(): MutableMap<String, *> = data.toMutableMap()

    override fun getString(key: String, defValue: String?): String? = data[key] as? String ?: defValue

    override fun getStringSet(key: String, defValues: MutableSet<String>?): MutableSet<String>? =
        (data[key] as? Set<String>)?.toMutableSet() ?: defValues

    override fun getInt(key: String, defValue: Int): Int = (data[key] as? Int) ?: defValue

    override fun getLong(key: String, defValue: Long): Long = (data[key] as? Long) ?: defValue

    override fun getFloat(key: String, defValue: Float): Float = (data[key] as? Float) ?: defValue

    override fun getBoolean(key: String, defValue: Boolean): Boolean = (data[key] as? Boolean) ?: defValue

    override fun contains(key: String): Boolean = data.containsKey(key)

    override fun edit(): SharedPreferences.Editor = EditorImpl()

    override fun registerOnSharedPreferenceChangeListener(
        listener: SharedPreferences.OnSharedPreferenceChangeListener?,
    ) = Unit

    override fun unregisterOnSharedPreferenceChangeListener(
        listener: SharedPreferences.OnSharedPreferenceChangeListener?,
    ) = Unit

    private inner class EditorImpl : SharedPreferences.Editor {
        override fun putString(key: String, value: String?): SharedPreferences.Editor = apply {
            if (value == null) data.remove(key) else data[key] = value
        }

        override fun putStringSet(key: String, values: MutableSet<String>?): SharedPreferences.Editor = apply {
            if (values == null) data.remove(key) else data[key] = values.toSet()
        }

        override fun putInt(key: String, value: Int): SharedPreferences.Editor = apply { data[key] = value }

        override fun putLong(key: String, value: Long): SharedPreferences.Editor = apply { data[key] = value }

        override fun putFloat(key: String, value: Float): SharedPreferences.Editor = apply { data[key] = value }

        override fun putBoolean(key: String, value: Boolean): SharedPreferences.Editor = apply { data[key] = value }

        override fun remove(key: String): SharedPreferences.Editor = apply { data.remove(key) }

        override fun clear(): SharedPreferences.Editor = apply { data.clear() }

        override fun commit(): Boolean = true

        override fun apply() = Unit
    }
}
