package com.oneclickalias.app.data.api

import com.oneclickalias.app.data.session.InMemorySessionStore
import com.oneclickalias.app.data.session.Session
import com.oneclickalias.app.data.session.SessionManager
import com.oneclickalias.contracts.ams.Alias
import com.oneclickalias.contracts.ams.AliasList
import com.oneclickalias.contracts.ams.ErrorCode as AmsErrorCode
import com.oneclickalias.contracts.ams.Recipient
import com.oneclickalias.contracts.ams.RecipientList
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class AmsApiTest {
    private val server = MockWebServer()
    private val transport = ApiTransport(createHttpClient())
    private val store = InMemorySessionStore()

    @Before
    fun setUp() {
        server.start()
        val now = System.currentTimeMillis()
        store.stored = Session("at-0", "rt-0", DEVICE, now + 900_000, now)
    }

    @After
    fun tearDown() {
        server.close()
    }

    private suspend fun api(): AmsApi {
        val sessions = SessionManager(AuthApi(transport, server.url("/")), store)
        sessions.restore()
        return AmsApi(transport, server.url("/"), sessions)
    }

    private fun enqueue(response: MockResponse) = server.enqueue(response)

    private fun take(): RecordedRequest = server.takeRequest()

    @Test
    fun `list aliases sends a bearer GET and decodes the list`() = runTest {
        enqueue(json("""{"aliases":[$ALIAS_JSON]}"""))

        val result = api().listAliases()

        assertEquals(AliasList(listOf(ALIAS)), result.value())
        val request = take()
        assertEquals("GET", request.method)
        assertEquals("/api/aliases", request.url.encodedPath)
        assertEquals("Bearer at-0", request.headers["Authorization"])
    }

    @Test
    fun `create alias posts the recipient id and decodes the new alias`() = runTest {
        enqueue(json(ALIAS_JSON, code = 201))

        val result = api().createAlias(RECIPIENT_ID)

        assertEquals(ALIAS, result.value())
        val request = take()
        assertEquals("POST", request.method)
        assertEquals("/api/aliases", request.url.encodedPath)
        assertEquals("""{"recipient_id":"$RECIPIENT_ID"}""", request.body?.utf8())
        assertEquals("Bearer at-0", request.headers["Authorization"])
    }

    @Test
    fun `tracker blocking is patched as a flag only`() = runTest {
        enqueue(json(ALIAS_JSON.replace("\"block_trackers\":true", "\"block_trackers\":false")))

        val result = api().setBlockTrackers(ALIAS.id, blockTrackers = false)

        assertEquals(false, result.value().blockTrackers)
        val request = take()
        assertEquals("PATCH", request.method)
        assertEquals("/api/aliases/${ALIAS.id}", request.url.encodedPath)
        assertEquals("""{"block_trackers":false}""", request.body?.utf8())
    }

    @Test
    fun `delete alias sends DELETE and accepts 204`() = runTest {
        enqueue(MockResponse.Builder().code(204).build())

        val result = api().deleteAlias(ALIAS.id)

        assertEquals(ApiResult.Success(Unit), result)
        val request = take()
        assertEquals("DELETE", request.method)
        assertEquals("/api/aliases/${ALIAS.id}", request.url.encodedPath)
        assertEquals("Bearer at-0", request.headers["Authorization"])
    }

    @Test
    fun `alias ids are sent as one encoded path segment`() = runTest {
        enqueue(MockResponse.Builder().code(204).build())

        api().deleteAlias("a/b?c")

        assertEquals("/api/aliases/a%2Fb%3Fc", take().url.encodedPath)
    }

    @Test
    fun `list recipients decodes verified and unverified addresses`() = runTest {
        enqueue(
            json(
                """{"recipients":[
                {"id":"$RECIPIENT_ID","email":"me@example.com","verified":true,"created_at":"2026-11-01T10:00:00Z"},
                {"id":"r2","email":"new@example.com","verified":false,"created_at":"2026-11-02T10:00:00Z"}]}"""
            )
        )

        val result = api().listRecipients()

        assertEquals(
            RecipientList(
                listOf(
                    Recipient(RECIPIENT_ID, "me@example.com", true, "2026-11-01T10:00:00Z"),
                    Recipient("r2", "new@example.com", false, "2026-11-02T10:00:00Z")
                )
            ),
            result.value()
        )
        val request = take()
        assertEquals("GET", request.method)
        assertEquals("/api/recipients", request.url.encodedPath)
        assertEquals("Bearer at-0", request.headers["Authorization"])
    }

    @Test
    fun `payment required is an http error with its code`() = runTest {
        enqueue(error(403, "payment_required"))

        val result = api().createAlias(RECIPIENT_ID)

        assertEquals(ApiResult.HttpError(403, "payment_required"), result)
        assertEquals(AmsErrorCode.PAYMENT_REQUIRED, (result as ApiResult.HttpError).amsErrorCode())
    }

    @Test
    fun `write rate limit is an http error with its code`() = runTest {
        enqueue(error(429, "rate_limited"))

        val result = api().setBlockTrackers(ALIAS.id, blockTrackers = true)

        assertEquals(ApiResult.HttpError(429, "rate_limited"), result)
        assertEquals(AmsErrorCode.RATE_LIMITED, (result as ApiResult.HttpError).amsErrorCode())
    }

    @Test
    fun `recipient errors keep their codes`() = runTest {
        enqueue(error(400, "recipient_not_verified"))
        enqueue(error(404, "not_found"))

        val api = api()

        assertEquals(ApiResult.HttpError(400, "recipient_not_verified"), api.createAlias(RECIPIENT_ID))
        assertEquals(ApiResult.HttpError(404, "not_found"), api.deleteAlias(ALIAS.id))
    }

    @Test
    fun `create recipient posts the address and decodes the new recipient`() = runTest {
        enqueue(json(RECIPIENT_JSON.replace("\"verified\":true", "\"verified\":false"), code = 201))

        val result = api().createRecipient("me@example.com")

        assertEquals(RECIPIENT.copy(verified = false), result.value())
        val request = take()
        assertEquals("POST", request.method)
        assertEquals("/api/recipients", request.url.encodedPath)
        assertEquals("""{"email":"me@example.com"}""", request.body?.utf8())
        assertEquals("Bearer at-0", request.headers["Authorization"])
    }

    @Test
    fun `resend posts to the recipient's resend path`() = runTest {
        enqueue(MockResponse.Builder().code(204).build())

        val result = api().resendRecipientConfirmation(RECIPIENT_ID)

        assertEquals(ApiResult.Success(Unit), result)
        val request = take()
        assertEquals("POST", request.method)
        assertEquals("/api/recipients/$RECIPIENT_ID/resend", request.url.encodedPath)
        assertEquals("Bearer at-0", request.headers["Authorization"])
    }

    @Test
    fun `delete recipient sends a bearer DELETE`() = runTest {
        enqueue(MockResponse.Builder().code(204).build())

        val result = api().deleteRecipient(RECIPIENT_ID)

        assertEquals(ApiResult.Success(Unit), result)
        val request = take()
        assertEquals("DELETE", request.method)
        assertEquals("/api/recipients/$RECIPIENT_ID", request.url.encodedPath)
    }

    @Test
    fun `recipient write errors keep their codes`() = runTest {
        enqueue(error(409, "already_exists"))
        enqueue(error(409, "recipient_in_use"))
        enqueue(error(409, "already_verified"))
        enqueue(error(403, "payment_required"))

        val api = api()

        assertEquals(AmsErrorCode.ALREADY_EXISTS, (api.createRecipient("a@b.c") as ApiResult.HttpError).amsErrorCode())
        assertEquals(
            AmsErrorCode.RECIPIENT_IN_USE,
            (api.deleteRecipient(RECIPIENT_ID) as ApiResult.HttpError).amsErrorCode()
        )
        assertEquals(
            AmsErrorCode.ALREADY_VERIFIED,
            (api.resendRecipientConfirmation(RECIPIENT_ID) as ApiResult.HttpError).amsErrorCode()
        )
        assertEquals(
            AmsErrorCode.PAYMENT_REQUIRED,
            (api.deleteRecipient(RECIPIENT_ID) as ApiResult.HttpError).amsErrorCode()
        )
    }

    @Test
    fun `recipient ids cannot escape their path`() = runTest {
        enqueue(error(400, "invalid_recipient_id"))

        api().deleteRecipient("../aliases")

        assertEquals("/api/recipients/..%2Faliases", take().url.encodedPath)
    }

    @Test
    fun `codes unknown to this app version map to null`() = runTest {
        enqueue(error(418, "brand_new_code"))

        val result = api().listAliases() as ApiResult.HttpError

        assertEquals("brand_new_code", result.code)
        assertNull(result.amsErrorCode())
    }

    @Test
    fun `a 2xx body that does not match the model is an unknown error`() = runTest {
        enqueue(json("""{"aliases":"nope"}"""))

        assertEquals(ApiResult.HttpError(200, ApiResult.UNKNOWN_ERROR_CODE), api().listAliases())
    }

    @Test
    fun `401 refreshes the token once and retries`() = runTest {
        enqueue(error(401, "unauthorized"))
        enqueue(json("""{"accessToken":"at-1","refreshToken":"rt-1","expiresIn":900}"""))
        enqueue(json("""{"aliases":[]}"""))

        val result = api().listAliases()

        assertEquals(AliasList(emptyList()), result.value())
        assertEquals("/api/aliases", take().url.encodedPath)
        assertEquals("/auth/refresh", take().url.encodedPath)
        assertEquals("Bearer at-1", take().headers["Authorization"])
    }

    @Test
    fun `no connection is a network error`() = runTest {
        val api = api()
        server.close()

        assertEquals(ApiResult.NetworkError(NetworkErrorKind.Unreachable), api.listRecipients())
    }

    private fun <T> ApiResult<T>.value(): T = (this as ApiResult.Success<T>).value

    private companion object {
        const val DEVICE = "9315bf6d-7672-44db-9c09-2dd3212c9b0a"
        const val RECIPIENT_ID = "d1e2f3a4-0000-4000-8000-000000000001"
        const val ALIAS_JSON =
            """{"id":"a1b2c3d4-0000-4000-8000-000000000001","alias":"quiet-otter-1234@alias.test",""" +
                """"dest_email":"me@example.com","recipient_id":"$RECIPIENT_ID","block_trackers":true,""" +
                """"created_at":"2026-11-10T10:00:00Z","updated_at":"2026-11-10T10:00:00Z"}"""
        const val RECIPIENT_JSON =
            """{"id":"$RECIPIENT_ID","email":"me@example.com","verified":true,"created_at":"2026-11-01T10:00:00Z"}"""
        val RECIPIENT = Recipient(RECIPIENT_ID, "me@example.com", true, "2026-11-01T10:00:00Z")
        val ALIAS = Alias(
            id = "a1b2c3d4-0000-4000-8000-000000000001",
            alias = "quiet-otter-1234@alias.test",
            destEmail = "me@example.com",
            recipientId = RECIPIENT_ID,
            blockTrackers = true,
            createdAt = "2026-11-10T10:00:00Z",
            updatedAt = "2026-11-10T10:00:00Z"
        )

        fun json(body: String, code: Int = 200) = MockResponse.Builder()
            .code(code)
            .addHeader("Content-Type", "application/json")
            .body(body)
            .build()

        fun error(code: Int, error: String) = json("""{"error":"$error","message":"m"}""", code)
    }
}
