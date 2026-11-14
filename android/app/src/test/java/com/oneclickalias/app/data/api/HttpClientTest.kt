package com.oneclickalias.app.data.api

import kotlinx.serialization.Serializable
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * Proves the test setup for API work: a real OkHttp client from [createHttpClient]
 * talks to MockWebServer, and [ApiJson] (de)serializes the payloads.
 */
class HttpClientTest {
    // Test-only payload. Real API models come from api/kotlin/ (generated from the OpenAPI specs).
    @Serializable
    private data class Echo(val name: String, val count: Int)

    private val server = MockWebServer()

    @Before
    fun setUp() {
        server.start()
    }

    @After
    fun tearDown() {
        server.close()
    }

    @Test
    fun `client round-trips json through mock web server`() {
        server.enqueue(
            MockResponse.Builder()
                .code(200)
                .addHeader("Content-Type", "application/json")
                // Unknown fields must not break decoding: servers may add fields.
                .body("""{"name":"alias","count":3,"added_later":true}""")
                .build()
        )
        val client = createHttpClient()
        val request = Request.Builder()
            .url(server.url("/api/echo"))
            .post(ApiJson.encodeToString(Echo("ping", 1)).toRequestBody("application/json".toMediaType()))
            .build()

        val decoded = client.newCall(request).execute().use { response ->
            assertEquals(200, response.code)
            ApiJson.decodeFromString<Echo>(response.body.string())
        }

        assertEquals(Echo("alias", 3), decoded)
        val recorded = server.takeRequest()
        assertEquals("/api/echo", recorded.url.encodedPath)
        assertEquals("""{"name":"ping","count":1}""", recorded.body?.utf8())
    }

    @Test
    fun `client uses bounded timeouts`() {
        val client = createHttpClient()
        assertEquals(10_000, client.connectTimeoutMillis)
        assertEquals(10_000, client.readTimeoutMillis)
        assertEquals(10_000, client.writeTimeoutMillis)
        assertEquals(30_000, client.callTimeoutMillis)
    }
}
