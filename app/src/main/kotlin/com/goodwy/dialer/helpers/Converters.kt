package com.goodwy.dialer.helpers

import androidx.room.TypeConverter
import com.goodwy.dialer.extensions.RuntimeTypeAdapterFactory
import com.goodwy.dialer.models.StateWrapper
import com.goodwy.dialer.models.TimerState
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.TypeAdapterFactory
import com.google.gson.reflect.TypeToken

class Converters {
    private val gson = Gson()
    private val stringType = object : TypeToken<List<String>>() {}.type

    fun jsonToStringList(value: String) = gson.fromJson<ArrayList<String>>(value, stringType)

    fun stringListToJson(list: ArrayList<String>) = gson.toJson(list)

    //timer
    val timerStates = valueOf<TimerState>()
        .registerSubtype(TimerState.Idle::class.java)
        .registerSubtype(TimerState.Running::class.java)
        .registerSubtype(TimerState.Paused::class.java)
        .registerSubtype(TimerState.Finished::class.java)

    inline fun <reified T : Any> valueOf(): RuntimeTypeAdapterFactory<T> = RuntimeTypeAdapterFactory.of(T::class.java)

    fun GsonBuilder.registerTypes(vararg types: TypeAdapterFactory) = apply {
        types.forEach { registerTypeAdapterFactory(it) }
    }

    val gsonTimer: Gson = GsonBuilder().registerTypes(timerStates).create()

    @TypeConverter
    fun jsonToTimerState(value: String): TimerState {
        return try {
            gsonTimer.fromJson(value, StateWrapper::class.java).state
        } catch (e: Exception) {
            TimerState.Idle
        }
    }

    @TypeConverter
    fun timerStateToJson(state: TimerState) = gsonTimer.toJson(StateWrapper(state))
}
