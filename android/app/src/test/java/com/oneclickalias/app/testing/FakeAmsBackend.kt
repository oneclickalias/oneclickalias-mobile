package com.oneclickalias.app.testing

import com.oneclickalias.app.data.api.ApiJson
import com.oneclickalias.contracts.ams.Alias
import com.oneclickalias.contracts.ams.AliasList
import com.oneclickalias.contracts.ams.AliasRequest
import com.oneclickalias.contracts.ams.AliasSettingsRequest
import com.oneclickalias.contracts.ams.ClaimResult
import com.oneclickalias.contracts.ams.Recipient
import com.oneclickalias.contracts.ams.RecipientList
import com.oneclickalias.contracts.ams.RecipientRequest
import java.util.Collections
import java.util.concurrent.atomic.AtomicInteger
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.RecordedRequest

/**
 * In-memory Alias Management Service for MockWebServer: keeps aliases and
 * recipients, answers like the real API (oldest first, recipient checks) and
 * records every request. Any single endpoint can be overridden per test.
 */
class FakeAmsBackend : Dispatcher() {
    val requests: MutableList<String> = Collections.synchronizedList(mutableListOf())
    val bodies: MutableList<String> = Collections.synchronizedList(mutableListOf())
    val aliases: MutableList<Alias> = Collections.synchronizedList(mutableListOf())
    val recipients: MutableList<Recipient> = Collections.synchronizedList(mutableListOf())

    /** Ready aliases of the (single) device; not in [aliases] until claimed. */
    val ready: MutableList<Alias> = Collections.synchronizedList(mutableListOf())

    /** Access tokens the fake accepts; null accepts every bearer token. */
    var validTokens: Set<String>? = null

    var listAliasesResponse: (() -> MockResponse)? = null
    var listReadyResponse: (() -> MockResponse)? = null
    var ensureReadyResponse: (() -> MockResponse)? = null
    var claimResponse: (() -> MockResponse)? = null
    var createResponse: (() -> MockResponse)? = null
    var patchResponse: (() -> MockResponse)? = null
    var deleteResponse: (() -> MockResponse)? = null
    var listRecipientsResponse: (() -> MockResponse)? = null
    var createRecipientResponse: (() -> MockResponse)? = null
    var resendResponse: (() -> MockResponse)? = null
    var deleteRecipientResponse: (() -> MockResponse)? = null

    private val counter = AtomicInteger(1000)

    /** Writes recorded so far (POST, PATCH, DELETE), e.g. "POST /api/aliases". */
    val writes: List<String> get() = requests.filterNot { it.startsWith("GET") }

    fun addRecipient(id: String, email: String, verified: Boolean = true): Recipient =
        Recipient(id, email, verified, "2026-11-01T10:00:${pad(recipients.size)}Z").also { recipients += it }

    fun addAlias(name: String, recipient: Recipient, createdAt: String = nextTimestamp()): Alias = Alias(
        id = "alias-${counter.incrementAndGet()}",
        alias = "$name@alias.test",
        destEmail = recipient.email,
        recipientId = recipient.id,
        blockTrackers = true,
        createdAt = createdAt,
        updatedAt = createdAt
    ).also { aliases += it }

    override fun dispatch(request: RecordedRequest): MockResponse {
        val path = request.url.encodedPath
        val line = "${request.method} $path"
        requests += line
        val body = request.body?.utf8().orEmpty()
        bodies += body
        val token = request.headers["Authorization"]?.removePrefix("Bearer ")
        if (token == null || validTokens?.contains(token) == false) return error(401, "unauthorized")
        val id = path.removePrefix("/api/aliases/")
        return when {
            line == "GET /api/aliases" -> listAliasesResponse?.invoke()
                ?: json(ApiJson.encodeToString(AliasList.serializer(), AliasList(aliases.toList())))

            line == "POST /api/aliases" -> createResponse?.invoke() ?: create(body)

            line == "GET /api/aliases/ready" -> listReadyResponse?.invoke()
                ?: json(ApiJson.encodeToString(AliasList.serializer(), AliasList(ready.toList())))

            line == "POST /api/aliases/ready" -> ensureReadyResponse?.invoke() ?: ensureReady(body)

            request.method == "POST" && path.startsWith("/api/aliases/") && path.endsWith("/claim") ->
                claimResponse?.invoke() ?: claim(id.removeSuffix("/claim"))

            line == "GET /api/recipients" -> listRecipientsResponse?.invoke()
                ?: json(ApiJson.encodeToString(RecipientList.serializer(), RecipientList(recipients.toList())))

            line == "POST /api/recipients" -> createRecipientResponse?.invoke() ?: createRecipient(body)

            request.method == "POST" && path.startsWith("/api/recipients/") && path.endsWith("/resend") ->
                resendResponse?.invoke() ?: resend(path.removePrefix("/api/recipients/").removeSuffix("/resend"))

            request.method == "DELETE" && path.startsWith("/api/recipients/") ->
                deleteRecipientResponse?.invoke() ?: deleteRecipient(path.removePrefix("/api/recipients/"))

            request.method == "PATCH" && path.startsWith("/api/aliases/") -> patchResponse?.invoke() ?: patch(id, body)

            request.method == "DELETE" && path.startsWith("/api/aliases/") -> deleteResponse?.invoke() ?: delete(id)

            else -> error(404, "not_found")
        }
    }

