package com.openjarvis.mobile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenJarvisApiValidationTest {
    private val api = OpenJarvisApi()

    @Test
    fun acceptsLocalLoopbackHttpServer() {
        assertEquals("http://127.0.0.1:8080", api.validateServerUrl("http://127.0.0.1:8080/"))
        assertTrue(api.isLocalLoopbackHttp("http://127.0.0.1:8080"))
    }

    @Test
    fun acceptsRemoteHttpsServer() {
        assertEquals("https://server.example", api.validateServerUrl("https://server.example/"))
        assertFalse(api.isLocalLoopbackHttp("https://server.example"))
    }

    @Test
    fun rejectsRemotePlainHttp() {
        try {
            api.validateServerUrl("http://server.example")
            throw AssertionError("Remote HTTP must be rejected")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message.orEmpty().contains("HTTPS"))
        }
    }

    @Test
    fun rejectsUrlCredentialsAndQueryParameters() {
        listOf(
            "https://user:pass@server.example",
            "https://server.example?token=secret",
            "https://server.example#fragment",
        ).forEach { value ->
            try {
                api.validateServerUrl(value)
                throw AssertionError("Unsafe URL components must be rejected: $value")
            } catch (expected: IllegalArgumentException) {
                assertTrue(expected.message.orEmpty().isNotBlank())
            }
        }
    }
}
