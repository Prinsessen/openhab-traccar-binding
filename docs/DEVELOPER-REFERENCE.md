# Beacon Feature - Developer Reference Card

Quick reference for developers working on the Traccar binding beacon functionality.

## Architecture

```
Teltonika FMM920
    ↓ (webhook/polling)
Traccar Server Position Data
    ↓
TraccarDeviceHandler.processBeaconsWithMacRouting()
    ↓
macToBeaconSlot HashMap (MAC → slot mapping)
    ↓
updateBeaconData() for each beacon
    ↓
OpenHAB Channels (beacon1-4 groups)
```

## Key Classes & Methods

### TraccarDeviceHandler.java

**Fields**:
```java
private final Map<String, Integer> macToBeaconSlot = new HashMap<>();
```

**Initialization**:
```java
private void connect() {
    // ... existing code ...
    initializeMacMappingFromConfig();  // Load configured MACs
}

private void initializeMacMappingFromConfig() {
    // Reads beacon1Mac-4Mac from thing configuration
    // Populates macToBeaconSlot
}
```

**Position Processing**:
```java
private void updatePositionChannels(Position position) {
    // ... position channels ...
    processBeaconsWithMacRouting(attributes);  // Beacon routing
}

private void processBeaconsWithMacRouting(Map<String, Object> attributes) {
    // For each tag1-4 from Teltonika:
    //   1. Extract MAC address
    //   2. Look up beacon slot in macToBeaconSlot
    //   3. If not found, call findAvailableBeaconSlot()
    //   4. Route to appropriate beacon1-4 channel
}

private @Nullable Integer findAvailableBeaconSlot(String mac) {
    // Finds first available slot (1-4)
    // Automatically assigns unconfigured MAC to slot
}

private void updateBeaconData(Map<String, Object> attributes, String sourcePrefix, String targetPrefix) {
    // Updates channels: rssi, distance, battery, name, temperature, humidity
    // Clears name to UNDEF if not present (prevents stale data)
}
```

## Data Flow

### Input: Teltonika FMM920 Data

```json
{
  "position": {
    "attributes": {
      "tag1Mac": "aabbcc112233",
      "tag1Name": "MainBag",
      "tag1Rssi": -55,
      "tag1Battery": 2.87,
      "tag1Temp": 2123,
      "tag1Humidity": 45,
      "tag1LowBattery": 0,
      "tag2Mac": "aabbcc778899",
      "tag2Rssi": -63,
      "tag2Battery": 2.85,
      "tag3Mac": "aabbcc445566",
      "tag3Name": "SideBags",
      "tag3Rssi": -51,
      "tag3Battery": 2.86
    }
  }
}
```

**Note**: Tags are dynamically assigned based on scan order, not MAC address.

### Processing: MAC Routing

```
tag1 (aabbcc112233) → macToBeaconSlot lookup → beacon1
tag2 (aabbcc778899) → macToBeaconSlot lookup → beacon3
tag3 (aabbcc445566) → macToBeaconSlot lookup → beacon2
```

### Output: OpenHAB Channels

```
beacon1-mac: aabbcc112233
beacon1-name: MainBag
beacon1-rssi: -55
beacon1-distance: 0.63 m
beacon1-battery: 2.87 V
beacon1-temperature: 21.23 °C

beacon2-mac: aabbcc445566
beacon2-name: SideBags
beacon2-rssi: -51
beacon2-battery: 2.86 V

beacon3-mac: aabbcc778899
beacon3-rssi: -63
beacon3-distance: 1.78 m
beacon3-battery: 2.85 V
```

## Configuration

### thing-types.xml

```xml
<!-- Beacon MAC routing -->
<parameter name="beacon1Mac" type="text">
    <label>Beacon 1 MAC Address</label>
    <description>MAC address to assign to Beacon 1 slot (e.g., aabbcc112233)</description>
    <advanced>true</advanced>
</parameter>

<!-- Distance calculation tuning -->
<parameter name="beaconTxPower" type="integer">
    <label>Beacon Tx Power</label>
    <description>Beacon transmit power at 1m (dBm)</description>
    <default>-59</default>
    <advanced>true</advanced>
</parameter>

<parameter name="beaconPathLoss" type="decimal">
    <label>Beacon Path Loss Exponent</label>
    <description>Environment-dependent path loss (2.0=free space, 3.0-4.0=indoor)</description>
    <default>2.0</default>
    <advanced>true</advanced>
</parameter>
```

### Channel Groups

Each beacon has own channel group with 8 channels:
- `mac` (String)
- `name` (String)
- `rssi` (Number)
- `distance` (Number:Length)
- `battery` (Number:ElectricPotential)
- `lowBattery` (Switch)
- `temperature` (Number:Temperature)
- `humidity` (Number:Dimensionless)

## Distance Calculation

