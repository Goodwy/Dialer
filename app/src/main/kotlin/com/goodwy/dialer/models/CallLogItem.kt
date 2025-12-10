package com.goodwy.dialer.models

import java.io.Serializable
import com.goodwy.commons.helpers.DAY_SECONDS

@kotlinx.serialization.Serializable
sealed class CallLogItem : Serializable {
    data class Date(
        val timestamp: Long,
        val dayCode: String,
    ) : CallLogItem()

    fun getItemId(): Int {
        return when (this) {
            is Date -> -(timestamp / (DAY_SECONDS * 1000L)).toInt()
            is RecentCall -> id
        }
    }
}
