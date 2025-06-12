package com.shahryar.fan.network

import com.shahryar.fan.data.ThermostatStatus
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Retrofit interface for thermostat API
 */
interface ThermostatApi {
    /**
     * Get the current thermostat status
     */
    @GET("thermostat/status")
    suspend fun getThermostatStatus(): Response<ThermostatStatus>

    /**
     * Set the thermostat mode and target temperature
     * @param mode The mode to set (off, on, heating, cooling)
     * @param target The target temperature in Celsius
     */
    @GET("set_thermostat")
    suspend fun setThermostat(
        @Query("mode") mode: String,
        @Query("target") target: Double
    ): Response<Void>
}
