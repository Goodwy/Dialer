package com.goodwy.dialer.interfaces

interface RefreshItemsListener {
    fun refreshItems(callback: (() -> Unit)? = null)
}
