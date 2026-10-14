package com.litejump.app.rules

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RuleRepositorySnapshotTest {

    private fun rule(
        id: String,
        type: RuleType,
        packageName: String? = null,
        keywords: List<String> = listOf("跳过"),
    ) = Rule(id = id, type = type, keywords = keywords, packageName = packageName)

    private fun snapshot(
        masterEnabled: Boolean = true,
        builtin: RuleFile = RuleFile(),
        builtinDisabled: Set<String> = emptySet(),
        splashBlacklist: Set<String> = emptySet(),
        customRules: List<Rule> = emptyList(),
    ) = EffectiveRules(
        masterEnabled = masterEnabled,
        builtinRules = builtin,
        builtinDisabled = builtinDisabled,
        splashBlacklist = splashBlacklist,
        customRules = customRules,
    )

    @Test
    fun `master switch off disables everything`() {
        val snap = snapshot(
            masterEnabled = false,
            builtin = RuleFile(global = listOf(rule("g", RuleType.SPLASH))),
        )
        assertEquals(0, snap.rulesFor("com.any").size)
    }

    @Test
    fun `global splash applies unless blacklisted`() {
        val g = rule("g", RuleType.SPLASH)
        val snap = snapshot(builtin = RuleFile(global = listOf(g)), splashBlacklist = setOf("com.bad"))
        assertTrue(snap.rulesFor("com.good").any { it.id == "g" })
        assertTrue(snap.rulesFor("com.bad").none { it.id == "g" })
    }

    @Test
    fun `consent and popup require matching package`() {
        val c = rule("c", RuleType.CONSENT, packageName = "com.a")
        val p = rule("p", RuleType.POPUP, packageName = "com.b")
        val snap = snapshot(builtin = RuleFile(apps = listOf(c, p)))
        assertTrue(snap.rulesFor("com.a").any { it.id == "c" })
        assertTrue(snap.rulesFor("com.a").none { it.id == "p" })
        assertTrue(snap.rulesFor("com.b").any { it.id == "p" })
        assertEquals(0, snap.rulesFor("com.c").size)
    }

    @Test
    fun `disabled builtin rules are filtered`() {
        val snap = snapshot(
            builtin = RuleFile(global = listOf(rule("g", RuleType.SPLASH), rule("k", RuleType.CONFIRM))),
            builtinDisabled = setOf("g"),
        )
        assertTrue(snap.rulesFor("com.any").none { it.id == "g" })
        assertTrue(snap.rulesFor("com.any").any { it.id == "k" })
    }

    @Test
    fun `global confirm respects splash blacklist`() {
        val k = rule("k", RuleType.CONFIRM)
        val snap = snapshot(builtin = RuleFile(global = listOf(k)), splashBlacklist = setOf("com.bad"))
        assertTrue(snap.rulesFor("com.bad").none { it.id == "k" })
    }

    @Test
    fun `package-scoped splash still works while global is blacklisted`() {
        val local = rule("local", RuleType.SPLASH, packageName = "com.bad")
        val g = rule("g", RuleType.SPLASH)
        val snap = snapshot(
            builtin = RuleFile(global = listOf(g), apps = listOf(local)),
            splashBlacklist = setOf("com.bad"),
        )
        val hits = snap.rulesFor("com.bad")
        assertTrue(hits.any { it.id == "local" })
        assertTrue(hits.none { it.id == "g" })
    }

    @Test
    fun `custom rules are included`() {
        val c = rule("custom", RuleType.CONSENT, packageName = "com.a")
        val snap = snapshot(customRules = listOf(c))
        assertTrue(snap.rulesFor("com.a").any { it.id == "custom" })
    }
}
