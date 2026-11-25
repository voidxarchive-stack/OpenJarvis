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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    JarvisScreen(keyStore)
                }
            }
        }
    }
}

@Composable
private fun JarvisScreen(keyStore: SecureKeyStore) {
    val api = remember { OpenJarvisApi() }
    val scope = rememberCoroutineScope()
    var serverUrl by remember { mutableStateOf("") }
    var apiKey by remember { mutableStateOf("") }
    var model by remember { mutableStateOf("") }
    var prompt by remember { mutableStateOf("") }
    var answer by remember { mutableStateOf("") }
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
                    val models = withContext(Dispatchers.IO) {
                        api.checkConnection(serverUrl, apiKey)
                    }
                    require(models.isNotEmpty()) { "Connected, but the server has no available models." }
                    keyStore.saveApiKey(apiKey)
                    model = models.first()
                    status = "Connected · ${models.size} model(s) available"
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
        Spacer(Modifier.height(4.dp))
        OutlinedTextField(
            value = prompt,
            onValueChange = { prompt = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Ask Jarvis") },
            minLines = 3,
        )
        Button(
            onClick = {
                runTask {
                    require(apiKey.isNotBlank()) { "Connect to your server first." }
                    status = "Thinking…"
                    val result = withContext(Dispatchers.IO) {
                        api.sendMessage(serverUrl, apiKey, model, prompt)
                    }
                    answer = result
                    prompt = ""
                    status = "Reply received"
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
        if (answer.isNotBlank()) {
            Text("Jarvis", style = MaterialTheme.typography.titleMedium)
            Text(answer, style = MaterialTheme.typography.bodyLarge)
        }
    }
}
