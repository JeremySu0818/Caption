package com.jeremysu0818.caption

import android.Manifest
import android.app.Activity
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.net.toUri
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.Crossfade
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.scaleIn
import androidx.compose.animation.core.tween
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jeremysu0818.caption.data.CaptionLanguage
import com.jeremysu0818.caption.data.CaptionLanguages
import com.jeremysu0818.caption.data.CaptionRuntimeState
import com.jeremysu0818.caption.data.CaptionSettings
import com.jeremysu0818.caption.data.SpeechEngineOption
import com.jeremysu0818.caption.data.WhisperModelOption
import com.jeremysu0818.caption.data.I18n
import com.jeremysu0818.caption.data.t
import com.jeremysu0818.caption.accessibility.CaptionAccessibilityService
import com.jeremysu0818.caption.service.CaptionCaptureService
import com.jeremysu0818.caption.ui.theme.CaptionTheme
import com.jeremysu0818.caption.whisper.ModelDownloadState
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private var startRequestCount by mutableIntStateOf(0)
    private var resumeCount by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        CaptionGraph.ensureInitialized(this)
        enableEdgeToEdge()
        if (intent?.action == ACTION_START_FROM_TILE) {
            startRequestCount++
        }
        setContent {
            CaptionTheme {
                CaptionApp(
                    startRequestCount = startRequestCount,
                    resumeCount = resumeCount,
                    onStartRequested = { startRequestCount++ },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.action == ACTION_START_FROM_TILE) {
            startRequestCount++
        }
    }

    override fun onResume() {
        super.onResume()
        resumeCount++
    }

    companion object {
        const val ACTION_START_FROM_TILE = "com.jeremysu0818.caption.action.START_FROM_TILE"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CaptionApp(
    startRequestCount: Int,
    resumeCount: Int,
    onStartRequested: () -> Unit,
) {
    val context = LocalContext.current
    val settings by CaptionGraph.preferences.settings.collectAsState()
    val runtimeState by CaptionGraph.runtimeStore.state.collectAsState()
    val downloadState by CaptionGraph.modelRepository.downloadState.collectAsState()
    val scope = rememberCoroutineScope()

    var permissionRefresh by remember { mutableIntStateOf(0) }
    var pendingStart by remember { mutableStateOf(false) }
    var overlayPrompted by remember { mutableStateOf(false) }
    var recordPrompted by remember { mutableStateOf(false) }
    var notificationPrompted by remember { mutableStateOf(false) }
    var accessibilityPrompted by remember { mutableStateOf(false) }
    var isMlKitAdvancedAvailable by remember { mutableStateOf<Boolean?>(null) }
    var downloadJob by remember { mutableStateOf<Job?>(null) }


    val mediaProjectionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            CaptionCaptureService.start(context, result.resultCode, result.data!!)
        } else {
            CaptionGraph.runtimeStore.setStopped(I18n.getString("error_projection_cancelled"))
        }
    }

    val overlaySettingsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        permissionRefresh++
    }

    val accessibilitySettingsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        permissionRefresh++
    }

    val recordPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (!granted) {
            pendingStart = false
            CaptionGraph.runtimeStore.setError(I18n.getString("error_no_record"))
        }
        permissionRefresh++
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (!granted) {
            pendingStart = false
            CaptionGraph.runtimeStore.setError(I18n.getString("error_no_notification"))
        }
        permissionRefresh++
    }

    val overlayGranted = remember(resumeCount, permissionRefresh) {
        Settings.canDrawOverlays(context)
    }
    val recordGranted = remember(resumeCount, permissionRefresh) {
        context.hasPermission(Manifest.permission.RECORD_AUDIO)
    }
    val notificationGranted = remember(resumeCount, permissionRefresh) {
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            context.hasPermission(Manifest.permission.POST_NOTIFICATIONS)
    }
    val accessibilityGranted = remember(resumeCount, permissionRefresh) {
        context.isCaptionAccessibilityServiceEnabled()
    }

    LaunchedEffect(settings.model) {
        CaptionGraph.modelRepository.refresh(settings.model)
    }

    LaunchedEffect(settings.sourceLanguageTag) {
        isMlKitAdvancedAvailable = runCatching {
            CaptionGraph.mlKitSpeechTranscriber.isAdvancedAvailable(settings.sourceLanguageTag)
        }.getOrDefault(false)
    }

    LaunchedEffect(isMlKitAdvancedAvailable, settings.speechEngine) {
        if (
            isMlKitAdvancedAvailable == false &&
            settings.speechEngine == SpeechEngineOption.MLKIT_ADVANCED
        ) {
            CaptionGraph.preferences.updateSpeechEngine(SpeechEngineOption.MLKIT_BASIC)
        }
    }

    LaunchedEffect(startRequestCount) {
        if (startRequestCount > 0) {
            pendingStart = true
            overlayPrompted = false
            recordPrompted = false
            notificationPrompted = false
            accessibilityPrompted = false
        }
    }

    LaunchedEffect(
        pendingStart,
        overlayGranted,
        recordGranted,
        notificationGranted,
        accessibilityGranted,
        permissionRefresh,
        resumeCount,
    ) {
        if (!pendingStart) return@LaunchedEffect
        when {
            !accessibilityGranted -> {
                if (!accessibilityPrompted) {
                    accessibilityPrompted = true
                    accessibilitySettingsLauncher.launch(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                }
            }

            !overlayGranted -> {
                if (!overlayPrompted) {
                    overlayPrompted = true
                    overlaySettingsLauncher.launch(context.overlaySettingsIntent())
                }
            }

            !recordGranted -> {
                if (!recordPrompted) {
                    recordPrompted = true
                    recordPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                }
            }

            !notificationGranted -> {
                if (!notificationPrompted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    notificationPrompted = true
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }

            else -> {
                pendingStart = false
                val projectionManager = context.getSystemService(MediaProjectionManager::class.java)
                mediaProjectionLauncher.launch(projectionManager.createScreenCaptureIntent())
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Caption",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            ControlCenterCard(
                runtimeState = runtimeState,
                isRunning = runtimeState.isRunning,
                canStart = accessibilityGranted && overlayGranted && recordGranted,
                onStart = onStartRequested,
                onStop = { CaptionCaptureService.stop(context) }
            )

            val allPermissionsGranted =
                accessibilityGranted && overlayGranted && recordGranted && notificationGranted
            AnimatedVisibility(
                visible = !allPermissionsGranted,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                PermissionAlertCard(
                    overlayGranted = overlayGranted,
                    recordGranted = recordGranted,
                    notificationGranted = notificationGranted,
                    accessibilityGranted = accessibilityGranted,
                    onOpenAccessibilitySettings = {
                        accessibilityPrompted = true
                        accessibilitySettingsLauncher.launch(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                    },
                    onOpenOverlaySettings = {
                        overlayPrompted = true
                        overlaySettingsLauncher.launch(context.overlaySettingsIntent())
                    },
                    onRequestRecord = {
                        recordPrompted = true
                        recordPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    },
                    onRequestNotifications = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            notificationPrompted = true
                            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    },
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = t("settings"),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 4.dp)
                )

                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.animateContentSize()) {
                        SpeechEngineSection(
                            settings = settings,
                            isMlKitAdvancedAvailable = isMlKitAdvancedAvailable != false,
                            onEngineSelected = CaptionGraph.preferences::updateSpeechEngine,
                            onUnsupportedAdvancedSelected = {
                                Toast.makeText(
                                    context,
                                    I18n.getString("mlkit_advanced_unsupported"),
                                    Toast.LENGTH_SHORT,
                                ).show()
                            },
                        )
                        
                        AnimatedVisibility(
                            visible = settings.speechEngine == SpeechEngineOption.WHISPER,
                            enter = expandVertically() + fadeIn(),
                            exit = shrinkVertically() + fadeOut()
                        ) {
                            Column {
                                HorizontalDivider(modifier = Modifier.padding(horizontal = 18.dp), color = MaterialTheme.colorScheme.surfaceVariant)
                                WhisperModelSection(
                                    settings = settings,
                                    downloadState = downloadState,
                                    onModelSelected = CaptionGraph.preferences::updateModel,
                                    onDownloadModel = {
                                        downloadJob = scope.launch {
                                            try {
                                                CaptionGraph.modelRepository.ensureModel(settings.model)
                                            } finally {
                                                downloadJob = null
                                            }
                                        }
                                    },
                                    onCancelDownload = {
                                        downloadJob?.cancel()
                                        downloadJob = null
                                    },
                                    onDeleteModel = {
                                        scope.launch {
                                            CaptionGraph.modelRepository.deleteModel(settings.model)
                                        }
                                    },
                                )
                            }
                        }

                        HorizontalDivider(modifier = Modifier.padding(horizontal = 18.dp), color = MaterialTheme.colorScheme.surfaceVariant)
                        
                        TranslationSection(
                            settings = settings,
                            onEnabledChanged = CaptionGraph.preferences::updateTranslationEnabled,
                            onSourceChanged = CaptionGraph.preferences::updateSourceLanguage,
                            onTargetChanged = CaptionGraph.preferences::updateTargetLanguage,
                        )

                        HorizontalDivider(modifier = Modifier.padding(horizontal = 18.dp), color = MaterialTheme.colorScheme.surfaceVariant)

                        UiLanguageSection(
                            settings = settings,
                            onLanguageSelected = CaptionGraph.preferences::updateUiLanguage,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ControlCenterCard(
    runtimeState: CaptionRuntimeState,
    isRunning: Boolean,
    canStart: Boolean,
    onStart: () -> Unit,
    onStop: () -> Unit,
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column(
            modifier = Modifier
                .padding(18.dp)
                .animateContentSize(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    modifier = Modifier.weight(1f),
                    onClick = onStart,
                    enabled = !isRunning,
                ) {
                    Text(if (canStart) t("start_caption") else t("check_permission"))
                }
                OutlinedButton(
                    modifier = Modifier.weight(1f),
                    onClick = onStop,
                    enabled = isRunning,
                ) {
                    Text(t("stop"))
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(t("status"), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                AnimatedContent(
                    targetState = runtimeState.status,
                    label = "status",
                    transitionSpec = {
                        val downloadingPrefix = I18n.getString("model_downloading_status").substringBefore("{").trim()
                        val isDownloading = (downloadingPrefix.isNotEmpty() && (targetState.startsWith(downloadingPrefix) || initialState.startsWith(downloadingPrefix))) ||
                            targetState.startsWith("模型下載中") || initialState.startsWith("模型下載中")
                        if (isDownloading) {
                            ContentTransform(
                                targetContentEnter = EnterTransition.None,
                                initialContentExit = ExitTransition.None
                            )
                        } else {
                            (fadeIn(animationSpec = tween(220, delayMillis = 90)) +
                                    scaleIn(initialScale = 0.92f, animationSpec = tween(220, delayMillis = 90)))
                                .togetherWith(fadeOut(animationSpec = tween(90)))
                        }
                    }
                ) { targetStatus ->
                    Text(t(targetStatus), style = MaterialTheme.typography.titleMedium)
                }
                val lastLine = runtimeState.lines.lastOrNull()
                AnimatedVisibility(visible = lastLine != null && lastLine.sourceText.isNotBlank()) {
                    Text(lastLine?.sourceText.orEmpty(), style = MaterialTheme.typography.bodyLarge)
                }
                AnimatedVisibility(
                    visible = lastLine != null &&
                        lastLine.isTranslating &&
                        lastLine.translatedText.isNullOrBlank()
                ) {
                    Text(t("translating"), style = MaterialTheme.typography.bodyMedium)
                }
                AnimatedVisibility(visible = lastLine != null && !lastLine.translatedText.isNullOrBlank()) {
                    Text(lastLine?.translatedText.orEmpty(), style = MaterialTheme.typography.bodyMedium)
                }
                AnimatedVisibility(visible = runtimeState.errorMessage != null) {
                    Text(t(runtimeState.errorMessage.orEmpty()), color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
private fun PermissionAlertCard(
    overlayGranted: Boolean,
    recordGranted: Boolean,
    notificationGranted: Boolean,
    accessibilityGranted: Boolean,
    onOpenOverlaySettings: () -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
    onRequestRecord: () -> Unit,
    onRequestNotifications: () -> Unit,
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer
        )
    ) {
        Column(
            modifier = Modifier
                .padding(18.dp)
                .animateContentSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(t("permission_required"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(t("permission_reason"), style = MaterialTheme.typography.bodyMedium)

            if (!accessibilityGranted) {
                PermissionRow(
                    label = t("permission_accessibility"),
                    actionText = t("open_settings"),
                    onAction = onOpenAccessibilitySettings,
                )
            }
            if (!overlayGranted) {
                PermissionRow(
                    label = t("permission_overlay"),
                    actionText = t("open_settings"),
                    onAction = onOpenOverlaySettings,
                )
            }
            if (!recordGranted) {
                PermissionRow(
                    label = t("permission_record"),
                    actionText = t("allow"),
                    onAction = onRequestRecord,
                )
            }
            if (!notificationGranted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                PermissionRow(
                    label = t("permission_notification"),
                    actionText = t("allow"),
                    onAction = onRequestNotifications,
                )
            }
        }
    }
}

@Composable
private fun PermissionRow(
    label: String,
    actionText: String,
    onAction: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Button(onClick = onAction) {
            Text(actionText)
        }
    }
}

@Composable
private fun SpeechEngineSection(
    settings: CaptionSettings,
    isMlKitAdvancedAvailable: Boolean,
    onEngineSelected: (SpeechEngineOption) -> Unit,
    onUnsupportedAdvancedSelected: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(t("speech_engine"), style = MaterialTheme.typography.titleMedium)
        SpeechEngineOption.entries.forEach { option ->
            val isAdvancedOption = option == SpeechEngineOption.MLKIT_ADVANCED
            val isOptionEnabled = !isAdvancedOption || isMlKitAdvancedAvailable

            FilterChip(
                modifier = Modifier.alpha(if (isOptionEnabled) 1f else 0.45f),
                selected = settings.speechEngine == option,
                onClick = {
                    if (isOptionEnabled) {
                        onEngineSelected(option)
                    } else {
                        onUnsupportedAdvancedSelected()
                    }
                },
                label = { Text(option.label) },
            )
        }
        AnimatedContent(
            targetState = settings.speechEngine,
            label = "engine_description"
        ) { engine ->
            Text(
                text = when (engine) {
                    SpeechEngineOption.WHISPER -> t("engine_whisper_desc")
                    SpeechEngineOption.MLKIT_BASIC -> t("engine_mlkit_basic_desc")
                    SpeechEngineOption.MLKIT_ADVANCED -> t("engine_mlkit_advanced_desc")
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun WhisperModelSection(
    settings: CaptionSettings,
    downloadState: ModelDownloadState,
    onModelSelected: (WhisperModelOption) -> Unit,
    onDownloadModel: () -> Unit,
    onCancelDownload: () -> Unit,
    onDeleteModel: () -> Unit,
) {
    var showDeleteConfirmation by remember { mutableStateOf(false) }
    var showCancelConfirmation by remember { mutableStateOf(false) }

    if (showCancelConfirmation) {
        AlertDialog(
            onDismissRequest = { showCancelConfirmation = false },
            title = {
                Text(text = t("cancel_download_title"))
            },
            text = {
                Text(text = t("cancel_download_message", settings.model.displayName))
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showCancelConfirmation = false
                        onCancelDownload()
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text(t("confirm_cancel"))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showCancelConfirmation = false }
                ) {
                    Text(t("back"))
                }
            }
        )
    }

    if (showDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmation = false },
            title = {
                Text(text = t("delete_model_title"))
            },
            text = {
                Text(text = t("delete_model_message", settings.model.displayName))
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirmation = false
                        onDeleteModel()
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text(t("confirm_delete"))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDeleteConfirmation = false }
                ) {
                    Text(t("cancel"))
                }
            }
        )
    }

    val selectedDownloadState = if (downloadState.model == settings.model) {
        downloadState
    } else {
        ModelDownloadState(model = settings.model)
    }

    Column(
        modifier = Modifier.fillMaxWidth().padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(t("whisper_model"), style = MaterialTheme.typography.titleMedium)
        WhisperModelOption.entries.chunked(2).forEach { rowModels ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                rowModels.forEach { option ->
                    FilterChip(
                        modifier = Modifier.weight(1f),
                        selected = settings.model == option,
                        onClick = { onModelSelected(option) },
                        label = {
                            Text("${option.displayName} · ${option.sizeLabel}")
                        },
                    )
                }
                if (rowModels.size == 1) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
        Text(
            text = when {
                selectedDownloadState.isDownloaded -> t("model_downloaded")
                selectedDownloadState.isDownloading -> selectedDownloadState.buildStatusText()
                selectedDownloadState.errorMessage != null -> t(selectedDownloadState.errorMessage)
                else -> t("model_not_downloaded")
            },
            style = MaterialTheme.typography.bodySmall,
            color = if (selectedDownloadState.errorMessage != null) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
        AnimatedVisibility(
            visible = selectedDownloadState.isDownloading,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            LinearProgressIndicator(
                progress = { selectedDownloadState.progress.coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(
                modifier = Modifier.weight(1f),
                onClick = onDownloadModel,
                enabled = !selectedDownloadState.isDownloaded && !selectedDownloadState.isDownloading,
            ) {
                Text(t("download_model", settings.model.displayName))
            }
            val buttonState = when {
                selectedDownloadState.isDownloading -> 1
                selectedDownloadState.isDownloaded -> 2
                else -> 0
            }
            Crossfade(
                targetState = buttonState,
                modifier = Modifier.weight(1f),
                label = "action_button"
            ) { state ->
                when (state) {
                    1 -> {
                        OutlinedButton(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = { showCancelConfirmation = true },
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            ),
                            border = BorderStroke(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.error
                            )
                        ) {
                            Text(t("cancel_download"))
                        }
                    }
                    2 -> {
                        OutlinedButton(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = { showDeleteConfirmation = true },
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            ),
                            border = BorderStroke(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.error
                            )
                        ) {
                            Text(t("delete_model", settings.model.displayName))
                        }
                    }
                    else -> Spacer(modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }
}

@Composable
private fun TranslationSection(
    settings: CaptionSettings,
    onEnabledChanged: (Boolean) -> Unit,
    onSourceChanged: (String) -> Unit,
    onTargetChanged: (String) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(t("local_translation"), style = MaterialTheme.typography.titleMedium)
                Text(
                    text = t("translation_desc"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = settings.translationEnabled,
                onCheckedChange = onEnabledChanged,
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .animateContentSize(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val sourceLanguages = remember(settings.speechEngine, settings.translationEnabled) {
                CaptionLanguages.getFilteredLanguages(settings.speechEngine, settings.translationEnabled)
            }
            LanguageDropdown(
                modifier = Modifier.weight(1f),
                label = t("source"),
                selectedTag = settings.sourceLanguageTag,
                languages = sourceLanguages,
                onSelected = onSourceChanged,
            )
            Crossfade(
                targetState = settings.translationEnabled,
                modifier = Modifier.weight(1f),
                label = "target_language"
            ) { enabled ->
                if (enabled) {
                    val targetLanguages = remember {
                        CaptionLanguages.targetLanguages()
                    }
                    LanguageDropdown(
                        modifier = Modifier.fillMaxWidth(),
                        label = t("target"),
                        selectedTag = settings.targetLanguageTag,
                        languages = targetLanguages,
                        onSelected = onTargetChanged,
                    )
                } else {
                    Spacer(modifier = Modifier.fillMaxWidth())
                }
            }
        }
        AnimatedContent(
            targetState = settings.translationEnabled,
            label = "translation_hint"
        ) { enabled ->
            Text(
                text = if (enabled) t("translation_enabled_desc") else t("translation_disabled_desc"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun LanguageDropdown(
    modifier: Modifier = Modifier,
    label: String,
    selectedTag: String,
    languages: List<CaptionLanguage>,
    onSelected: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Column(modifier = modifier) {
        Text(label, style = MaterialTheme.typography.labelLarge)
        Spacer(modifier = Modifier.height(6.dp))
        OutlinedButton(
            modifier = Modifier.fillMaxWidth(),
            onClick = { expanded = true },
        ) {
            Text(CaptionLanguages.labelFor(selectedTag))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            languages.forEach { language ->
                DropdownMenuItem(
                    text = { Text(language.label) },
                    onClick = {
                        expanded = false
                        onSelected(language.tag)
                    },
                )
            }
        }
    }
}

private fun Context.hasPermission(permission: String): Boolean =
    checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

private fun Context.overlaySettingsIntent(): Intent =
    Intent(
        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
        "package:$packageName".toUri(),
    )

private fun Context.isCaptionAccessibilityServiceEnabled(): Boolean {
    val captionService = ComponentName(this, CaptionAccessibilityService::class.java)
    return getSystemService(AccessibilityManager::class.java)
        .getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
        .any { service ->
            val serviceInfo = service.resolveInfo.serviceInfo
            ComponentName(serviceInfo.packageName, serviceInfo.name) == captionService
        }
}

private fun Float.asPercent(): String = "${(this * 100).toInt().coerceIn(0, 100)}%"

private fun Long.asReadableSpeed(): String =
    if (this <= 0L) I18n.getString("calculating")
    else "${asReadableSize()}/s"

private fun Long.asReadableSize(): String {
    if (this < 0L) return "--"
    if (this < 1024L) return "${this} B"

    val units = arrayOf("KB", "MB", "GB", "TB")
    var value = this.toDouble()
    var unitIndex = -1
    while (value >= 1024.0 && unitIndex < units.lastIndex) {
        value /= 1024.0
        unitIndex++
    }

    val decimals = if (value >= 100 || unitIndex == 0) 0 else 1
    return "%.${decimals}f %s".format(value, units[unitIndex])
}

@Composable
private fun UiLanguageSection(
    settings: CaptionSettings,
    onLanguageSelected: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val languages = remember {
        val systemOption = CaptionLanguage(tag = "system", label = I18n.getString("system_default"))
        listOf(systemOption) + CaptionLanguages.supported
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(t("ui_language"), style = MaterialTheme.typography.titleMedium)
        Box(modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(
                modifier = Modifier.fillMaxWidth(),
                onClick = { expanded = true },
            ) {
                val currentLabel = if (settings.uiLanguageTag == "system") {
                    t("system_default")
                } else {
                    CaptionLanguages.labelFor(settings.uiLanguageTag)
                }
                Text(currentLabel)
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.fillMaxWidth(0.9f).height(300.dp)
            ) {
                languages.forEach { language ->
                    DropdownMenuItem(
                        text = { Text(language.label) },
                        onClick = {
                            expanded = false
                            onLanguageSelected(language.tag)
                        },
                    )
                }
            }
        }
    }
}
