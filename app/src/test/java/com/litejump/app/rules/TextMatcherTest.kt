package com.litejump.app.rules

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TextMatcherTest {

    private fun rule(mode: MatchMode, vararg keywords: String) =
        Rule(id = "t", type = RuleType.SPLASH, keywords = keywords.toList(), matchMode = mode)

    @Test
    fun `contains matches substring ignoring case and trimming`() {
        assertTrue(TextMatcher.hits(rule(MatchMode.CONTAINS, "跳过"), "  跳过广告 "))
        assertTrue(TextMatcher.hits(rule(MatchMode.CONTAINS, "skip"), "Skip Ad"))
        assertTrue(TextMatcher.hits(rule(MatchMode.CONTAINS, "同意并继续"), "同意并继续"))
    }

    @Test
    fun `contains does not hit unrelated text`() {
        assertFalse(TextMatcher.hits(rule(MatchMode.CONTAINS, "跳过"), "继续观看"))
    }

    @Test
    fun `equals requires exact match after trim`() {
        assertTrue(TextMatcher.hits(rule(MatchMode.EQUALS, "同意"), " 同意 "))
        assertFalse(TextMatcher.hits(rule(MatchMode.EQUALS, "同意"), "同意并继续"))
    }

    @Test
    fun `empty keywords never match`() {
        assertFalse(TextMatcher.hits(rule(MatchMode.CONTAINS), "跳过"))
    }

    @Test
    fun `blank text never matches`() {
        assertFalse(TextMatcher.hits(rule(MatchMode.CONTAINS, "跳过"), "   "))
    }

    @Test
    fun `case insensitive equals`() {
        assertTrue(TextMatcher.hits(rule(MatchMode.EQUALS, "skip"), "SKIP"))
    }
}
