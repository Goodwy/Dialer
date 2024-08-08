package com.goodwy.dialer.models

sealed class Events {
    data object RefreshCallLog : Events()
	
    class StateChanged(val isEnabled: Boolean)

    class CameraUnavailable

    class StopStroboscope

    class StopSOS
}
