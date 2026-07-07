package com.jeremysu0818.caption

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.net.toUri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jeremysu0818.caption.data.CaptionLanguages
import com.jeremysu0818.caption.data.CaptionRuntimeState
import com.jeremysu0818.caption.data.CaptionSettings
import com.jeremysu0818.caption.data.SpeechEngineOption
import com.jeremysu0818.caption.data.WhisperModelOption
import com.jeremysu0818.caption.service.CaptionCaptureService
import com.jeremysu0818.caption.ui.theme.CaptionTheme
import com.jeremysu0818.caption.whisper.ModelDownloadState
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

    val mediaProjectionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            CaptionCaptureService.start(context, result.resultCode, result.data!!)
        } else {
            CaptionGraph.runtimeStore.setStopped("MediaProjection 授權已取消")
        }
    }

    val overlaySettingsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        permissionRefresh++
    }

    val recordPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (!granted) {
            pendingStart = false
            CaptionGraph.runtimeStore.setError("尚未允許錄音權限。")
        }
        permissionRefresh++
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (!granted) {
            pendingStart = false
            CaptionGraph.runtimeStore.setError("尚未允許通知權限。")
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

    LaunchedEffect(settings.model) {
        CaptionGraph.modelRepository.refresh(settings.model)
    }

    LaunchedEffect(startRequestCount) {
        if (startRequestCount > 0) {
            pendingStart = true
            overlayPrompted = false
            recordPrompted = false
            notificationPrompted = false
        }
    }

    LaunchedEffect(
        pendingStart,
        overlayGranted,
        recordGranted,
        notificationGranted,
        permissionRefresh,
        resumeCount,
    ) {
        if (!pendingStart) return@LaunchedEffect
        when {
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
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            RuntimeCard(runtimeState = runtimeState)
            PermissionCard(
                overlayGranted = overlayGranted,
                recordGranted = recordGranted,
                notificationGranted = notificationGranted,
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
            SpeechEngineCard(
                settings = settings,
                onEngineSelected = CaptionGraph.preferences::updateSpeechEngine,
            )
            ModelCard(
                settings = settings,
                downloadState = downloadState,
                onModelSelected = CaptionGraph.preferences::updateModel,
                onDownloadModel = {
                    scope.launch {
                        CaptionGraph.modelRepository.ensureModel(settings.model)
                    }
                },
            )
            TranslationCard(
                settings = settings,
                onEnabledChanged = CaptionGraph.preferences::updateTranslationEnabled,
                onSourceChanged = CaptionGraph.preferences::updateSourceLanguage,
                onTargetChanged = CaptionGraph.preferences::updateTargetLanguage,
            )
            StartStopCard(
                isRunning = runtimeState.isRunning,
                canStart = overlayGranted && recordGranted,
                onStart = onStartRequested,
                onStop = { CaptionCaptureService.stop(context) },
            )
        }
    }
}

@Composable
private fun RuntimeCard(runtimeState: CaptionRuntimeState) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("狀態", style = MaterialTheme.typography.labelLarge)
            Text(runtimeState.status, style = MaterialTheme.typography.titleMedium)
            if (runtimeState.sourceText.isNotBlank()) {
                Text(runtimeState.sourceText, style = MaterialTheme.typography.bodyLarge)
            }
            runtimeState.translatedText?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium)
            }
            runtimeState.errorMessage?.let {
                Text(it, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun PermissionCard(
    overlayGranted: Boolean,
    recordGranted: Boolean,
    notificationGranted: Boolean,
    onOpenOverlaySettings: () -> Unit,
    onRequestRecord: () -> Unit,
    onRequestNotifications: () -> Unit,
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("授權", style = MaterialTheme.typography.titleMedium)
            PermissionRow(
                label = "覆蓋其他應用程式",
                granted = overlayGranted,
                actionText = "開啟設定",
                onAction = onOpenOverlaySettings,
            )
            PermissionRow(
                label = "系統音訊擷取",
                granted = recordGranted,
                actionText = "允許",
                onAction = onRequestRecord,
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                PermissionRow(
                    label = "前景服務通知",
                    granted = notificationGranted,
                    actionText = "允許",
                    onAction = onRequestNotifications,
                )
            }
        }
    }
}

