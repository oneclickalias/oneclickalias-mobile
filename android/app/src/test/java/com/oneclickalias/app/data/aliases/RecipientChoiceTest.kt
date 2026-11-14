package com.oneclickalias.app.data.aliases

import com.oneclickalias.contracts.ams.Recipient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The decision table in docs/flows.md ("Recipient for new aliases"), row by row. */
class RecipientChoiceTest {
    private val home = recipient("r-home", "home@example.com")
    private val work = recipient("r-work", "work@example.com")
    private val pending = recipient("r-new", "new@example.com", verified = false)

    @Test
    fun `no recipient at all asks to add one`() {
        assertEquals(RecipientDecision.NoneConfirmed(awaitingConfirmation = false), decideRecipient(emptyList(), null))
    }

    @Test
    fun `only unconfirmed recipients asks to confirm one`() {
        assertEquals(
            RecipientDecision.NoneConfirmed(awaitingConfirmation = true),
            decideRecipient(listOf(pending), "r-new")
        )
    }

    @Test
    fun `exactly one confirmed recipient is used without asking`() {
        val decision = decideRecipient(listOf(pending, home), null)

        assertEquals(RecipientDecision.Use(home, options = listOf(home)), decision)
        assertFalse((decision as RecipientDecision.Use).canChange)
    }

    @Test
    fun `exactly one confirmed recipient wins over a stale remembered choice`() {
        assertEquals(RecipientDecision.Use(home, listOf(home)), decideRecipient(listOf(home), "r-gone"))
    }

    @Test
    fun `several confirmed and nothing remembered asks, in server order`() {
        assertEquals(RecipientDecision.Ask(listOf(home, work)), decideRecipient(listOf(home, pending, work), null))
    }

    @Test
    fun `several confirmed uses the remembered one`() {
        val decision = decideRecipient(listOf(home, work), "r-work")

        assertEquals(RecipientDecision.Use(work, options = listOf(home, work)), decision)
        assertTrue((decision as RecipientDecision.Use).canChange)
    }

    @Test
    fun `remembered recipient that no longer exists asks again`() {
        assertEquals(RecipientDecision.Ask(listOf(home, work)), decideRecipient(listOf(home, work), "r-gone"))
    }

    @Test
    fun `remembered recipient that is no longer confirmed asks again`() {
        val unconfirmedWork = work.copy(verified = false)
        val other = recipient("r-other", "other@example.com")

        assertEquals(
            RecipientDecision.Ask(listOf(home, other)),
            decideRecipient(listOf(home, unconfirmedWork, other), "r-work")
        )
    }

    private fun recipient(id: String, email: String, verified: Boolean = true) =
        Recipient(id = id, email = email, verified = verified, createdAt = "2026-11-01T10:00:00Z")
}
