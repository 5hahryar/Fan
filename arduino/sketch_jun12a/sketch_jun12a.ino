#include <ESP8266WiFi.h>
#include <OneWire.h>
#include <DallasTemperature.h>
#define ONE_WIRE_BUS D8

OneWire oneWire(ONE_WIRE_BUS);
DallasTemperature sensors(&oneWire);
WiFiClient client;
WiFiServer server(80); 

const char* ssid = "";
const char* pass = "";
const int port = 5000;

int status = WL_IDLE_STATUS;   

// --- Thermostat Modes ---
enum ThermostatMode {
  ON,
  OFF,
  COOLING,
  HEATING
};

// --- Thermostat State Variables ---
ThermostatMode currentThermostatMode = OFF;     // Default mode is OFF
float targetTemperature = 22.0;                 // Default target temperature in Celsius
const float HYSTERESIS = 1.0;                   // 1.0 degree Celsius hysteresis to prevent rapid cycling

// --- Control Pin for the Switch/Relay ---
const int controlPin = 2;                       // Digital pin 2 (e.g., for a relay module)
int currentSwitchState = LOW;                   // Track the current state of the switch (LOW = OFF, HIGH = ON)

// Function prototypes
float readTemperature();
void applyThermostatLogic(float currentTemp);
void printCurrentNet();
void printWiFiData();

void setup() {
  Serial.begin(115200);
  delay(10);
  
  WiFi.begin(ssid, pass);
  Serial.print("connecting to: ");
  Serial.print(ssid);
  Serial.println(" ...");

  while (WiFi.status() != WL_CONNECTED) {
    delay(500);
    Serial.print(".");
  }

  Serial.println("");
  Serial.println("Wifi connected.");
  Serial.print("IP Address: ");
  Serial.println(WiFi.localIP());

  Serial.println("Beginning sensor");
  sensors.begin();

  // Start the HTTP server
  server.begin();
  Serial.println("HTTP Server started on port 80.");

  // Initialize the digital pin (switchPin) as an output.
  // This means the Arduino will control the voltage on this pin.
  pinMode(controlPin, OUTPUT);
}

void loop() {
  // Check WiFi connection status periodically
  if (WiFi.status() != WL_CONNECTED) {
    Serial.println("WiFi connection lost. Reconnecting...");
    status = WL_IDLE_STATUS; // Reset status
    while (status != WL_CONNECTED) {
      status = WiFi.begin(ssid, pass);
      delay(10000); // Wait 10 seconds for reconnection
    }
    Serial.println("Reconnected to WiFi!");
    printWiFiData();
    server.begin(); // Restart the server after reconnecting
  }

  // Always read temperature and apply thermostat logic
  float currentTemp = readTemperature();

  // Listen for incoming clients
  WiFiClient client = server.available(); 

  if (client) { // If a new client connects
    Serial.println("\nNew client connected.");
    String currentLine = "";      // Make a String to hold incoming data from the client
    String httpRequestLine = "";  // To store the first line of the HTTP request (e.g., "GET / HTTP/1.1")

    while (client.connected()) { // Loop while the client is connected
      if (client.available()) {  // If there's bytes to read from the client
        char c = client.read();  // Read a byte
        Serial.write(c);         // Print it to the serial monitor for debugging
        if (c == '\n') {         // If the byte is a newline character
          // If the current line is blank, you got two newline characters in a row.
          // That means the client has sent the end of its HTTP request, so send a response:
          if (currentLine.length() == 0) {
            // Determine the requested path and parse parameters if present
            String path = "";
            int firstSpace = httpRequestLine.indexOf(' ');
            if (firstSpace != -1) {
                int secondSpace = httpRequestLine.indexOf(' ', firstSpace + 1);
                if (secondSpace != -1) {
                    // Extract the path including query parameters
                    path = httpRequestLine.substring(firstSpace + 1, secondSpace);
                }
            }

            // --- Handle /set_thermostat endpoint ---
            if (path.startsWith("/set_thermostat?")) {
                Serial.println("Set thermostat request received.");
                // Parse mode and target parameters from the query string
                int modeIndex = path.indexOf("mode=");
                int targetIndex = path.indexOf("target=");

                if (modeIndex != -1) {
                    String modeString = "";
                    // Find the end of the mode parameter (either '&' or end of string)
                    int endModeIndex = path.indexOf("&", modeIndex);
                    if (endModeIndex == -1) { 
                        modeString = path.substring(modeIndex + 5); // +5 for "mode="
                    } else {
                        modeString = path.substring(modeIndex + 5, endModeIndex);
                    }
                    
                    // Set the thermostat mode based on the parsed string
                    if (modeString.startsWith("heating")) {
                        currentThermostatMode = HEATING;
                        Serial.println("Thermostat mode set to: HEATING");
                    } else if (modeString.startsWith("cooling")) {
                        currentThermostatMode = COOLING;
                        Serial.println("Thermostat mode set to: COOLING");
                    } else if (modeString.startsWith("on")) { // New "on" mode
                        currentThermostatMode = ON;
                        Serial.println("Thermostat mode set to: ON");
                    } else { // Default to OFF for any other value or if not specified
                        currentThermostatMode = OFF;
                        Serial.println("Thermostat mode set to: OFF");
                    }
                }

                if (targetIndex != -1) {
                    String targetString = "";
                    // Find the end of the target parameter (either '&' or end of string)
                    int endTargetIndex = path.indexOf("&", targetIndex);
                    if (endTargetIndex == -1) {
                        targetString = path.substring(targetIndex + 7); // +7 for "target="
                    } else {
                        targetString = path.substring(targetIndex + 7, endTargetIndex);
                    }
                    targetTemperature = targetString.toFloat(); // Convert string to float
                    Serial.print("Target temperature set to: ");
                    Serial.println(targetTemperature);
                }
            }
            // --- End /set_thermostat endpoint handling ---

            // --- Construct JSON response (always include full status) ---
            String jsonResponse = "{";
            jsonResponse += "\"temperatureC\": " + String(currentTemp) + ","; // Use the freshly read temp
            jsonResponse += "\"targetTemperatureC\": " + String(targetTemperature, 1) + ","; // Format to 1 decimal place
            jsonResponse += "\"mode\": \"";
            if (currentThermostatMode == OFF) jsonResponse += "OFF";
            else if (currentThermostatMode == COOLING) jsonResponse += "COOLING";
            else if (currentThermostatMode == HEATING) jsonResponse += "HEATING";
            else if (currentThermostatMode == ON) jsonResponse += "ON"; // Report "ON" mode
            jsonResponse += "\",";
            jsonResponse += "\"switchState\": \"";
            jsonResponse += (currentSwitchState == HIGH ? "ON" : "OFF"); // Report current switch state
            jsonResponse += "\"}";
            
            // Send standard HTTP response header
            client.println("HTTP/1.1 200 OK");
            client.println("Content-Type: application/json"); // Indicate JSON content
            client.print("Content-Length: ");
            client.println(jsonResponse.length()); // Specify content length
            client.println("Connection: close");   // The server will close the connection
            client.println();                      // Blank line to indicate end of headers

            // Send the JSON content
            client.println(jsonResponse);

            break; // Break out of the while loop to stop reading the client's request
          } else { // If you got a newline, and the current line is not blank
            if (httpRequestLine.length() == 0) { // Capture the first line of the request (e.g., GET / HTTP/1.1)
                httpRequestLine = currentLine;
            }
            currentLine = ""; // Clear the currentLine for the next line
          }
        } else if (c != '\r') { // If you got anything else but a carriage return character
          currentLine += c; // Add it to the end of the currentLine
        }
      }
    }
    // Give the web browser time to receive the data
    delay(1); 
    // Close the connection
    client.stop();
    Serial.println("Client disconnected.");
  }

  applyThermostatLogic(currentTemp);
}

