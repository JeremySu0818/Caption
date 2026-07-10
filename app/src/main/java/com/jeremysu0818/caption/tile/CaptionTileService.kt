package com.jeremysu0818.caption.tile

import android.annotation.SuppressLint
import android.Manifest
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.view.accessibility.AccessibilityManager
import com.jeremysu0818.caption.CaptionGraph
import com.jeremysu0818.caption.MainActivity
import com.jeremysu0818.caption.ProjectionPermissionActivity
import com.jeremysu0818.caption.R
import com.jeremysu0818.caption.accessibility.CaptionAccessibilityService
import com.jeremysu0818.caption.data.SpeechEngineOption
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
        val destination = if (canStartWithoutOpeningApp()) {
            ProjectionPermissionActivity::class.java
        } else {
            MainActivity::class.java
        }
        val intent = Intent(this, destination)
            .apply {
                if (destination == MainActivity::class.java) {
                    action = MainActivity.ACTION_START_FROM_TILE
                }
            }
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

    private fun canStartWithoutOpeningApp(): Boolean {
        CaptionGraph.ensureInitialized(this)
        val settings = CaptionGraph.preferences.settings.value
        val whisperModelReady = settings.speechEngine != SpeechEngineOption.WHISPER ||
            CaptionGraph.modelRepository.modelFile(settings.model).exists()

        return Settings.canDrawOverlays(this) &&
            checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED &&
            (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) &&
            isCaptionAccessibilityServiceEnabled() &&
            CaptionAccessibilityService.activeOrNull() != null &&
            whisperModelReady
    }

    private fun Context.isCaptionAccessibilityServiceEnabled(): Boolean {
        val captionService = ComponentName(this, CaptionAccessibilityService::class.java)
        return getSystemService(AccessibilityManager::class.java)
            .getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            .any { service ->
                val serviceInfo = service.resolveInfo.serviceInfo
                ComponentName(serviceInfo.packageName, serviceInfo.name) == captionService
            }
    }

    private fun updateTile(isActive: Boolean = CaptionCaptureService.isRunning) {
        qsTile?.apply {
            label = getString(R.string.tile_label)
            subtitle = null
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
