package com.klaviyo.core.utils

import org.junit.Test

class TakeIfTest {
    private val aThing = "a thing"

    @Test
    fun `takeIf runs on matching type`() {
        var itRan = false
        aThing.takeIf<String>()?.let { itRan = true }
        assert(itRan) { "takeIf should run on matching type" }
    }

    @Test
    fun `takeIf does not run on non-matching type`() {
        var itRan = false
        aThing.takeIf<Int>()?.let { itRan = true }
        assert(!itRan) { "takeIf should not run on non-matching type" }
    }

    @Test
    fun `takeIfNot runs on non-matching type`() {
        var itRan = false
        aThing.takeIfNot<Int>()?.let { itRan = true }
        assert(itRan) { "takeIfNot should run on non-matching type" }
    }

    @Test
    fun `takeIfNot does not run on matching type`() {
        var itRan = false
        aThing.takeIfNot<String>()?.let { itRan = true }
        assert(!itRan) { "takeIfNot should not run on matching type" }
    }
}
