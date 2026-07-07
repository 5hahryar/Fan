package com.shahryar.fan.service

import android.content.Context
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.annotation.RequiresApi
import androidx.compose.ui.text.toLowerCase
import com.shahryar.fan.FanMode
import com.shahryar.fan.R
import com.shahryar.fan.network.ThermostatService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class FanTileService : TileService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    @RequiresApi(Build.VERSION_CODES.Q)
    override fun onStartListening() {
        super.onStartListening()
        runBlocking {
            scope.launch {
                val status = ThermostatService.api.getThermostatStatus(
                    ThermostatService.getStatusUrl(getSharedPreferences("settings", Context.MODE_PRIVATE).getString("url", "") ?: "")
                )
                val mode = status.body()?.mode?.lowercase() ?: "off"

                qsTile?.apply {
                    label = "Fan"
                    subtitle = if (mode == "off") "Off" else "On"
                    icon = Icon.createWithResource(this@FanTileService, if (mode == "off") R.drawable.ic_mode_fan_off else R.drawable.ic_mode_fan)
                    state = if (mode == "off") Tile.STATE_INACTIVE else Tile.STATE_ACTIVE
                    updateTile()
                }
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    override fun onClick() {
        super.onClick()

        qsTile?.apply {
            state = if (state == Tile.STATE_ACTIVE) {
                runBlocking {
                    scope.launch {
                        ThermostatService.api.setThermostat(ThermostatService.getSetThermostatUrl(getSharedPreferences("settings", Context.MODE_PRIVATE).getString("url", "") ?: ""), FanMode.OFF.name.lowercase(), 22.0)
                    }
                }
                Tile.STATE_INACTIVE
            } else {
                runBlocking {
                    scope.launch {
                        ThermostatService.api.setThermostat(ThermostatService.getSetThermostatUrl(getSharedPreferences("settings", Context.MODE_PRIVATE).getString("url", "") ?: ""), FanMode.ON.name.lowercase(), 22.0)
                    }
                }
                Tile.STATE_ACTIVE
            }

            icon = if (state == Tile.STATE_ACTIVE) {
                Icon.createWithResource(this@FanTileService, R.drawable.ic_mode_fan)
            } else {
                Icon.createWithResource(this@FanTileService, R.drawable.ic_mode_fan_off)
            }

            subtitle = if (state == Tile.STATE_ACTIVE) {
                "On"
            } else {
                "Off"
            }

            updateTile()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }
}