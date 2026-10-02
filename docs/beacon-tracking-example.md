# BLE Beacon Tracking with Teltonika FMM920 - Complete Example

This document provides a complete, ready-to-use configuration for tracking BLE beacons with the Traccar binding and Teltonika FMM920 GPS tracker.

## Use Case

Track cargo bags or panniers on a motorcycle or vehicle. Get alerts when:
- A bag becomes detached (distance > threshold while moving)
- Beacon battery is low
- Temperature is too high/low
- Not all expected beacons are present at startup

## Hardware Requirements

- **GPS Tracker**: Teltonika FMM920 (or compatible BLE-enabled tracker)
- **BLE Beacons**: Any Bluetooth Low Energy beacons (4 maximum)
  - Recommended: Teltonika EYE Beacon (has temperature/humidity sensors)
  - Also works with: iBeacon, Eddystone, or generic BLE beacons

## Step 1: Configure FMM920 Device

### Enable BLE Scanning

1. Connect to FMM920 via Teltonika Configurator
2. Go to **Features** → **Bluetooth**
3. Enable **Bluetooth**
4. Set **Scan interval**: 10 seconds
5. Set **Scan duration**: 5 seconds
6. Enable **Send beacon data**
7. Save and upload configuration

### Configure Beacons in FMM920

1. Go to **Features** → **Bluetooth** → **Beacons**
2. Click **Scan for beacons**
3. Add each discovered beacon to the list
4. Optionally name them (e.g., "MainBag", "SideBags", "RearBag")
5. Save configuration

**Note**: The beacon names configured in FMM920 will appear in the `beaconX-name` channels.

## Step 2: Discover Beacon MAC Addresses

After FMM920 starts sending beacon data, monitor OpenHAB logs to discover MAC addresses:

```bash
# Watch for beacon detection (live monitoring)
tail -f /var/log/openhab/openhab.log | grep "Found tag.*with MAC"

# Or search historical data
grep "Found tag.*with MAC" /var/log/openhab/openhab.log | tail -20
```

Example output:
```
2026-01-20 22:20:55.123 [DEBUG] Found tag1 with MAC aabbcc112233
2026-01-20 22:20:55.124 [DEBUG] Found tag2 with MAC aabbcc778899
2026-01-20 22:20:55.125 [DEBUG] Found tag3 with MAC aabbcc445566
```

Write down these MAC addresses - you'll need them for configuration.

## Step 3: Configure OpenHAB Things

### traccar.things

```openhab
// Traccar Server Bridge
Bridge traccar:server:gpsserver "Traccar GPS Server" [ 
    url="https://gps.yourdomain.com",
    username="your@email.com",
    password="yourpassword",
    refreshInterval=10,
    webhookPort=8090,
    speedUnit="kmh",
    speedThreshold=2.0,
    useNominatim=false,
    beaconTxPower=-59,           // Default: -59 dBm (adjust for your beacons)
    beaconPathLoss=2.2           // Default: 2.0 (2.0=outdoor, 2.7-4.3=indoor)
] {
    // Vehicle/Motorcycle with BLE beacons
    Thing device motorcycle "My Motorcycle" [
        deviceId=10,
        // Beacon MAC addresses (discovered in Step 2)
        beacon1Mac="aabbcc112233",  // Replace with your MAC
        beacon2Mac="aabbcc445566",  // Replace with your MAC
        beacon3Mac="aabbcc778899"   // Replace with your MAC
        // beacon4Mac="xxxxxxxxxxxx"  // Optional 4th beacon
    ]
}
```

**Important**: 
- Replace `deviceId` with your Traccar device ID (found in Traccar web interface)
- Replace beacon MAC addresses with the ones you discovered
- MAC addresses must be lowercase without colons: `aabbcc112233` ✓, `AA:BB:CC:11:22:33` ✗

## Step 4: Create Items

### traccar.items

