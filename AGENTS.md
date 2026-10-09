# AGENTS.md - Traccar Binding Development Guide

## Overview

This is a comprehensive GPS tracking binding that integrates Traccar server with openHAB. The binding supports real-time position updates via webhooks, extensive channel support, and handles multiple GPS protocols with their specific attribute differences.

**Key Achievement**: Full dual-mode operation (polling + webhooks) with 101 channels per device (plus the 60-channel `can` group on a device with `canAdapter=lvcan`), protocol-agnostic attribute handling, and automatic unit conversions.

## Recent Updates (January 2026)

### Distance Channel Architecture
- **Three separate channels**: `odometer`, `totalDistance`, and `distance` for different tracking needs
- **Protocol-aware implementation**: 
  - Teltonika devices: Use `totalDistance` (actual vehicle odometer)
  - OSMand phones: Use `odometer` (device reading)
- **No more protocol fallback logic**: Each channel directly maps to its Traccar field
- **Breaking change**: `Vehicle10_Odometer` renamed to `Vehicle10_TotalDistance` for Teltonika devices
  - **Action required**: Update any rules referencing `Vehicle10_Odometer` to use `Vehicle10_TotalDistance`
  - Example: Motorcycle_Ignition.rules updated in commit 0bf7c99

### Speed Threshold Filtering
- **GPS noise filtering**: Configurable speed threshold (0-10 km/h, default 2.0)
- **Bridge-level configuration**: Single setting applies to all devices
- **Implementation**: Speeds below threshold displayed as 0 km/h
- **Use case**: Eliminates false motion detection from GPS drift

### Nominatim Reverse Geocoding
- **Enhanced address resolution**: OpenStreetMap Nominatim integration
- **Formatted addresses**: "Street number, Postcode City, Province, Country"
- **English worldwide**: Transliterates Greek, Cyrillic, Arabic, Chinese names
- **Rate limiting**: 1 request/second compliance with OSM usage policy
- **Intelligent caching**: Haversine distance with 50m threshold

## Architecture

### Core Components

```
TraccarBindingConstants.java
├── Channel constants (CHANNEL_POSITION, CHANNEL_ALTITUDE, etc.)
└── Thing type UIDs (THING_TYPE_SERVER, THING_TYPE_DEVICE)

TraccarServerHandler.java (Bridge)
├── Traccar API client (authentication, device discovery)
├── Webhook server (TraccarWebhookServer on configurable port)
├── Polling mechanism (position refresh every N seconds)
└── Device registry (maps deviceId to TraccarDeviceHandler)

TraccarDeviceHandler.java (Thing)
├── Position channel updates (dual source: polling + webhooks)
├── Geofence event handling
├── Protocol-agnostic attribute extraction
└── Unit conversions (speed, distance, time)

TraccarWebhookServer.java
├── Jetty HTTP server (default port 8090)
├── POST/GET webhook receiver
├── JSON parsing (position + event data)
└── Handler dispatch to TraccarServerHandler
```

### Data Flow

**Polling Flow**:
1. `TraccarServerHandler.startPolling()` → scheduleWithFixedDelay every `refreshInterval`
2. HTTP GET `/api/positions?deviceId=X` to Traccar API
3. Parse JSON → `TraccarDeviceHandler.updatePositionChannels(position)`
4. Extract attributes → `updateState()` for each channel

**Webhook Flow**:
1. Traccar sends POST to `http://openhab:8090/webhook` with JSON body
2. `TraccarWebhookServer.handle()` receives request
3. Parse JSON → `TraccarServerHandler.handleWebhookEvent(data)`
4. Look up device by deviceId → `TraccarDeviceHandler.handleWebhookEvent(position)`
5. `updatePositionChannels()` extracts all attributes → channel updates

### Channel Implementation Pattern

All channels follow this pattern in `TraccarDeviceHandler.updatePositionChannels()`:

