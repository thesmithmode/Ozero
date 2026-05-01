package ru.ozero.app.settings

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import ru.ozero.app.BootReceiver
import ru.ozero.enginescore.settings.AutoStartGateway
import javax.inject.Inject

class BootReceiverAutoStartGateway @Inject constructor(
    @ApplicationContext private val context: Context,
) : AutoStartGateway {
    override fun setAutoStart(enabled: Boolean) {
        BootReceiver.setAutoStart(context, enabled)
    }
}
