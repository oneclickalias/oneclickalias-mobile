package com.oneclickalias.app.data.aliases

import com.oneclickalias.contracts.ams.Alias
import org.junit.Assert.assertEquals
import org.junit.Test

class AliasOrderTest {
    private fun alias(id: String, createdAt: String) = Alias(
        id = id,
        alias = "$id-1234@alias.test",
        destEmail = "me@example.com",
        recipientId = "r",
        blockTrackers = true,
        createdAt = createdAt,
        updatedAt = createdAt
    )

    @Test
    fun `newest alias comes first`() {
        val old = alias("old", "2026-11-01T10:00:00Z")
        val mid = alias("mid", "2026-11-05T10:00:00Z")
        val new = alias("new", "2026-11-10T10:00:00Z")

        assertEquals(listOf(new, mid, old), newestFirst(listOf(old, mid, new)))
    }

    @Test
    fun `timestamps with offsets and fractions compare as instants`() {
        val utc = alias("utc", "2026-11-10T10:00:00Z")
        val berlin = alias("berlin", "2026-11-10T10:30:00.123456+01:00") // 09:30 UTC

        assertEquals(listOf(utc, berlin), newestFirst(listOf(berlin, utc)))
    }

    @Test
    fun `equal or unreadable timestamps keep the later server position first`() {
        val a = alias("a", "2026-11-10T10:00:00Z")
        val b = alias("b", "2026-11-10T10:00:00Z")
        val broken = alias("broken", "yesterday")

        assertEquals(listOf(b, a, broken), newestFirst(listOf(broken, a, b)))
    }
}
