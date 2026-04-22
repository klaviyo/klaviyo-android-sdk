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
 * These cover the offset / sizing / keyboard-overlap math without requiring
 * Android framework mocks. Screen bounds are expressed in raw pixels and all
 * input offsets are in dp, scaled by [density] to match production behavior.
 */
class FloatingFormWindowTest {

    private val density = 2.0f
    private val screenWidth = 1080
    private val screenHeight = 1920

    private fun layout(
        position: FormPosition,
        width: Dimension = Dimension.dp(300f),
        height: Dimension = Dimension.dp(200f),
        offsets: Offsets = Offsets()
    ) = FormLayout(
        position = position,
        width = width,
        height = height,
        offsets = offsets
    )

    private fun compute(
        layout: FormLayout,
        screenW: Int = screenWidth,
        screenH: Int = screenHeight,
        safeTop: Int = 0,
        safeBottom: Int = 0,
        safeLeft: Int = 0,
        safeRight: Int = 0
    ) = FloatingFormWindow.calculateLayoutParams(
        layout = layout,
        screenWidth = screenW,
        screenHeight = screenH,
        density = density,
        safeAreaTop = safeTop,
        safeAreaBottom = safeBottom,
        safeAreaLeft = safeLeft,
        safeAreaRight = safeRight
    )

    // ===== Clamping =====

    @Test
    fun `width clamps to available when requested equals screen and offsets shrink it`() {
        // 500px form on 500px screen with 25px offsets (left and right) -> 450px
        val l = layout(
            position = FormPosition.TOP_LEFT,
            width = Dimension.dp(250f), // * density 2 = 500px
            offsets = Offsets(left = 12.5f, right = 12.5f) // * density 2 = 25px each
        )
        val computed = compute(l, screenW = 500, screenH = 500)
        assertEquals(450, computed.width)
    }

    @Test
    fun `width does not shrink when form already fits within available space`() {
        // 400px form on 500px screen with 25px offsets -> 400px (no shrink)
        val l = layout(
            position = FormPosition.TOP_LEFT,
            width = Dimension.dp(200f), // 400px
            offsets = Offsets(left = 12.5f, right = 12.5f) // 25 + 25
        )
        val computed = compute(l, screenW = 500, screenH = 500)
        assertEquals(400, computed.width)
    }

    @Test
    fun `height clamps to available when requested equals screen and offsets shrink it`() {
        // 500px form on 500px screen, 25 top + 25 bottom -> 450px
        val l = layout(
            position = FormPosition.TOP_LEFT,
            height = Dimension.dp(250f),
            offsets = Offsets(top = 12.5f, bottom = 12.5f)
        )
        val computed = compute(l, screenW = 500, screenH = 500)
        assertEquals(450, computed.height)
    }

    @Test
    fun `height does not shrink when form already fits within available space`() {
        val l = layout(
            position = FormPosition.TOP_LEFT,
            height = Dimension.dp(200f), // 400px
            offsets = Offsets(top = 12.5f, bottom = 12.5f)
        )
        val computed = compute(l, screenW = 500, screenH = 500)
        assertEquals(400, computed.height)
    }

    @Test
    fun `safe area insets are folded into available space alongside offsets`() {
        // screen 500, safeLeft 30 + safeRight 20 + leftOffset 25 + rightOffset 25 = 100
        // available = 400; requested 500 clamps to 400.
        val l = layout(
            position = FormPosition.TOP_LEFT,
            width = Dimension.dp(250f),
            offsets = Offsets(left = 12.5f, right = 12.5f)
        )
        val computed = compute(l, screenW = 500, screenH = 500, safeLeft = 30, safeRight = 20)
        assertEquals(400, computed.width)
    }

    @Test
    fun `available space floors at zero when offsets exceed screen`() {
        // huge offsets, clamped width must be >= 0
        val l = layout(
            position = FormPosition.TOP_LEFT,
            width = Dimension.dp(100f),
            height = Dimension.dp(100f),
            offsets = Offsets(left = 1000f, right = 1000f, top = 1000f, bottom = 1000f)
        )
        val computed = compute(l, screenW = 500, screenH = 500)
        assertEquals(0, computed.width)
        assertEquals(0, computed.height)
    }

    // ===== Corner positioning =====

    @Test
    fun `TOP_LEFT x is safeAreaLeft plus left offset`() {
        val l = layout(FormPosition.TOP_LEFT, offsets = Offsets(left = 10f, top = 5f))
        val computed = compute(l, safeLeft = 40, safeTop = 50)
        assertEquals(60, computed.x) // 40 + 20
        assertEquals(60, computed.y) // 50 + 10
    }