```java
// 1. Extract from position object or attributes
Object valueObj = position.get("fieldName");  // Main position fields
// OR
Object valueObj = attributes.get("attributeName");  // Protocol-specific attributes

// 2. Type check and conversion
if (valueObj instanceof Number) {
    double value = ((Number) valueObj).doubleValue();
    
    // 3. Apply unit conversion if needed
    updateState(CHANNEL_NAME, new QuantityType<>(value, Units.UNIT));
}
// OR for boolean
if (valueObj instanceof Boolean boolValue) {
    updateState(CHANNEL_NAME, OnOffType.from(boolValue));
}
// OR for string
if (valueObj != null) {
    updateState(CHANNEL_NAME, new StringType(valueObj.toString()));
}
```

## Channel Implementation Details

### Position & Navigation Channels

**Source**: Main `position` object from Traccar API/webhook

| Channel | Type | Source Field | Unit | Notes |
|---------|------|--------------|------|-------|
| `position` | Location | `latitude`, `longitude`, `altitude` | - | PointType with 3 coordinates |
| `altitude` | Number:Length | `altitude` | SIUnits.METRE | Direct from position |
| `speed` | Number:Speed | `speed` | Configurable | Traccar sends knots, converted to kmh/mph |
| `course` | Number:Angle | `course` | Units.DEGREE_ANGLE | 0-359° compass bearing |
| `accuracy` | Number:Length | `accuracy` | SIUnits.METRE | GPS accuracy radius |
| `valid` | Switch | `valid` | - | Boolean: GPS fix validity |
| `address` | String | `address` | - | Reverse geocoded street address |

**Speed Conversion**:
```java
// Traccar sends speed in knots
double speedKnots = ((Number) speedObj).doubleValue();
double convertedSpeed;
Unit<?> speedUnit;

switch(speedUnitConfig) {
    case "mph": 
        convertedSpeed = speedKnots * 1.15078; 
        speedUnit = ImperialUnits.MILES_PER_HOUR;
        break;
    case "knots":
        convertedSpeed = speedKnots;
        speedUnit = Units.KNOT;
        break;
    case "kmh":
    default:
        convertedSpeed = speedKnots * 1.852;
        speedUnit = SIUnits.KILOMETRE_PER_HOUR;
}
updateState(CHANNEL_SPEED, new QuantityType<>(convertedSpeed, speedUnit));
```

### Distance Channels (Three Separate Channels)

**Critical Implementation**: Three distinct channels for different distance tracking needs

| Channel | Source Field | Description | Protocol |
|---------|--------------|-------------|----------|
| `odometer` | `attributes.odometer` | Device odometer reading | OSMand only |
| `totalDistance` | `attributes.totalDistance` | Server cumulative distance | All protocols |
| `distance` | `attributes.distance` | Trip distance since last update | All protocols |

**Implementation**:
```java
// Odometer (device-reported, mainly for OSMand)
Object odometerObj = attributes.get("odometer");
if (odometerObj instanceof Number) {
    double odometerMeters = ((Number) odometerObj).doubleValue();
    updateState(CHANNEL_ODOMETER, new QuantityType<>(odometerMeters, SIUnits.METRE));
}

// Total Distance (Traccar server cumulative distance, all protocols)
Object totalDistanceObj = attributes.get("totalDistance");
if (totalDistanceObj instanceof Number) {
    double totalDistanceMeters = ((Number) totalDistanceObj).doubleValue();
    updateState(CHANNEL_TOTAL_DISTANCE, new QuantityType<>(totalDistanceMeters, SIUnits.METRE));
}

// Distance (incremental distance since last update)
Object distanceObj = attributes.get("distance");
if (distanceObj instanceof Number) {
    double distanceMeters = ((Number) distanceObj).doubleValue();
    updateState(CHANNEL_DISTANCE, new QuantityType<>(distanceMeters, SIUnits.METRE));
}
```

**Protocol-Specific Usage**:
- **Teltonika devices**: Use `totalDistance` channel - contains actual vehicle odometer value
  - Example: the motorcycle shows 33,280 km (real odometer reading)
  - Teltonika sends vehicle odometer in the `totalDistance` field
- **OSMand (phone tracking)**: Use `odometer` channel - contains device-reported distance
  - Example: Dream Catcher phone shows 347.8 km (distance tracked by app)
  - OSMand reports app's own tracking in the `odometer` field
  - The `totalDistance` field for OSMand contains Traccar's cumulative calculation (often unrealistically high)