@Composable
private fun PermissionRow(
    label: String,
    granted: Boolean,
    actionText: String,
    onAction: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = if (granted) "已允許" else "尚未允許",
                style = MaterialTheme.typography.bodySmall,
                color = if (granted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            )
        }
        OutlinedButton(onClick = onAction, enabled = !granted) {
            Text(actionText)
        }
    }
}

@Composable
private fun ModelCard(
    settings: CaptionSettings,
    downloadState: ModelDownloadState,
    onModelSelected: (WhisperModelOption) -> Unit,
    onDownloadModel: () -> Unit,
) {
    val selectedDownloadState = if (downloadState.model == settings.model) {
        downloadState
    } else {
        ModelDownloadState(model = settings.model)
    }

    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Whisper 模型", style = MaterialTheme.typography.titleMedium)
            if (settings.speechEngine != SpeechEngineOption.WHISPER) {
                Text(
                    "目前使用 ${settings.speechEngine.label}，不會下載或使用 Whisper 模型。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                return@Column
            }
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
                    selectedDownloadState.isDownloaded -> "模型已下載"
                    selectedDownloadState.isDownloading -> "模型下載中 ${selectedDownloadState.progress.asPercent()}"
                    selectedDownloadState.errorMessage != null -> selectedDownloadState.errorMessage
                    else -> "模型尚未下載"
                },
                color = if (selectedDownloadState.errorMessage != null) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
            if (selectedDownloadState.isDownloading) {
                LinearProgressIndicator(
                    progress = { selectedDownloadState.progress.coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Button(
                onClick = onDownloadModel,
                enabled = !selectedDownloadState.isDownloaded && !selectedDownloadState.isDownloading,
            ) {
                Text("下載 ${settings.model.displayName}")
            }
        }
    }
}

@Composable
private fun SpeechEngineCard(
    settings: CaptionSettings,
    onEngineSelected: (SpeechEngineOption) -> Unit,
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("辨識引擎", style = MaterialTheme.typography.titleMedium)
            SpeechEngineOption.entries.forEach { option ->
                FilterChip(
                    selected = settings.speechEngine == option,
                    onClick = { onEngineSelected(option) },
                    label = { Text(option.label) },
                )
            }
            Text(
                text = when (settings.speechEngine) {
                    SpeechEngineOption.WHISPER -> "使用 whisper.cpp，本機模型由 app 下載。"
                    SpeechEngineOption.MLKIT_BASIC -> "使用 ML Kit Basic，多數 Android 12+ 裝置可用。"
                    SpeechEngineOption.MLKIT_ADVANCED -> "使用 ML Kit Advanced，需支援 AICore/Gemini Nano 的裝置。"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun TranslationCard(
    settings: CaptionSettings,
    onEnabledChanged: (Boolean) -> Unit,
    onSourceChanged: (String) -> Unit,
    onTargetChanged: (String) -> Unit,
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("本機翻譯", style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = "來源語言會套用到 ML Kit，也會在翻譯開啟時套用到 Whisper。",
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
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                LanguageDropdown(
                    modifier = Modifier.weight(1f),
                    label = "來源",
                    selectedTag = settings.sourceLanguageTag,
                    onSelected = onSourceChanged,
                )
                if (settings.translationEnabled) {
                    LanguageDropdown(
                        modifier = Modifier.weight(1f),
                        label = "目標",
                        selectedTag = settings.targetLanguageTag,
                        onSelected = onTargetChanged,
                    )
                } else {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
            Text(
                text = if (settings.translationEnabled) {
                    "顯示來源與翻譯字幕"
                } else {
                    "只顯示來源字幕"
                },
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
            CaptionLanguages.supported.forEach { language ->
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

@Composable
private fun StartStopCard(
    isRunning: Boolean,
    canStart: Boolean,
    onStart: () -> Unit,
    onStop: () -> Unit,
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("字幕視窗", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    modifier = Modifier.weight(1f),
                    onClick = onStart,
                    enabled = !isRunning,
                ) {
                    Text(if (canStart) "啟動" else "檢查授權")
                }
                OutlinedButton(
                    modifier = Modifier.weight(1f),
                    onClick = onStop,
                    enabled = isRunning,
                ) {
                    Text("停止")
                }
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

private fun Float.asPercent(): String = "${(this * 100).toInt().coerceIn(0, 100)}%"
