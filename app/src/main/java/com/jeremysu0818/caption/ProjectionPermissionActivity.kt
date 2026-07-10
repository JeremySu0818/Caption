package com.jeremysu0818.caption

import android.app.Activity
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import com.jeremysu0818.caption.service.CaptionCaptureService

/** Launches only the system capture-consent dialog, then immediately closes. */
class ProjectionPermissionActivity : ComponentActivity() {
    private val capturePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            CaptionCaptureService.start(this, result.resultCode, result.data!!)
        }
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        CaptionGraph.ensureInitialized(this)
        val projectionManager = getSystemService(MediaProjectionManager::class.java)
        capturePermissionLauncher.launch(projectionManager.createScreenCaptureIntent())
    }
}