- **All protocols**: Use `distance` channel for real-time trip tracking since last position update

**Unit Conversion Philosophy**:
- Binding sends values in **base units** (meters, not kilometers)
- OpenHAB framework handles conversion based on item `unit="km"` metadata
- This allows users to choose any unit (km, mi, ft, etc.) without binding changes

### Vehicle Information Channels

**Source**: `attributes` object from Traccar webhook/API

| Channel | Type | Source Field | Conversion | Notes |
|---------|------|--------------|------------|-------|
| `ignition` | Switch | `attributes.ignition` | Boolean → OnOffType | Real-time ignition status |
| `hours` | Number:Time | `attributes.hours` | milliseconds → hours | Engine running time |
| `event` | Number | `attributes.event` | Direct | Teltonika AVL event codes |

**Ignition Implementation**:
```java
// Ignition status - available on compatible vehicle trackers (e.g., Teltonika)
Object ignitionObj = attributes.get("ignition");
if (ignitionObj instanceof Boolean) {
    updateState(CHANNEL_IGNITION, OnOffType.from((Boolean) ignitionObj));
}
```

The `ignition` channel is essential for vehicle automation:
- Triggers ignition ON/OFF events in rules
- Enables automatic notifications when vehicle starts/parks
- Provides real-time ignition state monitoring
- Works with Traccar's `ignitionOn`/`ignitionOff` event webhooks

**Example ignition notification rule** (see `Motorcycle_Ignition.rules` in examples):
```java
// Global debounce variables (prevent duplicate notifications within 30 seconds)
var Long lastIgnitionOnTime = 0L
var Long lastIgnitionOffTime = 0L

rule "Motorcycle Ignition ON"
when
    Item Vehicle10_Ignition changed to ON
then
    try {
        // Debounce check
        val currentTime = new java.util.Date().time
        if ((currentTime - lastIgnitionOnTime) < 30000) {
            logInfo("motorcycle_ignition", "Ignition ON triggered too soon - skipping")
            return
        }
        lastIgnitionOnTime = currentTime
        
        // Extract values
        val odometerState = Vehicle10_TotalDistance.state
        val odometer = if (odometerState != NULL) 
            String.format("%.1f km", (odometerState as Number).doubleValue) 
            else "Unknown"
        
        // Send email notification
        sendHtmlMail("email@example.com", "Motorcycle Ignition ON", 
            "<html><body><h2>Motorcycle</h2>" +
            "<table><tr><td><strong>Status:</strong></td><td>Ignition ON</td></tr>" +
            "<tr><td><strong>Odometer:</strong></td><td>" + odometer + "</td></tr></table>" +
            "</body></html>")
    } catch (Exception e) {
        logError("motorcycle_ignition", "Error: {}", e.message)
    }
end
```

**Key learnings from ignition notifications:**
- Use debounce (30-second minimum) to prevent duplicate notifications from multiple webhooks
- OpenHAB DSL requires `new java.util.Date().time` for timestamps (not `now.millis`)
- Use `Long` type for timestamp variables
- Protocol-aware odometer reading ensures correct values (Teltonika uses `totalDistance`)

**Hours (Engine Time) Conversion**:
```java
// Engine hours - convert milliseconds to hours
Object hoursObj = attributes.get("hours");
if (hoursObj instanceof Number) {
    double hoursMillis = ((Number) hoursObj).doubleValue();
    double hoursValue = hoursMillis / 1000.0 / 3600.0;  // ms → seconds → hours
    updateState(CHANNEL_HOURS, new QuantityType<>(hoursValue, Units.HOUR));
}
```

**Event Codes**:
```java
// Event code - device-specific event identifiers
Object eventObj = attributes.get("event");
if (eventObj instanceof Number) {
    int eventCode = ((Number) eventObj).intValue();
    updateState(CHANNEL_EVENT, new DecimalType(eventCode));
}
```

Teltonika event codes (see `transform/teltonika_event.map`):
- 239 = Ignition status change
- 240 = Movement status change  
- 10828/10829/10831 = BLE beacon events
- 253 = Green driving (harsh acceleration/braking)
- 255 = Overspeeding

