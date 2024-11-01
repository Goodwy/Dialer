package com.goodwy.dialer.extensions

import android.graphics.Rect
import android.view.View

val View.boundingBox
    get() = Rect().also { getGlobalVisibleRect(it) }

fun View.setWidth(size: Int) {
    val lp = layoutParams
    lp.width = size
    layoutParams = lp
}
