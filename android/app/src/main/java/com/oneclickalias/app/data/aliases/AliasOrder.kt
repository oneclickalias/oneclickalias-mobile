package com.oneclickalias.app.data.aliases

import com.oneclickalias.contracts.ams.Alias
import java.time.Instant
import java.time.OffsetDateTime
import java.time.format.DateTimeParseException

/**
 * The list order on screen: newest alias first, so a freshly created alias
 * stays on top after every reload (the server lists oldest first). Ties and
 * unreadable timestamps keep the reversed server order.
 */
fun newestFirst(aliases: List<Alias>): List<Alias> = aliases
    .withIndex()
    .sortedWith(
        compareByDescending<IndexedValue<Alias>> { createdAt(it.value) ?: Instant.MIN }.thenByDescending { it.index }
    )
    .map { it.value }

// OffsetDateTime, not Instant.parse: on API 26 that only accepts a "Z" suffix.
private fun createdAt(alias: Alias): Instant? = try {
    OffsetDateTime.parse(alias.createdAt).toInstant()
} catch (e: DateTimeParseException) {
    null
}