### Activity & Protocol Channels
- REST API shows converted state: `"state": "33279.52 km"` with `"unitSymbol": "km"`

### Vehicle Information Channels

**Engine Hours** (Teltonika-specific):
```java
// Engine hours (in milliseconds from Traccar)
Object hoursObj = attributes.get("hours");
if (hoursObj instanceof Number) {
    double hoursMs = ((Number) hoursObj).doubleValue();
    double hoursValue = hoursMs / 3600000.0; // Convert milliseconds to hours
    logger.debug("Engine hours: {} ms = {} hours", hoursMs, hoursValue);
    updateState(CHANNEL_HOURS, new QuantityType<>(hoursValue, Units.HOUR));
}
```

**Event Codes** (Teltonika-specific):
```java
// Event code (device-specific)
Object eventObj = attributes.get("event");
if (eventObj instanceof Number) {
    int eventCode = ((Number) eventObj).intValue();
    updateState(CHANNEL_EVENT, new DecimalType(eventCode));
}
```

### Activity Recognition (OSMand-specific)

```java
// Activity (OSMand-specific activity detection)
Object activityObj = attributes.get("activity");
if (activityObj != null) {
    updateState(CHANNEL_ACTIVITY, new StringType(activityObj.toString()));
}
```

Values: `walking`, `in_vehicle`, `still`, `on_bicycle`, `on_foot`, `running`

### Protocol Identification

```java
// Protocol from main position object (not attributes)
Object protocolObj = position.get("protocol");
if (protocolObj != null) {
    updateState(CHANNEL_PROTOCOL, new StringType(protocolObj.toString()));
}
```

## Protocol-Specific Attributes

### Teltonika (Industrial GPS Trackers)

**Webhook attributes example**:
```json
{
  "attributes": {
    "priority": 0,
    "sat": 0,
    "event": 10831,
    "distance": 0.0,
    "totalDistance": 33279520.98,
    "motion": false,
    "hours": 861239290
  }
}
```

**Key points**:
- Uses `totalDistance` instead of `odometer`
- Provides `hours` in milliseconds (engine hours)
- Provides device-specific `event` codes (10828=ignition off, 10829=ignition on, 10831=movement)
- `sat` attribute for GPS satellite count
- No battery (wired devices) or activity recognition

### OSMand / Traccar Client (Mobile Apps)

**Webhook attributes example**:
```json
{
  "attributes": {
    "batteryLevel": 85.0,
    "distance": 15.3,
    "odometer": 179924.0,
    "motion": true,
    "activity": "walking"
  }
}
```

**Key points**:
- Uses `odometer` attribute (not `totalDistance`)
- Provides `activity` recognition (Android/iOS motion APIs)
- Provides `batteryLevel` (percentage)
- No GPS satellite count, no engine hours, no event codes

### Implementation Strategy for Protocol Differences

1. **Always check both attribute names** (fallback pattern):
   ```java
   Object value = attributes.get("preferredName");
   if (value == null) {
       value = attributes.get("alternativeName");
   }
   ```

2. **Null-safe extraction** - channels show NULL if attribute missing (this is expected):
   ```java
   if (valueObj instanceof Type) {
       // Only update if present
       updateState(CHANNEL_NAME, ...);
   }
   // No else needed - channel stays NULL if not supported by protocol
   ```

3. **Debug logging** helps identify protocol differences:
   ```java
   logger.debug("Using totalDistance for odometer: {}", odometerObj);
   logger.debug("Using odometer attribute: {}", odometerObj);
   ```

## Webhook Server Implementation

### Server Lifecycle

**Startup** (in `TraccarServerHandler.initialize()`):
```java
webhookServer = new TraccarWebhookServer(webhookPort, this);
webhookServer.start();
```

**Shutdown** (in `TraccarServerHandler.dispose()`):
```java
if (webhookServer != null) {
    webhookServer.stop();
}
```

### Request Handling

`TraccarWebhookServer.java` extends Jetty `AbstractHandler`:

