package ru.ozero.app

import android.app.Application
import android.content.Context
import android.view.ContextThemeWrapper
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class ThemeOzeroAppCompatSentinelTest {

    @Test
    fun themeOzeroMustDeriveFromAppCompat() {
        val ctx: Context = RuntimeEnvironment.getApplication()
        val themed = ContextThemeWrapper(ctx, R.style.Theme_Ozero)
        val attrs = intArrayOf(androidx.appcompat.R.attr.windowActionBar)
        val ta = themed.obtainStyledAttributes(attrs)
        val hasAppCompatAttr = try {
            ta.hasValue(0)
        } finally {
            ta.recycle()
        }
        assertTrue(
            hasAppCompatAttr,
            "Theme.Ozero must derive from Theme.AppCompat — иначе MainActivity:AppCompatActivity " +
                "падает на super.onCreate с IllegalStateException на старте процесса. " +
                "Не менять parent на android:Theme.* без AppCompat-предка.",
        )
    }
}
