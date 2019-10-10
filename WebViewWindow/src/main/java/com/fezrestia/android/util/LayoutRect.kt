package com.fezrestia.android.util

import kotlin.math.max
import kotlin.math.min

/**
 * Rectangle coordinate for layout.
 * Origin point is x-y point. Setting width/height is based on origin.
 *
 * @constructor
 * @param x origin.
 * @param y origin.
 * @param width
 * @param height
 */
class LayoutRect(var x: Int, var y: Int, var width: Int, var height: Int) {
    /**
     * Get longer edge line length.
     *
     * @return Length
     */
    val longLine: Int
        get() = max(width, height)

    /**
     * Get shorter edge line length.
     *
     * @return Length
     */
    val shortLine: Int
        get() = min(width, height)

    override fun toString(): String {
        return "LayoutRect : X-Y=${x}x${y} W-H=${width}x${height}"
    }
}
