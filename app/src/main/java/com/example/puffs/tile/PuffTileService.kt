package com.example.puffs.tile

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.example.puffs.data.PuffRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class PuffTileService: TileService(){
    private val scope = CoroutineScope(Dispatchers.IO)
    override fun onClick() {
        super.onClick()
        scope.launch { PuffRepository(applicationContext).addPuff() }
        qsTile.state = Tile.STATE_ACTIVE
        qsTile.updateTile()
    }
}