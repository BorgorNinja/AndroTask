package com.borgorninja.androtask

import android.content.Intent
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

class MacroQuickTile : TileService() {

    override fun onStartListening() = updateTile()

    override fun onClick() {
        val cmd = if (MacroAccessibilityService.isRecording)
            FloatingOverlayService.CMD_STOP else FloatingOverlayService.CMD_START
        startService(Intent(this, FloatingOverlayService::class.java)
            .putExtra(FloatingOverlayService.CMD, cmd))
        updateTile()
    }

    private fun updateTile() {
        val tile = qsTile ?: return
        val recording = MacroAccessibilityService.isRecording
        tile.state = if (recording) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = if (recording) "Stop Recording" else "Record Macro"
        tile.updateTile()
    }
}
