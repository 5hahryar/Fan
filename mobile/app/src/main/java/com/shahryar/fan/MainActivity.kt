package com.shahryar.fan

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shahryar.fan.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import com.shahryar.fan.data.ThermostatStatus
import com.shahryar.fan.network.ThermostatService
import android.util.Log
import androidx.compose.ui.res.painterResource

// Define fan modes
enum class FanMode { OFF, ON, HEATING, COOLING }
enum class FanState { OFF, ON }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FanTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    ThermostatScreen(
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThermostatScreen(modifier: Modifier = Modifier) {
    var currentTemperature by remember { mutableStateOf(22.0) }
    var targetTemperature by remember { mutableStateOf(22.0) }
    var fanMode by remember { mutableStateOf(FanMode.OFF) }
    var fanState by remember { mutableStateOf(FanState.OFF) }
    var isRefreshing by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()

    // Determine temperature color based on value
    val temperatureColor = when {
        currentTemperature < 20 -> CoolBlue
        currentTemperature > 26 -> WarmRed
        else -> NeutralGreen
    }

    // Function to fetch data from server
    fun refreshData() {
        if (!isRefreshing) {
            coroutineScope.launch {
                isRefreshing = true
                errorMessage = null

                try {
                    // Make API call to get thermostat status
                    val response = ThermostatService.api.getThermostatStatus()

                    if (response.isSuccessful) {
                        // Update UI with response data
                        response.body()?.let { status ->
                            currentTemperature = status.temperatureC
                            targetTemperature = status.targetTemperatureC
                            fanMode = status.toFanMode()
                            fanState = status.toFanState()
                            Log.d("ThermostatScreen", "Data refreshed: $status")
                        } ?: run {
                            errorMessage = "Empty response from server"
                            Log.e("ThermostatScreen", "Empty response body")
                        }
                    } else {
                        errorMessage = "Error: ${response.code()} ${response.message()}"
                        Log.e("ThermostatScreen", "API error: ${response.code()} ${response.message()}")
                    }
                } catch (e: Exception) {
                    errorMessage = "Error: ${e.message}"
                    Log.e("ThermostatScreen", "Exception during API call", e)
                } finally {
                    isRefreshing = false
                }
            }
        }
    }

    // Function to set thermostat mode and target temperature
    fun setThermostat(newMode: FanMode? = null, newTarget: Double? = null) {
        coroutineScope.launch {
            try {
                // Use current values if new ones aren't provided
                val modeToSet = newMode ?: fanMode
                val targetToSet = newTarget ?: targetTemperature

                // Convert FanMode enum to lowercase string for API
                val modeString = modeToSet.name.lowercase()

                // Make API call to set thermostat
                val response = ThermostatService.api.setThermostat(
                    mode = modeString,
                    target = targetToSet
                )

                if (response.isSuccessful) {
                    Log.d("ThermostatScreen", "Thermostat set: mode=$modeString, target=$targetToSet")
                    // Refresh data to get updated status
                    refreshData()
                } else {
                    errorMessage = "Error setting thermostat: ${response.code()} ${response.message()}"
                    Log.e("ThermostatScreen", "API error: ${response.code()} ${response.message()}")
                }
            } catch (e: Exception) {
                errorMessage = "Error setting thermostat: ${e.message}"
                Log.e("ThermostatScreen", "Exception during API call", e)
            }
        }
    }

    // Load initial data when the screen is first displayed
    LaunchedEffect(Unit) {
        refreshData()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        // App Title
        Text(
            text = "Thermostat Control",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = 16.dp, bottom = 24.dp)
        )

        // Current Temperature Display
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = temperatureColor.copy(alpha = 0.2f)
            )
        ) {
            Box(
                modifier = Modifier.fillMaxSize()
            ) {
                // Refresh Button
                IconButton(
                    onClick = { refreshData() },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp),
                    enabled = !isRefreshing
                ) {
                    if (isRefreshing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = temperatureColor,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh",
                            tint = temperatureColor
                        )
                    }
                }

                // Temperature Display
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "Current Temperature",
                        fontSize = 18.sp,
                        color = temperatureColor
                    )
                    Text(
                        text = "${String.format("%.2f", currentTemperature)}°C",
                        fontSize = 72.sp,
                        fontWeight = FontWeight.Bold,
                        color = temperatureColor
                    )
                    Icon(
                        painter = painterResource(if (fanState == FanState.ON) R.drawable.ic_mode_fan else R.drawable.ic_mode_fan_off),
                        contentDescription = "Fan",
                        tint = if (fanState == FanState.ON) FanBlue else FanGrey,
                        modifier = Modifier.padding(top = 6.dp).size(24.dp)
                    )
                }
            }
        }

        // Temperature Controls
        AnimatedVisibility(
            visible = fanMode == FanMode.HEATING || fanMode == FanMode.COOLING,
            enter = fadeIn(animationSpec = tween(300)) + expandVertically(
                animationSpec = tween(300),
                expandFrom = Alignment.Top
            ),
            exit = fadeOut(animationSpec = tween(300)) + shrinkVertically(
                animationSpec = tween(300),
                shrinkTowards = Alignment.Top
            )
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Set Temperature",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = { 
                                if (targetTemperature > 15) {
                                    val newTarget = (targetTemperature - 1).coerceAtLeast(15.0)
                                    targetTemperature = newTarget
                                    setThermostat(newTarget = newTarget)
                                }
                            },
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = CoolBlue
                            )
                        ) {
                            Text(text = "-", fontSize = 24.sp)
                        }

                        Text(
                            text = "${String.format("%.2f", targetTemperature)}°C",
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Bold,
                            color = temperatureColor,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )

                        Button(
                            onClick = { 
                                if (targetTemperature < 35) {
                                    val newTarget = (targetTemperature + 1).coerceAtMost(35.0)
                                    targetTemperature = newTarget
                                    setThermostat(newTarget = newTarget)
                                }
                            },
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = WarmRed
                            )
                        ) {
                            Text(text = "+", fontSize = 24.sp)
                        }
                    }
                }
            }
        }

        // Fan Control
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalAlignment = Alignment.Start
            ) {
                Text(
                    text = "Fan Mode",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium,
                    color = when (fanMode) {
                        FanMode.OFF -> MaterialTheme.colorScheme.onSurface
                        FanMode.ON -> FanBlue
                        FanMode.HEATING -> WarmRed
                        FanMode.COOLING -> CoolBlue
                    },
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                // Mode selection buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    FanModeButton(
                        text = "OFF",
                        selected = fanMode == FanMode.OFF,
                        onClick = { 
                            fanMode = FanMode.OFF
                            setThermostat(newMode = FanMode.OFF)
                        },
                        color = MaterialTheme.colorScheme.outline
                    )

                    FanModeButton(
                        text = "ON",
                        selected = fanMode == FanMode.ON,
                        onClick = { 
                            fanMode = FanMode.ON
                            setThermostat(newMode = FanMode.ON)
                        },
                        color = FanBlue
                    )

                    FanModeButton(
                        text = "HEATING",
                        selected = fanMode == FanMode.HEATING,
                        onClick = { 
                            fanMode = FanMode.HEATING
                            setThermostat(newMode = FanMode.HEATING)
                        },
                        color = WarmRed
                    )

                    FanModeButton(
                        text = "COOLING",
                        selected = fanMode == FanMode.COOLING,
                        onClick = { 
                            fanMode = FanMode.COOLING
                            setThermostat(newMode = FanMode.COOLING)
                        },
                        color = CoolBlue
                    )
                }
            }
        }

        // Status Text
        Text(
            text = "Fan Mode: ${fanMode.name}",
            fontSize = 16.sp,
            color = when (fanMode) {
                FanMode.OFF -> MaterialTheme.colorScheme.outline
                FanMode.ON -> FanBlue
                FanMode.HEATING -> WarmRed
                FanMode.COOLING -> CoolBlue
            },
            modifier = Modifier.padding(top = 8.dp)
        )

        // Error message
        errorMessage?.let { error ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Text(
                    text = error,
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(16.dp)
                )
            }
        }
    }
}

@Composable
fun FanModeButton(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    color: Color
) {
    OutlinedButton(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = if (selected) color.copy(alpha = 0.1f) else Color.Transparent,
            contentColor = color
        ),
        border = BorderStroke(
            width = 1.dp,
            color = if (selected) color else color.copy(alpha = 0.5f)
        ),
        modifier = Modifier.padding(horizontal = 4.dp)
    ) {
        Text(
            text = text,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            maxLines = 1
        )
    }
}

@Preview(showBackground = true)
@Composable
fun ThermostatScreenPreview() {
    FanTheme {
        ThermostatScreen()
    }
}