```java
@Override
public void handle(String target, Request baseRequest, HttpServletRequest request,
        HttpServletResponse response) throws IOException {
    
    if ("/webhook".equals(target) && "POST".equals(request.getMethod())) {
        // Read JSON body
        StringBuilder jsonBuilder = new StringBuilder();
        try (BufferedReader reader = request.getReader()) {
            String line;
            while ((line = reader.readLine()) != null) {
                jsonBuilder.append(line);
            }
        }
        
        // Parse JSON
        Gson gson = new Gson();
        Type type = new TypeToken<Map<String, Object>>() {}.getType();
        Map<String, Object> eventData = gson.fromJson(jsonBuilder.toString(), type);
        
        // Log webhook type
        Object eventObj = eventData.get("event");
        if (eventObj instanceof Map<?, ?>) {
            // Geofence/device event webhook
            logger.info("Processing webhook event type: {}", event.get("type"));
        } else {
            // Position-only webhook
            logger.info("Processing webhook position update");
        }
        
        // Dispatch to handler
        serverHandler.handleWebhookEvent(eventData);
        
        // Send response
        response.setStatus(HttpServletResponse.SC_OK);
        baseRequest.setHandled(true);
    }
}
```

### Webhook vs Polling Coordination

Both mechanisms call the same `updatePositionChannels()` method:

- **Polling**: `TraccarServerHandler` queries API → `TraccarDeviceHandler.updatePositionChannels()`
- **Webhooks**: Server receives POST → `TraccarServerHandler.handleWebhookEvent()` → `TraccarDeviceHandler.handleWebhookEvent()` → `updatePositionChannels()`

This ensures consistent channel updates regardless of source.

**Order guard**: `updatePositionChannels()` skips a record whose `deviceTime` is
older than the newest one applied, and runs the check and all channel updates
under one lock (`recordLock`). Webhook records of one batch arrive on parallel
Jetty threads; without the lock an older record could pass the check first and
be applied last. A `deviceTime` more than 10 minutes ahead of `serverTime` is
applied but does not move the bar.

## Unit Conversion Best Practices

### DO: Send Base Units, Let OpenHAB Convert

✅ **Correct approach**:
```java
// Send in meters (base unit)
updateState(CHANNEL_ODOMETER, new QuantityType<>(33279520.98, SIUnits.METRE));

// Item definition with unit metadata
Number:Length Vehicle_Odometer "Odometer [%.1f km]" 
    {channel="traccar:device:myserver:car1:odometer", unit="km"}
```

Result: OpenHAB displays `33,279.5 km`, REST API returns `"state": "33279.52 km"`

### DON'T: Convert in Binding Code

❌ **Wrong approach**:
```java
// Don't do this - breaks flexibility
double odometerKm = odometerMeters / 1000.0;
updateState(CHANNEL_ODOMETER, new QuantityType<>(odometerKm, Units.KILOMETRE));
```

Problem: User can't change units without binding modification.

### Why This Matters

1. **Flexibility**: Users can choose any unit (km, mi, ft) via item metadata
2. **Consistency**: OpenHAB framework handles all conversions uniformly
3. **API Correctness**: REST API returns proper unitSymbol
4. **Persistence**: State stored in normalized base units

### Exception: Speed Conversion

Speed requires conversion because Traccar uses **knots** (not SI base unit m/s):

```java
// This is necessary because knots is not the openHAB base unit
double speedKnots = ((Number) speedObj).doubleValue();
double convertedSpeed = speedKnots * 1.852; // to km/h
updateState(CHANNEL_SPEED, new QuantityType<>(convertedSpeed, SIUnits.KILOMETRE_PER_HOUR));
```

User can still override with `unit="mph"` in item - OpenHAB will convert km/h → mph.

## Debugging and Troubleshooting

### Enable Debug Logging

```
openhab> log:set DEBUG org.openhab.binding.traccar
openhab> log:set TRACE org.openhab.binding.traccar  # For full detail
```

### Key Log Messages

**Webhook server**:
```
[INFO] Webhook server started on port 8090
[DEBUG] Received webhook (POST): {"position":...}
[DEBUG] Processing webhook position update
[DEBUG] Processing webhook event type: geofenceEnter
```

