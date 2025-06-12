package com.shahryar.fan.data

import com.shahryar.fan.FanMode
import com.shahryar.fan.FanState

/**
 * Data class representing the thermostat status from the API
 */
data class ThermostatStatus(
    val temperatureC: Double,
    val targetTemperatureC: Double,
    val mode: String,
    val switchState: String
) {
    /**
     * Maps the API mode string to the app's FanMode enum
     */
    fun toFanMode(): FanMode {
        return when (mode) {
            "HEATING" -> FanMode.HEATING
            "COOLING" -> FanMode.COOLING
            "ON" -> FanMode.ON
            else -> FanMode.OFF
        }
    }

    fun toFanState(): FanState {
        return when (switchState) {
            "ON" -> FanState.ON
            else -> FanState.OFF
        }
    }
}