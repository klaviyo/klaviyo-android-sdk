package com.klaviyo.forms.presentation

import com.klaviyo.forms.bridge.Dimension
import com.klaviyo.forms.bridge.FormLayout
import com.klaviyo.forms.bridge.FormPosition
import com.klaviyo.forms.bridge.Offsets
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Tests for [FloatingFormWindow] pure calculation functions.
 *
 * These cover the offset, positioning, and keyboard-overlap math
 * without requiring Android framework mocks.
 */
class FloatingFormWindowTest {

    private val density = 2.0f

    private fun layoutAt(
        position: FormPosition,
        offsets: Offsets = Offsets()
    ) = FormLayout(
        position = position,
        width = Dimension.dp(300f),
        height = Dimension.dp(200f),
        offsets = offsets
    )

    // ===== calculateVerticalOffset =====

    @Test
    fun `vertical offset for TOP positions uses top offset in pixels`() {
        val layout = layoutAt(FormPosition.TOP, Offsets(top = 16f))
        assertEquals(32, FloatingFormWindow.calculateVerticalOffset(layout, density))
    }

    @Test
    fun `vertical offset for TOP_LEFT uses top offset`() {
        val layout = layoutAt(FormPosition.TOP_LEFT, Offsets(top = 10f))
        assertEquals(20, FloatingFormWindow.calculateVerticalOffset(layout, density))
    }

    @Test
    fun `vertical offset for BOTTOM positions uses bottom offset in pixels`() {
        val layout = layoutAt(FormPosition.BOTTOM, Offsets(bottom = 24f))
        assertEquals(48, FloatingFormWindow.calculateVerticalOffset(layout, density))
    }

    @Test
    fun `vertical offset for BOTTOM_RIGHT uses bottom offset`() {
        val layout = layoutAt(FormPosition.BOTTOM_RIGHT, Offsets(bottom = 8f))
        assertEquals(16, FloatingFormWindow.calculateVerticalOffset(layout, density))
    }

    @Test
    fun `vertical offset for CENTER is zero`() {
        val layout = layoutAt(FormPosition.CENTER, Offsets(top = 50f, bottom = 50f))
        assertEquals(0, FloatingFormWindow.calculateVerticalOffset(layout, density))
    }

    @Test
    fun `vertical offset for FULLSCREEN is zero`() {
        val layout = layoutAt(FormPosition.FULLSCREEN)
        assertEquals(0, FloatingFormWindow.calculateVerticalOffset(layout, density))
    }

    // ===== calculateHorizontalOffset =====

    @Test
    fun `horizontal offset for LEFT positions uses left offset`() {
        val layout = layoutAt(FormPosition.TOP_LEFT, Offsets(left = 12f))
        assertEquals(24, FloatingFormWindow.calculateHorizontalOffset(layout, density))
    }

    @Test
    fun `horizontal offset for BOTTOM_LEFT uses left offset`() {
        val layout = layoutAt(FormPosition.BOTTOM_LEFT, Offsets(left = 8f))
        assertEquals(16, FloatingFormWindow.calculateHorizontalOffset(layout, density))
    }

    @Test
    fun `horizontal offset for RIGHT positions uses positive right offset`() {
        val layout = layoutAt(FormPosition.TOP_RIGHT, Offsets(right = 16f))
        assertEquals(32, FloatingFormWindow.calculateHorizontalOffset(layout, density))
    }

    @Test
    fun `horizontal offset for BOTTOM_RIGHT uses positive right offset`() {
        val layout = layoutAt(FormPosition.BOTTOM_RIGHT, Offsets(right = 10f))
        assertEquals(20, FloatingFormWindow.calculateHorizontalOffset(layout, density))
    }

