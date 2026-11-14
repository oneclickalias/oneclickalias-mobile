package com.oneclickalias.app.data.aliases

import com.oneclickalias.contracts.ams.Recipient

/**
 * Where a new alias forwards to, decided before "New alias" sends anything.
 * Only confirmed (`verified`) recipients are valid targets; the server
 * enforces that too (`400 recipient_not_verified`). Table: docs/flows.md.
 */
sealed interface RecipientDecision {
    /**
     * No confirmed recipient: creating is not possible yet.
     *
     * @property awaitingConfirmation an address exists but its confirmation link
     *   has not been opened yet.
     */
    data class NoneConfirmed(val awaitingConfirmation: Boolean) : RecipientDecision

    /**
     * Create without asking.
     *
     * @property options all confirmed recipients, for changing the choice.
     */
    data class Use(val recipient: Recipient, val options: List<Recipient>) : RecipientDecision {
        val canChange: Boolean get() = options.size > 1
    }

    /** Several confirmed recipients and no valid remembered choice: let the user pick. */
    data class Ask(val options: List<Recipient>) : RecipientDecision
}

/**
 * Decides the recipient for new aliases:
 *
 * | Confirmed recipients | Remembered choice                  | Decision                  |
 * |----------------------|------------------------------------|---------------------------|
 * | none                 | any                                | [RecipientDecision.NoneConfirmed] |
 * | exactly one          | any                                | use it                    |
 * | several              | one of them                        | use the remembered one    |
 * | several              | none, deleted or no longer confirmed | ask                     |
 *
 * @param recipients the account's recipients in server order (oldest first).
 * @param rememberedId the id the user picked last time, if any.
 */
fun decideRecipient(recipients: List<Recipient>, rememberedId: String?): RecipientDecision {
    val confirmed = recipients.filter { it.verified }
    return when {
        confirmed.isEmpty() -> RecipientDecision.NoneConfirmed(awaitingConfirmation = recipients.isNotEmpty())

        confirmed.size == 1 -> RecipientDecision.Use(confirmed.single(), confirmed)

        else -> confirmed.firstOrNull { it.id == rememberedId }
            ?.let { RecipientDecision.Use(it, confirmed) }
            ?: RecipientDecision.Ask(confirmed)
    }
}
