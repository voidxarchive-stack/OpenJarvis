package com.openjarvis.mobile

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import ai.picovoice.porcupine.Porcupine
import ai.picovoice.porcupine.PorcupineManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val keyStore = SecureKeyStore(applicationContext)
        val historyStore = ChatHistoryStore(applicationContext)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    JarvisScreen(keyStore, historyStore)
                }
            }
        }
    }
}

@Composable
private fun JarvisScreen(keyStore: SecureKeyStore, historyStore: ChatHistoryStore) {
    val api = remember { OpenJarvisApi() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val messages = remember {
        mutableStateListOf<ChatMessage>().apply { addAll(historyStore.load()) }
    }
    var serverUrl by remember { mutableStateOf("") }
    var apiKey by remember { mutableStateOf("") }
    var model by remember { mutableStateOf("") }
    var prompt by remember { mutableStateOf("") }
    var assistantDraft by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("Connect to your OpenJarvis server to begin.") }
    var busy by remember { mutableStateOf(false) }
    var speakReplies by remember { mutableStateOf(true) }
    var tts by remember { mutableStateOf<TextToSpeech?>(null) }
    var ttsReady by remember { mutableStateOf(false) }
    var wakeWordKey by remember { mutableStateOf("") }
    var porcupineManager by remember { mutableStateOf<PorcupineManager?>(null) }
    var wakeListening by remember { mutableStateOf(false) }

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
                status = "Voice input added. Review it, then send when ready."
            } else {
                status = "No speech was recognised. Please try again."
            }
        } else {
            status = "Voice input cancelled."
        }
    }

    fun stopWakeWord() {
        val manager = porcupineManager
        porcupineManager = null
        wakeListening = false
        try {
            manager?.stop()
        } catch (_: Exception) {
            // The engine may already have stopped after a wake-word callback.
        }
        manager?.delete()
    }

    fun startWakeWord() {
        if (wakeWordKey.isBlank()) {
            status = "Enter and save your Picovoice AccessKey first."
            return
        }
        if (wakeListening) return
        try {
            val manager = PorcupineManager.Builder()
                .setAccessKey(wakeWordKey.trim())
                .setKeyword(Porcupine.BuiltInKeyword.JARVIS)
                .build(context) {
                    Handler(Looper.getMainLooper()).post {
                        status = "Wake word detected. Speak your message."
                        stopWakeWord()
                        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                            putExtra(
                                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM,
                            )
                            putExtra(RecognizerIntent.EXTRA_PROMPT, "What can I help you with?")
                        }
                        try {
                            speechInput.launch(intent)
                        } catch (_: ActivityNotFoundException) {
                            status = "Wake word detected, but no speech recognizer is installed."
                        }
                    }
                }
            porcupineManager = manager
            manager.start()
            wakeListening = true
            tts?.stop()
            status = "Listening for 'Jarvis' while OpenJarvis remains in the foreground."
        } catch (error: Exception) {
            stopWakeWord()
            status = error.message ?: "Wake-word engine could not start."
        }
    }

    val microphonePermission = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) startWakeWord()
        else status = "Microphone permission was denied. Wake-word listening remains off."
    }

    DisposableEffect(context) {
        val owner = context as? LifecycleOwner
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP && wakeListening) {
                stopWakeWord()
                status = "Wake-word listening paused because OpenJarvis left the foreground."
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
            if (!ttsReady) status = "Text-to-speech is unavailable on this device."
        }
        tts = engine
        onDispose {
            engine.stop()
            engine.shutdown()
            tts = null
            ttsReady = false
        }
    }

    LaunchedEffect(Unit) {
        apiKey = keyStore.loadApiKey().orEmpty()
        wakeWordKey = keyStore.loadWakeWordAccessKey().orEmpty()
    }

    fun runTask(task: suspend () -> Unit) {
        if (busy) return
        scope.launch {
            busy = true
            try {
                task()
            } catch (error: Exception) {
                status = error.message ?: "Something went wrong."
            } finally {
                busy = false
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("OpenJarvis", style = MaterialTheme.typography.headlineLarge)
        Text("Your personal AI companion · Android preview")
        OutlinedTextField(
            value = serverUrl,
            onValueChange = { serverUrl = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Server URL (HTTPS)") },
            placeholder = { Text("https://your-server.example") },
            singleLine = true,
        )
        OutlinedTextField(
            value = apiKey,
            onValueChange = { apiKey = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("OpenJarvis API key") },
            visualTransformation = PasswordVisualTransformation(),
            singleLine = true,
        )
        Button(
            onClick = {
                runTask {
                    require(apiKey.isNotBlank()) { "Enter your server API key." }
                    status = "Checking server…"
                    val modelsFound = withContext(Dispatchers.IO) {
                        api.checkConnection(serverUrl, apiKey)
                    }
                    require(modelsFound.isNotEmpty()) {
                        "Connected, but the server has no available models."
                    }
                    keyStore.saveApiKey(apiKey)
                    model = modelsFound.first()
                    status = "Connected · ${modelsFound.size} model(s) available"
                }
            },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Save key & connect")
        }
        if (model.isNotBlank()) {
            Text("Model: $model", style = MaterialTheme.typography.labelLarge)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Conversation", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.weight(1f))
            Button(
                onClick = {
                    messages.clear()
                    historyStore.clear()
                    assistantDraft = ""
                    tts?.stop()
                    status = "Local conversation history cleared."
                },
                enabled = !busy && messages.isNotEmpty(),
            ) {
                Text("Clear")
            }
        }
        messages.forEach { message ->
            Text(
                if (message.role == "user") "You" else "Jarvis",
                style = MaterialTheme.typography.labelLarge,
            )
            Text(message.content, style = MaterialTheme.typography.bodyLarge)
        }
        if (assistantDraft.isNotEmpty()) {
            Text("Jarvis", style = MaterialTheme.typography.labelLarge)
            Text(assistantDraft, style = MaterialTheme.typography.bodyLarge)
        }
        OutlinedTextField(
            value = prompt,
            onValueChange = { prompt = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Ask Jarvis") },
            minLines = 3,
            enabled = !busy,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = {
                    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                        putExtra(
                            RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                            RecognizerIntent.LANGUAGE_MODEL_FREE_FORM,
                        )
                        putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak your message to Jarvis")
                    }
                    try {
                        speechInput.launch(intent)
                    } catch (_: ActivityNotFoundException) {
                        status = "No speech recognition app is available on this device."
                    }
                },
                enabled = !busy,
                modifier = Modifier.weight(1f),
            ) {
                Text("Speak")
            }
            Button(
                onClick = {
                    tts?.stop()
                    val lastReply = messages.lastOrNull { it.role == "assistant" }?.content
                    if (ttsReady && !lastReply.isNullOrBlank()) {
                        tts?.language = Locale.getDefault()
                        tts?.speak(lastReply, TextToSpeech.QUEUE_FLUSH, null, "jarvis-last-reply")
                        status = "Speaking the latest reply."
                    } else {
                        status = "No reply is ready to speak yet."
                    }
                },
                enabled = !busy && ttsReady && messages.any { it.role == "assistant" },
                modifier = Modifier.weight(1f),
            ) {
                Text("Speak reply")
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Automatically speak replies")
            Spacer(Modifier.weight(1f))
            Switch(checked = speakReplies, onCheckedChange = { speakReplies = it })
        }
        Text("Wake word", style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(
            value = wakeWordKey,
            onValueChange = { wakeWordKey = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Picovoice AccessKey") },
            visualTransformation = PasswordVisualTransformation(),
            singleLine = true,
            enabled = !wakeListening,
        )
        Button(
            onClick = {
                try {
                    keyStore.saveWakeWordAccessKey(wakeWordKey.trim())
                    status = "Wake-word AccessKey saved securely on this device."
                } catch (error: Exception) {
                    status = error.message ?: "Could not save wake-word AccessKey."
                }
            },
            enabled = !wakeListening && wakeWordKey.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Save wake-word key")
        }
        Text(
            "Wake detection runs on-device for the built-in 'Jarvis' trigger and only while this screen is in the foreground. It stops when you leave the app.",
            style = MaterialTheme.typography.bodySmall,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = {
                    if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                        startWakeWord()
                    } else {
                        microphonePermission.launch(Manifest.permission.RECORD_AUDIO)
                    }
                },
                enabled = !busy && !wakeListening,
                modifier = Modifier.weight(1f),
            ) {
                Text("Start listening")
            }
            Button(
                onClick = {
                    stopWakeWord()
                    status = "Wake-word listening stopped."
                },
                enabled = wakeListening,
                modifier = Modifier.weight(1f),
            ) {
                Text("Stop")
            }
        }
        Button(
            onClick = {
                val question = prompt.trim()
                runTask {
                    require(apiKey.isNotBlank() && model.isNotBlank()) {
                        "Connect to your server first."
                    }
                    require(question.isNotBlank()) { "Enter a message first." }
                    messages.add(ChatMessage("user", question))
                    prompt = ""
                    assistantDraft = ""
                    status = "Jarvis is thinking…"
                    val response = api.streamMessage(
                        serverUrl,
                        apiKey,
                        model,
                        messages.toList(),
                    ) { delta ->
                        assistantDraft += delta
                    }
                    messages.add(ChatMessage("assistant", response))
                    historyStore.save(messages.toList())
                    assistantDraft = ""
                    status = "Reply complete · conversation saved on this device"
                    if (speakReplies && ttsReady) {
                        tts?.language = Locale.getDefault()
                        tts?.speak(response, TextToSpeech.QUEUE_FLUSH, null, "jarvis-reply")
                    }
                }
            },
            enabled = !busy && model.isNotBlank() && prompt.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Send message")
        }
        if (busy) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CircularProgressIndicator()
                Text(status)
            }
        } else {
            Text(status, style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.height(8.dp))
    }
}