```openhab
// ============================================================================
// VEHICLE/MOTORCYCLE GPS TRACKING
// ============================================================================

Group gMotorcycle "Motorcycle" <motorbike>
Group gMotorcyclePosition "Position" (gMotorcycle)
Group gBeacons "Cargo Beacons" (gMotorcycle) <bag>

// ----------------------------------------------------------------------------
// Position & Navigation
// ----------------------------------------------------------------------------

Location Motorcycle_Position "Position" (gMotorcyclePosition) 
    {channel="traccar:device:gpsserver:motorcycle:position"}

Number:Length Motorcycle_Altitude "Altitude [%.1f m]" (gMotorcyclePosition) 
    {channel="traccar:device:gpsserver:motorcycle:altitude"}

Number:Speed Motorcycle_Speed "Speed [%.1f %unit%]" (gMotorcyclePosition) 
    {channel="traccar:device:gpsserver:motorcycle:speed"}

Number:Angle Motorcycle_Course "Direction [%.0f °]" (gMotorcyclePosition) 
    {channel="traccar:device:gpsserver:motorcycle:course"}

String Motorcycle_Address "Address [%s]" (gMotorcyclePosition) 
    {channel="traccar:device:gpsserver:motorcycle:address"}

Switch Motorcycle_Motion "Motion" (gMotorcyclePosition) 
    {channel="traccar:device:gpsserver:motorcycle:motion"}

Number:Length Motorcycle_TotalDistance "Odometer [%.1f km]" (gMotorcyclePosition) 
    {channel="traccar:device:gpsserver:motorcycle:totalDistance", unit="km"}

DateTime Motorcycle_LastUpdate "Last Update [%1$tF %1$tR]" (gMotorcyclePosition) 
    {channel="traccar:device:gpsserver:motorcycle:lastUpdate"}

String Motorcycle_Status "Status [%s]" (gMotorcyclePosition) 
    {channel="traccar:device:gpsserver:motorcycle:status"}

Switch Motorcycle_Ignition "Ignition [MAP(ignition.map):%s]" (gMotorcyclePosition) 
    {channel="traccar:device:gpsserver:motorcycle:ignition"}

// ----------------------------------------------------------------------------
// BEACON 1 - Primary Cargo (e.g., MainBag Bag)
// ----------------------------------------------------------------------------

String Beacon1_Mac "Beacon 1 MAC [%s]" <bluetooth> (gBeacons)
    {channel="traccar:device:gpsserver:motorcycle:beacon1-mac"}

String Beacon1_Name "Beacon 1 [%s]" <bag> (gBeacons)
    {channel="traccar:device:gpsserver:motorcycle:beacon1-name"}

Number Beacon1_RSSI "Beacon 1 Signal [%d dBm]" <signal> (gBeacons)
    {channel="traccar:device:gpsserver:motorcycle:beacon1-rssi"}

Number:Length Beacon1_Distance "Beacon 1 Distance [%.2f m]" <distance> (gBeacons)
    {channel="traccar:device:gpsserver:motorcycle:beacon1-distance"}

Number:ElectricPotential Beacon1_Battery "Beacon 1 Battery [%.2f V]" <battery> (gBeacons)
    {channel="traccar:device:gpsserver:motorcycle:beacon1-battery"}

Switch Beacon1_LowBattery "Beacon 1 Low Battery" <lowbattery> (gBeacons)
    {channel="traccar:device:gpsserver:motorcycle:beacon1-lowBattery"}

Number:Temperature Beacon1_Temperature "Beacon 1 Temp [%.1f °C]" <temperature> (gBeacons)
    {channel="traccar:device:gpsserver:motorcycle:beacon1-temperature"}

Number:Dimensionless Beacon1_Humidity "Beacon 1 Humidity [%.0f %%]" <humidity> (gBeacons)
    {channel="traccar:device:gpsserver:motorcycle:beacon1-humidity"}

// ----------------------------------------------------------------------------
// BEACON 2 - Secondary Cargo (e.g., side bags)
// ----------------------------------------------------------------------------

String Beacon2_Mac "Beacon 2 MAC [%s]" <bluetooth> (gBeacons)
    {channel="traccar:device:gpsserver:motorcycle:beacon2-mac"}

String Beacon2_Name "Beacon 2 [%s]" <bag> (gBeacons)
    {channel="traccar:device:gpsserver:motorcycle:beacon2-name"}

Number Beacon2_RSSI "Beacon 2 Signal [%d dBm]" <signal> (gBeacons)
    {channel="traccar:device:gpsserver:motorcycle:beacon2-rssi"}

Number:Length Beacon2_Distance "Beacon 2 Distance [%.2f m]" <distance> (gBeacons)
    {channel="traccar:device:gpsserver:motorcycle:beacon2-distance"}

Number:ElectricPotential Beacon2_Battery "Beacon 2 Battery [%.2f V]" <battery> (gBeacons)
    {channel="traccar:device:gpsserver:motorcycle:beacon2-battery"}

Switch Beacon2_LowBattery "Beacon 2 Low Battery" <lowbattery> (gBeacons)
    {channel="traccar:device:gpsserver:motorcycle:beacon2-lowBattery"}

Number:Temperature Beacon2_Temperature "Beacon 2 Temp [%.1f °C]" <temperature> (gBeacons)
    {channel="traccar:device:gpsserver:motorcycle:beacon2-temperature"}

Number:Dimensionless Beacon2_Humidity "Beacon 2 Humidity [%.0f %%]" <humidity> (gBeacons)
    {channel="traccar:device:gpsserver:motorcycle:beacon2-humidity"}

// ----------------------------------------------------------------------------
// BEACON 3 - Tertiary Cargo (e.g., RearBag Bag)
// ----------------------------------------------------------------------------

String Beacon3_Mac "Beacon 3 MAC [%s]" <bluetooth> (gBeacons)
    {channel="traccar:device:gpsserver:motorcycle:beacon3-mac"}

String Beacon3_Name "Beacon 3 [%s]" <bag> (gBeacons)
    {channel="traccar:device:gpsserver:motorcycle:beacon3-name"}

Number Beacon3_RSSI "Beacon 3 Signal [%d dBm]" <signal> (gBeacons)
    {channel="traccar:device:gpsserver:motorcycle:beacon3-rssi"}

Number:Length Beacon3_Distance "Beacon 3 Distance [%.2f m]" <distance> (gBeacons)
    {channel="traccar:device:gpsserver:motorcycle:beacon3-distance"}

Number:ElectricPotential Beacon3_Battery "Beacon 3 Battery [%.2f V]" <battery> (gBeacons)
    {channel="traccar:device:gpsserver:motorcycle:beacon3-battery"}

Switch Beacon3_LowBattery "Beacon 3 Low Battery" <lowbattery> (gBeacons)
    {channel="traccar:device:gpsserver:motorcycle:beacon3-lowBattery"}

// ----------------------------------------------------------------------------
// BEACON 4 - Optional 4th Beacon
// ----------------------------------------------------------------------------

String Beacon4_Name "Beacon 4 [%s]" <bag> (gBeacons)
    {channel="traccar:device:gpsserver:motorcycle:beacon4-name"}

Number:Length Beacon4_Distance "Beacon 4 Distance [%.2f m]" <distance> (gBeacons)
    {channel="traccar:device:gpsserver:motorcycle:beacon4-distance"}
```