    @Test
    fun `BOTTOM_RIGHT x and y use right and bottom margins`() {
        val l = layout(FormPosition.BOTTOM_RIGHT, offsets = Offsets(right = 10f, bottom = 5f))
        val computed = compute(l, safeRight = 40, safeBottom = 50)
        assertEquals(60, computed.x) // 40 + 20
        assertEquals(60, computed.y) // 50 + 10
    }

    // ===== Centered positioning (asymmetric offsets) =====

    @Test
    fun `centered horizontal anchor shifts toward smaller left-right margin`() {
        // leftMargin 100, rightMargin 0 => x = (100 - 0) / 2 = 50 (shift right of center)
        val l = layout(FormPosition.TOP, offsets = Offsets(left = 50f))
        assertEquals(50, compute(l).x)

        // leftMargin 0, rightMargin 100 => x = -50 (shift left of center)
        val l2 = layout(FormPosition.TOP, offsets = Offsets(right = 50f))
        assertEquals(-50, compute(l2).x)

        // Symmetric offsets => 0
        val l3 = layout(FormPosition.TOP, offsets = Offsets(left = 30f, right = 30f))
        assertEquals(0, compute(l3).x)
    }

    @Test
    fun `CENTER vertical anchor shifts for asymmetric top-bottom margins`() {
        // topMargin 100, bottomMargin 0 => y = 50 (shift down from center)
        val l = layout(FormPosition.CENTER, offsets = Offsets(top = 50f))
        assertEquals(50, compute(l).y)

        val l2 = layout(FormPosition.CENTER, offsets = Offsets(bottom = 50f))
        assertEquals(-50, compute(l2).y)
    }

    @Test
    fun `centered position folds safe area into margin asymmetry`() {
        val l = layout(FormPosition.TOP)
        // safeLeft 100, no offsets: x = (100 - 0) / 2 = 50
        assertEquals(50, compute(l, safeLeft = 100).x)
        // safeRight 100: x = -50
        assertEquals(-50, compute(l, safeRight = 100).x)
        // Symmetric safe areas cancel out
        assertEquals(0, compute(l, safeLeft = 60, safeRight = 60).x)
    }

    // ===== FULLSCREEN =====

    @Test
    fun `FULLSCREEN ignores offsets and fills screen`() {
        val l = layout(
            FormPosition.FULLSCREEN,
            offsets = Offsets(top = 50f, bottom = 50f, left = 50f, right = 50f)
        )
        val computed = compute(l, safeTop = 100, safeBottom = 100, safeLeft = 100, safeRight = 100)
        assertEquals(screenWidth, computed.width)
        assertEquals(screenHeight, computed.height)
        assertEquals(0, computed.x)
        assertEquals(0, computed.y)
    }

    // ===== calculateFormBottomGap =====

    @Test
    fun `bottom gap for BOTTOM uses safe area plus bottom offset`() {
        val l = layout(FormPosition.BOTTOM, offsets = Offsets(bottom = 10f))
        assertEquals(
            50,
            FloatingFormWindow.calculateFormBottomGap(l, 400, screenHeight, density, 0, 30)
        )
    }

    @Test
    fun `bottom gap for TOP equals screenHeight minus top margin minus form height`() {
        val l = layout(FormPosition.TOP, offsets = Offsets(top = 10f))
        // 1920 - (50 + 20) - 400 = 1450
        assertEquals(
            1450,
            FloatingFormWindow.calculateFormBottomGap(l, 400, screenHeight, density, 50, 0)
        )
    }

    @Test
    fun `bottom gap for CENTER with symmetric margins is centered`() {
        val l = layout(FormPosition.CENTER)
        // (1920 - 400) / 2 = 760, asymmetry term is 0
        assertEquals(
            760,
            FloatingFormWindow.calculateFormBottomGap(l, 400, screenHeight, density, 0, 0)
        )
    }

    @Test
    fun `bottom gap for CENTER reflects margin asymmetry`() {
        val l = layout(FormPosition.CENTER, offsets = Offsets(top = 50f))
        // topMargin 100, bottomMargin 0 -> form shifts down by 50 -> bottomGap shrinks by 50
        // baseline centered gap = (1920 - 400) / 2 = 760; with (bottomMargin-topMargin)/2 = -50
        assertEquals(
            710,
            FloatingFormWindow.calculateFormBottomGap(l, 400, screenHeight, density, 0, 0)
        )
    }

    @Test
    fun `bottom gap for FULLSCREEN is zero`() {
        val l = layout(FormPosition.FULLSCREEN)
        assertEquals(
            0,
            FloatingFormWindow.calculateFormBottomGap(l, screenHeight, screenHeight, density, 0, 0)
        )
    }

    @Test
    fun `bottom gap with density 1 uses raw dp values`() {
        val l = layout(FormPosition.BOTTOM, offsets = Offsets(bottom = 16f))
        assertEquals(
            16,
            FloatingFormWindow.calculateFormBottomGap(l, 400, screenHeight, 1.0f, 0, 0)
        )
    }
}
