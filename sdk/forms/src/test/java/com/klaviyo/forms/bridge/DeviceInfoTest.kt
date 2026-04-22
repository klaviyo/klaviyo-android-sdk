package com.klaviyo.forms.bridge

import android.content.res.Configuration
import android.util.DisplayMetrics
import android.view.Surface
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

class DeviceInfoTest {

    private fun displayMetrics(widthPx: Int, heightPx: Int, density: Float): DisplayMetrics =
        DisplayMetrics().apply {
            this.widthPixels = widthPx
            this.heightPixels = heightPx
            this.density = density
        }

    private fun configuration(orientation: Int): Configuration =
        Configuration().apply {
            this.orientation = orientation
        }

    @Test
    fun `toJson emits the documented shape`() {
        val info = DeviceInfo(
            screenWidthDp = 402,
            screenHeightDp = 874,
            insetTopDp = 47,
            insetBottomDp = 34,
            insetLeftDp = 0,
            insetRightDp = 0,
            orientation = DeviceInfo.Orientation.PortraitPrimary,
            dpr = 3
        )

        val json = JSONObject(info.toJson())

        assertEquals(402, json.getJSONObject("screen").getInt("width"))
        assertEquals(874, json.getJSONObject("screen").getInt("height"))
        assertEquals(47, json.getJSONObject("safeAreaInsets").getInt("top"))
        assertEquals(34, json.getJSONObject("safeAreaInsets").getInt("bottom"))
        assertEquals(0, json.getJSONObject("safeAreaInsets").getInt("left"))
        assertEquals(0, json.getJSONObject("safeAreaInsets").getInt("right"))
        assertEquals("portrait-primary", json.getString("orientation"))
        assertEquals(3, json.getInt("dpr"))
    }

    @Test
    fun `Orientation maps rotation and config to CSSOM labels`() {
        val portrait = Configuration.ORIENTATION_PORTRAIT
        val landscape = Configuration.ORIENTATION_LANDSCAPE

        assertEquals(
            DeviceInfo.Orientation.PortraitPrimary,
            DeviceInfo.Orientation.from(portrait, Surface.ROTATION_0)
        )
        assertEquals(
            DeviceInfo.Orientation.PortraitSecondary,
            DeviceInfo.Orientation.from(portrait, Surface.ROTATION_180)
        )
        assertEquals(
            DeviceInfo.Orientation.LandscapePrimary,
            DeviceInfo.Orientation.from(landscape, Surface.ROTATION_90)
        )
        assertEquals(
            DeviceInfo.Orientation.LandscapeSecondary,
            DeviceInfo.Orientation.from(landscape, Surface.ROTATION_270)
        )
    }

    @Test
    fun `from converts pixels to dp at density 1`() {
        val info = DeviceInfo.from(
            displayMetrics = displayMetrics(widthPx = 400, heightPx = 800, density = 1f),
            configuration = configuration(Configuration.ORIENTATION_PORTRAIT),
            rotation = Surface.ROTATION_0,
            insetLeftPx = 0,
            insetTopPx = 50,
            insetRightPx = 0,
            insetBottomPx = 30
        )

        assertEquals(400, info.screenWidthDp)
        assertEquals(800, info.screenHeightDp)
        assertEquals(50, info.insetTopDp)
        assertEquals(30, info.insetBottomDp)
        assertEquals(1, info.dpr)
    }

    @Test
    fun `from converts pixels to dp at density 2`() {
        val info = DeviceInfo.from(
            displayMetrics = displayMetrics(widthPx = 800, heightPx = 1600, density = 2f),
            configuration = configuration(Configuration.ORIENTATION_PORTRAIT),
            rotation = Surface.ROTATION_0,
            insetLeftPx = 0,
            insetTopPx = 94,
            insetRightPx = 0,
            insetBottomPx = 68
        )

        assertEquals(400, info.screenWidthDp)
        assertEquals(800, info.screenHeightDp)
        assertEquals(47, info.insetTopDp)
        assertEquals(34, info.insetBottomDp)
        assertEquals(2, info.dpr)
    }

    @Test
    fun `from converts pixels to dp at density 3`() {
        val info = DeviceInfo.from(
            displayMetrics = displayMetrics(widthPx = 1206, heightPx = 2622, density = 3f),
            configuration = configuration(Configuration.ORIENTATION_PORTRAIT),
            rotation = Surface.ROTATION_0,
            insetLeftPx = 0,
            insetTopPx = 141,
            insetRightPx = 0,
            insetBottomPx = 102
        )

        assertEquals(402, info.screenWidthDp)
        assertEquals(874, info.screenHeightDp)
        assertEquals(47, info.insetTopDp)
        assertEquals(34, info.insetBottomDp)
        assertEquals(3, info.dpr)
    }

    @Test
    fun `from defends against zero density`() {
        val info = DeviceInfo.from(
            displayMetrics = displayMetrics(widthPx = 100, heightPx = 200, density = 0f),
            configuration = configuration(Configuration.ORIENTATION_UNDEFINED),
            rotation = Surface.ROTATION_0,
            insetLeftPx = 0,
            insetTopPx = 0,
            insetRightPx = 0,
            insetBottomPx = 0
        )

        // density 0 should fall back to 1, so px == dp
        assertEquals(100, info.screenWidthDp)
        assertEquals(200, info.screenHeightDp)
        assertEquals(1, info.dpr)
    }

    @Test
    fun `jsEscape escapes backslashes and single quotes`() {
        val raw = "it's a back\\slash"
        val escaped = raw.jsEscape()
        assertEquals("it\\'s a back\\\\slash", escaped)
    }

    @Test
    fun `jsEscape leaves double quotes untouched since caller wraps in single quotes`() {
        // JSONObject already escapes double-quotes inside string values, but the JSON's
        // outer syntax uses them freely — those must survive embedding in a JS single-quote literal.
        val raw = """{"screen":{"width":402}}"""
        assertEquals(raw, raw.jsEscape())
    }
}
