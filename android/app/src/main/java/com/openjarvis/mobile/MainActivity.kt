package com.openjarvis.mobile

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.BitmapFactory
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Base64
import android.os.Handler
import android.os.Looper
import android.provider.AlarmClock
import android.provider.CalendarContract
import android.provider.Settings
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.openwakeword.OpenWakeWord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.util.Locale

private val Void = Color(0xFF050B13)
private val Panel = Color(0xFF0B1724)
private val PanelRaised = Color(0xFF102235)
private val Cyan = Color(0xFF33D6F2)
private val Blue = Color(0xFF468BFF)
private val Muted = Color(0xFF91A6B9)
private val White = Color(0xFFEAF5FC)
private val Green = Color(0xFF52D6A0)
private val Amber = Color(0xFFFFC66D)

private enum class Destination(val label: String, val symbol: String) {
    HOME("Home", "⌂"),
    CHAT("Chat", "◉"),
    MEMORY("Memory", "◇"),
    LIVE("Live", "◉"),
    HUB("Hub", "✣"),
    SETTINGS("Settings", "⚙"),
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Void.toArgb()
        window.navigationBarColor = Void.toArgb()
        val keyStore = SecureKeyStore(applicationContext)
        val historyStore = ChatHistoryStore(applicationContext)
        val sharedText = intent?.let(::extractSharedText).orEmpty()
        val openLive = intent?.getBooleanExtra(EXTRA_OPEN_LIVE, false) == true
        setContent {
            JarvisTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = Void) {
                    JarvisScreen(keyStore, historyStore, sharedText, openLive)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (LiveScreenCaptureState.isActive.value) {
            startService(
                Intent(this, LiveScreenCaptureService::class.java)
                    .setAction(LiveScreenCaptureService.ACTION_PAUSE),
            )
            startService(
                Intent(this, LiveScreenCaptureService::class.java)
                    .setAction(LiveScreenCaptureService.ACTION_APP_FOREGROUND),
            )
        }
    }

    override fun onPause() {
        if (LiveScreenCaptureState.isActive.value) {
            startService(
                Intent(this, LiveScreenCaptureService::class.java)
                    .setAction(LiveScreenCaptureService.ACTION_RESUME),
            )
            startService(
                Intent(this, LiveScreenCaptureService::class.java)
                    .setAction(LiveScreenCaptureService.ACTION_APP_BACKGROUND),
            )
        }
        super.onPause()
    }

    companion object {
        const val EXTRA_OPEN_LIVE = "com.openjarvis.mobile.extra.OPEN_LIVE"
    }

    private fun extractSharedText(intent: Intent): String? {
        if (intent.action != Intent.ACTION_SEND) return null
        return intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()
    }
}

@Composable
private fun JarvisTheme(content: @Composable () -> Unit) {
    val colors = darkColorScheme(
        primary = Cyan,
        onPrimary = Color(0xFF00212A),
        secondary = Blue,
        onSecondary = White,
        background = Void,
        onBackground = White,
        surface = Panel,
        onSurface = White,
        surfaceVariant = PanelRaised,
        onSurfaceVariant = Muted,
        outline = Color(0xFF29445B),
        error = Color(0xFFFF7777),
    )
    MaterialTheme(colorScheme = colors, content = content)
}

