package com.goodwy.dialer.models

import android.telecom.CallAudioState
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.goodwy.dialer.R

enum class AudioRoute(val route: Int, @StringRes val stringRes: Int, @DrawableRes val iconRes: Int) {
    SPEAKER(CallAudioState.ROUTE_SPEAKER, R.string.audio_route_speaker, R.drawable.ic_volume_up_vector), //Speaker
    EARPIECE(CallAudioState.ROUTE_EARPIECE, R.string.audio_route_earpiece_g, R.drawable.ic_phone_device), //Phone
    BLUETOOTH(CallAudioState.ROUTE_BLUETOOTH, R.string.audio_route_bluetooth, R.drawable.ic_bluetooth_audio_vector),
    WIRED_HEADSET(CallAudioState.ROUTE_WIRED_HEADSET, R.string.audio_route_wired_headset, R.drawable.ic_headset_wired), //Wired headphones
    WIRED_OR_EARPIECE(CallAudioState.ROUTE_WIRED_OR_EARPIECE, R.string.audio_route_wired_or_earpiece, R.drawable.ic_volume_down_vector); //Phone or Wired headphones

    companion object {
        fun fromRoute(route: Int?) = values().firstOrNull { it.route == route }
    }
}
