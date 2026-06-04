package ru.ozero.enginewarp

import android.content.SharedPreferences
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PrefsMirrorRankerTest {

    @Test
    fun `order groups mirrors by score and keeps neutral mirrors`() {
        val prefs = FakePrefs(
            mutableMapOf(
                "mirror_score:a" to 4,
                "mirror_score:b" to 0,
                "mirror_score:c" to -2,
            ),
        )
        val ranker = PrefsMirrorRanker(prefs)

        val ordered = ranker.order(listOf("b", "c", "a"))

        assertEquals("a", ordered.first())
        assertTrue(ordered.containsAll(listOf("a", "b", "c")))
        assertEquals("c", ordered.last())
    }

    @Test
    fun `recordSuccess and recordFailure clamp scores`() {
        val prefs = FakePrefs(mutableMapOf("mirror_score:x" to 19, "mirror_score:y" to -20))
        val ranker = PrefsMirrorRanker(prefs)

        ranker.recordSuccess("x")
        ranker.recordFailure("y")

        assertEquals(20, prefs.map["mirror_score:x"])
        assertEquals(-20, prefs.map["mirror_score:y"])
    }

    private class FakePrefs(
        val map: MutableMap<String, Int> = mutableMapOf(),
    ) : SharedPreferences {
        override fun getAll(): MutableMap<String, *> = map.toMutableMap()
        override fun getString(key: String?, defValue: String?): String? = defValue
        override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? = defValues
        override fun getInt(key: String?, defValue: Int): Int = map[key] ?: defValue
        override fun getLong(key: String?, defValue: Long): Long = defValue
        override fun getFloat(key: String?, defValue: Float): Float = defValue
        override fun getBoolean(key: String?, defValue: Boolean): Boolean = defValue
        override fun contains(key: String?): Boolean = key != null && map.containsKey(key)
        override fun edit(): SharedPreferences.Editor = object : SharedPreferences.Editor {
            override fun putString(key: String?, value: String?): SharedPreferences.Editor = this
            override fun putStringSet(key: String?, values: MutableSet<String>?): SharedPreferences.Editor = this
            override fun putInt(key: String?, value: Int): SharedPreferences.Editor {
                if (key != null) map[key] = value
                return this
            }

            override fun putLong(key: String?, value: Long): SharedPreferences.Editor = this
            override fun putFloat(key: String?, value: Float): SharedPreferences.Editor = this
            override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor = this
            override fun remove(key: String?): SharedPreferences.Editor {
                if (key != null) map.remove(key)
                return this
            }

            override fun clear(): SharedPreferences.Editor {
                map.clear()
                return this
            }

            override fun commit(): Boolean = true
            override fun apply() = Unit
        }
        override fun registerOnSharedPreferenceChangeListener(
            listener: SharedPreferences.OnSharedPreferenceChangeListener?,
        ) = Unit

        override fun unregisterOnSharedPreferenceChangeListener(
            listener: SharedPreferences.OnSharedPreferenceChangeListener?,
        ) = Unit
    }
}