**Odometer protocol detection**:
```
[DEBUG] Using totalDistance for odometer: 3.3279520E7
[DEBUG] Using odometer attribute: 179924.0
[DEBUG] Odometer: 33279520.98 meters
```

**Engine hours conversion**:
```
[DEBUG] Engine hours: 861239290.0 ms = 239.23 hours
```

### Webhook Testing Script

Create `webhook_monitor.py` to capture raw webhook traffic:

```python
#!/usr/bin/env python3
from http.server import BaseHTTPRequestHandler, HTTPServer
import json
import sys

class WebhookMonitor(BaseHTTPRequestHandler):
    def do_POST(self):
        content_length = self.headers.get('Content-Length')
        if content_length:
            body = self.rfile.read(int(content_length)).decode('utf-8')
            try:
                data = json.loads(body)
                print(json.dumps(data, indent=2))
            except:
                print(body)
        
        self.send_response(200)
        self.end_headers()
        self.wfile.write(b'OK')

if __name__ == '__main__':
    port = int(sys.argv[1]) if len(sys.argv) > 1 else 8091
    server = HTTPServer(('0.0.0.0', port), WebhookMonitor)
    print(f"Listening on port {port}...")
    server.serve_forever()
```

Usage:
```bash
python3 webhook_monitor.py 8091
# Update Traccar to forward to port 8091 temporarily
```

### Common Issues

**Odometer showing meters instead of km**:
- Check item has `unit="km"` metadata
- Verify binding sends `SIUnits.METRE` not manual division by 1000

**Engine hours showing milliseconds**:
- Check conversion: `hoursMs / 3600000.0`
- Verify `Units.HOUR` is used, not milliseconds

**Channels always NULL**:
- Check protocol supports that attribute (see Protocol-Specific Attributes section)
- Verify attribute exists in Traccar API: `curl -u "user:pass" "https://traccar/api/positions?deviceId=1"`

**Webhooks not received**:
- Check firewall allows port 8090
- Verify Traccar `forward.url` points to correct OpenHAB IP
- Test with: `curl -X POST http://localhost:8090/webhook -d '{"test":true}'`

## Build and Deployment

### Build binding:
```bash
cd /etc/openhab-addons/bundles/org.openhab.binding.traccar
mvn clean install -DskipTests
```

### Deploy:
```bash
sudo cp target/org.openhab.binding.traccar-5.2.0-SNAPSHOT.jar /usr/share/openhab/addons/
```

OpenHAB hot-reloads automatically (check karaf logs).

### Code Style

**Import ordering**: Spotless enforces alphabetical imports:
```java
import javax.measure.Unit;
import org.openhab.core.library.unit.ImperialUnits;
import org.openhab.core.library.unit.SIUnits;
import org.openhab.core.library.unit.Units;
```

Fix with: `mvn spotless:apply`

## Files to Synchronize

Keep these directories in sync:
- `/etc/openhab-addons/bundles/org.openhab.binding.traccar/` (Maven build source)
- `/etc/openhab/Traccar-Binding/` (Development copy)

Sync command:
```bash
cp -r /etc/openhab-addons/bundles/org.openhab.binding.traccar/src/main/java/org/openhab/binding/traccar/internal/* \
      /etc/openhab/Traccar-Binding/src/main/java/org/openhab/binding/traccar/internal/

cp /etc/openhab-addons/bundles/org.openhab.binding.traccar/src/main/resources/OH-INF/thing/thing-types.xml \
   /etc/openhab/Traccar-Binding/src/main/resources/OH-INF/thing/thing-types.xml
```

## Adding New Channels

**Find out what the device actually sends before naming anything.** Set the
`raw-attributes` channel on the thing and read it: it prints every attribute
Traccar decoded, sorted. Teltonika's AVL IO numbers are per-peripheral, so the
same number means different things from different hardware - `io38` here is
OBD-II vehicle speed from a Bluetooth dongle, while AVL ID 38 from a CAN adapter
is Control State Flags. Naming a channel from a protocol table rather than from
an observation is how you get a channel that is confidently wrong.


