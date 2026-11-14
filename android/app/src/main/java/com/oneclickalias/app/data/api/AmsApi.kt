package com.oneclickalias.app.data.api

import com.oneclickalias.app.data.session.SessionManager
import com.oneclickalias.contracts.ams.Alias
import com.oneclickalias.contracts.ams.AliasList
import com.oneclickalias.contracts.ams.AliasRequest
import com.oneclickalias.contracts.ams.AliasSettingsRequest
import com.oneclickalias.contracts.ams.ClaimResult
import com.oneclickalias.contracts.ams.ErrorCode as AmsErrorCode
import com.oneclickalias.contracts.ams.Recipient
import com.oneclickalias.contracts.ams.RecipientList
import com.oneclickalias.contracts.ams.RecipientRequest
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.HttpUrl
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Client for the Alias Management Service user API:
 * the aliases and the recipient addresses of the account. Every
 * call runs through [SessionManager.authorized], which supplies a valid access
 * token and handles refresh and `401`.
 *
 * Reads work for every account; writes need a paid account
 * (`403 payment_required`) and count towards the write rate limit of 30 per
 * minute and account (`429 rate_limited`).
 *
 * @param base origin of the service, e.g. [Endpoints.apiBase].
 */
class AmsApi(private val transport: ApiTransport, private val base: HttpUrl, private val sessions: SessionManager) {

    /** `GET /api/aliases`: active aliases, oldest first. */
    suspend fun listAliases(): ApiResult<AliasList> = sessions.authorized { token ->
        transport.call(Request.Builder().url(url("aliases")).get().bearer(token).build(), AliasList.serializer())
    }

    /** `POST /api/aliases`: new alias with a server-generated name, forwarding to [recipientId]. */
    suspend fun createAlias(recipientId: String): ApiResult<Alias> = sessions.authorized { token ->
        val body = transport.jsonBody(AliasRequest.serializer(), AliasRequest(recipientId))
        transport.call(Request.Builder().url(url("aliases")).post(body).bearer(token).build(), Alias.serializer())
    }

    /**
     * `GET /api/aliases/ready`: this device's ready aliases
     * (docs/flows.md, "New alias tab"), at most one per confirmed recipient. Not active
     * until claimed. Needs a paid account, but is not a write.
     */
    suspend fun listReadyAliases(): ApiResult<AliasList> = sessions.authorized { token ->
        transport.call(
            Request.Builder().url(url("aliases", "ready")).get().bearer(token).build(),
            AliasList.serializer()
        )
    }

    /** `POST /api/aliases/ready`: this device's ready alias for [recipientId], made if missing. */
    suspend fun ensureReadyAlias(recipientId: String): ApiResult<Alias> = sessions.authorized { token ->
        val body = transport.jsonBody(AliasRequest.serializer(), AliasRequest(recipientId))
        transport.call(
            Request.Builder().url(url("aliases", "ready")).post(body).bearer(token).build(),
            Alias.serializer()
        )
    }

    /**
     * `POST /api/aliases/{id}/claim`: makes a copied ready alias active; `next`
     * is the following ready alias for the same recipient, when one could be
     * made. Repeating it for an alias already claimed is fine (no `next`).
     */
    suspend fun claimReadyAlias(aliasId: String): ApiResult<ClaimResult> = sessions.authorized { token ->
        val request = Request.Builder()
            .url(url("aliases", aliasId, "claim"))
            .post(ByteArray(0).toRequestBody())
            .bearer(token)
            .build()
        transport.call(request, ClaimResult.serializer())
    }

    /** `PATCH /api/aliases/{id}`: tracker stripping on or off. */
    suspend fun setBlockTrackers(aliasId: String, blockTrackers: Boolean): ApiResult<Alias> =
        sessions.authorized { token ->
            val body = transport.jsonBody(AliasSettingsRequest.serializer(), AliasSettingsRequest(blockTrackers))
            transport.call(patch(url("aliases", aliasId), body, token), Alias.serializer())
        }

    /** `DELETE /api/aliases/{id}`: permanent; the name is never handed out again. */
    suspend fun deleteAlias(aliasId: String): ApiResult<Unit> = sessions.authorized { token ->
        transport.callNoContent(Request.Builder().url(url("aliases", aliasId)).delete().bearer(token).build())
    }

    /** `GET /api/recipients`: all recipient addresses, confirmed or not, oldest first. */
    suspend fun listRecipients(): ApiResult<RecipientList> = sessions.authorized { token ->
        transport.call(Request.Builder().url(url("recipients")).get().bearer(token).build(), RecipientList.serializer())
    }

    /** `POST /api/recipients`: new, unconfirmed address; the server emails the confirmation link. */
    suspend fun createRecipient(email: String): ApiResult<Recipient> = sessions.authorized { token ->
        val body = transport.jsonBody(RecipientRequest.serializer(), RecipientRequest(email))
        transport.call(
            Request.Builder().url(url("recipients")).post(body).bearer(token).build(),
            Recipient.serializer()
        )
    }

    /** `POST /api/recipients/{id}/resend`: a new confirmation link (unconfirmed addresses only). */
    suspend fun resendRecipientConfirmation(recipientId: String): ApiResult<Unit> = sessions.authorized { token ->
        val request = Request.Builder()
            .url(url("recipients", recipientId, "resend"))
            .post(ByteArray(0).toRequestBody())
            .bearer(token)
            .build()
        transport.callNoContent(request)
    }

    /** `DELETE /api/recipients/{id}`: only while no active alias forwards to it (`409 recipient_in_use`). */
    suspend fun deleteRecipient(recipientId: String): ApiResult<Unit> = sessions.authorized { token ->
        transport.callNoContent(Request.Builder().url(url("recipients", recipientId)).delete().bearer(token).build())
    }

    private fun url(vararg segments: String): HttpUrl =
        base.newBuilder().addPathSegment("api").apply { segments.forEach { addPathSegment(it) } }.build()

    private fun patch(url: HttpUrl, body: RequestBody, token: String): Request =
        Request.Builder().url(url).patch(body).bearer(token).build()
}

/**
 * The AMS error code of this error, or null when the code is unknown to this
 * app version (see [authErrorCode] for why the raw string is matched).
 */
fun ApiResult.HttpError.amsErrorCode(): AmsErrorCode? =
    runCatching { ApiJson.decodeFromJsonElement(AmsErrorCode.serializer(), JsonPrimitive(code)) }.getOrNull()
