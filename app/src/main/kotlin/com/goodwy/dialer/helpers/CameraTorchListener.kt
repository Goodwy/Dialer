package com.goodwy.dialer.helpers

interface CameraTorchListener {
    fun onTorchEnabled(isEnabled:Boolean)

    fun onTorchUnavailable()
}
