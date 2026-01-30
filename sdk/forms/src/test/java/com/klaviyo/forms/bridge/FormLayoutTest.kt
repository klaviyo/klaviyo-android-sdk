package com.klaviyo.forms.bridge

import android.view.Gravity
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for [FormLayout] and related data classes
 */
class FormLayoutTest {

    // ===== FormPosition tests =====

    @Test
    fun `FormPosition toGravity returns correct gravity flags`() {
        assertEquals(Gravity.CENTER, FormPosition.FULLSCREEN.toGravity())
        assertEquals(Gravity.TOP or Gravity.CENTER_HORIZONTAL, FormPosition.TOP.toGravity())
        assertEquals(Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL, FormPosition.BOTTOM.toGravity())
        assertEquals(Gravity.TOP or Gravity.START, FormPosition.TOP_LEFT.toGravity())
        assertEquals(Gravity.TOP or Gravity.END, FormPosition.TOP_RIGHT.toGravity())
        assertEquals(Gravity.BOTTOM or Gravity.START, FormPosition.BOTTOM_LEFT.toGravity())
        assertEquals(Gravity.BOTTOM or Gravity.END, FormPosition.BOTTOM_RIGHT.toGravity())
        assertEquals(Gravity.CENTER, FormPosition.CENTER.toGravity())
    }

    @Test
    fun `FormPosition fromString parses valid positions`() {
        assertEquals(FormPosition.FULLSCREEN, FormPosition.fromString("fullscreen"))
        assertEquals(FormPosition.FULLSCREEN, FormPosition.fromString("FULLSCREEN"))
        assertEquals(FormPosition.TOP, FormPosition.fromString("top"))
        assertEquals(FormPosition.BOTTOM, FormPosition.fromString("bottom"))
        assertEquals(FormPosition.TOP_LEFT, FormPosition.fromString("top_left"))
        assertEquals(FormPosition.TOP_RIGHT, FormPosition.fromString("top_right"))
        assertEquals(FormPosition.BOTTOM_LEFT, FormPosition.fromString("bottom_left"))
        assertEquals(FormPosition.BOTTOM_RIGHT, FormPosition.fromString("bottom_right"))
        assertEquals(FormPosition.CENTER, FormPosition.fromString("center"))
    }

    @Test
    fun `FormPosition fromString defaults to FULLSCREEN for invalid input`() {
        assertEquals(FormPosition.FULLSCREEN, FormPosition.fromString(null))
        assertEquals(FormPosition.FULLSCREEN, FormPosition.fromString(""))
        assertEquals(FormPosition.FULLSCREEN, FormPosition.fromString("invalid"))
    }

    // ===== DimensionUnit tests =====

    @Test
    fun `DimensionUnit fromString parses valid units`() {
        assertEquals(DimensionUnit.PERCENT, DimensionUnit.fromString("percent"))
        assertEquals(DimensionUnit.PERCENT, DimensionUnit.fromString("PERCENT"))
        assertEquals(DimensionUnit.FIXED, DimensionUnit.fromString("fixed"))
        assertEquals(DimensionUnit.FIXED, DimensionUnit.fromString("FIXED"))
    }

    @Test
    fun `DimensionUnit fromString defaults to FIXED for invalid input`() {
        assertEquals(DimensionUnit.FIXED, DimensionUnit.fromString(null))
        assertEquals(DimensionUnit.FIXED, DimensionUnit.fromString(""))
        assertEquals(DimensionUnit.FIXED, DimensionUnit.fromString("invalid"))
    }

    // ===== Dimension tests =====

    @Test
    fun `Dimension toPixels with FIXED unit converts dp to pixels`() {
        val dimension = Dimension(100f, DimensionUnit.FIXED)
        // With density 2.0, 100dp = 200px
        assertEquals(200, dimension.toPixels(1000, 2.0f))
        // With density 1.0, 100dp = 100px
        assertEquals(100, dimension.toPixels(1000, 1.0f))
        // With density 3.0, 100dp = 300px
        assertEquals(300, dimension.toPixels(1000, 3.0f))
    }

    @Test
    fun `Dimension toPixels with PERCENT unit calculates percentage of screen`() {
        val dimension = Dimension(50f, DimensionUnit.PERCENT)
        // 50% of 1000px = 500px (density doesn't matter for percent)
        assertEquals(500, dimension.toPixels(1000, 2.0f))
        assertEquals(500, dimension.toPixels(1000, 1.0f))
        // 50% of 800px = 400px
        assertEquals(400, dimension.toPixels(800, 2.0f))
    }

    @Test
    fun `Dimension fromJson parses valid JSON`() {
        val json = JSONObject("""{"value": 100, "unit": "fixed"}""")
        val dimension = Dimension.fromJson(json)
        assertNotNull(dimension)
        assertEquals(100f, dimension!!.value, 0.01f)
        assertEquals(DimensionUnit.FIXED, dimension.unit)
    }

