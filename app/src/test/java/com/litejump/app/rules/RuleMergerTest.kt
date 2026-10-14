package com.litejump.app.rules

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RuleMergerTest {

    private fun rule(id: String, keywords: List<String> = listOf("跳过"), viewId: String? = null) =
        Rule(id = id, type = RuleType.SPLASH, keywords = keywords, viewId = viewId)

    @Test
    fun `drops rules without keywords and viewId`() {
        val merged = RuleMerger.merge(
            existing = emptyList(),
            incoming = listOf(rule("a", keywords = emptyList()), rule("b")),
            takenIds = emptySet(),
        )
        assertEquals(listOf("b"), merged.map { it.id })
    }

    @Test
    fun `keeps rules that only have a viewId`() {
        val merged = RuleMerger.merge(
            existing = emptyList(),
            incoming = listOf(rule("a", keywords = emptyList(), viewId = "btn_skip")),
            takenIds = emptySet(),
        )
        assertEquals(1, merged.size)
    }

    @Test
    fun `renames colliding ids`() {
        // 同 ID 但内容不同: 都保留, 且 ID 被改成互不相同的新 ID
        val incoming = listOf(rule("dup"), rule("dup", keywords = listOf("关闭")))
        val merged = RuleMerger.merge(existing = emptyList(), incoming = incoming, takenIds = setOf("dup"))
        assertEquals(2, merged.size)
        assertTrue(merged[0].id != "dup")
        assertTrue(merged[1].id != "dup")
        assertTrue(merged[0].id != merged[1].id)
    }

    @Test
    fun `skips content duplicates within one import batch`() {
        val incoming = listOf(rule("dup"), rule("dup"))
        val merged = RuleMerger.merge(existing = emptyList(), incoming = incoming, takenIds = setOf("dup"))
        assertEquals(1, merged.size)
    }

    @Test
    fun `keeps unique ids untouched`() {
        val merged = RuleMerger.merge(
            existing = listOf(rule("existing")),
            incoming = listOf(rule("fresh", keywords = listOf("关闭"))),
            takenIds = setOf("existing", "builtin_x"),
        )
        assertEquals(listOf("fresh"), merged.map { it.id })
    }

    @Test
    fun `returns empty when nothing valid`() {
        val merged = RuleMerger.merge(
            existing = emptyList(),
            incoming = listOf(rule("a", keywords = emptyList())),
            takenIds = emptySet(),
        )
        assertTrue(merged.isEmpty())
    }

    @Test
    fun `skips content duplicates on import`() {
        val merged = RuleMerger.merge(
            existing = listOf(rule("already", keywords = listOf("同意"))),
            incoming = listOf(rule("dup", keywords = listOf("同意")), rule("fresh", keywords = listOf("关闭"))),
            takenIds = setOf("already"),
        )
        assertEquals(listOf("fresh"), merged.map { it.id })
    }

    @Test
    fun `sanitize caps total rule count`() {
        val many = (1..600).map { rule("r$it", keywords = listOf("k$it")) }
        assertEquals(RuleMerger.MAX_RULES, RuleMerger.sanitize(many).size)
    }

    @Test
    fun `sanitize caps keywords per rule and their length`() {
        val keywords = (1..50).map { "词$it".repeat(10) } // 每个远超长度上限
        val out = RuleMerger.sanitize(listOf(rule("r", keywords = keywords)))
        assertEquals(1, out.size)
        assertEquals(RuleMerger.MAX_KEYWORDS_PER_RULE, out[0].keywords.size)
        assertTrue(out[0].keywords.all { it.length <= RuleMerger.MAX_KEYWORD_LENGTH })
    }

    @Test
    fun `sanitize trims and dedups keywords, drops blanks`() {
        val out = RuleMerger.sanitize(
            listOf(rule("r", keywords = listOf(" 同意 ", "同意", "", "  ", "关闭"))),
        )
        assertEquals(listOf("同意", "关闭"), out[0].keywords)
    }

    @Test
    fun `sanitize truncates overlong viewId`() {
        val longId = "a".repeat(500)
        val out = RuleMerger.sanitize(listOf(rule("r", keywords = emptyList(), viewId = longId)))
        assertEquals(RuleMerger.MAX_VIEWID_LENGTH, out[0].viewId!!.length)
    }
}
