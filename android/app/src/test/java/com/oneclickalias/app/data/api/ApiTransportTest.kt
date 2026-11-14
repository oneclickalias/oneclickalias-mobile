package com.oneclickalias.app.data.api

import com.oneclickalias.contracts.auth.ErrorCode
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.Request
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class ApiTransportTest {
    // Test-only payload. Real API models come from api/kotlin/.
    @Serializable
    private data class Echo(val name: String)

    private val server = MockWebServer()
    private val transport = ApiTransport(createHttpClient())

    @Before
    fun setUp() {
        server.start()
    }

    @After
    fun tearDown() {
        server.close()
    }

    private fun get() = Request.Builder().url(server.url("/x")).build()

    private fun json(code: Int, body: String) = MockResponse.Builder()
        .code(code)
        .addHeader("Content-Type", "application/json")
        .body(body)
        .build()

    @Test
    fun `2xx decodes the body`() = runTest {
        server.enqueue(json(200, """{"name":"a","extra":1}"""))

        assertEquals(ApiResult.Success(Echo("a")), transport.call(get(), Echo.serializer()))
    }

    @Test
    fun `204 without body is a unit success`() = runTest {
        server.enqueue(MockResponse.Builder().code(204).build())

        assertEquals(ApiResult.Success(Unit), transport.callNoContent(get()))
    }

    @Test
    fun `error body is read tolerantly as raw code`() = runTest {
        server.enqueue(json(409, """{"error":"device_limit_reached","message":"ignored"}"""))

        val result = transport.call(get(), Echo.serializer())

        assertEquals(ApiResult.HttpError(409, "device_limit_reached"), result)
        assertEquals(ErrorCode.DEVICE_LIMIT_REACHED, (result as ApiResult.HttpError).authErrorCode())
    }

    @Test
    fun `unknown error code survives as raw string and maps to no known code`() = runTest {
        server.enqueue(json(418, """{"error":"brand_new_code","message":"m"}"""))

        val result = transport.call(get(), Echo.serializer()) as ApiResult.HttpError

        assertEquals(ApiResult.HttpError(418, "brand_new_code"), result)
        assertNull(result.authErrorCode())
    }

    @Test
    fun `broken or missing error body becomes unknown`() = runTest {
        server.enqueue(json(500, "<html>bad gateway</html>"))
        server.enqueue(json(502, ""))
        server.enqueue(json(400, """{"error":42}"""))
        server.enqueue(json(400, """["not","an","object"]"""))

        repeat(4) {
            val result = transport.call(get(), Echo.serializer()) as ApiResult.HttpError
            assertEquals(ApiResult.UNKNOWN_ERROR_CODE, result.code)
        }
    }

    @Test
    fun `2xx with a body that does not match the model is an unknown http error`() = runTest {
        server.enqueue(json(200, """{"nope":true}"""))
        server.enqueue(json(200, "not json"))

        repeat(2) {
            assertEquals(
                ApiResult.HttpError(200, ApiResult.UNKNOWN_ERROR_CODE),
                transport.call(get(), Echo.serializer())
            )
        }
    }

    @Test
    fun `unreachable server is a network error`() = runTest {
        val url = server.url("/x")
        server.close()

        val result = transport.call(Request.Builder().url(url).build(), Echo.serializer())

        assertEquals(ApiResult.NetworkError(NetworkErrorKind.Unreachable), result)
    }

    @Test
    fun `slow server is a timeout`() = runTest {
        server.enqueue(json(200, """{"name":"a"}""").newBuilder().headersDelay(2, TimeUnit.SECONDS).build())
        val impatient = ApiTransport(createHttpClient().newBuilder().readTimeout(200, TimeUnit.MILLISECONDS).build())

        assertEquals(ApiResult.NetworkError(NetworkErrorKind.Timeout), impatient.call(get(), Echo.serializer()))
    }

    @Test
    fun `success result hides its value in toString`() {
        assertEquals("Success(<redacted>)", ApiResult.Success("secret-token").toString())
    }
}