@Composable
private fun JarvisScreen(
    keyStore: SecureKeyStore,
    historyStore: ChatHistoryStore,
    initialSharedText: String,
    initialOpenLive: Boolean,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val api = remember { OpenJarvisApi() }
    val settingsStore = remember { CompanionSettingsStore(context) }
    val memoryStore = remember { PersonalMemoryStore(context) }
    val conversationStore = remember { ConversationStore(context, historyStore) }
    val appAccessStore = remember { AppAccessStore(context) }
    val scope = rememberCoroutineScope()
    val initialConversations = remember {
        conversationStore.loadAll().ifEmpty {
            listOf(SavedConversation.create()).also(conversationStore::saveAll)
        }
    }
    val startupConversation = remember(initialConversations) {
        initialConversations.maxByOrNull { it.updatedAt } ?: initialConversations.first()
    }
    val conversations = remember {
        mutableStateListOf<SavedConversation>().apply { addAll(initialConversations) }
    }
    var activeConversationId by remember { mutableStateOf(startupConversation.id) }
    val messages = remember {
        mutableStateListOf<ChatMessage>().apply { addAll(startupConversation.messages) }
    }
    val memories = remember {
        mutableStateListOf<String>().apply { addAll(memoryStore.load()) }
    }

    var destination by remember { mutableStateOf(if (initialOpenLive) Destination.LIVE else Destination.HOME) }
    var serverUrl by remember { mutableStateOf(keyStore.loadServerUrl().orEmpty()) }
    var apiKey by remember { mutableStateOf(keyStore.loadApiKey().orEmpty()) }
    var model by remember { mutableStateOf(settingsStore.loadModel()) }
    var availableModels by remember { mutableStateOf(emptyList<String>()) }
    var prompt by remember { mutableStateOf("") }
    var assistantDraft by remember { mutableStateOf("") }
    var memoryDraft by remember { mutableStateOf("") }
    var personality by remember { mutableStateOf(settingsStore.loadPersonality()) }
    var replyLanguage by remember { mutableStateOf(settingsStore.loadLanguage()) }
    var speakReplies by remember { mutableStateOf(settingsStore.loadSpeakReplies()) }
    var status by remember { mutableStateOf("Ready when you are.") }
    var connectionFeedback by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var connected by remember { mutableStateOf(false) }
    var wakeDetector by remember { mutableStateOf<OpenWakeWord?>(null) }
    var wakeListening by remember { mutableStateOf(false) }
    var routeWakeSpeechToLive by remember { mutableStateOf(false) }
    var tts by remember { mutableStateOf<TextToSpeech?>(null) }
    var ttsReady by remember { mutableStateOf(false) }
    var liveGreetingSpoken by remember { mutableStateOf(false) }
    var liveQuestion by remember { mutableStateOf("") }
    var liveAttachment by remember { mutableStateOf<LiveAttachment?>(null) }
    val liveFrameBase64 by LiveScreenCaptureState.frameBase64.collectAsState()
    val liveSharing by LiveScreenCaptureState.isActive.collectAsState()
    val floatingPanelEnabled by LiveScreenCaptureState.isOverlayEnabled.collectAsState()

    fun persistCurrentConversation() {
        val existing = conversations.firstOrNull { it.id == activeConversationId }
            ?: SavedConversation.create().also { conversations.add(0, it) }
        val title = if (existing.title == "New chat") SavedConversation.titleFor(messages) else existing.title
        val updated = existing.copy(
            title = title,
            updatedAt = System.currentTimeMillis(),
            messages = messages.map { ChatMessage(it.role, it.content) },
        )
        val ordered = (conversations.filterNot { it.id == activeConversationId } + updated)
            .sortedWith(compareByDescending<SavedConversation> { it.pinned }.thenByDescending { it.updatedAt })
        conversations.clear()
        conversations.addAll(ordered)
        conversationStore.saveAll(ordered)
    }

    fun startNewConversation() {
        if (busy) {
            status = "Finish the current reply before starting a new chat."
            return
        }
        persistCurrentConversation()
        val fresh = SavedConversation.create()
        conversations.add(0, fresh)
        activeConversationId = fresh.id
        messages.clear()
        prompt = ""
        assistantDraft = ""
        tts?.stop()
        conversationStore.saveAll(conversations.toList())
        destination = Destination.CHAT
        status = "Started a new conversation. Your previous chats are saved in History."
    }

    fun openConversation(id: String) {
        if (busy) {
            status = "Wait for the current reply to finish before switching chats."
            return
        }
        persistCurrentConversation()
        val selected = conversations.firstOrNull { it.id == id } ?: return
        activeConversationId = selected.id
        messages.clear()
        messages.addAll(selected.messages)
        prompt = ""
        assistantDraft = ""
        destination = Destination.CHAT
        status = "Opened \"${selected.title}\"."
    }

    fun renameConversation(id: String, title: String) {
        val cleanTitle = title.trim().take(60)
        if (cleanTitle.isBlank()) return
        val updated = conversations.map { item ->
            if (item.id == id) item.copy(title = cleanTitle, updatedAt = System.currentTimeMillis()) else item
        }.sortedWith(compareByDescending<SavedConversation> { it.pinned }.thenByDescending { it.updatedAt })
        conversations.clear()
        conversations.addAll(updated)
        conversationStore.saveAll(updated)
    }

    fun toggleConversationPin(id: String) {
        val updated = conversations.map { item ->
            if (item.id == id) item.copy(pinned = !item.pinned) else item
        }.sortedWith(compareByDescending<SavedConversation> { it.pinned }.thenByDescending { it.updatedAt })
        conversations.clear()
        conversations.addAll(updated)
        conversationStore.saveAll(updated)
    }

    fun deleteConversation(id: String) {
        if (busy && id == activeConversationId) {
            status = "Wait for the current reply before deleting this chat."
            return
        }
        val remaining = conversations.filterNot { it.id == id }
        val next = if (remaining.isEmpty()) listOf(SavedConversation.create()) else remaining
        conversations.clear()
        conversations.addAll(next)
        if (id == activeConversationId) {
            val selected = next.maxByOrNull { it.updatedAt } ?: next.first()
            activeConversationId = selected.id
            messages.clear()
            messages.addAll(selected.messages)
            prompt = ""
            assistantDraft = ""
        }
        conversationStore.saveAll(next)
        status = "Conversation deleted from this device."
    }

    val speechInput = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val spokenText = result.data
                ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()
                .orEmpty()
            if (spokenText.isNotBlank()) {
                val wakeAskedForLive = routeWakeSpeechToLive
                routeWakeSpeechToLive = false
                if (isLiveCommand(spokenText)) {
                    destination = Destination.LIVE
                    status = "Live workspace opened. Use + to share an app, screen or file."
                } else {
                    val appTarget = extractOpenAppTarget(spokenText)
                    if (appTarget != null) {
                        val resultMessage = launchInstalledApp(context, appTarget, appAccessStore)
                        messages.add(ChatMessage("user", spokenText))
                        messages.add(ChatMessage("assistant", resultMessage))
                        persistCurrentConversation()
                        prompt = ""
                        destination = Destination.CHAT
                        status = resultMessage
                    } else if (wakeAskedForLive) {
                        liveQuestion = spokenText
                        destination = Destination.LIVE
                        status = "Voice transcript ready. Review it in Live, then tap Ask Jarvis."
                    } else {
                        prompt = listOf(prompt.trim(), spokenText).filter(String::isNotBlank).joinToString(" ")
                        destination = Destination.CHAT
                        status = "Voice input is ready. Review it before sending."
                    }
                }
            } else {
                routeWakeSpeechToLive = false
                status = "I couldn't catch that. Try once more."
            }
        } else {
            routeWakeSpeechToLive = false
            status = "Voice input cancelled."
        }
    }

    val screenCaptureInput = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val data = result.data
        if (result.resultCode == android.app.Activity.RESULT_OK && data != null) {
            try {
                val serviceIntent = LiveScreenCaptureService.startIntent(context, result.resultCode, data)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent)
                } else {
                    context.startService(serviceIntent)
                }
                context.startService(
                    Intent(context, LiveScreenCaptureService::class.java)
                        .setAction(LiveScreenCaptureService.ACTION_PAUSE),
                )
                destination = Destination.LIVE
                status = "Screen sharing is active. Switch to an app, then return here to ask about it."
            } catch (error: Exception) {
                status = error.message ?: "Android couldn't start screen sharing."
            }
        } else {
            status = "Screen sharing was cancelled. Nothing was captured."
        }
    }

    val overlayPermissionInput = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(context)) {
            context.startService(
                Intent(context, LiveScreenCaptureService::class.java)
                    .setAction(LiveScreenCaptureService.ACTION_SHOW_OVERLAY),
            )
            status = "Floating Jarvis is enabled. Switch to another app while sharing to see the panel."
        } else {
            status = "Overlay permission was not enabled. In-app Live and screen sharing still work."
        }
    }

    val attachmentInput = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            scope.launch {
                status = "Reading the selected file locally…"
                try {
                    val loaded = withContext(Dispatchers.IO) { loadLiveAttachment(context, uri) }
                    liveAttachment = loaded
                    destination = Destination.LIVE
                    status = "Attached ${loaded.displayName}. Review it in Live, then choose Ask."
                } catch (error: Exception) {
                    status = error.message ?: "Couldn't read that attachment."
                }
            }
        } else {
            status = "File selection cancelled."
        }
    }

    fun stopWakeWord() {
        val detector = wakeDetector
        wakeDetector = null
        wakeListening = false
        try {
            detector?.stop()
        } catch (_: Exception) {
            // A detector may already have stopped after a wake-word callback.
        }
        detector?.release()
    }

    fun startWakeWord() {
        if (wakeListening || busy) return
        try {
            val detector = OpenWakeWord.Builder(context.applicationContext)
                .setModel(OpenWakeWord.BuiltInModel.HEY_JARVIS)
                .setThreshold(0.5f)
                .setDebounceMs(2500L)
                .build()
            wakeDetector = detector
            detector.start { score ->
                Handler(Looper.getMainLooper()).post {
                    status = "Hey Jarvis detected (" + (score * 100).toInt() + "%)."
                    destination = Destination.LIVE
                    routeWakeSpeechToLive = true
                    stopWakeWord()
                    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                        putExtra(
                            RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                            RecognizerIntent.LANGUAGE_MODEL_FREE_FORM,
                        )
                        putExtra(RecognizerIntent.EXTRA_PROMPT, "I'm listening. What's on your mind?")
                    }
                    try {
                        speechInput.launch(intent)
                    } catch (_: ActivityNotFoundException) {
                        routeWakeSpeechToLive = false
                        status = "Wake word detected, but no speech recognizer is installed."
                    }
                }
            }
            wakeListening = true
            tts?.stop()
            status = "Listening for Hey Jarvis while this app stays open."
        } catch (error: Exception) {
            stopWakeWord()
            status = error.message ?: "Wake-word engine couldn't start."
        }
    }

    val microphonePermission = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) startWakeWord()
        else status = "Microphone permission was denied. Listening stays off."
    }

    fun requestWakeWord() {
        if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            startWakeWord()
        } else {
            microphonePermission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP && wakeListening) {
                stopWakeWord()
                status = "Listening paused because Jarvis left the foreground."
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            stopWakeWord()
        }
    }

    fun requestScreenShare() {
        if (liveSharing) {
            status = "Screen sharing is already active."
            destination = Destination.LIVE
            return
        }
        try {
            val manager = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            screenCaptureInput.launch(manager.createScreenCaptureIntent())
        } catch (error: Exception) {
            status = error.message ?: "Android couldn't request screen sharing."
        }
    }

    fun stopScreenShare() {
        try {
            context.startService(LiveScreenCaptureService.stopIntent(context))
            status = "Screen sharing stopped."
        } catch (error: Exception) {
            status = error.message ?: "Couldn't stop screen sharing."
        }
    }

    fun enableFloatingPanel() {
        if (!liveSharing) {
            status = "Start screen sharing before enabling the floating panel."
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(context)) {
            overlayPermissionInput.launch(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:${context.packageName}"),
                ),
            )
        } else {
            context.startService(
                Intent(context, LiveScreenCaptureService::class.java)
                    .setAction(LiveScreenCaptureService.ACTION_SHOW_OVERLAY),
            )
            status = "Floating Jarvis is enabled. Switch to another app to see the panel."
        }
    }

    fun hideFloatingPanel() {
        context.startService(
            Intent(context, LiveScreenCaptureService::class.java)
                .setAction(LiveScreenCaptureService.ACTION_HIDE_OVERLAY),
        )
        status = "Floating panel disabled. Screen sharing remains active."
    }

    DisposableEffect(context) {
        val engine = TextToSpeech(context.applicationContext) { result ->
            ttsReady = result == TextToSpeech.SUCCESS
            if (!ttsReady) status = "Text-to-speech isn't available on this device."
        }
        tts = engine
        onDispose {
            engine.stop()
            engine.shutdown()
            tts = null
            ttsReady = false
        }
    }

    LaunchedEffect(destination, ttsReady) {
        if (destination == Destination.LIVE && ttsReady && !liveGreetingSpoken) {
            liveGreetingSpoken = true
            tts?.language = Locale.US
            tts?.speak(
                "Jarvis online. How may I assist?",
                TextToSpeech.QUEUE_FLUSH,
                null,
                "jarvis-live-welcome",
            )
        }
    }

    LaunchedEffect(Unit) {
        if (serverUrl.isNotBlank() && (apiKey.isNotBlank() || api.isLocalLoopbackHttp(serverUrl))) {
            status = "Restoring your saved AI connection…"
            try {
                val modelsFound = withContext(Dispatchers.IO) {
                    api.checkConnection(serverUrl, apiKey)
                }
                if (modelsFound.isNotEmpty()) {
                    availableModels = modelsFound
                    if (model !in modelsFound) model = modelsFound.first()
                    settingsStore.saveModel(model)
                    connected = true
                    status = "Saved AI connection restored · ${modelsFound.size} model(s) available."
                }
            } catch (_: Exception) {
                connected = false
                status = "Saved settings kept. Reconnect your AI server when it is available."
            }
        }
    }

    LaunchedEffect(initialSharedText) {
        if (initialSharedText.isNotBlank()) {
            prompt = initialSharedText
            destination = Destination.CHAT
            status = "Shared text is attached. Review it, then ask me about it."
        }
    }

    fun connectToServer() {
        if (busy) return
        scope.launch {
            busy = true
            connected = false
            connectionFeedback = ""
            status = "Checking your OpenJarvis server…"
            try {
                api.validateServerUrl(serverUrl)
                require(apiKey.isNotBlank() || api.isLocalLoopbackHttp(serverUrl)) {
                    "Enter the server API key. A blank key is accepted only for this device's local loopback server."
                }
                val modelsFound = withContext(Dispatchers.IO) {
                    api.checkConnection(serverUrl, apiKey)
                }
                require(modelsFound.isNotEmpty()) {
                    "The server responded, but it returned no model IDs. Check that its /v1/models endpoint is configured."
                }
                availableModels = modelsFound
                val requestedModel = model.trim()
                require(requestedModel.isBlank() || requestedModel in modelsFound) {
                    "Model ID not found: $requestedModel. Available model IDs: ${modelsFound.joinToString(", ")}"
                }
                if (apiKey.isBlank()) keyStore.clear() else keyStore.saveApiKey(apiKey)
                keyStore.saveServerUrl(serverUrl.trim().trimEnd('/'))
                if (requestedModel.isBlank()) model = modelsFound.first()
                settingsStore.saveModel(model)
                connected = true
                connectionFeedback = "Connected. ${modelsFound.size} model(s) found; selected model: $model"
                status = "AI server connected · ${modelsFound.size} model(s) found."
            } catch (error: Exception) {
                val detail = when (error) {
                    is java.net.UnknownHostException -> "Server host not found. Check the server URL."
                    is java.net.ConnectException -> "Couldn't reach the server. Check that it is running and the port is correct."
                    is java.net.SocketTimeoutException -> "The server took too long to respond. Check the connection and try again."
                    is javax.net.ssl.SSLException -> "Secure HTTPS connection failed. Check the server certificate and URL."
                    else -> error.message ?: "Couldn't connect. Check the URL, API key and model ID."
                }
                connectionFeedback = detail
                status = detail
            } finally {
                busy = false
            }
        }
    }

    fun sendMessage(
        screenFrame: String? = null,
        questionOverride: String? = null,
        attachmentImages: List<String> = emptyList(),
        attachmentText: String? = null,
    ) {
        val question = (questionOverride ?: prompt).trim()
        if (question.isBlank() || busy) return

        if (screenFrame == null && attachmentImages.isEmpty() && attachmentText.isNullOrBlank()) {
            val appTarget = extractOpenAppTarget(question)
            if (appTarget != null) {
                val resultMessage = launchInstalledApp(context, appTarget, appAccessStore)
                messages.add(ChatMessage("user", question))
                messages.add(ChatMessage("assistant", resultMessage))
                persistCurrentConversation()
                prompt = ""
                if (questionOverride != null) liveQuestion = ""
                status = resultMessage
                return
            }
        }

        if (serverUrl.isBlank() || model.isBlank() ||
            (apiKey.isBlank() && !api.isLocalLoopbackHttp(serverUrl))
        ) {
            status = "Connect your AI server in Settings before chatting."
            destination = Destination.SETTINGS
            return
        }

        val rememberMatch = Regex(
            "^remember(?:\\s+that)?\\s+(.+)$",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
        ).find(question)
        var memorySaved = false
        val explicitMemory = rememberMatch?.groupValues?.getOrNull(1)?.trim().orEmpty()
        if (explicitMemory.isNotBlank()) {
            memoryStore.add(explicitMemory)
            memories.clear()
            memories.addAll(memoryStore.load())
            memorySaved = true
        }

        val messageContent = if (attachmentText.isNullOrBlank()) question else {
            "$question\n\n$attachmentText"
        }
        if (questionOverride != null) liveQuestion = ""
        messages.add(ChatMessage("user", messageContent, screenFrame, attachmentImages))
        persistCurrentConversation()
        prompt = ""
        assistantDraft = ""
        val systemContext = buildCompanionSystemPrompt(
            personality = personality,
            language = replyLanguage,
            memories = memories.toList(),
        )
        val conversation = messages.takeLast(40)
            .map { ChatMessage(it.role, it.content, it.imageBase64, it.imageBase64List) }
            .toMutableList()
        if (!screenFrame.isNullOrBlank() || attachmentImages.isNotEmpty()) {
            val currentQuestionIndex = conversation.indexOfLast {
                it.role == "user" && it.content == messageContent
            }
            if (currentQuestionIndex >= 0) {
                conversation[currentQuestionIndex] = ChatMessage(
                    "user",
                    messageContent,
                    screenFrame,
                    attachmentImages,
                )
            }
        }
        val requestMessages = listOf(ChatMessage("system", systemContext)) + conversation
        busy = true
        status = if (memorySaved) "Saved to Memory. Thinking…" else "Thinking with your personal context…"
        scope.launch {
            try {
                val response = api.streamMessage(
                    serverUrl,
                    apiKey,
                    model,
                    requestMessages,
                ) { delta ->
                    assistantDraft += delta
                }
                messages.add(ChatMessage("assistant", response))
                persistCurrentConversation()
                assistantDraft = ""
                status = if (memorySaved) {
                    "Reply complete · I saved that detail to Memory."
                } else {
                    "Reply complete · conversation saved on this device."
                }
                if (speakReplies && ttsReady) {
                    tts?.language = speechLocale(replyLanguage)
                    tts?.speak(response, TextToSpeech.QUEUE_FLUSH, null, "jarvis-reply")
                }
            } catch (error: Exception) {
                assistantDraft = ""
                status = error.message ?: "I couldn't complete that message."
            } finally {
                busy = false
            }
        }
    }

    fun speakLatestReply() {
        val latest = messages.lastOrNull { it.role == "assistant" }?.content
        if (ttsReady && !latest.isNullOrBlank()) {
            tts?.language = speechLocale(replyLanguage)
            tts?.speak(latest, TextToSpeech.QUEUE_FLUSH, null, "jarvis-last-reply")
            status = "Speaking the latest reply."
        } else {
            status = "There isn't a reply ready to speak yet."
        }
    }

    fun addMemory() {
        val value = memoryDraft.trim()
        if (value.isBlank()) return
        memoryStore.add(value)
        memories.clear()
        memories.addAll(memoryStore.load())
        memoryDraft = ""
        status = "Added to your private Memory bank."
    }

    fun removeMemory(value: String) {
        memoryStore.remove(value)
        memories.clear()
        memories.addAll(memoryStore.load())
        status = "Memory item removed."
    }

    Scaffold(
        containerColor = Void,
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Void)
                    .padding(start = 18.dp, end = 18.dp, top = 12.dp, bottom = 10.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(Brush.linearGradient(listOf(Cyan, Blue))),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("J", color = Void, fontWeight = FontWeight.Black)
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("J.A.R.V.I.S.", color = White, fontWeight = FontWeight.Bold)
                        Text("PERSONAL COMPANION", color = Muted, style = MaterialTheme.typography.labelSmall)
                    }
                    StatusPill(
                        label = if (liveSharing) "LIVE SHARING" else if (wakeListening) "LISTENING" else if (connected) "AI LINKED" else "STANDBY",
                        color = if (liveSharing || wakeListening) Green else if (connected) Cyan else Muted,
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    status,
                    color = Muted,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        },
        bottomBar = {
            NavigationBar(containerColor = Color(0xFF09131F), contentColor = White) {
                Destination.values().forEach { item ->
                    NavigationBarItem(
                        selected = destination == item,
                        onClick = { destination = item },
                        icon = { Text(item.symbol, style = MaterialTheme.typography.titleMedium) },
                        label = { Text(item.label) },
                        alwaysShowLabel = true,
                    )
                }
            }
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Void),
        ) {
            when (destination) {
                Destination.HOME -> HomeScreen(
                    connected = connected,
                    model = model,
                    wakeListening = wakeListening,
                    liveSharing = liveSharing,
                    memoryCount = memories.size,
                    messageCount = messages.size,
                    onChat = { destination = Destination.CHAT },
                    onMemory = { destination = Destination.MEMORY },
                    onHub = { destination = Destination.HUB },
                    onLive = { destination = Destination.LIVE },
                    onSettings = { destination = Destination.SETTINGS },
                    onStartListening = ::requestWakeWord,
                    onStopListening = {
                        stopWakeWord()
                        status = "Wake-word listening stopped."
                    },
                )
                Destination.CHAT -> ChatScreen(
                    messages = messages,
                    conversations = conversations.toList(),
                    activeConversationId = activeConversationId,
                    assistantDraft = assistantDraft,
                    prompt = prompt,
                    onPromptChange = { prompt = it },
                    busy = busy,
                    connected = connected,
                    model = model,
                    speakReplies = speakReplies,
                    onSpeakChange = { speakReplies = it; settingsStore.saveSpeakReplies(it) },
                    onSend = { sendMessage() },
                    onSpeakLatest = ::speakLatestReply,
                    onDictate = {
                        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                            putExtra(
                                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM,
                            )
                            putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak to Jarvis")
                        }
                        try {
                            speechInput.launch(intent)
                        } catch (_: ActivityNotFoundException) {
                            status = "No speech-recognition app is installed."
                        }
                    },
                    onNewConversation = ::startNewConversation,
                    onSelectConversation = ::openConversation,
                    onRenameConversation = ::renameConversation,
                    onTogglePinConversation = ::toggleConversationPin,
                    onDeleteConversation = ::deleteConversation,
                    onOpenMemory = { destination = Destination.MEMORY },
                    onOpenHub = { destination = Destination.HUB },
                    onOpenLive = { destination = Destination.LIVE },
                )
                Destination.MEMORY -> MemoryScreen(
                    memories = memories,
                    draft = memoryDraft,
                    onDraftChange = { memoryDraft = it },
                    onAdd = ::addMemory,
                    onRemove = ::removeMemory,
                )
                Destination.LIVE -> LiveScreen(
                    isSharing = liveSharing,
                    floatingPanelEnabled = floatingPanelEnabled,
                    onEnableFloatingPanel = ::enableFloatingPanel,
                    onHideFloatingPanel = ::hideFloatingPanel,
                    frameBase64 = liveFrameBase64,
                    attachment = liveAttachment,
                    conversationMessages = messages.toList().takeLast(8),
                    assistantDraft = assistantDraft,
                    busy = busy,
                    question = liveQuestion,
                    onQuestionChange = { liveQuestion = it },
                    onStartSharing = ::requestScreenShare,
                    onStopSharing = ::stopScreenShare,
                    onPickAttachment = {
                        attachmentInput.launch(arrayOf("application/pdf", "image/*", "video/*", "text/plain"))
                    },
                    onTalk = {
                        destination = Destination.LIVE
                        routeWakeSpeechToLive = true
                        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                            putExtra(
                                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM,
                            )
                            putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak to Jarvis Live")
                        }
                        status = "Opening voice input. Speak when the phone prompts you."
                        try {
                            speechInput.launch(intent)
                        } catch (_: ActivityNotFoundException) {
                            routeWakeSpeechToLive = false
                            status = "No speech-recognition app is installed."
                        }
                    },
                    onRemoveAttachment = { liveAttachment = null },
                    onAskAboutContent = { frame, attachment ->
                        val images = attachment?.imageBase64.orEmpty()
                        val attachmentText = attachment?.let { file ->
                            buildString {
                                append("Attached file: ${file.displayName} (${file.mimeType}).")
                                if (file.note.isNotBlank()) append("\n${file.note}")
                                file.extractedText?.takeIf(String::isNotBlank)?.let {
                                    append("\n\nFile text:\n")
                                    append(it)
                                }
                            }
                        }
                        sendMessage(
                            screenFrame = frame,
                            questionOverride = liveQuestion,
                            attachmentImages = images,
                            attachmentText = attachmentText,
                        )
                    },
                )
                Destination.HUB -> HubScreen(
                    context = context,
                    connected = connected,
                    appAccessStore = appAccessStore,
                    onStatus = { status = it },
                )
                Destination.SETTINGS -> SettingsScreen(
                    serverUrl = serverUrl,
                    onServerUrlChange = {
                        serverUrl = it
                        connected = false
                        connectionFeedback = ""
                        availableModels = emptyList()
                        keyStore.saveServerUrl(it)
                    },
                    apiKey = apiKey,
                    onApiKeyChange = {
                        apiKey = it
                        connected = false
                        connectionFeedback = ""
                        if (it.isBlank()) keyStore.clear() else keyStore.saveApiKey(it)
                    },
                    model = model,
                    onModelChange = {
                        model = it
                        connectionFeedback = ""
                        settingsStore.saveModel(it)
                    },
                    availableModels = availableModels,
                    connectionFeedback = connectionFeedback,
                    connected = connected,
                    busy = busy,
                    personality = personality,
                    onPersonalityChange = {
                        personality = it
                        settingsStore.saveProfile(it, replyLanguage)
                    },
                    language = replyLanguage,
                    onLanguageChange = {
                        replyLanguage = it
                        settingsStore.saveProfile(personality, it)
                    },
                    onConnect = ::connectToServer,
                    onSaveProfile = {
                        settingsStore.saveProfile(personality, replyLanguage)
                        status = "Companion profile saved."
                    },
                    onClearKey = {
                        keyStore.clear()
                        keyStore.clearServerUrl()
                        apiKey = ""
                        serverUrl = ""
                        connected = false
                        connectionFeedback = ""
                        model = ""
                        settingsStore.saveModel("")
                        status = "AI connection details removed from this device."
                    },
                )
            }
        }
    }
}