## Step 5: Create Automation Rules

### rules/beacon-tracking.rules

```openhab
// ============================================================================
// BEACON CARGO TRACKING AUTOMATION
// ============================================================================

// ----------------------------------------------------------------------------
// Alert: Cargo Detached While Moving
// ----------------------------------------------------------------------------

rule "Cargo Detached Warning"
when
    Item Beacon1_Distance changed or
    Item Beacon2_Distance changed or
    Item Beacon3_Distance changed
then
    // Only check when vehicle is moving
    val speed = (Motorcycle_Speed.state as QuantityType<Speed>).toUnit("km/h").doubleValue()
    
    if (speed > 5.0) {
        // Check each beacon distance
        if (Beacon1_Distance.state != NULL && Beacon1_Distance.state != UNDEF) {
            val distance1 = (Beacon1_Distance.state as QuantityType<Length>).toUnit("m").doubleValue()
            
            if (distance1 > 10.0) {
                val name = Beacon1_Name.state.toString
                logWarn("Cargo", "⚠️ {} may be DETACHED! Distance: {:.1f} m", name, distance1)
                
                sendNotification("admin@example.com",
                    "⚠️ CARGO WARNING: " + name + " is " + String::format("%.1f", distance1) + "m away while moving at " + 
                    String::format("%.0f", speed) + " km/h!")
            }
        }
        
        if (Beacon2_Distance.state != NULL && Beacon2_Distance.state != UNDEF) {
            val distance2 = (Beacon2_Distance.state as QuantityType<Length>).toUnit("m").doubleValue()
            
            if (distance2 > 10.0) {
                val name = Beacon2_Name.state.toString
                logWarn("Cargo", "⚠️ {} may be DETACHED! Distance: {:.1f} m", name, distance2)
                
                sendNotification("admin@example.com",
                    "⚠️ CARGO WARNING: " + name + " is " + String::format("%.1f", distance2) + "m away!")
            }
        }
        
        if (Beacon3_Distance.state != NULL && Beacon3_Distance.state != UNDEF) {
            val distance3 = (Beacon3_Distance.state as QuantityType<Length>).toUnit("m").doubleValue()
            
            if (distance3 > 10.0) {
                val name = Beacon3_Name.state.toString
                logWarn("Cargo", "⚠️ {} may be DETACHED! Distance: {:.1f} m", name, distance3)
            }
        }
    }
end

// ----------------------------------------------------------------------------
// Alert: Low Battery
// ----------------------------------------------------------------------------

rule "Beacon Low Battery Alert"
when
    Item Beacon1_LowBattery changed to ON or
    Item Beacon2_LowBattery changed to ON or
    Item Beacon3_LowBattery changed to ON
then
    var String beaconName = ""
    var String battery = ""
    
    if (Beacon1_LowBattery.state == ON) {
        beaconName = Beacon1_Name.state.toString
        battery = Beacon1_Battery.state.toString
    } else if (Beacon2_LowBattery.state == ON) {
        beaconName = Beacon2_Name.state.toString
        battery = Beacon2_Battery.state.toString
    } else if (Beacon3_LowBattery.state == ON) {
        beaconName = Beacon3_Name.state.toString
        battery = Beacon3_Battery.state.toString
    }
    
    logWarn("Beacon", "🔋 Low battery alert: {} ({})", beaconName, battery)
    
    sendNotification("admin@example.com", 
        "🔋 Beacon Low Battery: " + beaconName + " - " + battery + ". Replace battery soon!")
end

// ----------------------------------------------------------------------------
// Alert: Temperature Warning
// ----------------------------------------------------------------------------

rule "Beacon High Temperature Alert"
when
    Item Beacon1_Temperature changed or
    Item Beacon2_Temperature changed
then
    val checkBeacon = [String name, Number:Temperature temp |
        if (temp != NULL && temp != UNDEF) {
            val celsius = (temp as QuantityType<Temperature>).toUnit("°C").doubleValue()
            
            if (celsius > 45.0) {
                logWarn("Beacon", "🔥 High temperature in {}: {:.1f} °C", name, celsius)
                sendNotification("admin@example.com",
                    "🔥 High temperature alert: " + name + " - " + String::format("%.1f", celsius) + "°C. " +
                    "Check for direct sunlight or overheating!")
                return true
            } else if (celsius < -10.0) {
                logWarn("Beacon", "❄️ Low temperature in {}: {:.1f} °C", name, celsius)
                sendNotification("admin@example.com",
                    "❄️ Low temperature alert: " + name + " - " + String::format("%.1f", celsius) + "°C")
                return true
            }
        }
        return false
    ]
    
    checkBeacon.apply(Beacon1_Name.state.toString, Beacon1_Temperature.state as Number:Temperature)
    checkBeacon.apply(Beacon2_Name.state.toString, Beacon2_Temperature.state as Number:Temperature)
end

// ----------------------------------------------------------------------------
// Startup Check: Verify All Cargo Present
// ----------------------------------------------------------------------------

rule "All Cargo Present Check on Ignition"
when
    Item Motorcycle_Ignition changed to ON
then
    logInfo("Cargo", "Ignition ON - checking cargo beacons...")
    
    // Wait 5 seconds for initial beacon scan
    Thread::sleep(5000)
    
    var int expectedBeacons = 3  // Change to match your number of beacons
    var int presentCount = 0
    val java.util.List<String> presentBeacons = newArrayList()
    val java.util.List<String> missingBeacons = newArrayList()
    
    // Check each beacon
    if (Beacon1_Name.state != NULL && Beacon1_Name.state != UNDEF && 
        Beacon1_Name.state.toString != "-" && Beacon1_Name.state.toString != "") {
        presentCount = presentCount + 1
        presentBeacons.add(Beacon1_Name.state.toString)
    } else {
        missingBeacons.add("Beacon 1")
    }
    
    if (Beacon2_Name.state != NULL && Beacon2_Name.state != UNDEF && 
        Beacon2_Name.state.toString != "-" && Beacon2_Name.state.toString != "") {
        presentCount = presentCount + 1
        presentBeacons.add(Beacon2_Name.state.toString)
    } else {
        missingBeacons.add("Beacon 2")
    }
    
    if (Beacon3_Name.state != NULL && Beacon3_Name.state != UNDEF && 
        Beacon3_Name.state.toString != "-" && Beacon3_Name.state.toString != "") {
        presentCount = presentCount + 1
        presentBeacons.add(Beacon3_Name.state.toString)
    } else {
        missingBeacons.add("Beacon 3")
    }
    
    // Report results
    if (presentCount < expectedBeacons) {
        logWarn("Cargo", "⚠️ Only {} of {} expected beacons detected!", presentCount, expectedBeacons)
        logWarn("Cargo", "Present: {}", presentBeacons.join(", "))
        logWarn("Cargo", "Missing: {}", missingBeacons.join(", "))
        
        sendNotification("admin@example.com",
            "⚠️ CARGO CHECK: Only " + presentCount + "/" + expectedBeacons + " bags detected at startup! " +
            "Missing: " + missingBeacons.join(", "))
    } else {
        logInfo("Cargo", "✓ All {} cargo beacons present: {}", expectedBeacons, presentBeacons.join(", "))
    }
end

// ----------------------------------------------------------------------------
// Periodic Check: Weak Signal Warning
// ----------------------------------------------------------------------------

rule "Beacon Weak Signal Check"
when
    Time cron "0 */5 * * * ?" // Every 5 minutes
then
    // Only check when moving
    val speed = (Motorcycle_Speed.state as QuantityType<Speed>).toUnit("km/h").doubleValue()
    
    if (speed > 5.0) {
        val checkSignal = [String name, Number rssi |
            if (rssi != NULL && rssi != UNDEF) {
                val signal = (rssi as Number).intValue()
                
                if (signal < -80) {
                    logWarn("Beacon", "📡 Weak signal from {}: {} dBm", name, signal)
                    return true
                }
            }
            return false
        ]
        
        checkSignal.apply(Beacon1_Name.state.toString, Beacon1_RSSI.state as Number)
        checkSignal.apply(Beacon2_Name.state.toString, Beacon2_RSSI.state as Number)
        checkSignal.apply(Beacon3_Name.state.toString, Beacon3_RSSI.state as Number)
    }
end
```