    private fun create(body: String): MockResponse {
        val recipientId = ApiJson.decodeFromString(AliasRequest.serializer(), body).recipientId
        val recipient = recipients.firstOrNull { it.id == recipientId } ?: return error(404, "not_found")
        if (!recipient.verified) return error(400, "recipient_not_verified")
        val alias = addAlias("new-alias-${counter.get() + 1}", recipient)
        return json(ApiJson.encodeToString(Alias.serializer(), alias), code = 201)
    }

    /** A ready alias for the recipient, named like the server would (word-word-1234). */
    fun addReady(recipient: Recipient): Alias {
        val n = counter.incrementAndGet()
        val alias = Alias(
            id = "ready-$n",
            alias = "ready-alias-$n@alias.test",
            destEmail = recipient.email,
            recipientId = recipient.id,
            blockTrackers = true,
            createdAt = nextTimestamp(),
            updatedAt = nextTimestamp()
        )
        ready += alias
        return alias
    }

    private fun ensureReady(body: String): MockResponse {
        val recipientId = ApiJson.decodeFromString(AliasRequest.serializer(), body).recipientId
        val recipient = recipients.firstOrNull { it.id == recipientId } ?: return error(404, "not_found")
        if (!recipient.verified) return error(400, "recipient_not_verified")
        ready.firstOrNull { it.recipientId == recipientId }?.let {
            return json(ApiJson.encodeToString(Alias.serializer(), it))
        }
        return json(ApiJson.encodeToString(Alias.serializer(), addReady(recipient)), 201)
    }

    private fun claim(id: String): MockResponse {
        aliases.firstOrNull { it.id == id }?.let {
            return json(ApiJson.encodeToString(ClaimResult.serializer(), ClaimResult(it)))
        }
        val alias = ready.firstOrNull { it.id == id } ?: return error(404, "not_found")
        ready.remove(alias)
        aliases += alias
        val next = recipients.firstOrNull { it.id == alias.recipientId }?.let { addReady(it) }
        return json(ApiJson.encodeToString(ClaimResult.serializer(), ClaimResult(alias, next)))
    }

    private fun patch(id: String, body: String): MockResponse {
        val index = aliases.indexOfFirst { it.id == id }
        if (index < 0) return error(404, "not_found")
        val flag = ApiJson.decodeFromString(AliasSettingsRequest.serializer(), body).blockTrackers
        val updated = aliases[index].copy(blockTrackers = flag)
        aliases[index] = updated
        return json(ApiJson.encodeToString(Alias.serializer(), updated))
    }

    private fun delete(id: String): MockResponse = if (aliases.removeIf { it.id == id }) {
        MockResponse.Builder().code(204).build()
    } else {
        error(404, "not_found")
    }

    private fun createRecipient(body: String): MockResponse {
        val email = ApiJson.decodeFromString(RecipientRequest.serializer(), body).email.trim().lowercase()
        if (!email.contains('@')) return error(400, "invalid_dest_email")
        if (recipients.any { it.email == email }) return error(409, "already_exists")
        val recipient = addRecipient("r-${counter.incrementAndGet()}", email, verified = false)
        return json(ApiJson.encodeToString(Recipient.serializer(), recipient), code = 201)
    }

    private fun resend(id: String): MockResponse {
        val recipient = recipients.firstOrNull { it.id == id } ?: return error(404, "not_found")
        return if (recipient.verified) error(409, "already_verified") else MockResponse.Builder().code(204).build()
    }

    private fun deleteRecipient(id: String): MockResponse {
        if (aliases.any { it.recipientId == id }) return error(409, "recipient_in_use")
        return if (recipients.removeIf { it.id == id }) {
            MockResponse.Builder().code(204).build()
        } else {
            error(404, "not_found")
        }
    }

    private fun nextTimestamp(): String = "2026-11-10T10:${pad(aliases.size / 60)}:${pad(aliases.size % 60)}Z"

    private fun pad(n: Int) = n.toString().padStart(2, '0')

    companion object {
        fun json(body: String, code: Int = 200): MockResponse = MockResponse.Builder()
            .code(code)
            .addHeader("Content-Type", "application/json")
            .body(body)
            .build()

        fun error(code: Int, error: String): MockResponse = json("""{"error":"$error","message":"m"}""", code)
    }
}

/** One origin for Auth and AMS, as behind the website's nginx: `/api` goes to [ams]. */
class CombinedBackend(private val auth: Dispatcher, private val ams: Dispatcher) : Dispatcher() {
    override fun dispatch(request: RecordedRequest): MockResponse =
        if (request.url.encodedPath.startsWith("/api/")) ams.dispatch(request) else auth.dispatch(request)
}
