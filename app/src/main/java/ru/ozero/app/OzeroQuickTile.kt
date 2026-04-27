package ru.ozero.app

import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import ru.ozero.commonvpn.OzeroVpnService

@RequiresApi(Build.VERSION_CODES.N)
class OzeroQuickTile : TileService() {
    override fun onStartListening() {
        super.onStartListening()
        syncTileState()
    }

    override fun onClick() {
        super.onClick()
        val tile = qsTile ?: return
        when (tile.state) {
            Tile.STATE_ACTIVE -> stopVpn()
            else -> startVpn()
        }
    }

    private fun startVpn() {
                        ContextCompat.startForegroundService(
            this,
            Intent(this, OzeroVpnService::class.java).apply {
                action = OzeroVpnService.ACTION_START
            },
        )
        updateTile(Tile.STATE_ACTIVE)
    }

    private fun stopVpn() {
        startService(
            Intent(this, OzeroVpnService::class.java).apply {
                action = OzeroVpnService.ACTION_STOP
            },
        )
        updateTile(Tile.STATE_INACTIVE)
    }

    private fun syncTileState() {
        updateTile(Tile.STATE_INACTIVE)
    }

    private fun updateTile(state: Int) {
        val tile = qsTile ?: return
        tile.state = state
        tile.updateTile()
    }
}