@Composable
private fun HomeScreen(
    connected: Boolean,
    model: String,
    wakeListening: Boolean,
    liveSharing: Boolean,
    memoryCount: Int,
    messageCount: Int,
    onChat: () -> Unit,
    onMemory: () -> Unit,
    onHub: () -> Unit,
    onLive: () -> Unit,
    onSettings: () -> Unit,
    onStartListening: () -> Unit,
    onStopListening: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Good to have you back.", color = White, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            Text("Your day, your ideas, your Jarvis.", color = Muted)
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = Panel),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color(0xFF10283D), Panel)))
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("JARVIS CORE", color = Cyan, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    StatusPill(if (connected) "AI CONNECTED" else "AI NOT CONNECTED", if (connected) Green else Amber)
                }
                JarvisOrb(active = wakeListening || liveSharing)
                Text(
                    when {
                        liveSharing -> "LIVE SCREEN SESSION ACTIVE"
                        wakeListening -> "I'M LISTENING"
                        else -> "READY WHEN YOU ARE"
                    },
                    color = if (liveSharing || wakeListening) Green else Cyan,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    when {
                        liveSharing -> "Your approved screen-share session is active. Open Live to ask about a frame."
                        wakeListening -> "Say “Hey Jarvis” to start talking."
                        else -> "Tap below to speak, or open a conversation."
                    },
                    color = Muted,
                    style = MaterialTheme.typography.bodySmall,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (wakeListening) {
                        OutlinedButton(
                            onClick = onStopListening,
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = White),
                        ) { Text("Stop listening") }
                    } else {
                        Button(
                            onClick = onStartListening,
                            colors = ButtonDefaults.buttonColors(containerColor = Cyan, contentColor = Void),
                        ) { Text("Activate voice") }
                    }
                    FilledTonalButton(onClick = onChat) { Text("Open chat") }
                }
                Text(
                    "Wake-word preview runs only while the app is in the foreground. It pauses when you switch to another app.",
                    color = Muted,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }

        Text("YOUR COMPANION", color = Muted, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MetricCard(
                modifier = Modifier.weight(1f),
                label = "MEMORY",
                value = memoryCount.toString(),
                detail = "Saved by you",
                accent = Cyan,
                onClick = onMemory,
            )
            MetricCard(
                modifier = Modifier.weight(1f),
                label = "CHAT LOG",
                value = messageCount.toString(),
                detail = "Messages on device",
                accent = Blue,
                onClick = onChat,
            )
        }

        Text("QUICK ACCESS", color = Muted, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            QuickActionCard("✧", "Memory bank", "What I should remember", Modifier.weight(1f), onMemory)
            QuickActionCard("⌘", "Phone & tools", "Shortcuts and services", Modifier.weight(1f), onHub)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            QuickActionCard("⚙", "Companion setup", "Voice, model and personality", Modifier.weight(1f), onSettings)
            QuickActionCard("◎", "New conversation", "Think something through", Modifier.weight(1f), onChat)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            QuickActionCard("◉", "Live screen", "Share screen with Jarvis", Modifier.weight(1f), onLive)
        }

        if (model.isNotBlank()) {
            Text("ACTIVE MODEL  ·  " + model, color = Muted, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun JarvisOrb(active: Boolean) {
    val transition = rememberInfiniteTransition(label = "jarvis-orb")
    val pulse by transition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "orb-pulse",
    )
    Box(
        modifier = Modifier.size(198.dp),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val base = size.minDimension * 0.47f
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Cyan.copy(alpha = if (active) 0.30f else 0.20f), Blue.copy(alpha = 0.08f), Color.Transparent),
                    center = center,
                    radius = base * 1.15f,
                ),
                radius = base * 1.15f,
                center = center,
            )
            drawCircle(color = Cyan.copy(alpha = 0.24f), radius = base * pulse, center = center, style = Stroke(width = 1.3.dp.toPx()))
            drawCircle(color = Blue.copy(alpha = 0.70f), radius = base * 0.82f, center = center, style = Stroke(width = 2.dp.toPx()))
            drawCircle(color = Cyan.copy(alpha = 0.70f), radius = base * 0.62f, center = center, style = Stroke(width = 1.dp.toPx()))
            drawArc(
                color = Cyan,
                startAngle = -92f,
                sweepAngle = 250f,
                useCenter = false,
                topLeft = Offset(center.x - base * 0.92f, center.y - base * 0.92f),
                size = androidx.compose.ui.geometry.Size(base * 1.84f, base * 1.84f),
                style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round),
            )
            drawCircle(
                brush = Brush.radialGradient(listOf(Color(0xFFB8F8FF), Cyan, Blue.copy(alpha = 0.30f))),
                radius = base * 0.30f,
                center = center,
            )
        }
        Text("J", color = Void, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Black)
    }
}

