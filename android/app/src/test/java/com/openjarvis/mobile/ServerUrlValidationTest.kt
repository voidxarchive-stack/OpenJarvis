package com.openjarvis.mobile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ServerUrlValidationTest {
    private val api = OpenJarvisApi()

    @Test
    fun rejectsPlainHttp() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            api.validateServerUrl("http://192.168.1.10:8000")
        }
        assertEquals(
            "Remote servers must use HTTPS. Plain HTTP is allowed only for this device's loopback address (127.0.0.1 or localhost).",
            error.message,
        )
    }

    @Test
    fun rejectsCredentialsInUrl() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            api.validateServerUrl("https://user:secret@example.com")
        }
        assertEquals(
            "Server URL must not contain credentials, query parameters, or fragments.",
            error.message,
        )
    }

    @Test
    fun normalizesHttpsUrl() {
        assertEquals(
            "https://jarvis.example",
            api.validateServerUrl("https://jarvis.example/"),
        )
    }
}
