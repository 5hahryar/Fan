package com.shahryar.fan.network

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Service class to provide an instance of the ThermostatApi
 */
object ThermostatService {
    fun getStatusUrl(baseUrl: String) = "${baseUrl}/thermostat/status"
    fun getSetThermostatUrl(baseUrl: String) = "${baseUrl}/set_thermostat"

    // Create OkHttpClient with logging
    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        })
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()
    
    // Create Gson instance
    private val gson: Gson = GsonBuilder()
        .setLenient()
        .create()

    // Create Retrofit instance
    private val retrofit = Retrofit.Builder()
        .baseUrl("http://localhost")
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create(gson))
        .build()
    
    // Create API instance
    val api: ThermostatApi = retrofit.create(ThermostatApi::class.java)
}