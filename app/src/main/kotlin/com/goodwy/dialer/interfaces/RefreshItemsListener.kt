package com.goodwy.dialer.interfaces

interface RefreshItemsListener {
    fun refreshItems(invalidate: Boolean = false, needUpdate: Boolean = false, callback: (() -> Unit)? = null)
}
