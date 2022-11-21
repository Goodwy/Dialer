package com.goodwy.dialer.models

data class SpeedDial(val id: Int, var number: String, var displayName: String) {
    fun isValid() = number.trim().isNotEmpty()
}