    @Test
    fun `Dimension fromJson parses percent unit`() {
        val json = JSONObject("""{"value": 50, "unit": "percent"}""")
        val dimension = Dimension.fromJson(json)
        assertNotNull(dimension)
        assertEquals(50f, dimension!!.value, 0.01f)
        assertEquals(DimensionUnit.PERCENT, dimension.unit)
    }

    @Test
    fun `Dimension fromJson returns null for null input`() {
        assertNull(Dimension.fromJson(null))
    }

    @Test
    fun `Dimension dp helper creates fixed dimension`() {
        val dimension = Dimension.dp(150f)
        assertEquals(150f, dimension.value, 0.01f)
        assertEquals(DimensionUnit.FIXED, dimension.unit)
    }

    @Test
    fun `Dimension percent helper creates percent dimension`() {
        val dimension = Dimension.percent(75f)
        assertEquals(75f, dimension.value, 0.01f)
        assertEquals(DimensionUnit.PERCENT, dimension.unit)
    }

    // ===== Margins tests =====

    @Test
    fun `Margins fromJson parses valid JSON`() {
        val json = JSONObject("""{"top": 10, "bottom": 20, "left": 30, "right": 40}""")
        val margins = Margins.fromJson(json)
        assertEquals(10f, margins.top, 0.01f)
        assertEquals(20f, margins.bottom, 0.01f)
        assertEquals(30f, margins.left, 0.01f)
        assertEquals(40f, margins.right, 0.01f)
    }

    @Test
    fun `Margins fromJson defaults to zero for missing values`() {
        val json = JSONObject("""{"top": 10}""")
        val margins = Margins.fromJson(json)
        assertEquals(10f, margins.top, 0.01f)
        assertEquals(0f, margins.bottom, 0.01f)
        assertEquals(0f, margins.left, 0.01f)
        assertEquals(0f, margins.right, 0.01f)
    }

    @Test
    fun `Margins fromJson returns default margins for null input`() {
        val margins = Margins.fromJson(null)
        assertEquals(0f, margins.top, 0.01f)
        assertEquals(0f, margins.bottom, 0.01f)
        assertEquals(0f, margins.left, 0.01f)
        assertEquals(0f, margins.right, 0.01f)
    }

    @Test
    fun `Margins all helper creates uniform margins`() {
        val margins = Margins.all(16f)
        assertEquals(16f, margins.top, 0.01f)
        assertEquals(16f, margins.bottom, 0.01f)
        assertEquals(16f, margins.left, 0.01f)
        assertEquals(16f, margins.right, 0.01f)
    }

    // ===== FormLayout tests =====

    @Test
    fun `FormLayout fromJson parses complete valid JSON`() {
        val json = JSONObject(
            """
            {
                "position": "bottom_right",
                "width": {"value": 300, "unit": "fixed"},
                "height": {"value": 200, "unit": "fixed"},
                "margins": {"top": 0, "bottom": 16, "left": 0, "right": 16}
            }
            """.trimIndent()
        )

        val layout = FormLayout.fromJson(json)
        assertNotNull(layout)
        assertEquals(FormPosition.BOTTOM_RIGHT, layout!!.position)
        assertEquals(300f, layout.width.value, 0.01f)
        assertEquals(DimensionUnit.FIXED, layout.width.unit)
        assertEquals(200f, layout.height.value, 0.01f)
        assertEquals(DimensionUnit.FIXED, layout.height.unit)
        assertEquals(16f, layout.margins.bottom, 0.01f)
        assertEquals(16f, layout.margins.right, 0.01f)
    }

    @Test
    fun `FormLayout fromJson returns null for null input`() {
        assertNull(FormLayout.fromJson(null))
    }

    @Test
    fun `FormLayout fromJson returns null when width is missing`() {
        val json = JSONObject(
            """
            {
                "position": "center",
                "height": {"value": 200, "unit": "fixed"}
            }
            """.trimIndent()
        )
        assertNull(FormLayout.fromJson(json))
    }

    @Test
    fun `FormLayout fromJson returns null when height is missing`() {
        val json = JSONObject(
            """
            {
                "position": "center",
                "width": {"value": 300, "unit": "fixed"}
            }
            """.trimIndent()
        )
        assertNull(FormLayout.fromJson(json))
    }

    @Test
    fun `FormLayout isFullscreen returns true for FULLSCREEN position`() {
        val layout = FormLayout(
            position = FormPosition.FULLSCREEN,
            width = Dimension.percent(100f),
            height = Dimension.percent(100f)
        )
        assertTrue(layout.isFullscreen)
    }

    @Test
    fun `FormLayout isFullscreen returns false for non-FULLSCREEN positions`() {
        val positions = listOf(
            FormPosition.TOP,
            FormPosition.BOTTOM,
            FormPosition.CENTER,
            FormPosition.TOP_LEFT,
            FormPosition.TOP_RIGHT,
            FormPosition.BOTTOM_LEFT,
            FormPosition.BOTTOM_RIGHT
        )

        for (position in positions) {
            val layout = FormLayout(
                position = position,
                width = Dimension.dp(300f),
                height = Dimension.dp(200f)
            )
            assertFalse("$position should not be fullscreen", layout.isFullscreen)
        }
    }
}
