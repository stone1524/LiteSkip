package com.litejump.app.engine

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.litejump.app.rules.RuleRepository

/** 下拉快捷栏里的总开关磁贴, 长按可编辑快捷栏把它拖出来 */
class SkipTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        render()
    }

    override fun onClick() {
        super.onClick()
        RuleRepository.setMaster(!RuleRepository.masterEnabled.value)
        render()
    }

    private fun render() {
        val tile = qsTile ?: return
        tile.state = if (RuleRepository.masterEnabled.value) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.updateTile()
    }
}
