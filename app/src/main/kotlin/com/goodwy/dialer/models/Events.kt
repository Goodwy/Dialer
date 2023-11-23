package com.goodwy.dialer.models

class Events {
    class StateChanged(val isEnabled: Boolean)

    class CameraUnavailable

    class StopStroboscope

    class StopSOS
}
