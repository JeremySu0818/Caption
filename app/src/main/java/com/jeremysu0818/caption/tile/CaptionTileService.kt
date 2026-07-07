package com.jeremysu0818.caption.tile

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.jeremysu0818.caption.MainActivity
import com.jeremysu0818.caption.R
import com.jeremysu0818.caption.service.CaptionCaptureService

class CaptionTileService : TileService() {
    override fun onStartListening() {
        super.onStartListening()
        updateTile()
    }

    override fun onClick() {
        super.onClick()
        if (CaptionCaptureService.isRunning) {
            CaptionCaptureService.stop(this)
            updateTile(isActive = false)
        } else {
            if (isLocked) {
                unlockAndRun { launchStartFlow() }
            } else {
                launchStartFlow()
            }
        }
    }

    @SuppressLint("StartActivityAndCollapseDeprecated")
    private fun launchStartFlow() {
        val intent = Intent(this, MainActivity::class.java)
            .setAction(MainActivity.ACTION_START_FROM_TILE)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val pendingIntent = PendingIntent.getActivity(
                this,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            startActivityAndCollapse(pendingIntent)
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
        updateTile(isActive = true)
    }

    private fun updateTile(isActive: Boolean = CaptionCaptureService.isRunning) {
        qsTile?.apply {
            label = getString(R.string.tile_label)
            subtitle = if (isActive) getString(R.string.tile_subtitle_on) else getString(R.string.tile_subtitle_off)
            state = if (isActive) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            updateTile()
        }
    }

    companion object {
        fun requestTileRefresh(context: Context) {
            runCatching {
                TileService.requestListeningState(
                    context,
                    ComponentName(context, CaptionTileService::class.java),
                )
            }
        }
    }
}