    @Test
    fun `horizontal offset for centered positions is zero`() {
        val offsets = Offsets(left = 50f, right = 50f)
        assertEquals(
            0,
            FloatingFormWindow.calculateHorizontalOffset(
                layoutAt(FormPosition.TOP, offsets),
                density
            )
        )
        assertEquals(
            0,
            FloatingFormWindow.calculateHorizontalOffset(
                layoutAt(FormPosition.BOTTOM, offsets),
                density
            )
        )
        assertEquals(
            0,
            FloatingFormWindow.calculateHorizontalOffset(
                layoutAt(FormPosition.CENTER, offsets),
                density
            )
        )
        assertEquals(
            0,
            FloatingFormWindow.calculateHorizontalOffset(
                layoutAt(FormPosition.FULLSCREEN, offsets),
                density
            )
        )
    }

    // ===== calculateFormBottomGap =====

    @Test
    fun `bottom gap for BOTTOM position equals bottom offset in pixels`() {
        val layout = layoutAt(FormPosition.BOTTOM, Offsets(bottom = 16f))
        val formHeight = 400
        val screenHeight = 1920
        assertEquals(
            32,
            FloatingFormWindow.calculateFormBottomGap(layout, formHeight, screenHeight, density)
        )
    }

    @Test
    fun `bottom gap for BOTTOM with zero offset is zero`() {
        val layout = layoutAt(FormPosition.BOTTOM)
        assertEquals(0, FloatingFormWindow.calculateFormBottomGap(layout, 400, 1920, density))
    }

    @Test
    fun `bottom gap for TOP position is screen minus top offset minus form height`() {
        val layout = layoutAt(FormPosition.TOP, Offsets(top = 16f))
        val formHeight = 400
        val screenHeight = 1920
        // gap = 1920 - (16*2) - 400 = 1488
        assertEquals(
            1488,
            FloatingFormWindow.calculateFormBottomGap(layout, formHeight, screenHeight, density)
        )
    }

    @Test
    fun `bottom gap for TOP with tall form leaves small gap`() {
        val layout = layoutAt(FormPosition.TOP, Offsets(top = 0f))
        // gap = 1920 - 0 - 1800 = 120
        assertEquals(120, FloatingFormWindow.calculateFormBottomGap(layout, 1800, 1920, density))
    }

    @Test
    fun `bottom gap for CENTER is half the remaining space`() {
        val layout = layoutAt(FormPosition.CENTER)
        val formHeight = 400
        val screenHeight = 1920
        // gap = (1920 - 400) / 2 = 760
        assertEquals(
            760,
            FloatingFormWindow.calculateFormBottomGap(layout, formHeight, screenHeight, density)
        )
    }

    @Test
    fun `bottom gap for FULLSCREEN is zero`() {
        val layout = layoutAt(FormPosition.FULLSCREEN)
        assertEquals(0, FloatingFormWindow.calculateFormBottomGap(layout, 1920, 1920, density))
    }

    @Test
    fun `bottom gap for BOTTOM_LEFT uses bottom offset`() {
        val layout = layoutAt(FormPosition.BOTTOM_LEFT, Offsets(bottom = 20f))
        assertEquals(40, FloatingFormWindow.calculateFormBottomGap(layout, 400, 1920, density))
    }

    @Test
    fun `bottom gap for TOP_RIGHT uses top offset and form height`() {
        val layout = layoutAt(FormPosition.TOP_RIGHT, Offsets(top = 8f))
        // gap = 1920 - (8*2) - 400 = 1504
        assertEquals(1504, FloatingFormWindow.calculateFormBottomGap(layout, 400, 1920, density))
    }

    // ===== Zero offset edge cases =====

    @Test
    fun `all calculations handle zero offsets correctly`() {
        val layout = layoutAt(FormPosition.TOP)
        assertEquals(0, FloatingFormWindow.calculateVerticalOffset(layout, density))
        assertEquals(0, FloatingFormWindow.calculateHorizontalOffset(layout, density))
    }

    @Test
    fun `calculations with density 1 produce dp values directly`() {
        val layout = layoutAt(FormPosition.BOTTOM, Offsets(bottom = 16f))
        assertEquals(16, FloatingFormWindow.calculateVerticalOffset(layout, 1.0f))
        assertEquals(16, FloatingFormWindow.calculateFormBottomGap(layout, 400, 1920, 1.0f))
    }
}
