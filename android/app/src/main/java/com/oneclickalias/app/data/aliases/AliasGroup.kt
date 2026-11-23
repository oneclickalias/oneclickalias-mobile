package com.oneclickalias.app.data.aliases

import com.oneclickalias.contracts.ams.Alias
import java.util.Locale

/** The aliases that forward to one recipient address. */
data class AliasGroup(val destEmail: String, val aliases: List<Alias>) {
    /** Identifies the group across reloads: the address, ignoring case (as the grouping does). */
    val key: String get() = destEmail.lowercase(Locale.ROOT)
}

/**
 * The alias list by recipient address. [aliases] keeps its order inside each
 * group, and the groups follow the first alias of each address: with a
 * newest-first list, the recipient used last comes first.
 */
fun groupByRecipient(aliases: List<Alias>): List<AliasGroup> = aliases
    .groupBy { it.destEmail.lowercase(Locale.ROOT) }
    .values
    .map { AliasGroup(destEmail = it.first().destEmail, aliases = it) }