### 1. Add constant to `TraccarBindingConstants.java`:
```java
public static final String CHANNEL_NEW = "newChannel";
```

### 2. Add channel definition to `thing-types.xml`:
```xml
<channel id="newChannel" typeId="new-channel"/>

<!-- Later in file -->
<channel-type id="new-channel">
    <item-type>Number:Length</item-type>
    <label>New Channel</label>
    <description>Description of new channel</description>
    <state readOnly="true" pattern="%.1f m"/>
</channel-type>
```

### 3. Add extraction logic to `TraccarDeviceHandler.java`:

```java
// In updatePositionChannels() method
Object newObj = attributes.get("newAttribute");
if (newObj instanceof Number) {
    double value = ((Number) newObj).doubleValue();
    updateState(CHANNEL_NEW, new QuantityType<>(value, SIUnits.METRE));
}
```

### 4. Add items to configuration:
```openhab
Number:Length Vehicle_New "New Channel [%.1f m]" 
    {channel="traccar:device:myserver:car1:newChannel"}
```

### 5. Update the documentation — every place, not the first one

This step is written down because it was skipped: `AGENTS.md` claimed 22
channels per device while the thing had 101, and sample log output still showed
INFO lines months after they moved to DEBUG. Stale documentation is worse than
none, because it is believed.

- [ ] **`README.md`** — add a row to the channel table in the matching section,
      or a new `###` section if the channel belongs to no existing group
- [ ] **`AGENTS.md` line 7** — the channel count. Get the real number with:
      `sed -n '/thing-type id="device"/,/<\/thing-type>/p' src/main/resources/OH-INF/thing/thing-types.xml | grep -c "<channel id="`
- [ ] **`CHANGELOG.md`** — an entry under `### Added` in the unreleased section
- [ ] **`EXAMPLES.md`** — only if the channel needs showing in use; add it to the
      table of contents too
- [ ] **`MARKETPLACE_POST.md`** — the feature list, if a user would choose the
      binding because of it
- [ ] **`docs/INDEX.md`** — only when a new documentation *file* appears

Then re-read the diff looking for anything house-specific. This is a public
repository: no addresses, no coordinates, no real IMEIs, no private hostnames.
Sample values belong in the documentation ranges — `traccar.example.com`,
`350000000000000`.

### 6. Rebuild and deploy

See the Build and Deployment section. `scripts/deploy-binding.sh` in the openHAB
tree builds in the reactor, verifies no class was lost against the deployed JAR,
backs the old one up and installs the new one.

## CAN Adapter Channels

**Built** (unreleased): `LvcanDecoder` decodes an FMx6 tracker's LVCAN fields
into the `can` channel group, which `TraccarDeviceHandler.updateCanChannels()`
adds to the thing only with `canAdapter=lvcan` (and removes without it). The
decoder is pure - attributes in, channel states out - so every rule it keeps is
covered by `LvcanDecoderTest`; run the tests in the build directory, because
`deploy-binding.sh` skips them. To add a signal: a row in `VALUES` or the flag
tables, the channel in the `can` channel-group-type, a test, and the README row
marked tested or untested.

The original design notes, still the reasoning behind it:

A CAN adapter (ALL-CAN300 and similar) reports far more than a GPS tracker, and
it needs a shape decided before the first channel is written.

**The values are easy.** State of charge, range, speed, odometer, pedal
position, VIN — one channel each, as above, with proper units.

**The flags are the decision.** They do not arrive one per attribute; they come
packed into bitfields, typically a *control state* and a *security state* word
plus a separate door bitmask. A single vehicle can carry forty or more of them —
doors, lights, belts, gear position, charging cable, handbrake, ABS, airbag.

Three ways to surface that, and it is worth choosing deliberately:

1. **One Switch channel per flag.** Most usable: rules and sitemaps read them
   directly, no transform. Costs a lot of channel definitions.
2. **The raw bitfield as a Number**, decoded per item with a JS transform.
   Cheap in the binding, pushes the work onto every user.
3. **Group the useful ones** — doors, lights, drivetrain — and leave the rest in
   the raw number.

