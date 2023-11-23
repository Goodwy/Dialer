package com.goodwy.dialer.models

import androidx.annotation.Keep
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "timers")
@Keep
data class Timer(
    @PrimaryKey(autoGenerate = true) var id: Int?,
    var seconds: Int,
    val state: TimerState,
    var vibrate: Boolean,
    var soundUri: String,
    var soundTitle: String,
    var title: String,
    var label: String,
    var description: String,
    var createdAt: Long,
    var channelId: String? = null,
    var oneShot: Boolean = false
)

@Keep
data class ObfuscatedTimer(
    var a: Int?,
    var b: Int,
    // We ignore timer state and will just use idle
    val c: Map<Any, Any>,
    var d: Boolean,
    var e: String,
    var f: String,
    var g: String,
    var h: String,
    var i: String,
    var j: Long,
    var k: String? = null,
    var l: Boolean = false
) {
    fun toTimer() = Timer(a, b, TimerState.Idle, d, e, f, g, h, i, j, k, l)
}