// Function to read temperature from analog sensor
float readTemperature() {
  sensors.requestTemperatures();
  float tempC = sensors.getTempCByIndex(0); // تعریف سنسور با float و دریافت عدد اعشاری
  //Serial.println("read temperature as: " + String(tempC));

  return tempC;
}

// Function to apply thermostat logic based on current temperature, target, and mode
void applyThermostatLogic(float currentTemp) {
  // Serial.print("Mode: ");
  // Serial.print(currentThermostatMode == OFF ? "OFF" : (currentThermostatMode == COOLING ? "COOLING" : (currentThermostatMode == HEATING ? "HEATING" : "ON")));
  // Serial.print(", Target: ");
  // Serial.print(targetTemperature);
  // Serial.print(", Current Switch State: ");
  // Serial.println(currentSwitchState == HIGH ? "ON" : "OFF");

  if (currentThermostatMode == COOLING) {
    // Cooling mode: Turn ON if temperature > target + hysteresis, turn OFF if temperature < target
    if (currentTemp > (targetTemperature + HYSTERESIS)) {
      if (currentSwitchState == LOW) { // Only change if state is different
        digitalWrite(controlPin, HIGH); // Assuming HIGH turns the cooling device ON
        currentSwitchState = HIGH;
        Serial.println("Cooling ON (Temperature above target + hysteresis)");
      }
    } else if (currentTemp < targetTemperature) {
      if (currentSwitchState == HIGH) { // Only change if state is different
        digitalWrite(controlPin, LOW);  // Turn cooling device OFF
        currentSwitchState = LOW;
        Serial.println("Cooling OFF (Temperature below target)");
      }
    }
  } else if (currentThermostatMode == HEATING) {
    // Heating mode: Turn ON if temperature < target - hysteresis, turn OFF if temperature > target
    if (currentTemp < (targetTemperature - HYSTERESIS)) {
      if (currentSwitchState == LOW) { // Only change if state is different
        digitalWrite(controlPin, HIGH); // Assuming HIGH turns the heating device ON
        currentSwitchState = HIGH;
        Serial.println("Heating ON (Temperature below target - hysteresis)");
      }
    } else if (currentTemp > targetTemperature) {
      if (currentSwitchState == HIGH) { // Only change if state is different
        digitalWrite(controlPin, LOW);  // Turn heating device OFF
        currentSwitchState = LOW;
        Serial.println("Heating OFF (Temperature above target)");
      }
    }
  } else if (currentThermostatMode == ON) { // New "ON" mode: always keep the switch HIGH
    if (currentSwitchState == LOW) { // Only change if state is different
      digitalWrite(controlPin, HIGH);
      currentSwitchState = HIGH;
      Serial.println("Thermostat ON mode: Switch is ON.");
    }
  } else { // OFF mode: Ensure the switch is always off
    if (currentSwitchState == HIGH) { // Only change if state is different
      digitalWrite(controlPin, LOW);  
      currentSwitchState = LOW;
      Serial.println("Thermostat OFF mode: Switch is OFF.");
    }
  }
}

// Function to print the board's IP address
void printWiFiData() {
  IPAddress ip = WiFi.localIP();
  Serial.print("IP Address: ");
  Serial.println(ip);
}
