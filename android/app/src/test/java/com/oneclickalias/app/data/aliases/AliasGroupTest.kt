package com.oneclickalias.app.data.aliases

import com.oneclickalias.contracts.ams.Alias
import org.junit.Assert.assertEquals
import org.junit.Test

class AliasGroupTest {
    private fun alias(id: String, destEmail: String) =
        Alias(id, "$id@alias.test", destEmail, "r-$destEmail", true, "2026-11-10T10:00:00Z", "2026-11-10T10:00:00Z")

    @Test
    fun `aliases of one recipient form one group in the given order`() {
        val groups = groupByRecipient(listOf(alias("c", "home@example.com"), alias("a", "home@example.com")))

        assertEquals(listOf("home@example.com"), groups.map { it.destEmail })
        assertEquals(listOf("c", "a"), groups.single().aliases.map { it.id })
    }

    @Test
    fun `groups follow the first alias of each recipient`() {
        val groups = groupByRecipient(
            listOf(
                alias("newest", "work@example.com"),
                alias("second", "home@example.com"),
                alias("third", "work@example.com"),
                alias("oldest", "home@example.com")
            )
        )

        assertEquals(listOf("work@example.com", "home@example.com"), groups.map { it.destEmail })
        assertEquals(listOf("newest", "third"), groups[0].aliases.map { it.id })
        assertEquals(listOf("second", "oldest"), groups[1].aliases.map { it.id })
    }

    @Test
    fun `the same address in another spelling is the same group`() {
        val groups = groupByRecipient(listOf(alias("a", "Home@Example.com"), alias("b", "home@example.com")))

        assertEquals(1, groups.size)
        assertEquals("Home@Example.com", groups.single().destEmail)
    }

    @Test
    fun `no aliases, no groups`() {
        assertEquals(emptyList<AliasGroup>(), groupByRecipient(emptyList()))
    }

    @Test
    fun `a group's key ignores the case of the address`() {
        val groups = groupByRecipient(listOf(alias("a", "Home@Example.com"), alias("b", "home@example.com")))

        assertEquals("home@example.com", groups.single().key)
    }
}