Prefer (1) for anything a rule would plausibly trigger on, and leave the rest.
A channel nobody reads still has to be documented and kept working.

**And confirm every bit against an observation.** Open one door, watch which bit
moves. The published tables are per-device-family and do not survive being
assumed.

## Complete Working Examples

### Real-Time Vehicle Tracker HTML Dashboard

Located at: `src/main/resources/examples/vehicle_tracker.html`

**Production-ready web interface featuring:**
- **Click-to-follow mode**: Click any vehicle icon to track it (yellow glow indicator shows active)
- **Smooth map panning**: Map smoothly follows selected vehicle with 1-second animation
- **Custom vehicle icons**: 
  - Blue circle with "P" for phones (OSMand protocol)
  - Orange circle with "S" for motorcycle trackers (Teltonika)
  - Icons rotate to show vehicle heading direction
- **Real-time updates**: Fetches position every 10 seconds via OpenHAB REST API
- **Status popups**: Click markers for speed, battery, GPS satellites, status
- **Info panel**: Shows last update times and status for all vehicles
- **GPS accuracy priority**: Vehicles stay at actual GPS coordinates on roads

**Technical Implementation:**
- Leaflet.js 1.9.4 for mapping with OpenStreetMap tiles
- Fetches data from `/rest/items/gVehicle{ID}?recursive=true` endpoints
- Smooth animations using `map.panTo()` with easeLinearity 0.25
- Custom SVG icons for vehicle types with rotation transforms
- Follow state management (toggle on/off by clicking icons)

**How to use:**
1. Copy to `/etc/openhab/html/vehicle_tracker.html`
2. Update item group names in JavaScript (`gVehicle1`, `gVehicle10` to match your devices)
3. Access at `http://your-openhab:8080/static/vehicle_tracker.html`
4. Embed in sitemap: `Webview url="/static/vehicle_tracker.html" height=15`

**Item requirements per vehicle:**
```openhab
Group gVehicle1 "Vehicle Name"
Location Vehicle1_Position {channel="traccar:device:server:device1:position"}
Number:Speed Vehicle1_Speed {channel="traccar:device:server:device1:speed"}
Number:Angle Vehicle1_Course {channel="traccar:device:server:device1:course"}
String Vehicle1_Status {channel="traccar:device:server:device1:status"}
Number:Dimensionless Vehicle1_BatteryLevel {channel="traccar:device:server:device1:batteryLevel"}
DateTime Vehicle1_LastUpdate {channel="traccar:device:server:device1:lastUpdate"}
```

**Design decisions:**
- No map rotation (kept north-up for stability and readability)
- Smooth panning over forced centering to preserve GPS accuracy
- Vehicles stay on roads even with slight GPS drift
- 10-second refresh balances responsiveness with API load
- Yellow glow provides clear visual feedback for followed vehicle

This example demonstrates the full capabilities of the Traccar binding for building custom fleet management interfaces.

## Future Enhancement Ideas

1. **Historic position tracking**: Store position history in persistence, display tracks on map
2. **Geofence creation via UI**: Allow defining geofences from openHAB instead of Traccar
3. **Multi-vehicle routing**: Display routes for multiple vehicles simultaneously (see vehicle_tracker.html for foundation)
4. **Fuel consumption**: Calculate based on odometer + engine hours for compatible devices
5. **Driver behavior scoring**: Aggregate harsh acceleration/braking/cornering events
6. **Bluetooth beacon support**: For Teltonika devices with BLE sensors
7. **CAN bus data**: done for LVCAN on FMx6 (see CAN Adapter Channels); the FMB family's LVCAN ids still to do
8. **Temperature sensors**: Many industrial trackers support external temperature probes

## Resources

- **Traccar API Documentation**: https://www.traccar.org/api-reference/
- **Traccar Protocol List**: https://www.traccar.org/protocols/
- **OpenHAB Units**: https://www.openhab.org/docs/concepts/units-of-measurement.html
- **Jetty Documentation**: https://eclipse.dev/jetty/documentation/

## Contact

- **Author**: Nanna Agesen
- **Email**: Nanna@agesen.dk
- **GitHub**: [@Prinsessen](https://github.com/Prinsessen)