## Step 6: Create Sitemap (Optional)

### sitemaps/motorcycle.sitemap

```openhab
sitemap motorcycle label="Motorcycle Tracking" {
    Frame label="Position" {
        Mapview item=Motorcycle_Position height=8
        Text item=Motorcycle_Address icon="location"
        Text item=Motorcycle_Speed icon="speed"
        Text item=Motorcycle_TotalDistance icon="line"
        Switch item=Motorcycle_Ignition icon="fire"
        Text item=Motorcycle_LastUpdate icon="time"
    }
    
    Frame label="Cargo Beacons" {
        Text label="Beacon 1 - MainBag Bag" icon="bag" {
            Default item=Beacon1_Name
            Text item=Beacon1_Mac icon="bluetooth"
            Text item=Beacon1_RSSI icon="signal" 
                valuecolor=[Beacon1_RSSI>-50="green", Beacon1_RSSI>-70="orange", ="red"]
            Text item=Beacon1_Distance icon="distance"
                valuecolor=[Beacon1_Distance<2="green", Beacon1_Distance<5="orange", ="red"]
            Text item=Beacon1_Battery icon="battery"
                valuecolor=[Beacon1_Battery>2.8="green", Beacon1_Battery>2.5="orange", ="red"]
            Switch item=Beacon1_LowBattery icon="lowbattery"
            Text item=Beacon1_Temperature icon="temperature"
            Text item=Beacon1_Humidity icon="humidity"
        }
        
        Text label="Beacon 2 - Side bags" icon="bag" {
            Default item=Beacon2_Name
            Text item=Beacon2_Mac icon="bluetooth"
            Text item=Beacon2_RSSI icon="signal"
                valuecolor=[Beacon2_RSSI>-50="green", Beacon2_RSSI>-70="orange", ="red"]
            Text item=Beacon2_Distance icon="distance"
                valuecolor=[Beacon2_Distance<2="green", Beacon2_Distance<5="orange", ="red"]
            Text item=Beacon2_Battery icon="battery"
                valuecolor=[Beacon2_Battery>2.8="green", Beacon2_Battery>2.5="orange", ="red"]
            Switch item=Beacon2_LowBattery icon="lowbattery"
            Text item=Beacon2_Temperature icon="temperature"
            Text item=Beacon2_Humidity icon="humidity"
        }
        
        Text label="Beacon 3 - RearBag Bag" icon="bag" {
            Default item=Beacon3_Name
            Text item=Beacon3_Mac icon="bluetooth"
            Text item=Beacon3_RSSI icon="signal"
            Text item=Beacon3_Distance icon="distance"
            Text item=Beacon3_Battery icon="battery"
            Switch item=Beacon3_LowBattery icon="lowbattery"
        }
    }
}
```