@Composable
private fun LiveScreen(
    isSharing: Boolean,
    floatingPanelEnabled: Boolean,
    onEnableFloatingPanel: () -> Unit,
    onHideFloatingPanel: () -> Unit,
    frameBase64: String?,
    attachment: LiveAttachment?,
    conversationMessages: List<ChatMessage>,
    assistantDraft: String,
    busy: Boolean,
    question: String,
    onQuestionChange: (String) -> Unit,
    onStartSharing: () -> Unit,
    onStopSharing: () -> Unit,
    onPickAttachment: () -> Unit,
    onTalk: () -> Unit,
    onRemoveAttachment: () -> Unit,
    onAskAboutContent: (String?, LiveAttachment?) -> Unit,
) {
    var plusMenuExpanded by remember { mutableStateOf(false) }
    val frameBitmap = remember(frameBase64) {
        try {
            val encoded = frameBase64 ?: return@remember null
            val bytes = Base64.decode(encoded, Base64.NO_WRAP)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        } catch (_: Exception) {
            null
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Jarvis Live", color = White, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text("Voice and screen assistance", color = Muted, style = MaterialTheme.typography.bodySmall)
            }
            StatusPill(
                if (busy) "THINKING" else if (isSharing) "SCREEN ON" else "STANDBY",
                if (busy) Amber else if (isSharing) Green else Muted,
            )
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = Panel),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                JarvisOrb(active = isSharing || busy)
                Text(
                    when {
                        busy -> "JARVIS IS THINKING"
                        isSharing && frameBitmap != null -> "SCREEN SHARE ACTIVE · FRAME CAPTURED"
                        isSharing -> "SCREEN SHARE ACTIVE · WAITING FOR FRAME"
                        else -> "NO SCREEN IS BEING SHARED"
                    },
                    color = if (busy || isSharing) Green else Amber,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    when {
                        busy -> "Your request is being processed."
                        isSharing -> "1. Switch to the app you want help with.  2. Return to Jarvis.  3. The latest frame will appear below."
                        else -> "Tap Share app or screen. Android will ask you to choose what Jarvis may view. Nothing is captured before you approve."
                    },
                    color = White,
                    style = MaterialTheme.typography.bodySmall,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (isSharing) {
                        OutlinedButton(onClick = onStopSharing, modifier = Modifier.weight(1f)) {
                            Text("Stop sharing")
                        }
                    } else {
                        Button(
                            onClick = onStartSharing,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = Cyan, contentColor = Void),
                        ) { Text("Share app or screen") }
                    }
                }
                if (isSharing) {
                    OutlinedButton(
                        onClick = if (floatingPanelEnabled) onHideFloatingPanel else onEnableFloatingPanel,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(if (floatingPanelEnabled) "Disable floating Jarvis panel" else "Keep Jarvis visible over other apps")
                    }
                    if (floatingPanelEnabled) {
                        Text(
                            "Floating panel enabled. It appears when you switch to another app; tap Open Jarvis to return here.",
                            color = Green,
                            style = MaterialTheme.typography.labelSmall,
                        )
                    } else {
                        Text(
                            "Optional: grant “Display over other apps” to keep a small Jarvis panel visible while you use another app.",
                            color = Muted,
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = PanelRaised),
        ) {
            Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("SCREEN PREVIEW", color = Cyan, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.weight(1f))
                    Text(
                        if (frameBitmap != null) "Latest frame" else if (isSharing) "Waiting" else "Not shared",
                        color = if (frameBitmap != null) Green else Muted,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
                if (frameBitmap != null) {
                    Image(
                        bitmap = frameBitmap.asImageBitmap(),
                        contentDescription = "Latest captured frame from the user-approved screen share",
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)),
                        contentScale = ContentScale.Fit,
                    )
                } else {
                    Box(
                        modifier = Modifier.fillMaxWidth().height(128.dp).clip(RoundedCornerShape(12.dp)).background(Panel),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            if (isSharing) "Switch to the selected app, then return here. Jarvis will show the latest captured frame."
                            else "No preview yet. Start screen sharing to choose an app or display.",
                            color = Muted,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = Panel),
        ) {
            Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("CONVERSATION", color = Cyan, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.weight(1f))
                    StatusPill(
                        if (busy) "THINKING" else if (conversationMessages.isEmpty()) "READY" else "ACTIVE",
                        if (busy) Amber else Green,
                    )
                }
                if (conversationMessages.isEmpty() && assistantDraft.isBlank()) {
                    Text("Your conversation will appear here. Tap Speak to dictate a question, or type below.", color = Muted, style = MaterialTheme.typography.bodySmall)
                } else {
                    conversationMessages.takeLast(5).forEach { MessageBubble(it) }
                }
                if (assistantDraft.isNotBlank()) MessageBubble(ChatMessage("assistant", assistantDraft))
                if (busy && assistantDraft.isBlank()) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = Cyan)
                        Text("Jarvis is thinking…", color = Muted, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }

        if (attachment != null) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Panel),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("ATTACHED FILE", color = Cyan, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                        Text(attachment.displayName, color = White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(attachment.note.ifBlank { attachment.mimeType }, color = Muted, style = MaterialTheme.typography.labelSmall)
                    }
                    TextButton(onClick = onRemoveAttachment) { Text("Remove") }
                }
            }
        }

        OutlinedTextField(
            value = question,
            onValueChange = onQuestionChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Ask about the screen, or type a question…") },
            minLines = 1,
            maxLines = 4,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            OutlinedButton(onClick = onTalk, enabled = !busy) { Text("🎙 Speak") }
            Box {
                OutlinedButton(onClick = { plusMenuExpanded = true }, enabled = !busy) { Text("+") }
                DropdownMenu(
                    expanded = plusMenuExpanded,
                    onDismissRequest = { plusMenuExpanded = false },
                ) {
                    DropdownMenuItem(
                        text = { Text("Share an app or full screen") },
                        onClick = { plusMenuExpanded = false; onStartSharing() },
                    )
                    DropdownMenuItem(
                        text = { Text("Add PDF, image, video or text file") },
                        onClick = { plusMenuExpanded = false; onPickAttachment() },
                    )
                }
            }
            Button(
                onClick = { onAskAboutContent(if (isSharing) frameBase64 else null, attachment) },
                enabled = question.isNotBlank() && !busy,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = Blue, contentColor = White),
            ) {
                Text("Ask Jarvis")
            }
        }
        Text(
            "Your screen and files stay on this phone until you tap Ask. The AI model must support images to interpret a screen or PDF frame; a text-only model can answer text questions but cannot see the preview.",
            color = Muted,
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

@Composable
private fun ChatScreen(
    messages: List<ChatMessage>,
    conversations: List<SavedConversation>,
    activeConversationId: String,
    assistantDraft: String,
    prompt: String,
    onPromptChange: (String) -> Unit,
    busy: Boolean,
    connected: Boolean,
    model: String,
    speakReplies: Boolean,
    onSpeakChange: (Boolean) -> Unit,
    onSend: () -> Unit,
    onSpeakLatest: () -> Unit,
    onDictate: () -> Unit,
    onNewConversation: () -> Unit,
    onSelectConversation: (String) -> Unit,
    onRenameConversation: (String, String) -> Unit,
    onTogglePinConversation: (String) -> Unit,
    onDeleteConversation: (String) -> Unit,
    onOpenMemory: () -> Unit,
    onOpenHub: () -> Unit,
    onOpenLive: () -> Unit,
) {
    val scroll = rememberScrollState()
    var showHistory by remember { mutableStateOf(false) }
    var showChatMenu by remember { mutableStateOf(false) }
    var showPlusMenu by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<SavedConversation?>(null) }
    var renameDraft by remember { mutableStateOf("") }
    var deleteTarget by remember { mutableStateOf<SavedConversation?>(null) }
    val activeConversation = conversations.firstOrNull { it.id == activeConversationId }

    LaunchedEffect(messages.size, assistantDraft) {
        scroll.animateScrollTo(scroll.maxValue)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box {
                IconButton(onClick = { showChatMenu = true }) {
                    Text("☰", color = White, style = MaterialTheme.typography.titleLarge)
                }
                DropdownMenu(
                    expanded = showChatMenu,
                    onDismissRequest = { showChatMenu = false },
                ) {
                    DropdownMenuItem(
                        text = { Text("New chat") },
                        onClick = { showChatMenu = false; onNewConversation() },
                    )
                    DropdownMenuItem(
                        text = { Text("Chat history") },
                        onClick = { showChatMenu = false; showHistory = true },
                    )
                    DropdownMenuItem(
                        text = { Text(if (activeConversation?.pinned == true) "Unpin this chat" else "Pin this chat") },
                        enabled = activeConversation != null,
                        onClick = {
                            showChatMenu = false
                            activeConversation?.let { onTogglePinConversation(it.id) }
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Rename this chat") },
                        enabled = activeConversation != null,
                        onClick = {
                            showChatMenu = false
                            activeConversation?.let {
                                renameDraft = it.title
                                renameTarget = it
                            }
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Delete this chat", color = MaterialTheme.colorScheme.error) },
                        enabled = activeConversation != null && !busy,
                        onClick = {
                            showChatMenu = false
                            deleteTarget = activeConversation
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(if (speakReplies) "Turn spoken replies off" else "Turn spoken replies on") },
                        onClick = { showChatMenu = false; onSpeakChange(!speakReplies) },
                    )
                    DropdownMenuItem(
                        text = { Text("Read the latest reply aloud") },
                        enabled = messages.any { it.role == "assistant" } && !busy,
                        onClick = { showChatMenu = false; onSpeakLatest() },
                    )
                    DropdownMenuItem(
                        text = { Text("Memory bank") },
                        onClick = { showChatMenu = false; onOpenMemory() },
                    )
                    DropdownMenuItem(
                        text = { Text("Phone & tools") },
                        onClick = { showChatMenu = false; onOpenHub() },
                    )
                }
            }
            Column(Modifier.weight(1f)) {
                Text("J.A.R.V.I.S.", color = White, fontWeight = FontWeight.Bold)
                Text(
                    activeConversation?.title ?: "New conversation",
                    color = Muted,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(scroll),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (messages.isEmpty() && assistantDraft.isEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Panel),
                    shape = RoundedCornerShape(22.dp),
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("At your service.", color = Cyan, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                        Text("What shall we work on?", color = White, style = MaterialTheme.typography.bodyLarge)
                        Text("I can help you learn, reason through a problem or plan your next step.", color = Muted, style = MaterialTheme.typography.bodySmall)
                    }
                }
                AssistPrompt("Teach me a topic from zero, step by step.") {
                    onPromptChange("Teach me a topic from zero, step by step.")
                }
                AssistPrompt("Challenge my plan and identify weak assumptions.") {
                    onPromptChange("Challenge my plan and identify weak assumptions.")
                }
            }
            messages.forEach { message -> MessageBubble(message) }
            if (assistantDraft.isNotEmpty()) MessageBubble(ChatMessage("assistant", assistantDraft))
            if (busy && assistantDraft.isEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = Cyan)
                    Text("Preparing a response…", color = Muted, style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(26.dp),
            colors = CardDefaults.cardColors(containerColor = PanelRaised),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Box {
                    IconButton(onClick = { showPlusMenu = true }, enabled = !busy) {
                        Text("+", color = Cyan, style = MaterialTheme.typography.headlineSmall)
                    }
                    DropdownMenu(
                        expanded = showPlusMenu,
                        onDismissRequest = { showPlusMenu = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text("New conversation") },
                            onClick = { showPlusMenu = false; onNewConversation() },
                        )
                        DropdownMenuItem(
                            text = { Text("Conversation history") },
                            onClick = { showPlusMenu = false; showHistory = true },
                        )
                        DropdownMenuItem(
                            text = { Text("Share a screen or file in Live") },
                            onClick = { showPlusMenu = false; onOpenLive() },
                        )
                    }
                }
                OutlinedTextField(
                    value = prompt,
                    onValueChange = onPromptChange,
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Message Jarvis…") },
                    minLines = 1,
                    maxLines = 6,
                    enabled = !busy,
                )
                IconButton(onClick = onDictate, enabled = !busy) {
                    Text("🎙", style = MaterialTheme.typography.titleMedium)
                }
                Button(
                    onClick = onSend,
                    enabled = !busy && prompt.isNotBlank() &&
                        (connected || extractOpenAppTarget(prompt) != null),
                    modifier = Modifier.size(44.dp),
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(containerColor = Cyan, contentColor = Void),
                ) {
                    Text("↑", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    if (showHistory) {
        AlertDialog(
            onDismissRequest = { showHistory = false },
            title = { Text("Conversation history") },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth().height(420.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    conversations
                        .sortedWith(compareByDescending<SavedConversation> { it.pinned }.thenByDescending { it.updatedAt })
                        .forEach { conversation ->
                            var menuExpanded by remember(conversation.id) { mutableStateOf(false) }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (conversation.id == activeConversationId) PanelRaised else Panel)
                                    .clickable {
                                        showHistory = false
                                        onSelectConversation(conversation.id)
                                    }
                                    .padding(start = 10.dp, end = 2.dp, top = 9.dp, bottom = 9.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        (if (conversation.pinned) "📌 " else "") + conversation.title,
                                        color = White,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = if (conversation.id == activeConversationId) FontWeight.Bold else FontWeight.Normal,
                                    )
                                    Text(
                                        conversation.messages.size.toString() + " messages · " +
                                            android.text.format.DateUtils.getRelativeTimeSpanString(conversation.updatedAt),
                                        color = Muted,
                                        style = MaterialTheme.typography.labelSmall,
                                    )
                                }
                                Box {
                                    IconButton(onClick = { menuExpanded = true }) {
                                        Text("⋮", color = White, style = MaterialTheme.typography.titleLarge)
                                    }
                                    DropdownMenu(
                                        expanded = menuExpanded,
                                        onDismissRequest = { menuExpanded = false },
                                    ) {
                                        DropdownMenuItem(
                                            text = { Text(if (conversation.pinned) "Unpin chat" else "Pin chat") },
                                            onClick = {
                                                menuExpanded = false
                                                onTogglePinConversation(conversation.id)
                                            },
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Rename") },
                                            onClick = {
                                                menuExpanded = false
                                                renameDraft = conversation.title
                                                renameTarget = conversation
                                            },
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                                            onClick = {
                                                menuExpanded = false
                                                deleteTarget = conversation
                                            },
                                        )
                                    }
                                }
                            }
                        }
                }
            },
            confirmButton = { TextButton(onClick = { showHistory = false }) { Text("Done") } },
        )
    }

    renameTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text("Rename conversation") },
            text = {
                OutlinedTextField(
                    value = renameDraft,
                    onValueChange = { renameDraft = it.take(60) },
                    singleLine = true,
                    label = { Text("Conversation title") },
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onRenameConversation(target.id, renameDraft)
                        renameTarget = null
                    },
                    enabled = renameDraft.isNotBlank(),
                ) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { renameTarget = null }) { Text("Cancel") } },
        )
    }

    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Delete this conversation?") },
            text = { Text("Only this saved chat will be deleted. Your other conversations and Memory notes will stay untouched.") },
            confirmButton = {
                TextButton(onClick = {
                    onDeleteConversation(target.id)
                    deleteTarget = null
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun MessageBubble(message: ChatMessage) {
    val user = message.role == "user"
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (user) Arrangement.End else Arrangement.Start,
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(0.90f),
            shape = RoundedCornerShape(
                topStart = 18.dp,
                topEnd = 18.dp,
                bottomStart = if (user) 18.dp else 5.dp,
                bottomEnd = if (user) 5.dp else 18.dp,
            ),
            colors = CardDefaults.cardColors(containerColor = if (user) Color(0xFF123B55) else Panel),
        ) {
            Column(Modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(if (user) "YOU" else "JARVIS", color = if (user) Cyan else Blue, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                if (message.imageBase64 != null || message.imageBase64List.isNotEmpty()) {
                    val count = (if (message.imageBase64 != null) 1 else 0) + message.imageBase64List.size
                    Text("$count VISUAL ATTACHMENT FRAME(S)", color = Cyan, style = MaterialTheme.typography.labelSmall)
                }
                Text(message.content, color = White, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun AssistPrompt(text: String, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = PanelRaised),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("✧", color = Cyan)
            Spacer(Modifier.width(10.dp))
            Text(text, color = White, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun MemoryScreen(
    memories: List<String>,
    draft: String,
    onDraftChange: (String) -> Unit,
    onAdd: () -> Unit,
    onRemove: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("Memory bank", color = White, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text("Only keep details you choose to save. Jarvis receives these notes as context in future chats.", color = Muted)
        Card(colors = CardDefaults.cardColors(containerColor = PanelRaised), shape = RoundedCornerShape(18.dp)) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("WHAT DOES ADD MEMORY DO?", color = Cyan, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                Text("Memory is a small set of notes you explicitly choose to keep, such as your preferred answer language, learning goals or explanation style. Jarvis can use these notes as context in future conversations.", color = White, style = MaterialTheme.typography.bodySmall)
                Text("It is not a copy of your chats. Your conversations are saved separately in Chat > History. Memory does not read Gmail, Google Pay or other apps, and it does not automatically save every message. You can remove any note here.", color = Muted, style = MaterialTheme.typography.bodySmall)
            }
        }
        Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(22.dp)) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("ADD A MEMORY", color = Cyan, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                OutlinedTextField(
                    value = draft,
                    onValueChange = onDraftChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("What should Jarvis remember?") },
                    placeholder = { Text("Example: I prefer practical, step-by-step explanations.") },
                    minLines = 2,
                )
                Button(
                    onClick = onAdd,
                    enabled = draft.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = Cyan, contentColor = Void),
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Save to memory") }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("SAVED DETAILS", color = Muted, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            StatusPill(memories.size.toString() + " ITEMS", Blue)
        }
        if (memories.isEmpty()) {
            Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(18.dp)) {
                Text("Your memory bank is empty. Add something here, or tell Jarvis “Remember that…” in chat.", color = Muted, modifier = Modifier.padding(16.dp))
            }
        } else {
            memories.forEach { item ->
                Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(13.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("◇", color = Cyan)
                        Spacer(Modifier.width(10.dp))
                        Text(item, color = White, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "×",
                            color = Amber,
                            modifier = Modifier
                                .clip(CircleShape)
                                .clickable { onRemove(item) }
                                .padding(8.dp),
                            style = MaterialTheme.typography.titleLarge,
                        )
                    }
                }
            }
        }
        Text("Memory stays in this app's private storage. It is not separately encrypted in this preview; avoid saving passwords or highly sensitive secrets.", color = Muted, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun HubScreen(
    context: Context,
    connected: Boolean,
    appAccessStore: AppAccessStore,
    onStatus: (String) -> Unit,
) {
    var installedApps by remember(context) { mutableStateOf(queryInstalledLauncherApps(context)) }
    var appSearch by remember { mutableStateOf("") }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("Tools & connections", color = White, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text("Useful phone shortcuts today. Deeper integrations are added only when their permissions and data handling are ready.", color = Muted)
        Text("APP LAUNCHER", color = Muted, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
        var appQuery by remember { mutableStateOf("") }
        OutlinedTextField(
            value = appQuery,
            onValueChange = { appQuery = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("App name (e.g. GPay, WhatsApp)") },
            singleLine = true,
        )
        Button(
            onClick = { onStatus(launchInstalledApp(context, appQuery, appAccessStore)) },
            enabled = appQuery.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Open app") }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "APP ACCESS CONTROL",
                color = Muted,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            OutlinedButton(onClick = { installedApps = queryInstalledLauncherApps(context) }) {
                Text("Refresh")
            }
        }
        Text(
            "Choose how Jarvis may interact with each launchable app. Gmail and payment/banking apps start blocked. These are Jarvis settings, not Android system permissions.",
            color = Muted,
            style = MaterialTheme.typography.bodySmall,
        )
        OutlinedTextField(
            value = appSearch,
            onValueChange = { appSearch = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Find an installed app") },
            singleLine = true,
        )
        val matchingApps = installedApps.filter {
            it.label.contains(appSearch, ignoreCase = true) ||
                it.packageName.contains(appSearch, ignoreCase = true)
        }
        Text("${matchingApps.size} of ${installedApps.size} launchable apps", color = Muted, style = MaterialTheme.typography.labelSmall)
        if (matchingApps.isEmpty()) {
            Text("No matching launchable apps were found.", color = Muted, style = MaterialTheme.typography.bodySmall)
        } else {
            matchingApps.forEach { app ->
                AppAccessRow(
                    app = app,
                    initialLevel = appAccessStore.get(app.packageName, app.label),
                    onChange = { level -> appAccessStore.set(app.packageName, level) },
                )
            }
        }

        Text("PHONE SHORTCUTS", color = Muted, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ToolCard("▣", "Calendar", "Open phone calendar", Modifier.weight(1f)) {
                launchIntent(context, Intent(Intent.ACTION_VIEW, CalendarContract.CONTENT_URI), onStatus, "Opened your calendar.")
            }
            ToolCard("◷", "Alarms", "Open clock & alarms", Modifier.weight(1f)) {
                launchIntent(context, Intent(AlarmClock.ACTION_SHOW_ALARMS), onStatus, "Opened your alarms.")
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ToolCard("⌖", "Maps", "Open Google Maps", Modifier.weight(1f)) {
                launchIntent(context, Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=")), onStatus, "Opened Maps.")
            }
            ToolCard("⌕", "Web", "Open browser search", Modifier.weight(1f)) {
                launchIntent(context, Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com")), onStatus, "Opened your browser.")
            }
        }
        Text("SERVICE CONNECTIONS", color = Muted, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
        IntegrationCard(
            title = "OpenJarvis AI server",
            detail = if (connected) "Your chat model is connected." else "Not connected. Add your HTTPS endpoint and key in Settings.",
            status = if (connected) "CONNECTED" else "SETUP NEEDED",
            color = if (connected) Green else Amber,
        )
        IntegrationCard(
            title = "Google Calendar",
            detail = "Native calendar shortcut works. Google OAuth, event reading and 20–30 minute proactive alerts are not wired yet.",
            status = "NOT CONNECTED",
            color = Muted,
        )
        IntegrationCard(
            title = "Gmail",
            detail = "Jarvis has no Gmail read/search/reply access. Gmail is blocked by default in App Access Control; official OAuth is not connected.",
            status = "NOT CONNECTED",
            color = Muted,
        )
        IntegrationCard(
            title = "Screen & documents",
            detail = "Live supports Android-approved app/full-screen sharing, PDFs (up to 5 rendered pages), images, sampled video frames and plain-text files. Content is sent only after you tap Ask.",
            status = "LIVE READY",
            color = Cyan,
        )
        IntegrationCard(
            title = "Phone automation",
            detail = "Voice and Hub commands can open installed apps. Tapping controls or typing into other apps still needs a permission-reviewed Accessibility feature.",
            status = "APP LAUNCH READY",
            color = Green,
        )
    }
}

@Composable
private fun AppAccessRow(
    app: InstalledJarvisApp,
    initialLevel: AppAccessLevel,
    onChange: (AppAccessLevel) -> Unit,
) {
    var selected by remember(app.packageName) { mutableStateOf(initialLevel) }
    androidx.compose.runtime.LaunchedEffect(initialLevel) { selected = initialLevel }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Panel),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Text(app.label, color = White, fontWeight = FontWeight.SemiBold)
            Text(app.packageName, color = Muted, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                AppAccessLevel.values().forEach { level ->
                    FilterChip(
                        selected = selected == level,
                        onClick = {
                            selected = level
                            onChange(level)
                        },
                        label = { Text(level.label) },
                    )
                }
            }
            Text(
                when (selected) {
                    AppAccessLevel.ALLOW_ALL -> "Jarvis may use currently supported actions. Any future in-app control still requires a separate reviewed feature and Android approval."
                    AppAccessLevel.READ_ONLY -> "Jarvis may open this app and use only content you explicitly share through Android."
                    AppAccessLevel.DENY -> "Blocked: Jarvis voice and app-launch commands cannot open this app."
                },
                color = if (selected == AppAccessLevel.DENY) Amber else Muted,
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

private fun launchInstalledApp(context: Context, requestedName: String, appAccessStore: AppAccessStore): String {
    val target = requestedName.trim()
    if (target.isBlank()) return "Tell me which app to open."

    return try {
        val packageManager = context.packageManager
        val launcherQuery = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val apps = packageManager.queryIntentActivities(launcherQuery, 0)
            .mapNotNull { resolveInfo: ResolveInfo ->
                val activity = resolveInfo.activityInfo ?: return@mapNotNull null
                val label = resolveInfo.loadLabel(packageManager)?.toString().orEmpty()
                if (label.isBlank()) null else Triple(label, activity.packageName, resolveInfo)
            }
            .distinctBy { it.second }
        val matches = apps.filter { (label, packageName, _) ->
            appLabelMatchesTarget(label, packageName, target)
        }
        val exact = matches.filter { normalizeAppSearchValue(it.first) == normalizeAppSearchValue(target) }
        val candidate = when {
            exact.size == 1 -> exact.first()
            exact.size > 1 -> return "I found more than one app matching $target. Please use its full app name."
            matches.size == 1 -> matches.first()
            matches.isEmpty() -> return "I couldn't find an installed app named $target."
            else -> return "I found multiple apps matching $target. Please say the full app name."
        }
        val access = appAccessStore.get(candidate.second, candidate.first)
        if (access == AppAccessLevel.DENY) {
            return "Access to ${candidate.first} is denied in Hub > App access control. You can change it there whenever you choose."
        }
        val launchIntent = packageManager.getLaunchIntentForPackage(candidate.second)
            ?: return "Android doesn't provide a launcher screen for ${candidate.first}."
        context.startActivity(launchIntent)
        "Opening ${candidate.first}."
    } catch (_: ActivityNotFoundException) {
        "That app is installed, but Android couldn't open it."
    } catch (error: Exception) {
        error.message ?: "Android couldn't open $target."
    }
}

private fun launchIntent(context: Context, intent: Intent, onStatus: (String) -> Unit, success: String) {
    try {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        onStatus(success)
    } catch (_: ActivityNotFoundException) {
        onStatus("That app or action isn't available on this phone.")
    } catch (_: Exception) {
        onStatus("Android couldn't open that action.")
    }
}

@Composable
private fun SettingsScreen(
    serverUrl: String,
    onServerUrlChange: (String) -> Unit,
    apiKey: String,
    onApiKeyChange: (String) -> Unit,
    model: String,
    onModelChange: (String) -> Unit,
    availableModels: List<String>,
    connectionFeedback: String,
    connected: Boolean,
    busy: Boolean,
    personality: String,
    onPersonalityChange: (String) -> Unit,
    language: String,
    onLanguageChange: (String) -> Unit,
    onConnect: () -> Unit,
    onSaveProfile: () -> Unit,
    onClearKey: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("Companion settings", color = White, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text("Choose the brain, voice behaviour and personality that feel right to you.", color = Muted)

        Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(22.dp)) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("AI CONNECTION", color = Cyan, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.weight(1f))
                    StatusPill(if (connected) "CONNECTED" else "OFFLINE", if (connected) Green else Amber)
                }
                OutlinedTextField(
                    value = serverUrl,
                    onValueChange = onServerUrlChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("OpenJarvis server URL") },
                    placeholder = { Text("https://server.example or http://127.0.0.1:8080") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = onApiKeyChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Server API key") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = model,
                    onValueChange = onModelChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Model ID") },
                    singleLine = true,
                )
                if (availableModels.isNotEmpty()) {
                    Text("Available models: " + availableModels.joinToString(" · "), color = Muted, style = MaterialTheme.typography.labelSmall)
                }
                Button(
                    onClick = onConnect,
                    enabled = !busy && serverUrl.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Cyan, contentColor = Void),
                ) {
                    Text(if (busy) "Checking…" else "Verify & connect")
                }
                if (connectionFeedback.isNotBlank()) {
                    Text(
                        connectionFeedback,
                        color = if (connected) Green else Amber,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                OutlinedButton(onClick = onClearKey, enabled = !busy && (apiKey.isNotBlank() || serverUrl.isNotBlank()), modifier = Modifier.fillMaxWidth()) {
                    Text("Disconnect and clear saved key")
                }
                Text(
                    "Remote servers must use HTTPS. HTTP is accepted only for 127.0.0.1 / localhost on this phone. The API key is kept out of the URL.",
                    color = Muted,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }

        Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(22.dp)) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("PERSONALITY", color = Cyan, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                OutlinedTextField(
                    value = personality,
                    onValueChange = onPersonalityChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("How should Jarvis behave?") },
                    minLines = 4,
                )
                Text("Default: friendly, humorous and intellectually honest. Jarvis should correct you respectfully instead of agreeing just to please you.", color = Muted, style = MaterialTheme.typography.labelSmall)
                Text("REPLY LANGUAGE", color = Muted, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    FilterChip(selected = language == "Auto", onClick = { onLanguageChange("Auto") }, label = { Text("Auto") })
                    FilterChip(selected = language == "English", onClick = { onLanguageChange("English") }, label = { Text("English") })
                    FilterChip(selected = language == "Hindi/Hinglish", onClick = { onLanguageChange("Hindi/Hinglish") }, label = { Text("Hindi / Hinglish") })
                }
                Button(
                    onClick = onSaveProfile,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Blue, contentColor = White),
                ) { Text("Save companion profile") }
            }
        }
        Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(22.dp)) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("ABOUT JARVIS", color = Cyan, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                Text("Your personal, voice-first AI companion", color = White, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text("Jarvis is designed to help you think, learn and use your phone through a friendly voice-and-chat experience. It connects to the OpenAI-compatible AI server you configure; your API key is protected with Android Keystore.", color = Muted, style = MaterialTheme.typography.bodySmall)
                Text("Available in this preview", color = White, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall)
                Text("• Separate saved conversations with pin, rename and delete controls\n• User-managed Memory notes kept separately from chat history\n• Foreground-only Hey Jarvis wake phrase, speech input and spoken replies\n• Local app launching controlled by your per-app access choices\n• User-approved single-app/full-screen sharing and PDF, image, video-frame or text attachments in Live", color = Muted, style = MaterialTheme.typography.bodySmall)
                Text("Not connected yet", color = White, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall)
                Text("Gmail and Google Calendar OAuth, proactive meeting alerts, in-app tapping/typing, full video/audio understanding and background wake-word listening are not enabled in this build.", color = Muted, style = MaterialTheme.typography.bodySmall)
                Text("Security: Gmail and payment apps default to Deny; Jarvis has no payment or transaction controls. Screen capture uses Android's own consent flow; attached content is sent only after you tap Ask.", color = Green, style = MaterialTheme.typography.bodySmall)
            }
        }
        Text("Voice input uses Android's speech recognizer. Spoken replies use the phone's installed text-to-speech voice. A dedicated ElevenLabs voice integration is not part of this build.", color = Muted, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun MetricCard(
    modifier: Modifier,
    label: String,
    value: String,
    detail: String,
    accent: Color,
    onClick: () -> Unit,
) {
    Card(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Panel),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(label, color = accent, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
            Text(value, color = White, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text(detail, color = Muted, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun QuickActionCard(
    symbol: String,
    title: String,
    subtitle: String,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    Card(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Panel),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(symbol, color = Cyan, style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.width(10.dp))
            Column {
                Text(title, color = White, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                Text(subtitle, color = Muted, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun ToolCard(
    symbol: String,
    title: String,
    detail: String,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    Card(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Panel),
    ) {
        Column(Modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(symbol, color = Cyan, style = MaterialTheme.typography.headlineSmall)
            Text(title, color = White, fontWeight = FontWeight.SemiBold)
            Text(detail, color = Muted, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun IntegrationCard(title: String, detail: String, status: String, color: Color) {
    Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, color = White, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                StatusPill(status, color)
            }
            Text(detail, color = Muted, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun StatusPill(label: String, color: Color) {
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(color.copy(alpha = 0.12f))
            .border(1.dp, color.copy(alpha = 0.30f), CircleShape)
            .padding(horizontal = 9.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(6.dp))
        Text(label, color = color, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
    }
}

private fun speechLocale(language: String): Locale = when (language) {
    "English" -> Locale.US
    "Hindi/Hinglish" -> Locale.forLanguageTag("hi-IN")
    else -> Locale.getDefault()
}

private fun buildCompanionSystemPrompt(
    personality: String,
    language: String,
    memories: List<String>,
): String {
    val languageRule = when (language) {
        "English" -> "Reply in clear English."
        "Hindi/Hinglish" -> "Reply naturally in Hindi or Hinglish, matching the user's wording."
        else -> "Reply in the same language and style the user uses; Hindi/Hinglish and English are both welcome."
    }
    val memoryBlock = if (memories.isEmpty()) {
        "No user memories have been saved yet."
    } else {
        memories.take(40).mapIndexed { index, value -> (index + 1).toString() + ". " + value }.joinToString("\n")
    }
    return """
You are Jarvis, the user's long-term personal companion and practical thinking partner, not a generic customer-support bot.
Personality preference: $personality
$languageRule
Adopt a composed, professional, capable JARVIS persona: calm, precise, courteous and quietly confident. Use polished natural language. Be lightly witty only when it genuinely fits; keep humour dry and restrained. Never use slang, excessive banter, or casual forms of address such as bro, bhai, buddy or mate. Do not prepend greetings like Hello or Hi to every reply. Do not repeat the user's name unnecessarily. Keep answers direct and useful, with enough detail to complete the task. Never output the literal strings null or undefined as conversational filler; omit missing values instead. Do not flatter or agree automatically. Correct mistakes respectfully, explain uncertainty, and distinguish facts from guesses. Teach step by step when the user is learning. Remember the ongoing context provided below, but never claim to know something that is not in the context. Ask only the most useful clarification when genuinely needed.
Respect user autonomy. Never claim to have opened apps, sent messages, read accounts, changed settings, or completed actions unless a tool actually did it.
User-approved personal memory notes:
$memoryBlock
""".trim()
}

/** User-controlled notes stored in app-private SharedPreferences. */
private class PersonalMemoryStore(context: Context) {
    private val preferences = context.getSharedPreferences("jarvis_personal_memory", Context.MODE_PRIVATE)

    fun load(): List<String> {
        val raw = preferences.getString(KEY_MEMORIES, null) ?: return emptyList()
        return try {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { index ->
                array.optString(index).takeIf { it.isNotBlank() }
            }.takeLast(MAX_MEMORIES)
        } catch (_: Exception) {
            clear()
            emptyList()
        }
    }

    fun add(value: String) {
        val clean = value.trim()
        if (clean.isBlank()) return
        val updated = (load().filterNot { it.equals(clean, ignoreCase = true) } + clean).takeLast(MAX_MEMORIES)
        save(updated)
    }

    fun remove(value: String) {
        save(load().filterNot { it == value })
    }

    private fun save(values: List<String>) {
        val array = JSONArray()
        values.forEach { array.put(it) }
        preferences.edit().putString(KEY_MEMORIES, array.toString()).apply()
    }

    private fun clear() {
        preferences.edit().remove(KEY_MEMORIES).apply()
    }

    private companion object {
        const val KEY_MEMORIES = "memories"
        const val MAX_MEMORIES = 80
    }
}

/** Non-secret companion preferences; API keys remain in Android Keystore. */
private class CompanionSettingsStore(context: Context) {
    private val preferences = context.getSharedPreferences("jarvis_companion_settings", Context.MODE_PRIVATE)

    fun loadPersonality(): String = preferences.getString(
        KEY_PERSONALITY,
        "Be composed, professional, quietly witty and intellectually honest. Act like a capable personal partner and teacher. Be concise, never use slang or repeated greetings, and correct me respectfully when I am wrong.",
    ).orEmpty()

    fun loadLanguage(): String = preferences.getString(KEY_LANGUAGE, "English").orEmpty()
    fun loadSpeakReplies(): Boolean = preferences.getBoolean(KEY_SPEAK, true)
    fun loadModel(): String = preferences.getString(KEY_MODEL, "").orEmpty()

    fun saveProfile(personality: String, language: String) {
        preferences.edit()
            .putString(KEY_PERSONALITY, personality.trim())
            .putString(KEY_LANGUAGE, language)
            .apply()
    }

    fun saveSpeakReplies(enabled: Boolean) {
        preferences.edit().putBoolean(KEY_SPEAK, enabled).apply()
    }

    fun saveModel(model: String) {
        preferences.edit().putString(KEY_MODEL, model.trim()).apply()
    }

    private companion object {
        const val KEY_PERSONALITY = "personality"
        const val KEY_LANGUAGE = "language"
        const val KEY_SPEAK = "speak_replies"
        const val KEY_MODEL = "model"
    }
}
