package com.oneclickalias.app.data.api

import com.oneclickalias.contracts.auth.Error as AuthError
import com.oneclickalias.contracts.auth.ErrorCode
import com.oneclickalias.contracts.auth.LoginRequest
import com.oneclickalias.contracts.auth.LoginResponse
import org.junit.Assert.assertEquals
import org.junit.Test

/** The generated models from api/kotlin/ compile into the app and work with [ApiJson]. */
class ApiModelsTest {
    @Test
    fun `login response decodes with the generated model`() {
        val decoded = ApiJson.decodeFromString<LoginResponse>(
            """{"accessToken":"a","refreshToken":"r","expiresIn":900,"deviceId":"d","later":1}"""
        )

        assertEquals(LoginResponse(accessToken = "a", refreshToken = "r", expiresIn = 900, deviceId = "d"), decoded)
    }

    @Test
    fun `login request encodes with the spec field name`() {
        assertEquals(
            """{"accountNumber":"1234567890123456"}""",
            ApiJson.encodeToString(LoginRequest(accountNumber = "1234567890123456"))
        )
    }

    @Test
    fun `generated error model is strict about known codes`() {
        val decoded = ApiJson.decodeFromString<AuthError>("""{"error":"device_limit_reached","message":"m"}""")

        assertEquals(ErrorCode.DEVICE_LIMIT_REACHED, decoded.error)
    }
}
