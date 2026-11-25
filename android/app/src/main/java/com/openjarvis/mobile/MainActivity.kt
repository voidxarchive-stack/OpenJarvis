package com.openjarvis.mobile

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.AlarmClock
import android.provider.CalendarContract
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
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
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
        setContent {
            JarvisTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = Void) {
                    JarvisScreen(keyStore, historyStore, sharedText)
                }
            }
        }
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
) {
    val context = LocalContext.current
    val api = remember { OpenJarvisApi() }
    val settingsStore = remember { CompanionSettingsStore(context) }
    val memoryStore = remember { PersonalMemoryStore(context) }
    val scope = rememberCoroutineScope()
    val messages = remember {
        mutableStateListOf<ChatMessage>().apply { addAll(historyStore.load()) }
    }
    val memories = remember {
        mutableStateListOf<String>().apply { addAll(memoryStore.load()) }
    }

    var destination by remember { mutableStateOf(Destination.HOME) }
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
    var busy by remember { mutableStateOf(false) }
    var connected by remember { mutableStateOf(false) }
    var wakeDetector by remember { mutableStateOf<OpenWakeWord?>(null) }
    var wakeListening by remember { mutableStateOf(false) }
    var tts by remember { mutableStateOf<TextToSpeech?>(null) }
    var ttsReady by remember { mutableStateOf(false) }

    val speechInput = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val spokenText = result.data
                ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()
                .orEmpty()
            if (spokenText.isNotBlank()) {
                prompt = listOf(prompt.trim(), spokenText).filter(String::isNotBlank).joinToString(" ")
                destination = Destination.CHAT
                status = "Voice input is ready. Review it before sending."
            } else {
                status = "I couldn't catch that. Try once more."
            }
        } else {
            status = "Voice input cancelled."
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

    DisposableEffect(context) {
        val owner = context as? LifecycleOwner
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP && wakeListening) {
                stopWakeWord()
                status = "Listening paused because Jarvis left the foreground."
            }
        }
        owner?.lifecycle?.addObserver(observer)
        onDispose {
            owner?.lifecycle?.removeObserver(observer)
            stopWakeWord()
        }
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
            status = "Checking your OpenJarvis server…"
            try {
                require(apiKey.isNotBlank()) { "Enter your server API key first." }
                val modelsFound = withContext(Dispatchers.IO) {
                    api.checkConnection(serverUrl, apiKey)
                }
                require(modelsFound.isNotEmpty()) { "Connected, but the server returned no models." }
                keyStore.saveApiKey(apiKey)
                keyStore.saveServerUrl(serverUrl.trim().trimEnd('/'))
                availableModels = modelsFound
                if (model !in modelsFound) model = modelsFound.first()
                settingsStore.saveModel(model)
                connected = true
                status = "Connected securely · " + modelsFound.size + " model(s) found."
            } catch (error: Exception) {
                status = error.message ?: "Couldn't connect. Check the URL and key."
            } finally {
                busy = false
            }
        }
    }

    fun sendMessage() {
        val question = prompt.trim()
        if (question.isBlank() || busy) return
        if (apiKey.isBlank() || serverUrl.isBlank() || model.isBlank()) {
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

        messages.add(ChatMessage("user", question))
        historyStore.save(messages.toList())
        prompt = ""
        assistantDraft = ""
        val systemContext = buildCompanionSystemPrompt(
            personality = personality,
            language = replyLanguage,
            memories = memories.toList(),
        )
        val requestMessages = listOf(ChatMessage("system", systemContext)) + messages.takeLast(40)
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
                historyStore.save(messages.toList())
                assistantDraft = ""
                status = if (memorySaved) {
                    "Reply complete · I saved that detail to Memory."
                } else {
                    "Reply complete · conversation saved on this device."
                }
                if (speakReplies && ttsReady) {
                    tts?.language = Locale.getDefault()
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
            tts?.language = Locale.getDefault()
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
                        label = if (wakeListening) "LISTENING" else if (connected) "AI LINKED" else "STANDBY",
                        color = if (wakeListening) Green else if (connected) Cyan else Muted,
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
                    memoryCount = memories.size,
                    messageCount = messages.size,
                    onChat = { destination = Destination.CHAT },
                    onMemory = { destination = Destination.MEMORY },
                    onHub = { destination = Destination.HUB },
                    onSettings = { destination = Destination.SETTINGS },
                    onStartListening = ::requestWakeWord,
                    onStopListening = {
                        stopWakeWord()
                        status = "Wake-word listening stopped."
                    },
                )
                Destination.CHAT -> ChatScreen(
                    messages = messages,
                    assistantDraft = assistantDraft,
                    prompt = prompt,
                    onPromptChange = { prompt = it },
                    busy = busy,
                    connected = connected,
                    model = model,
                    speakReplies = speakReplies,
                    onSpeakChange = { speakReplies = it; settingsStore.saveSpeakReplies(it) },
                    onSend = ::sendMessage,
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
                    onClear = {
                        messages.clear()
                        historyStore.clear()
                        tts?.stop()
                        status = "Conversation history cleared from this device."
                    },
                )
                Destination.MEMORY -> MemoryScreen(
                    memories = memories,
                    draft = memoryDraft,
                    onDraftChange = { memoryDraft = it },
                    onAdd = ::addMemory,
                    onRemove = ::removeMemory,
                )
                Destination.HUB -> HubScreen(
                    context = context,
                    connected = connected,
                    onStatus = { status = it },
                )
                Destination.SETTINGS -> SettingsScreen(
                    serverUrl = serverUrl,
                    onServerUrlChange = { serverUrl = it },
                    apiKey = apiKey,
                    onApiKeyChange = { apiKey = it },
                    model = model,
                    onModelChange = { model = it; settingsStore.saveModel(it) },
                    availableModels = availableModels,
                    connected = connected,
                    busy = busy,
                    personality = personality,
                    onPersonalityChange = { personality = it },
                    language = replyLanguage,
                    onLanguageChange = { replyLanguage = it },
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
    memoryCount: Int,
    messageCount: Int,
    onChat: () -> Unit,
    onMemory: () -> Unit,
    onHub: () -> Unit,
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
                JarvisOrb(active = wakeListening)
                Text(
                    if (wakeListening) "I'M LISTENING" else "READY WHEN YOU ARE",
                    color = if (wakeListening) Green else Cyan,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    if (wakeListening) "Say “Hey Jarvis” to start talking." else "Tap below to speak, or open a conversation.",
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
                    "Wake-word preview runs only while the app is visible. It pauses when you leave this screen.",
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
private fun ChatScreen(
    messages: List<ChatMessage>,
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
    onClear: () -> Unit,
) {
    val scroll = rememberScrollState()
    LaunchedEffect(messages.size, assistantDraft) {
        scroll.animateScrollTo(scroll.maxValue)
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Your conversation", color = White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(
                    if (connected) "Connected · " + model else "Connect your model in Settings",
                    color = if (connected) Green else Amber,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
            OutlinedButton(onClick = onClear, enabled = !busy && messages.isNotEmpty()) { Text("Clear") }
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(scroll),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (messages.isEmpty() && assistantDraft.isEmpty()) {
                Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(22.dp)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Let's think together.", color = Cyan, style = MaterialTheme.typography.titleMedium)
                        Text("Ask me to explain a concept, review an idea, plan your day or help you solve a problem.", color = Muted)
                        Text("Tip: start a message with “Remember that…” when you want to save a detail to your Memory bank.", color = Muted, style = MaterialTheme.typography.bodySmall)
                    }
                }
                listOf(
                    "Teach me a topic from zero, step by step.",
                    "Challenge my current plan and point out weak assumptions.",
                    "Help me organise what I need to do today.",
                ).forEach { suggestion ->
                    AssistPrompt(suggestion) { onPromptChange(suggestion) }
                }
            }
            messages.forEach { message ->
                MessageBubble(message)
            }
            if (assistantDraft.isNotEmpty()) {
                MessageBubble(ChatMessage("assistant", assistantDraft))
            }
            if (busy && assistantDraft.isEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = Cyan)
                    Text("Jarvis is thinking…", color = Muted, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = PanelRaised),
        ) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = prompt,
                    onValueChange = onPromptChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Message Jarvis") },
                    placeholder = { Text("Tell me what's on your mind…") },
                    minLines = 2,
                    maxLines = 5,
                    enabled = !busy,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(onClick = onDictate, enabled = !busy) { Text("Mic") }
                    Spacer(Modifier.width(8.dp))
                    OutlinedButton(onClick = onSpeakLatest, enabled = !busy && messages.any { it.role == "assistant" }) {
                        Text("Speak")
                    }
                    Spacer(Modifier.weight(1f))
                    Button(
                        onClick = onSend,
                        enabled = !busy && prompt.isNotBlank() && connected,
                        colors = ButtonDefaults.buttonColors(containerColor = Cyan, contentColor = Void),
                    ) {
                        Text(if (busy) "Working…" else "Send")
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Speak replies aloud", color = Muted, style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.weight(1f))
                    Switch(checked = speakReplies, onCheckedChange = onSpeakChange)
                }
            }
        }
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
    onStatus: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("Tools & connections", color = White, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text("Useful phone shortcuts today. Deeper integrations are added only when their permissions and data handling are ready.", color = Muted)
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
            detail = "Gmail can be opened from your phone. Read/search/reply through official OAuth is a future milestone.",
            status = "NOT CONNECTED",
            color = Muted,
        )
        IntegrationCard(
            title = "Screen & documents",
            detail = "Text shared from another app can be sent into Chat. PDF, image and live screen understanding are not implemented yet.",
            status = "TEXT SHARE READY",
            color = Cyan,
        )
        IntegrationCard(
            title = "Phone automation",
            detail = "This build offers explicit app shortcuts only. Accessibility-based tapping and typing are not enabled.",
            status = "SAFE SHORTCUTS ONLY",
            color = Muted,
        )
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
                    placeholder = { Text("https://your-server.example") },
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
                    enabled = !busy && serverUrl.isNotBlank() && apiKey.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Cyan, contentColor = Void),
                ) {
                    Text(if (busy) "Connecting…" else "Verify & connect")
                }
                OutlinedButton(onClick = onClearKey, enabled = !busy && apiKey.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
                    Text("Disconnect and clear saved key")
                }
                Text("Use a trusted HTTPS endpoint. Your API key is encrypted with Android Keystore and is never placed in the URL.", color = Muted, style = MaterialTheme.typography.labelSmall)
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
Be friendly, warm, lightly humorous when natural, and intellectually honest. Do not flatter or agree automatically. Correct mistakes respectfully, explain uncertainty, and distinguish facts from guesses. Teach step by step when the user is learning. Remember the ongoing context provided below, but never claim to know something that is not in the context. Ask only the most useful clarification when genuinely needed.
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
        "Be friendly, humorous and intellectually honest. Act like a supportive partner and teacher. Correct me respectfully when I am wrong; do not agree just to please me.",
    ).orEmpty()

    fun loadLanguage(): String = preferences.getString(KEY_LANGUAGE, "Auto").orEmpty()
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
