package com.fezrestia.android.webviewwindow.view

import android.content.Context
import android.graphics.Rect
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatImageView

/**
 * ImageView for blocking edge navigation gesture trigger.
 */
class BlockNavigationImageView(
        context: Context,
        attrs: AttributeSet?,
        defStyle: Int) : AppCompatImageView(context, attrs, defStyle) {
    constructor(context: Context) : this(context, null)
    constructor(context: Context, attrs: AttributeSet?) : this(context, attrs, 0)

    private val navExclusionRect = Rect()

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)

        navExclusionRect.set(left, top, right, bottom)
        val list = listOf(navExclusionRect)
        this.systemGestureExclusionRects = list
    }
}