## Step 7: Testing

### 1. Verify Beacon Detection

Check logs to see if beacons are being detected:

```bash
tail -f /var/log/openhab/openhab.log | grep -E "Found tag|Routing tag"
```

Expected output:
```
[DEBUG] Found tag1 with MAC aabbcc112233
[DEBUG] Routing tag1 (MAC aabbcc112233) to beacon1
[DEBUG] Found tag2 with MAC aabbcc445566
[DEBUG] Routing tag2 (MAC aabbcc445566) to beacon2
```

### 2. Check Item States

```bash
# Via OpenHAB CLI
openhab> openhab:status Beacon1_Name
openhab> openhab:status Beacon1_Distance
openhab> openhab:status Beacon1_Battery

# Via REST API
curl http://localhost:8080/rest/items/Beacon1_Name
curl http://localhost:8080/rest/items/Beacon2_Distance
```

### 3. Test Distance Alerts

Walk away from the vehicle with a beacon while it's running (ignition ON) and speed > 5 km/h. You should receive alerts when distance exceeds 10 meters.

### 4. Test Startup Check

Turn ignition OFF, remove one beacon, turn ignition ON. You should get a notification about missing beacon.

## Troubleshooting

### Beacons Not Appearing

1. **Check FMM920 configuration**: Ensure BLE scanning is enabled and beacons are added to device
2. **Verify webhook is working**: `grep "Received webhook" /var/log/openhab/openhab.log | tail -5`
3. **Enable debug logging**: 
   ```
   openhab> log:set DEBUG org.openhab.binding.traccar
   ```