```java
// In updateBeaconData()
Object txPowerObj = getThing().getConfiguration().get("beaconTxPower");
int txPower = (txPowerObj instanceof Number) ? ((Number) txPowerObj).intValue() : -59;

Object pathLossObj = getThing().getConfiguration().get("beaconPathLoss");
double pathLoss = (pathLossObj instanceof Number) ? ((Number) pathLossObj).doubleValue() : 2.0;

// Formula: distance = 10 ^ ((txPower - RSSI) / (10 * pathLossExponent))
double exponent = (txPower - rssiInt) / (10.0 * pathLoss);
double distanceMeters = Math.pow(10, exponent);
```

## Common Issues

### Issue: Handler doesn't reload MAC config on restart

**Cause**: Handler lifecycle - connect() only called on first initialization  
**Solution**: MAC mappings loaded in connect(), persisted in handler memory  
**Workaround**: User must reload thing or restart OpenHAB after config changes

### Issue: Name channel shows stale data

**Cause**: Previous versions didn't clear name when beacon had no name  
**Solution**: Now explicitly sets to UNDEF when name absent:
```java
if (name instanceof String nameValue) {
    updateState(groupId + "#" + CHANNEL_BEACON_NAME, new StringType(cleanName));
} else {
    updateState(groupId + "#" + CHANNEL_BEACON_NAME, UnDefType.UNDEF);
}
```

### Issue: Temperature shows huge number

**Cause**: Temperature transmitted in centidegrees (hundredths of °C)  
**Solution**: Divide by 100:
```java
double celsius = tempValue.doubleValue() / 100.0;
```

## Testing

### Enable Debug Logging

```
openhab> log:set DEBUG org.openhab.binding.traccar
```

### Monitor Beacon Processing

```bash
# Watch for beacon detection
tail -f /var/log/openhab/openhab.log | grep "Found tag.*with MAC"

# Watch routing decisions
tail -f /var/log/openhab/openhab.log | grep "Routing tag"

# Check MAC configuration loading
grep "Configured beacon MAC" /var/log/openhab/openhab.log
```

### Expected Log Output

```
[DEBUG] Initializing beacon MAC mappings from configuration
[DEBUG] Beacon slot 1 config param 'beacon1Mac' = aabbcc112233
[DEBUG] Configured beacon MAC aabbcc112233 for slot 1
[DEBUG] Beacon MAC mapping initialized with 3 entries
...
[DEBUG] Processing beacons with MAC routing
[DEBUG] Found tag1 with MAC aabbcc112233
[DEBUG] Routing tag1 (MAC aabbcc112233) to beacon1
```

## Build & Deploy

```bash
# Format code
cd /etc/openhab-addons/bundles/org.openhab.binding.traccar
mvn spotless:apply

# Build
mvn clean package -DskipTests

# Deploy
sudo cp target/org.openhab.binding.traccar-5.2.0-SNAPSHOT.jar /usr/share/openhab/addons/

# Watch logs
tail -f /var/log/openhab/openhab.log | grep traccar
```

## Code Style

### Null Checking

```java
if (mac instanceof String macValue && !macValue.isBlank()) {
    // Use macValue
}
```

### Logging Levels

- `logger.debug()` - Routine operations (beacon detection, routing)
- `logger.info()` - Important events (webhook received, position updated)
- `logger.warn()` - Unexpected but handled (missing config, null values)
- `logger.error()` - Errors requiring attention (exceptions, failures)

### Distance Handling

```java
// Always include units
updateState(channel, new QuantityType<>(distanceMeters, SIUnits.METRE));

// Not just:
updateState(channel, new DecimalType(distanceMeters));
```

## Future Enhancements

Potential improvements:

1. **Beacon RSSI smoothing**: Average RSSI over N samples to reduce jitter
2. **Configurable distance threshold per beacon**: Different alerts for different beacons
3. **Beacon geofencing**: Trigger when beacon enters/exits range
4. **Battery trend tracking**: Predict when battery replacement needed
5. **Beacon presence history**: Track when beacons were last seen
6. **Multiple MAC routing modes**: Strict (only configured) vs automatic (current)

## Related Files

- **Source**: `src/main/java/org/openhab/binding/traccar/internal/TraccarDeviceHandler.java`
- **Config**: `src/main/resources/OH-INF/thing/thing-types.xml`
- **Docs**: `README.md`, `docs/beacon-*.md`
- **Test Config**: `/etc/openhab/things/traccar.things`

## Support

- **Author**: Nanna Agesen <Nanna@agesen.dk>
- **GitHub**: @Prinsessen
- **Binding Version**: 5.2.0-SNAPSHOT

## References

- **Teltonika FMM920 Manual**: BLE beacon configuration and data format
- **RSSI to Distance Formula**: Log-distance path loss model
- **OpenHAB Units**: `javax.measure` (JSR-385) for type-safe quantities
- **Channel Groups**: OpenHAB thing channel group architecture
