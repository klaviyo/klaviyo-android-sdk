package com.klaviyo.forms.bridge

import android.view.Gravity
import org.json.JSONObject

/**
 * Position options for floating forms
 */
internal enum class FormPosition {
    FULLSCREEN,
    TOP,
    BOTTOM,
    TOP_LEFT,
    TOP_RIGHT,
    BOTTOM_LEFT,
    BOTTOM_RIGHT,
    CENTER;

    /**
     * Returns true if this position uses horizontal centering (CENTER_HORIZONTAL or CENTER).
     * These positions need width adjusted for horizontal safe area insets so forms
     * don't extend into display cutouts in landscape.
     */
    fun isHorizontallyCentered(): Boolean = when (this) {
        TOP, BOTTOM, CENTER, FULLSCREEN -> true
        TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT -> false
    }

    /**
     * Convert form position to Android Gravity flags
     */
    fun toGravity(): Int = when (this) {
        FULLSCREEN -> Gravity.CENTER
        TOP -> Gravity.TOP or Gravity.CENTER_HORIZONTAL
        BOTTOM -> Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
        TOP_LEFT -> Gravity.TOP or Gravity.START
        TOP_RIGHT -> Gravity.TOP or Gravity.END
        BOTTOM_LEFT -> Gravity.BOTTOM or Gravity.START
        BOTTOM_RIGHT -> Gravity.BOTTOM or Gravity.END
        CENTER -> Gravity.CENTER
    }

    companion object {
        fun fromString(value: String?): FormPosition = when (value?.uppercase()) {
            "FULLSCREEN" -> FULLSCREEN
            "TOP" -> TOP
            "BOTTOM" -> BOTTOM
            "TOP_LEFT" -> TOP_LEFT
            "TOP_RIGHT" -> TOP_RIGHT
            "BOTTOM_LEFT" -> BOTTOM_LEFT
            "BOTTOM_RIGHT" -> BOTTOM_RIGHT
            "CENTER" -> CENTER
            else -> FULLSCREEN
        }
    }
}

/**
 * Unit type for dimensions
 */
internal enum class DimensionUnit {
    PERCENT,
    FIXED;

    companion object {
        fun fromString(value: String?): DimensionUnit = when (value?.uppercase()) {
            "PERCENT" -> PERCENT
            "FIXED" -> FIXED
            else -> FIXED
        }
    }
}

/**
 * Represents a dimension value with its unit type
 */
internal data class Dimension(
    val value: Float,
    val unit: DimensionUnit
) {
    /**
     * Convert dimension to pixels
     *
     * @param screenDimension The screen dimension (width or height) in pixels
     * @param density The screen density (pixels per dp)
     * @return The dimension in pixels
     */
    // TODO: Test on devices with different zoom states
    //  (system-wide settings for controlling content scaling)
    fun toPixels(screenDimension: Int, density: Float): Int = when (unit) {
        DimensionUnit.PERCENT -> (screenDimension * (value / 100f)).toInt()
        DimensionUnit.FIXED -> (value * density).toInt()
    }

    companion object {
        fun fromJson(json: JSONObject?): Dimension? {
            if (json == null) return null
            return Dimension(
                value = json.optDouble("value", 0.0).toFloat(),
                unit = DimensionUnit.fromString(json.optString("unit"))
            )
        }

        /**
         * Create a fixed dimension in dp
         */
        fun dp(value: Float): Dimension = Dimension(value, DimensionUnit.FIXED)

        /**
         * Create a percent dimension
         */
        fun percent(value: Float): Dimension = Dimension(value, DimensionUnit.PERCENT)
    }
}

/**
 * Offsets around the form in dp
 */
internal data class Offsets(
    val top: Float = 0f,
    val bottom: Float = 0f,
    val left: Float = 0f,
    val right: Float = 0f
) {
    companion object {
        fun fromJson(json: JSONObject?): Offsets {
            if (json == null) return Offsets()
            return Offsets(
                top = json.optDouble("top", 0.0).toFloat(),
                bottom = json.optDouble("bottom", 0.0).toFloat(),
                left = json.optDouble("left", 0.0).toFloat(),
                right = json.optDouble("right", 0.0).toFloat()
            )
        }

        /**
         * Create uniform offsets
         */
        fun all(value: Float): Offsets = Offsets(value, value, value, value)
    }
}

/**
 * Complete layout configuration for a form
 */
internal data class FormLayout(
    val position: FormPosition,
    val width: Dimension,
    val height: Dimension,
    val offsets: Offsets = Offsets()
) {
    /**
     * Returns true if this layout represents a fullscreen form
     */
    val isFullscreen: Boolean
        get() = position == FormPosition.FULLSCREEN

    companion object {
        fun fromJson(json: JSONObject?): FormLayout? {
            if (json == null) return null

            val position = FormPosition.fromString(json.optString("position"))
            val width = Dimension.fromJson(json.optJSONObject("width")) ?: return null
            val height = Dimension.fromJson(json.optJSONObject("height")) ?: return null
            val offsets = Offsets.fromJson(json.optJSONObject("margin"))

            return FormLayout(
                position = position,
                width = width,
                height = height,
                offsets = offsets
            )
        }
    }
}
