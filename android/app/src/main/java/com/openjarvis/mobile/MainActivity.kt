package com.openjarvis.mobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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

    LaunchedEffect(Unit) {
        apiKey = keyStore.loadApiKey().orEmpty()
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