4. **Check for errors**: `grep ERROR /var/log/openhab/openhab.log | grep traccar`

### Beacon Data Shuffling

If beacon names/data keep changing between channels:

1. Ensure you configured `beacon1Mac`, `beacon2Mac`, etc. in thing configuration
2. MAC addresses must be lowercase without colons
3. Restart thing: `openhab-cli reload-thing traccar:device:gpsserver:motorcycle`
4. Check routing: `grep "Routing tag" /var/log/openhab/openhab.log | tail -10`

### Distance Inaccurate

Adjust `beaconTxPower` and `beaconPathLoss` values on the Server Bridge:

```openhab
Bridge traccar:server:gpsserver [
    url="https://gps.example.com",
    username="user@example.com",
    password="password",
    beaconTxPower=-62,      // Try different values: -59 to -65
    beaconPathLoss=2.5      // Try: 2.0 (outdoor) to 4.3 (indoor)
]
```

Test by placing beacon at known distances (1m, 2m, 5m) and compare calculated vs actual.

### Temperature/Humidity Always NULL

This is normal if your beacons don't have environmental sensors. Only advanced beacons like Teltonika EYE Beacon provide temperature/humidity. Basic BLE beacons only provide MAC, RSSI, battery, and name.

## Advanced Configuration

### Custom Distance Thresholds

Modify the rules to use different distance thresholds for each beacon:

```openhab
if (distance1 > 15.0) {  // Beacon 1: 15 meters
if (distance2 > 8.0) {   // Beacon 2: 8 meters
if (distance3 > 10.0) {  // Beacon 3: 10 meters
```

### Email Notifications with HTML

Replace `sendNotification()` with HTML email:

```openhab
val mailActions = getActions("mail","mail:smtp:yoursmtp")

val emailBody = "<html><body>" +
    "<h2>⚠️ Cargo Detached Warning</h2>" +
    "<p><strong>Beacon:</strong> " + name + "</p>" +
    "<p><strong>Distance:</strong> " + distance + " meters</p>" +
    "<p><strong>Speed:</strong> " + speed + " km/h</p>" +
    "<p><strong>Location:</strong> " + Motorcycle_Address.state + "</p>" +
    "</body></html>"

mailActions.sendHtmlMail("you@example.com", "⚠️ Cargo Warning", emailBody)
```

### Push Notifications (myOpenHAB Cloud)

```openhab
sendBroadcastNotification("⚠️ CARGO WARNING: " + name + " detached!")
```

## Support

For issues or questions:
- **Binding Author**: Nanna Agesen (Nanna@agesen.dk)
- **GitHub**: https://github.com/Prinsessen
- **Community Forum**: https://community.openhab.org/

## License

This configuration example is provided as-is under the same license as the openHAB Traccar binding (Eclipse Public License 2.0).
