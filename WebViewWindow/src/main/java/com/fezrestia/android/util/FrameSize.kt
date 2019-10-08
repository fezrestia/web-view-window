@file:Suppress("unused")

package com.fezrestia.android.util

import kotlin.math.max
import kotlin.math.min

/**
 * Frame size descriptor.
 *
 * @constructor
 * @param width Screen width.
 * @param height Screen height.
 */
class FrameSize(val width: Int, val height: Int) {
    /**
     * Get longer edge line length.
     *
     * @return Length
     */
    val longLineSize: Int
        get() = max(width, height)

    /**
     * Get shorter edge line length.
     *
     * @return Length
     */
    val shortLineSize: Int
        get() = min(width, height)

    override fun toString(): String {
        return "FrameSize= $width x $height"
    }
}
