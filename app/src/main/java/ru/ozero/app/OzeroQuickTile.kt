package ru.ozero.app

import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import ru.ozero.commonvpn.OzeroVpnService
import ru.ozero.commonvpn.TunnelController
import ru.ozero.commonvpn.TunnelState
import javax.inject.Inject

@AndroidEntryPoint
@RequiresApi(Build.VERSION_CODES.N)
class OzeroQuickTile : TileService() {

    @Inject lateinit var tunnelController: TunnelController

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var job: Job? = null

    override fun onStartListening() {
        super.onStartListening()
        job = scope.launch {
            tunnelController.state.collect { syncTileState(it) }
        }
    }

    override fun onStopListening() {
        super.onStopListening()
        job?.cancel()
        job = null
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }

    override fun onClick() {
        super.onClick()
        when (qsTile?.state) {
            Tile.STATE_ACTIVE -> stopVpn()
            else -> startVpn()
        }
    }

    private fun syncTileState(state: TunnelState) {
        val tileState = when (state) {
            is TunnelState.Connected -> Tile.STATE_ACTIVE
            is TunnelState.Idle,
            is TunnelState.Connecting,
            is TunnelState.Probing,
            is TunnelState.Failed,
            is TunnelState.Disconnecting,
            -> Tile.STATE_INACTIVE
        }
        updateTile(tileState)
    }

    private fun startVpn() {
        ContextCompat.startForegroundService(
            this,
            Intent(this, OzeroVpnService::class.java).apply {
                action = OzeroVpnService.ACTION_START
            },
        )
    }

    private fun stopVpn() {
        startService(
            Intent(this, OzeroVpnService::class.java).apply {
                action = OzeroVpnService.ACTION_STOP
            },
        )
        updateTile(Tile.STATE_INACTIVE)
    }

    private fun updateTile(state: Int) {
        val tile = qsTile ?: return
        tile.state = state
        tile.updateTile()
    }
}
