# Traccar Binding

The Traccar binding integrates [Traccar](https://www.traccar.org/) GPS tracking server with openHAB, enabling real-time vehicle/device tracking, geofencing automation, and comprehensive location monitoring.

Traccar is an open-source GPS tracking system that supports over 200 GPS protocols and 2000+ GPS tracking devices.

## Documentation

- **[BLE Beacon Quick Start](docs/beacon-quickstart.md)** - 5-minute setup guide for beacon tracking
- **[Complete Beacon Example](docs/beacon-tracking-example.md)** - Full working configuration with rules and automation
- **[OBD-II Integration Guide](OBD-II_IMPLEMENTATION.md)** - Complete OBD-II setup including dongle auto-reconnection fix

## Author

- **Name**: Nanna Agesen
- **Email**: Nanna@agesen.dk  
- **GitHub**: [@Prinsessen](https://github.com/Prinsessen)

## Supported Things

This binding supports the following thing types:

- **`server`** (Bridge) - Connection to a Traccar server instance (cloud or self-hosted)
- **`device`** - Individual GPS tracked device/vehicle managed by the Traccar server

## Features

### Real-Time Position Tracking
- GPS coordinates (latitude, longitude)
- Altitude/elevation above sea level
- Speed with configurable units (km/h, mph, knots)
- Direction/course (compass bearing)
- Location accuracy
- GPS fix validity indicator
- Street address with optional Nominatim reverse geocoding
- Last update timestamp
- Device protocol identification

### Advanced Reverse Geocoding (Nominatim)
- **Formatted addresses**: "Street number, Postcode City, Province, Country"
- **Worldwide transliteration**: Converts Greek, Cyrillic, Arabic, Chinese, Japanese, Thai, Korean, Hebrew to Latin alphabet
- **Language support**: English, Danish, German, French, Spanish
- **Intelligent caching**: Reduces API calls by reusing addresses within configurable radius (10-1000m)
- **Rate limiting**: Respects OpenStreetMap's 1 request/second usage policy
- **Automatic fallback**: Uses Traccar's address if Nominatim unavailable
- **Bridge-level configuration**: Single setting applies to all devices

### Distance Tracking
- **Three distance channels**: `odometer`, `totalDistance`, and `distance` for different tracking needs
- **Protocol-aware**: Automatic handling of protocol-specific fields
  - **Teltonika**: Use `totalDistance` (actual vehicle odometer from device)
  - **OSMand**: Use `odometer` (phone app distance tracking)
- **Incremental tracking**: `distance` channel shows trip distance since last update
- Automatic meter-to-kilometer conversion with unit specifications

### Device Information
- Battery level monitoring
- Motion detection
- Device activity recognition (walking, in_vehicle, still)
- Online/offline status
- Engine hours tracking (for vehicle trackers)
- Device-specific event codes
- GPS satellite count
- GSM/cellular signal strength

### Geofencing Automation
- Real-time geofence entry/exit events via webhooks
- Geofence ID and name tracking
- Instant notifications (no polling delay)
- Perfect for automating garage doors, lights, security systems

### Dual Update Mechanism
- **Polling**: Configurable interval (minimum 10 seconds) for position updates
- **Webhooks**: Real-time position updates + instant event notifications
- Efficient hybrid approach minimizes API calls while maintaining responsiveness
- Webhook support for position forwarding (every 1-2 seconds during movement)

### GPS Noise Filtering
- **Configurable speed threshold**: Filter small movements and GPS signal drift
- **Default 2 km/h**: Eliminates false motion from stationary devices
- **Adjustable 0-10 km/h**: Customize for walking (2 km/h), cycling (5 km/h), or vehicles (10 km/h)
- **Zero display**: Speeds below threshold shown as 0 for clean UI
- **Bridge-level setting**: Applies to all devices consistently

## Discovery

The binding automatically discovers devices configured in your Traccar server:

1. Add and configure the Traccar Server bridge
2. Devices will appear in the Inbox automatically
3. Accept discovered devices or manually configure them with device ID

## Thing Configuration

### Server Bridge

| Parameter | Type | Required | Default | Description |
|-----------|------|----------|---------|-------------|
| `url` | text | Yes | - | Traccar server URL (e.g., `https://demo.traccar.org`) |
| `username` | text | Yes | - | Traccar account username/email |
| `password` | text | Yes | - | Traccar account password |
| `refreshInterval` | integer | No | 60 | Position polling interval in seconds (minimum: 10) |
| `webhookPort` | integer | No | 8090 | Port for receiving webhooks (1024-65535) |
| `speedUnit` | text | No | kmh | Speed unit: `kmh`, `mph`, or `knots` |
| `speedThreshold` | decimal | No | 2.0 | Minimum speed (km/h) to display. Filters GPS noise and small movements (0-10) |
| `beaconTxPower` | integer | No | -59 | Beacon transmit power at 1m in dBm (for RSSI→distance calculation) |
| `beaconPathLoss` | decimal | No | 2.0 | Path loss exponent (2.0=free space, 2.7-4.3=indoor with obstacles) |
| `useNominatim` | boolean | No | false | Enable Nominatim reverse geocoding for all devices |
| `nominatimUrl` | text | No | https://nominatim.openstreetmap.org | Nominatim server URL |
| `nominatimLanguage` | text | No | en | Address language (en, da, de, fr, es) |
| `geocodingCacheDistance` | integer | No | 50 | Cache radius in meters (10-1000) |

**Advanced - Reverse Geocoding**: The binding can use [Nominatim](https://nominatim.org/) (OpenStreetMap) for reverse geocoding instead of Traccar's built-in address lookup. This provides formatted addresses in English (or other languages) with proper structure: "Street number, Postcode City, Province, Country". Nominatim transliterates special characters (Greek, Cyrillic, Arabic, Chinese, etc.) to Latin alphabet. Caching minimizes API calls - addresses are reused when moving within the configured radius. Respects OSM's 1 request/second usage policy.

### Device

| Parameter | Type | Required | Default | Description |
|-----------|------|----------|---------|-------------|
| `deviceId` | integer | Yes | - | Traccar device ID |
| `beacon1Mac` | text | No | - | MAC address to assign to beacon1 slot (e.g., aabbcc112233) |
| `beacon2Mac` | text | No | - | MAC address to assign to beacon2 slot |
| `beacon3Mac` | text | No | - | MAC address to assign to beacon3 slot |
| `beacon4Mac` | text | No | - | MAC address to assign to beacon4 slot |
| `deviceFamily` | text | No | `fmb` | `fmb` (FMB / FMM / FMC9xx, or not a Teltonika), `fmx6` (FMC650 and relatives), `phone` |
| `canAdapter` | text | No | `none` | `lvcan` for a Teltonika CAN adapter (ALL-CAN300, LV-CAN200) - adds the [CAN channels](#can-adapter-teltonika-all-can300--lv-can200) |
| `obdDongle` | text | No | `bluetooth` | `none` stops io30-io48 being read as OBD-II data (advanced) |
| `gearParkWhenOff` | boolean | No | `false` | CAN only: no gear bit while the car is not ready reads as P (advanced) |

**Note**: Beacon parameters only apply to devices with BLE capability (e.g., Teltonika FMM920).

**Why a thing says what it is.** Teltonika's AVL ids mean different things on
different hardware: io38 is vehicle speed from a Bluetooth OBD-II dongle and the
Control State Flags from a CAN adapter; AVL 113 is the internal battery level on
the FMB family and Service Distance on the FMx6 family. The binding does not
guess. The defaults decode exactly as every earlier release did, so existing
things need no change. With `canAdapter=lvcan` the OBD-II reading is off
whatever `obdDongle` says, and with `deviceFamily=fmx6` the `batteryLevel`
channel is not filled.

## Channels

### Position & Navigation

| Channel | Type | Description | Example State |
|---------|------|-------------|---------------|
| `position` | Location | GPS coordinates (lat, lon, altitude) | 55.6761,12.5683 |
| `altitude` | Number:Length | Altitude/elevation above sea level | 45.2 m |
| `speed` | Number:Speed | Current speed (converted to configured unit) | 65.5 km/h |
| `course` | Number:Angle | Direction/heading (0-359°) | 135° |
| `accuracy` | Number:Length | GPS accuracy in meters | 3.5 m |
| `valid` | Switch | GPS fix validity (ON=valid, OFF=no fix) | ON |
| `address` | String | Street address from reverse geocoding | "123 Main St" |

### Distance Tracking

| Channel | Type | Description | Example State |
|---------|------|-------------|---------------|
| `odometer` | Number:Length | Device odometer reading (OSMand protocol only) | 347.8 km |
| `totalDistance` | Number:Length | Cumulative distance tracked by Traccar server (all protocols) | 33,279.5 km |
| `distance` | Number:Length | Trip distance since last position update | 15.3 m |

**Protocol-Specific Usage:**
- **Teltonika devices**: Use `totalDistance` - contains actual vehicle odometer value
- **OSMand (phone tracking)**: Use `odometer` - contains device-reported distance
- **All protocols**: Use `distance` for incremental trip tracking

### Device Status

| Channel | Type | Description | Example State |
|---------|------|-------------|---------------|
| `status` | String | Device status | online/offline/unknown |
| `lastUpdate` | DateTime | Timestamp of last update | 2026-01-18T09:23:10 |
| `batteryLevel` | Number:Dimensionless | Battery level (0-100%) | 85% |
| `motion` | Switch | Movement detection (ON=moving) | ON |
| `activity` | String | Activity recognition (OSMand only) | walking/in_vehicle/still |
| `protocol` | String | Device protocol/connection type | teltonika/osmand |

### Vehicle Information

| Channel | Type | Description | Example State |
|---------|------|-------------|---------------|
| `ignition` | Switch | Ignition status (ON=ignition on, OFF=off) | ON |
| `hours` | Number:Time | Total engine running hours | 239.2 h |
| `event` | Number | Device-specific event code | 10831 |

### Connectivity

| Channel | Type | Description | Example State |
|---------|------|-------------|---------------|
| `gpsSatellites` | Number | Number of GPS satellites | 8 |
| `gsmSignal` | Number:Dimensionless | GSM/cellular signal strength | 75% |

### Geofencing

| Channel | Type | Description | Example State |
|---------|------|-------------|---------------|
| `geofenceEvent` | String | Event type | geofenceEnter/geofenceExit |
| `geofenceId` | Number | Numeric ID of triggered geofence | 1 |
| `geofenceName` | String | Name of triggered geofence | "Home" |

### Diagnostics

| Channel | Type | Description | Example State |
|---------|------|-------------|---------------|
| `raw-attributes` | String | Every attribute Traccar decoded, sorted, as `name=value` | `batteryLevel=71.0, distance=1.19, hours=3600000, motion=true` |

Advanced and read-only. The named channels above cover roughly thirty
attributes; a device that sends anything else is sending it into a map this
binding never reads. This channel shows the lot.

It earns its place the first time you fit an unfamiliar peripheral, because
Teltonika's AVL IO numbers are **per-peripheral**. `io38` from a Bluetooth OBD
dongle is vehicle speed; AVL ID 38 from a CAN adapter is Control State Flags.
The same number, two unrelated meanings, and no published table will tell you
which one your hardware is using. So: fit it, read this channel, see what
actually arrives, and only then decide what deserves a channel of its own.

Truncated past 4000 characters, so one chatty device cannot fill an item.

### CAN Adapter (Teltonika ALL-CAN300 / LV-CAN200)

With `canAdapter=lvcan` the device gets a channel group `can` with the values
and state flags a Teltonika CAN adapter reports through an FMx6-family tracker
(FMC650 and relatives). Without it the group is not there, so a motorcycle never
shows a traction battery.

**Tested on one car so far**: a Toyota bZ4X with an FMC650 and an ALL-CAN300
(read-only, contactless clamps). The AVL ids and bit numbers are Teltonika's and
the same for every car - the adapter's program number tells it which car it is
reading - but **which signals a car actually sends differs per car**. Channels
marked *tested* below were confirmed on that car against the manufacturer's app
or by switching the thing on and off; the rest are decoded as Teltonika
documents them and are untested. Nothing is invented: a signal the car does not
send leaves its channel as it was.

#### Values

| Channel | Type | AVL id | Notes | |
|---------|------|--------|-------|---|
| `can#batteryLevel` | Number:Dimensionless | 142 | Battery Level Percent - the traction battery on an EV | tested |
| `can#range` | Number:Length | 526 | Vehicles Range On Battery (sent in metres) | tested |
| `can#odometer` | Number:Length | 36 | Total Mileage (sent in metres) | tested |
| `can#speed` | Number:Speed | 30 | Vehicle Speed | tested |
| `can#pedal` | Number:Dimensionless | 31 | Accelerator Pedal Position | tested |
| `can#batteryLevelSeen`, `can#rangeSeen`, `can#odometerSeen`, `can#speedSeen`, `can#pedalSeen` | DateTime | - | Device time of the last record that carried the value, changed or not | tested |
| `can#lastData` | DateTime | - | Device time of the last record with any CAN field | tested |

**Freshness is per value.** Range, odometer, speed and pedal are only sent while
the car is ready to drive; the battery level and the flags also come while it
charges. On the tested car the battery level was not sent at all for the last
thirty minutes of a charge (from 98 % until it reported 100 %) while the flags
kept coming. So `lastData` says the link is alive, and only a value's own
`...Seen` channel says how old that value is.

#### Doors (AVL 143)

`can#doorFrontLeft`, `can#doorFrontRight`, `can#doorRearLeft`,
`can#doorRearRight`, `can#hood`, `can#trunk` - Contact. Tested: front left, all
closed.

#### State flags

Read from the P4 tables when the tracker sends them (12710 security, 12711
control, 12712 indicator), from the legacy tables otherwise (47 security, 38
control). The P4 tables are **separate I/O elements, off by default** in the
Teltonika Configurator; enable them. All Switch unless noted.

| Channels | Notes | |
|----------|-------|---|
| `can#ignition`, `can#keyInserted`, `can#ready`, `can#handbrake`, `can#footbrake` | | tested |
| `can#workMode` (String) | `private` / `company` | tested |
| `can#chargeCable` | | tested |
| `can#charging` | The car's own charging bit. On the tested car it went OFF at 98 % while the charger still delivered 8 kW, and came back with 100 %: it marks the main phase, not "current is flowing" | tested |
| `can#electricMotor`, `can#closedByRemote`, `can#locked` | | tested |
| `can#gearPark`, `can#gearReverse`, `can#gearNeutral`, `can#gearDrive`, `can#gear` (String P/R/N/D/-) | A switched-off car sends no gear bit; see `gearParkWhenOff` | tested |
| `can#adapterSleep` | P4 only | tested |
| `can#sidelights`, `can#dippedBeam`, `can#fullBeam`, `can#rearFog` | | tested |
| `can#frontFog` | | untested |
| `can#airConditioning`, `can#beltDriver` | | tested |
| `can#beltPassenger`, `can#passengerPresent` (P4 only) | | untested |
| `can#lampAirbag` | Lit for a second at start-up (lamp test) | tested |
| `can#lampAbs`, `can#lampEsp`, `can#espOff` (P4 only), `can#lampBrake`, `can#lampSteering`, `can#lampTyre` | | untested |
| `can#hazardSwitch`, `can#remoteClose`, `can#remoteOpen`, `can#remoteClose3x` (P4 only) | | untested |

A flag that is OFF means "no" **or** "not known": the tables carry no validity
mask, and a car that does not report a signal leaves its bit at 0.

#### Workbench (advanced)

| Channel | Type | Description |
|---------|------|-------------|
| `can#can1Link`, `can#can2Link` | String | The adapter's view of each CAN bus: `connected, data`, `connected, no data`, `not connected, needed`, `not connected, not needed`. **The P4 and legacy formats order these four values differently** - the binding reads each in its own order |
| `can#securityFlags`, `can#controlFlags`, `can#indicatorFlags` | String | The flag word in hex with its set bits, e.g. `0x0000000CA339  bits 0,3,4,5,8,9,13,15,18,19` - for finding a new bit by switching something on and off |
| `can#unmappedIo` | String | Every io element in the record that neither the CAN decoding nor the tracker accounts for - where a new field shows up first |

#### How the decoding behaves

- **A field that is missing is not zero.** With the tracker's ignition off it
  sends records with no CAN field at all; every CAN channel keeps its state.
- **Records older than the newest applied are ignored** for the CAN channels.
  After a cold boot the tracker uploads its buffer, and records can arrive out
  of order.
- Flag words are up to 64 bits and read as 64-bit integers.

#### Example

```openhab
Thing traccar:device:myserver:car "Car" (traccar:server:myserver) [
    deviceId=2, deviceFamily="fmx6", canAdapter="lvcan", gearParkWhenOff=true ]
```

```openhab
Number:Dimensionless Car_Battery      "Battery [%.0f %%]"   { channel="traccar:device:myserver:car:can#batteryLevel" }
DateTime             Car_Battery_Seen "Battery read [%1$tH:%1$tM]" { channel="traccar:device:myserver:car:can#batteryLevelSeen" }
Number:Length        Car_Range        "Range [%.0f km]"     { channel="traccar:device:myserver:car:can#range", unit="km" }
Switch               Car_Cable        "Cable in [%s]"       { channel="traccar:device:myserver:car:can#chargeCable" }
Switch               Car_Locked       "Locked [%s]"         { channel="traccar:device:myserver:car:can#locked" }
String               Car_Gear         "Gear [%s]"           { channel="traccar:device:myserver:car:can#gear" }
```

Range and odometer arrive in metres; give the item `unit="km"`.

#### Setting up the tracker - what was learned the hard way

- **COM1 baud rate: "Default", not 115200.** With 115200 the adapter read the
  car and the tracker never heard it.
- **Enable the P4 flag elements** (12710/12711/12712) in the Configurator's I/O
  settings - they are off by default.
- **With the tracker's ignition off, it sends no CAN values.** FMx6 ignition
  sources are the digital inputs, power voltage and movement - nothing from CAN.
  On an EV whose 12 V system drops to its resting voltage while charging, the
  tracker may call the ignition off in the middle of a charge.
- **Never enable LVCAN "Send data with 0, if ignition is off"**: it sends zeros,
  which read as a battery at 0 %.
- **Movement Source = CAN Speed** if the ignition is kept on through a charge;
  with Movement Source = Ignition a parked, charging car records as moving, a
  record every second.
- **Switch off the I/O elements that never change** - every enabled element is
  sent in every record. On an FMx6 that includes the accelerometer axes (see the
  next point), IMSI and ICCID (which also identify the SIM card), unused digital
  outputs and the network type. Read the configuration from the device before
  editing it in the Configurator: saving an older file to the device puts back
  whatever it held.
- **Traccar shows `alarm=general` on an FMx6 that has no alarm.** Traccar's
  Teltonika decoder reads AVL 236 as an alarm for every model; on the FMx6
  family 236 is Axis X of the accelerometer. Set Axis X (and Y, Z if unused) to
  None in the Configurator.
- **Traccar's `totalDistance` counts the jump to 0,0** that a tracker reports
  after a cold boot without a GPS fix. Prefer `can#odometer` for a car's
  mileage, and consider `filter.zero=true` in Traccar's configuration.
- On the FMx6 family, AVL 200 (sleep mode) reads 0 none, 1 deep sleep, 2 GPS
  sleep, 3 online sleep - **not** the order of the sleep mode parameter in the
  Configurator.

### BLE Beacon Tracking (Teltonika FMM920)

**Supported devices**: Teltonika FMM920 and other compatible BLE-capable GPS trackers

The binding supports tracking up to **4 Bluetooth Low Energy (BLE) beacons** simultaneously. This is perfect for tracking bags, cargo, or assets attached to your vehicle/motorcycle. Each beacon provides real-time proximity data including signal strength (RSSI), calculated distance, battery level, temperature, and humidity.

**4 beacon groups available**: `beacon1`, `beacon2`, `beacon3`, `beacon4`

#### Channels (per beacon)

| Channel | Type | Description | Example State |
|---------|------|-------------|---------------|
| `beacon1-mac` | String | Beacon MAC address | aabbcc112233 |
| `beacon1-name` | String | Beacon name (if configured in beacon) | MainBag |
| `beacon1-rssi` | Number | Signal strength in dBm (-30 = very close, -90 = far) | -55 |
| `beacon1-distance` | Number:Length | Calculated distance from vehicle (meters) | 1.2 m |
| `beacon1-battery` | Number:ElectricPotential | Beacon battery voltage | 2.87 V |
| `beacon1-lowBattery` | Switch | Low battery warning (ON = low battery) | OFF |
| `beacon1-temperature` | Number:Temperature | Ambient temperature from beacon sensor | 21.5 °C |
| `beacon1-humidity` | Number:Dimensionless | Relative humidity from beacon sensor | 45% |

**Note**: Temperature and humidity channels are only available if your beacons have these sensors (e.g., Teltonika EYE Beacon or similar environmental sensors).

**Beacon Name Persistence**: Beacon names are automatically stored when first detected and persist across openHAB restarts. This means the `beacon-name` channel will continue showing the last known name even when the beacon is out of range or the name isn't included in every webhook update. Names are automatically updated whenever the tracker reports a new name value.

#### Distance Calculation

Distance is automatically calculated from RSSI using the **log-distance path loss model**:

```
distance = 10 ^ ((txPower - RSSI) / (10 × pathLossExponent))
```

**Configurable parameters** (Server Bridge configuration):

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `beaconTxPower` | integer | -59 | Beacon transmit power at 1 meter (dBm). Check your beacon specs. |
| `beaconPathLoss` | decimal | 2.0 | Path loss exponent (2.0 = free space, 2.7-4.3 = indoor with obstacles) |

**Note**: These are server-wide settings that apply to all devices. Configure them on the Traccar Server Bridge.

Example with custom distance calculation:
```openhab
Bridge traccar:server:gpsserver "GPS Server" [
    url="https://gps.example.com",
    username="user@example.com",
    password="password",
    beaconTxPower=-59,
    beaconPathLoss=2.5
]
```

#### MAC Address Routing (Critical Feature!)

**Problem**: Teltonika FMM920 devices assign beacons to `tag1`, `tag2`, `tag3`, `tag4` **dynamically based on scan order**, not by MAC address. This means beacon data can shuffle between channels on every scan, causing chaos in your automation and UI.

**Solution**: The binding automatically routes beacons to consistent slots based on their MAC address.

**Configuration Parameters** (Thing configuration, optional but recommended):

| Parameter | Type | Description |
|-----------|------|-------------|
| `beacon1Mac` | text | MAC address to permanently assign to beacon1 slot |
| `beacon2Mac` | text | MAC address to permanently assign to beacon2 slot |
| `beacon3Mac` | text | MAC address to permanently assign to beacon3 slot |
| `beacon4Mac` | text | MAC address to permanently assign to beacon4 slot |

**How It Works**:

1. **With configuration** (recommended): Each configured MAC address always routes to its assigned beacon slot (beacon1-4)
2. **Without configuration** (automatic): First-discovered MACs are automatically assigned to available slots and remain consistent

**Configuration Example**:

```openhab
// First, discover your beacon MAC addresses
// Check logs: grep "Found tag.*with MAC" /var/log/openhab/openhab.log

Thing traccar:device:gpsserver:350000000000000 "Motorcycle" (traccar:server:gpsserver) [
    deviceId=10,
    beacon1Mac="aabbcc112233",  // MainBag
    beacon2Mac="aabbcc445566",  // SideBags
    beacon3Mac="aabbcc778899"   // RearBag
]
```

**Important Notes**:
- MAC addresses must be lowercase hex without colons (e.g., `aabbcc112233`, not `AA:BB:CC:11:22:33`)
- After adding/changing MAC configuration, restart openHAB or reload the thing: `openhab-cli reload-thing traccar:device:gpsserver:DEVICEID`
- Unconfigured beacons will automatically take first available slot (works great for most use cases)
- Name channel clears automatically when beacon has no configured name (fixes stale data)

#### Complete Beacon Example

##### traccar.things

```openhab
Bridge traccar:server:gpsserver "Traccar GPS Server" [ 
    url="https://gps.example.com",
    username="user@example.com",
    password="yourpassword",
    refreshInterval=10,
    webhookPort=8090,
    beaconTxPower=-59,
    beaconPathLoss=2.5
] {
    Thing device motorcycle "Motorcycle" [
        deviceId=10,
        beacon1Mac="aabbcc112233",
        beacon2Mac="aabbcc445566", 
        beacon3Mac="aabbcc778899"
    ]
}
```

##### traccar.items

```openhab
Group gMotorcycle "Motorcycle" <motorbike>
Group gBeacons "Cargo Beacons" (gMotorcycle) <bag>

// Beacon 1 - MainBag Bag
String Beacon1_Mac "Beacon 1 MAC [%s]" (gBeacons)
    {channel="traccar:device:gpsserver:motorcycle:beacon1-mac"}
String Beacon1_Name "Beacon 1 Name [%s]" <bag> (gBeacons)
    {channel="traccar:device:gpsserver:motorcycle:beacon1-name"}
Number Beacon1_RSSI "Beacon 1 Signal [%d dBm]" (gBeacons)
    {channel="traccar:device:gpsserver:motorcycle:beacon1-rssi"}
Number:Length Beacon1_Distance "Beacon 1 Distance [%.2f m]" <distance> (gBeacons)
    {channel="traccar:device:gpsserver:motorcycle:beacon1-distance"}
Number:ElectricPotential Beacon1_Battery "Beacon 1 Battery [%.2f V]" <battery> (gBeacons)
    {channel="traccar:device:gpsserver:motorcycle:beacon1-battery"}
Switch Beacon1_LowBattery "Beacon 1 Low Battery" <lowbattery> (gBeacons)
    {channel="traccar:device:gpsserver:motorcycle:beacon1-lowBattery"}
Number:Temperature Beacon1_Temperature "Beacon 1 Temperature [%.1f °C]" <temperature> (gBeacons)
    {channel="traccar:device:gpsserver:motorcycle:beacon1-temperature"}
Number:Dimensionless Beacon1_Humidity "Beacon 1 Humidity [%.0f %%]" <humidity> (gBeacons)
    {channel="traccar:device:gpsserver:motorcycle:beacon1-humidity"}

// Beacon 2 - Side bags
String Beacon2_Mac "Beacon 2 MAC [%s]" (gBeacons)
    {channel="traccar:device:gpsserver:motorcycle:beacon2-mac"}
String Beacon2_Name "Beacon 2 Name [%s]" <bag> (gBeacons)
    {channel="traccar:device:gpsserver:motorcycle:beacon2-name"}
Number Beacon2_RSSI "Beacon 2 Signal [%d dBm]" (gBeacons)
    {channel="traccar:device:gpsserver:motorcycle:beacon2-rssi"}
Number:Length Beacon2_Distance "Beacon 2 Distance [%.2f m]" <distance> (gBeacons)
    {channel="traccar:device:gpsserver:motorcycle:beacon2-distance"}
Number:ElectricPotential Beacon2_Battery "Beacon 2 Battery [%.2f V]" <battery> (gBeacons)
    {channel="traccar:device:gpsserver:motorcycle:beacon2-battery"}
Switch Beacon2_LowBattery "Beacon 2 Low Battery" <lowbattery> (gBeacons)
    {channel="traccar:device:gpsserver:motorcycle:beacon2-lowBattery"}

// Beacon 3 - RearBag Bag
String Beacon3_Mac "Beacon 3 MAC [%s]" (gBeacons)
    {channel="traccar:device:gpsserver:motorcycle:beacon3-mac"}
String Beacon3_Name "Beacon 3 Name [%s]" <bag> (gBeacons)
    {channel="traccar:device:gpsserver:motorcycle:beacon3-name"}
Number Beacon3_RSSI "Beacon 3 Signal [%d dBm]" (gBeacons)
    {channel="traccar:device:gpsserver:motorcycle:beacon3-rssi"}
Number:Length Beacon3_Distance "Beacon 3 Distance [%.2f m]" <distance> (gBeacons)
    {channel="traccar:device:gpsserver:motorcycle:beacon3-distance"}

// Beacon 4 - Available
String Beacon4_Name "Beacon 4 Name [%s]" <bag> (gBeacons)
    {channel="traccar:device:gpsserver:motorcycle:beacon4-name"}
Number:Length Beacon4_Distance "Beacon 4 Distance [%.2f m]" <distance> (gBeacons)
    {channel="traccar:device:gpsserver:motorcycle:beacon4-distance"}
```

##### Beacon Automation Rules

```openhab
rule "Beacon Low Battery Alert"
when
    Item Beacon1_LowBattery changed to ON or
    Item Beacon2_LowBattery changed to ON
then
    val beaconName = if (Beacon1_LowBattery.state == ON) {
        Beacon1_Name.state.toString
    } else {
        Beacon2_Name.state.toString
    }
    
    val battery = if (Beacon1_LowBattery.state == ON) {
        Beacon1_Battery.state.toString
    } else {
        Beacon2_Battery.state.toString
    }
    
    logWarn("Beacon", "Low battery alert: {} ({})", beaconName, battery)
    sendNotification("admin@example.com", 
        "⚠️ Beacon Low Battery: " + beaconName + " - " + battery)
end

rule "Cargo Detached Warning"
when
    Item Beacon1_Distance changed or
    Item Beacon2_Distance changed or
    Item Beacon3_Distance changed
then
    val speed = (Motorcycle_Speed.state as QuantityType<Speed>).toUnit("km/h").doubleValue()
    
    // Only check when vehicle is moving
    if (speed > 5.0) {
        val distance1 = (Beacon1_Distance.state as QuantityType<Length>).toUnit("m").doubleValue()
        val distance2 = (Beacon2_Distance.state as QuantityType<Length>).toUnit("m").doubleValue()
        val distance3 = (Beacon3_Distance.state as QuantityType<Length>).toUnit("m").doubleValue()
        
        // Alert if any beacon is more than 10 meters away while moving
        if (distance1 > 10.0) {
            logWarn("Cargo", "⚠️ {} may be detached! Distance: {} m", 
                Beacon1_Name.state, distance1)
            sendNotification("admin@example.com",
                "⚠️ CARGO WARNING: " + Beacon1_Name.state + " is " + distance1 + "m away!")
        }
        
        if (distance2 > 10.0) {
            logWarn("Cargo", "⚠️ {} may be detached! Distance: {} m", 
                Beacon2_Name.state, distance2)
        }
        
        if (distance3 > 10.0) {
            logWarn("Cargo", "⚠️ {} may be detached! Distance: {} m", 
                Beacon3_Name.state, distance3)
        }
    }
end

rule "Beacon Temperature Alert"
when
    Item Beacon1_Temperature changed
then
    val temp = (Beacon1_Temperature.state as QuantityType<Temperature>).toUnit("°C").doubleValue()
    
    if (temp > 45.0) {
        logWarn("Beacon", "High temperature in {}: {} °C", 
            Beacon1_Name.state, temp)
        sendNotification("admin@example.com",
            "🔥 High temperature alert: " + Beacon1_Name.state + " - " + temp + "°C")
    }
    
    if (temp < -10.0) {
        logWarn("Beacon", "Low temperature in {}: {} °C", 
            Beacon1_Name.state, temp)
    }
end

rule "All Cargo Present Check"
when
    Item Motorcycle_Ignition changed to ON
then
    Thread::sleep(2000)  // Wait for beacon scan
    
    val beacons = newArrayList(
        Beacon1_Name.state.toString,
        Beacon2_Name.state.toString,
        Beacon3_Name.state.toString
    )
    
    val present = beacons.filter[it != "UNDEF" && it != "NULL" && it != "-"].size
    
    if (present < 3) {
        logWarn("Cargo", "⚠️ Only {} of 3 expected beacons detected!", present)
        sendNotification("admin@example.com",
            "⚠️ CARGO CHECK: Only " + present + "/3 bags detected at startup!")
    } else {
        logInfo("Cargo", "✓ All 3 cargo beacons present")
    }
end
```

##### Beacon Sitemap

```openhab
sitemap motorcycle label="Motorcycle Tracking" {
    Frame label="Cargo Beacons" {
        Text label="Beacon 1 - MainBag Bag" icon="bag" {
            Text item=Beacon1_Mac label="MAC [%s]" icon="bluetooth"
            Text item=Beacon1_RSSI label="Signal [%d dBm]" icon="signal" 
                valuecolor=[Beacon1_RSSI>-50="green", Beacon1_RSSI>-70="orange", ="red"]
            Text item=Beacon1_Distance label="Distance [%.2f m]" icon="distance"
                valuecolor=[Beacon1_Distance<2="green", Beacon1_Distance<5="orange", ="red"]
            Text item=Beacon1_Battery label="Battery [%.2f V]" icon="battery"
                valuecolor=[Beacon1_Battery>2.8="green", Beacon1_Battery>2.5="orange", ="red"]
            Switch item=Beacon1_LowBattery label="Low Battery" icon="lowbattery"
            Text item=Beacon1_Temperature label="Temperature [%.1f °C]" icon="temperature"
            Text item=Beacon1_Humidity label="Humidity [%.0f %%]" icon="humidity"
        }
        
        Text label="Beacon 2 - Side bags" icon="bag" {
            Text item=Beacon2_Mac label="MAC [%s]" icon="bluetooth"
            Text item=Beacon2_RSSI label="Signal [%d dBm]" icon="signal"
                valuecolor=[Beacon2_RSSI>-50="green", Beacon2_RSSI>-70="orange", ="red"]
            Text item=Beacon2_Distance label="Distance [%.2f m]" icon="distance"
                valuecolor=[Beacon2_Distance<2="green", Beacon2_Distance<5="orange", ="red"]
            Text item=Beacon2_Battery label="Battery [%.2f V]" icon="battery"
                valuecolor=[Beacon2_Battery>2.8="green", Beacon2_Battery>2.5="orange", ="red"]
            Switch item=Beacon2_LowBattery label="Low Battery" icon="lowbattery"
        }
        
        Text label="Beacon 3 - RearBag Bag" icon="bag" {
            Text item=Beacon3_Mac label="MAC [%s]" icon="bluetooth"
            Text item=Beacon3_RSSI label="Signal [%d dBm]" icon="signal"
            Text item=Beacon3_Distance label="Distance [%.2f m]" icon="distance"
        }
        
        Text label="Beacon 4 - Available" icon="bag" {
            Text item=Beacon4_Name label="Name [%s]"
            Text item=Beacon4_Distance label="Distance [%.2f m]" icon="distance"
        }
    }
}
```

#### Troubleshooting Beacons

##### 1. Finding Beacon MAC Addresses

Monitor logs to discover beacon MAC addresses:

```bash
# Watch for beacon detection
tail -f /var/log/openhab/openhab.log | grep "Found tag.*with MAC"

# See routing decisions
tail -f /var/log/openhab/openhab.log | grep "Routing tag"

# Check current MAC assignments
grep "Configured beacon MAC" /var/log/openhab/openhab.log | tail -10
```

Example output:
```
[DEBUG] Found tag1 with MAC aabbcc112233
[DEBUG] Routing tag1 (MAC aabbcc112233) to beacon1
[DEBUG] Configured beacon MAC aabbcc112233 for slot 1
```

##### 2. Beacon Data Shuffling

**Symptom**: Beacon names and data keep changing between beacon1/beacon2/beacon3/beacon4 channels.

**Cause**: Teltonika FMM920 assigns beacons to tag1-4 dynamically based on which beacon it scans first.

**Solution**: Configure beacon MAC addresses in thing configuration (see MAC Address Routing above).

##### 3. Stale Beacon Names

**Symptom**: Beacon shows old name even though it's not present or has a different name.

**Cause**: Previous versions didn't clear the name channel when no name was received.

**Solution**: Update to latest binding version (5.2.0+). Name channel now automatically clears to UNDEF when beacon doesn't send a name.

##### 4. Inaccurate Distance

**Symptom**: Distance calculation seems wrong (too close or too far).

**Cause**: Default `beaconTxPower` or `beaconPathLoss` values don't match your beacon or environment.

**Solution**: 
1. Check your beacon's specifications for actual Tx power (usually -59 to -65 dBm)
2. Adjust `beaconPathLoss`: 
   - `2.0` = open space (outdoor, motorcycle)
   - `2.5-3.0` = light obstacles (car interior)
   - `2.7-4.3` = heavy obstacles (building with walls)

Configure on the Server Bridge:
```openhab
Bridge traccar:server:gpsserver [
    url="https://gps.example.com",
    username="user@example.com",
    password="password",
    beaconTxPower=-62,     // Check your beacon specs
    beaconPathLoss=2.5     // Adjust based on environment
]
```

##### 5. Missing Temperature/Humidity

**Symptom**: Temperature and humidity channels show NULL/UNDEF.

**Cause**: Your beacons don't have environmental sensors (temperature/humidity).

**Solution**: This is normal. Only beacons with built-in sensors (like Teltonika EYE Beacon) provide temperature and humidity. Basic BLE beacons only provide MAC, RSSI, battery, and name.

##### 6. Beacon Not Appearing

**Symptom**: Beacon visible in Traccar but not showing in openHAB.

**Checklist**:
1. Verify beacon is actually being transmitted by Teltonika device - check Traccar web interface
2. Check webhook is working: `grep "Received webhook" /var/log/openhab/openhab.log | tail -5`
3. Enable debug logging: `openhab> log:set DEBUG org.openhab.binding.traccar`
4. Check for errors: `grep ERROR /var/log/openhab/openhab.log | grep traccar | tail -10`
5. Restart thing: `openhab-cli reload-thing traccar:device:gpsserver:DEVICEID`

##### 7. Multiple Devices with Same Beacon

**Important**: Each beacon can only be tracked by ONE Teltonika device. If multiple vehicles have beacons, each vehicle must have its own unique set of beacons. The binding handles multiple devices correctly - just configure different MAC addresses for each device thing.

## Full Example

### traccar.things

```openhab
Bridge traccar:server:myserver "Traccar Server" [
    url="https://demo.traccar.org",
    username="user@example.com",
    password="password123",
    refreshInterval=30,
    webhookPort=8090,
    speedUnit="kmh",
    speedThreshold=2.0,
    useNominatim=true,
    nominatimLanguage="en",
    geocodingCacheDistance=50
] {
    Thing device car1 "Family Car" [ deviceId=1 ]
    Thing device phone1 "My Phone" [ deviceId=2 ]
}
```

### traccar.items

```openhab
Group gFamilyCar "Family Car" <car>

// Position & Navigation
Location FamilyCar_Position "Position" (gFamilyCar) 
    {channel="traccar:device:myserver:car1:position"}

Number:Length FamilyCar_Altitude "Altitude [%.1f m]" (gFamilyCar) 
    {channel="traccar:device:myserver:car1:altitude"}

Number:Speed FamilyCar_Speed "Speed [%.1f %unit%]" (gFamilyCar) 
    {channel="traccar:device:myserver:car1:speed"}

Number:Angle FamilyCar_Course "Direction [%.0f °]" (gFamilyCar) 
    {channel="traccar:device:myserver:car1:course"}

Number:Length FamilyCar_Accuracy "GPS Accuracy [%.1f m]" (gFamilyCar) 
    {channel="traccar:device:myserver:car1:accuracy"}

Switch FamilyCar_GpsValid "GPS Valid" (gFamilyCar) 
    {channel="traccar:device:myserver:car1:valid"}

String FamilyCar_Address "Address [%s]" (gFamilyCar) 
    {channel="traccar:device:myserver:car1:address"}

// Distance Tracking (automatic km conversion with unit="km")
// For Teltonika/vehicle trackers - use totalDistance (actual odometer)
Number:Length FamilyCar_TotalDistance "Odometer [%.1f km]" (gFamilyCar) 
    {channel="traccar:device:myserver:car1:totalDistance", unit="km"}

// For OSMand/phone trackers - use odometer (device reading)
Number:Length Phone_Odometer "Distance [%.1f km]" (gFamilyCar) 
    {channel="traccar:device:myserver:phone1:odometer", unit="km"}

// Trip distance (all protocols)
Number:Length FamilyCar_Distance "Trip Distance [%.1f m]" (gFamilyCar) 
    {channel="traccar:device:myserver:car1:distance"}

// Device Status
String FamilyCar_Status "Status [%s]" (gFamilyCar) 
    {channel="traccar:device:myserver:car1:status"}

DateTime FamilyCar_LastUpdate "Last Update [%1$tF %1$tR]" (gFamilyCar) 
    {channel="traccar:device:myserver:car1:lastUpdate"}

Number:Dimensionless FamilyCar_Battery "Battery [%.0f %%]" (gFamilyCar) 
    {channel="traccar:device:myserver:car1:batteryLevel"}

Switch FamilyCar_Motion "Motion" (gFamilyCar) 
    {channel="traccar:device:myserver:car1:motion"}

String FamilyCar_Activity "Activity [%s]" (gFamilyCar) 
    {channel="traccar:device:myserver:car1:activity"}

String FamilyCar_Protocol "Protocol [%s]" (gFamilyCar) 
    {channel="traccar:device:myserver:car1:protocol"}

// Vehicle Information
Switch FamilyCar_Ignition "Ignition [MAP(ignition.map):%s]" (gFamilyCar) 
    {channel="traccar:device:myserver:car1:ignition"}

Number:Time FamilyCar_Hours "Engine Hours [%.1f h]" (gFamilyCar) 
    {channel="traccar:device:myserver:car1:hours", unit="h"}

Number FamilyCar_Event "Event [MAP(teltonika_event.map):%s]" (gFamilyCar) 
    {channel="traccar:device:myserver:car1:event"}

// Connectivity
Number FamilyCar_GpsSatellites "GPS Satellites [%d]" (gFamilyCar) 
    {channel="traccar:device:myserver:car1:gpsSatellites"}

Number:Dimensionless FamilyCar_GsmSignal "GSM Signal [%.0f %%]" (gFamilyCar) 
    {channel="traccar:device:myserver:car1:gsmSignal"}

// Geofencing
String FamilyCar_GeofenceEvent "Event [%s]" (gFamilyCar) 
    {channel="traccar:device:myserver:car1:geofenceEvent"}

Number FamilyCar_GeofenceId "Geofence ID" (gFamilyCar) 
    {channel="traccar:device:myserver:car1:geofenceId"}

String FamilyCar_GeofenceName "Geofence [%s]" (gFamilyCar) 
    {channel="traccar:device:myserver:car1:geofenceName"}
```

### traccar.sitemap

```openhab
sitemap tracking label="Vehicle Tracking" {
    Frame label="Family Car" {
        Mapview item=FamilyCar_Position height=10
        
        Text item=FamilyCar_Status label="Status [%s]" icon="status"
        Text item=FamilyCar_LastUpdate label="Last Update" icon="time"
        
        Text item=FamilyCar_Speed label="Speed [%.1f %unit%]" icon="speed"
        Text item=FamilyCar_Course label="Course [%.0f°]" icon="wind"
        Text item=FamilyCar_Altitude label="Altitude [%.1f m]" icon="altitude"
        Switch item=FamilyCar_GpsValid label="GPS Valid" icon="network"
        Text item=FamilyCar_Accuracy label="Accuracy [%.1f m]" icon="zoom"
        
        Text item=FamilyCar_Odometer label="Odometer [%.1f km]" icon="line"
        Text item=FamilyCar_Distance label="Distance [%.1f m]" icon="line"
        Text item=FamilyCar_Hours label="Engine Hours [%.1f h]" icon="time"
        
        Text item=FamilyCar_Battery label="Battery [%.0f %%]" icon="battery"
        Switch item=FamilyCar_Motion label="Motion" icon="motion"
        Text item=FamilyCar_Activity label="Activity [%s]" icon="motion"
        
        Text item=FamilyCar_GpsSatellites label="GPS Satellites [%d]" icon="network"
        Text item=FamilyCar_GsmSignal label="GSM Signal [%.0f %%]" icon="qualityofservice"
        
        Text item=FamilyCar_GeofenceName label="Location [%s]" icon="location"
        Text item=FamilyCar_Address label="Address [%s]" icon="location"
        Text item=FamilyCar_Protocol label="Protocol [%s]" icon="text"
        Text item=FamilyCar_Event label="Event Code [%d]" icon="text"
    }
}
```

### Geofencing Rule Example

```openhab
rule "Open Garage on Arrival"
when
    Item FamilyCar_GeofenceEvent changed to "geofenceEnter"
then
    val geofenceId = (FamilyCar_GeofenceId.state as Number).intValue()
    
    if (geofenceId == 1) { // Home geofence
        logInfo("Garage", "Car arriving, opening garage door")
        GarageDoor.sendCommand(ON)
        GarageLight.sendCommand(ON)
    }
end

rule "Trip Tracking"
when
    Item FamilyCar_Distance received update
then
    val distance = (FamilyCar_Distance.state as QuantityType<Length>).toUnit("m").doubleValue()
    
    if (distance > 0) {
        logInfo("Trip", "Distance since last update: {} m", distance)
        // Accumulate trip distance
        TripDistance.postUpdate((TripDistance.state as Number).doubleValue() + distance)
    }
end

rule "Activity Detection"
when
    Item FamilyCar_Activity changed
then
    val activity = FamilyCar_Activity.state.toString()
    
    switch(activity) {
        case "walking": {
            logInfo("Activity", "Driver is walking")
        }
        case "in_vehicle": {
            logInfo("Activity", "Driver is in vehicle")
        }
        case "still": {
            logInfo("Activity", "Driver is stationary")
        }
    }
end

rule "Low GPS Accuracy Alert"
when
    Item FamilyCar_Accuracy changed
then
    val accuracy = (FamilyCar_Accuracy.state as QuantityType<Length>).toUnit("m").doubleValue()
    
    if (accuracy > 50) {
        logWarn("GPS", "Poor GPS accuracy: {} m", accuracy)
        sendNotification("admin@example.com", "Poor GPS signal on Family Car")
    }
end

rule "GPS Fix Lost"
when
    Item FamilyCar_GpsValid changed to OFF
then
    logWarn("GPS", "GPS fix lost - position may be unreliable")
    // Could trigger indoor parking automation
end
```

## Webhook Configuration

Webhooks enable real-time position updates and instant geofence notifications. The binding supports two types of webhook data:

### 1. Position Updates (Real-Time Tracking)
Receive position updates every 1-2 seconds during movement without polling.

### 2. Event Notifications (Geofencing)
Instant notifications for geofence entry/exit, device online/offline events.

### Traccar Server Configuration

#### Modern Configuration (Traccar 5.0+)

Edit `/opt/traccar/conf/traccar.xml` (or equivalent):

```xml
<!-- Enable position forwarding -->
<entry key='forward.enable'>true</entry>
<entry key='forward.url'>http://YOUR_OPENHAB_IP:8090/webhook</entry>
<entry key='forward.type'>json</entry>
<entry key='forward.retry'>true</entry>

<!-- Enable event forwarding -->
<entry key="event.forward.enable">true</entry>
<entry key='event.forward.url'>http://YOUR_OPENHAB_IP:8090/webhook</entry>
```

**Replace `YOUR_OPENHAB_IP`** with your openHAB server's IP address (e.g., `192.168.1.50`).

Restart Traccar:
```bash
sudo systemctl restart traccar
```

#### Web Interface Configuration (Alternative)

1. Go to **Traccar web interface** → **Settings** → **Notifications**
2. Add notification for events you want:
   - `geofenceEnter` - Device enters geofence
   - `geofenceExit` - Device exits geofence
   - `deviceOnline` - Device comes online
   - `deviceOffline` - Device goes offline
3. Select **Web Request (POST)**
4. Set URL: `http://YOUR_OPENHAB_IP:8090/webhook`
5. Content Type: `application/json`

### Webhook Port Configuration

The default webhook port is **8090**. You can change it in the bridge configuration:

```openhab
Bridge traccar:server:myserver [ webhookPort=8090 ]
```

**Important**: 
- Port must be between 1024-65535
- Ensure firewall allows incoming connections on this port
- Traccar server must be able to reach openHAB IP address

### Testing Webhooks

#### 1. Test with curl

```bash
curl -X POST http://localhost:8090/webhook \
  -H "Content-Type: application/json" \
  -d '{"position":{"id":1,"deviceId":1,"latitude":55.676,"longitude":12.568}}'
```

#### 2. Monitor Webhook Traffic

Create a monitoring script to see all incoming webhooks:

```python
#!/usr/bin/env python3
from http.server import BaseHTTPRequestHandler, HTTPServer
import json
import sys

class WebhookMonitor(BaseHTTPRequestHandler):
    def log_request_details(self):
        content_length = self.headers.get('Content-Length')
        
        # GET request
        if self.command == 'GET':
            query = self.path.split('?', 1)
            if len(query) > 1:
                params = dict(p.split('=') for p in query[1].split('&') if '=' in p)
                print(f"\n{'='*60}")
                print(f"GET Request - Query Parameters:")
                print(json.dumps(params, indent=2))
                
        # POST request
        elif self.command == 'POST' and content_length:
            body = self.rfile.read(int(content_length)).decode('utf-8')
            try:
                data = json.loads(body)
                print(f"\n{'='*60}")
                print(f"POST Request - JSON Body:")
                print(json.dumps(data, indent=2))
            except:
                print(f"\n{'='*60}")
                print(f"POST Request - Raw Body:")
                print(body)
    
    def do_GET(self):
        self.log_request_details()
        self.send_response(200)
        self.end_headers()
        self.wfile.write(b'OK')
    
    def do_POST(self):
        self.log_request_details()
        self.send_response(200)
        self.end_headers()
        self.wfile.write(b'OK')

if __name__ == '__main__':
    port = int(sys.argv[1]) if len(sys.argv) > 1 else 8091
    server = HTTPServer(('0.0.0.0', port), WebhookMonitor)
    print(f"Webhook monitor listening on port {port}...")
    server.serve_forever()
```

Save as `webhook_monitor.py` and run:
```bash
python3 webhook_monitor.py 8091
```

Then temporarily update Traccar to forward to port 8091 to see raw webhook data.

#### 3. Check OpenHAB Logs

Enable debug logging:
```
openhab> log:set DEBUG org.openhab.binding.traccar
```

Watch for webhook messages:
```bash
tail -f /var/log/openhab/openhab.log | grep "webhook"
```

You should see:
```
[DEBUG] Received webhook (POST): {"position":{"latitude":55.676,...}}
[DEBUG] Processing webhook position update
```

### Webhook Data Format

#### Position Update Example
```json
{
  "position": {
    "id": 0,
    "deviceId": 10,
    "protocol": "teltonika",
    "serverTime": "2026-01-18T08:23:16.836+00:00",
    "deviceTime": "2026-01-18T08:23:10.011+00:00",
    "fixTime": "2026-01-18T08:23:10.011+00:00",
    "valid": false,
    "latitude": 55.6761,
    "longitude": 12.5683,
    "altitude": 0.0,
    "speed": 0.0,
    "course": 0.0,
    "accuracy": 0.0,
    "attributes": {
      "priority": 0,
      "sat": 0,
      "event": 10828,
      "distance": 0.0,
      "totalDistance": 33279520.98,
      "motion": false,
      "hours": 861239290
    }
  },
  "device": {
    "id": 10,
    "name": "Dream Catcher - FMM920",
    "uniqueId": "350000000000000",
    "status": "online"
  }
}
```

#### Geofence Event Example
```json
{
  "event": {
    "id": 1234,
    "type": "geofenceEnter",
    "eventTime": "2026-01-18T08:23:16.836+00:00",
    "deviceId": 10,
    "geofenceId": 1
  },
  "position": {
    "latitude": 55.676,
    "longitude": 12.568
  },
  "device": {
    "id": 10,
    "name": "Family Car"
  },
  "geofence": {
    "id": 1,
    "name": "Home"
  }
}
```

## Speed Unit Conversion

Traccar reports speed in **knots**. The binding converts to:
- **kmh**: × 1.852 (default)
- **mph**: × 1.15078
- **knots**: no conversion

Configure in bridge:
```openhab
Bridge traccar:server:myserver [ speedUnit="kmh" ]
```

## Distance Unit Conversion

### Automatic Kilometer Conversion

The binding sends odometer values in **meters** (Traccar's base unit). To display in kilometers, use item metadata:

```openhab
Number:Length Vehicle_Odometer "Odometer [%.1f km]" 
    {channel="traccar:device:myserver:car1:odometer", unit="km"}
```

OpenHAB automatically converts:
- Traccar sends: `33,279,520.98` meters
- OpenHAB stores: `33279520.98 m` (base unit)
- Display with `unit="km"`: `33,279.5 km`
- REST API returns: `"state": "33279.52 km"` with `"unitSymbol": "km"`

### Other Distance Units

You can use any unit supported by OpenHAB:
```openhab
Number:Length Vehicle_Odometer_Miles "Odometer [%.1f mi]" 
    {channel="traccar:device:myserver:car1:odometer", unit="mi"}

Number:Length Vehicle_Distance_Feet "Distance [%.1f ft]" 
    {channel="traccar:device:myserver:car1:distance", unit="ft"}
```

### Protocol Differences

Different GPS protocols report odometer differently:

| Protocol | Attribute Name | Notes |
|----------|----------------|-------|
| Teltonika | `totalDistance` | Industrial trackers, cumulative distance |
| OSMand | `odometer` | Mobile apps, trip odometer |
| H02/GT06 | `odometer` | Hardware trackers |

The binding **automatically handles both**:
1. First checks for `odometer` attribute
2. Falls back to `totalDistance` if not present
3. Logs which attribute was used (visible in DEBUG mode)

## Advanced Features

### Ignition Monitoring & Notifications

The `ignition` channel provides real-time ignition status from compatible vehicle trackers (e.g., Teltonika FMM920). Use this for automatic notifications when your vehicle starts or is parked:

```openhab
// Items
Switch Vehicle_Ignition "Ignition [MAP(ignition.map):%s]" <fire>
    {channel="traccar:device:myserver:car1:ignition"}

// MAP transformation: transform/ignition.map
ON=On
OFF=Off
NULL=Unknown
-=Unknown

// Rule
rule "Vehicle Ignition ON Notification"
when
    Item Vehicle_Ignition changed from OFF to ON
then
    val position = Vehicle_Position.state.toString
    val address = Vehicle_Address.state.toString
    val speed = Vehicle_Speed.state
    val odometer = Vehicle_Odometer.state
    
    // Extract coordinates
    val coords = position.split(",")
    val latitude = coords.get(0)
    val longitude = coords.get(1)
    val mapLink = "https://www.google.com/maps?q=" + latitude + "," + longitude
    
    // Format timestamp
    val dateFormat = new java.text.SimpleDateFormat("dd/MM HH:mm")
    val timestamp = dateFormat.format(new java.util.Date())
    
    // Send HTML email
    val mailActions = getActions("mail","mail:smtp:samplesmtp")
    
    val emailBody = "<html><body style='font-family: Arial, sans-serif;'>" +
        "<div style='background: linear-gradient(135deg, #667eea 0%, #764ba2 100%); " +
        "color: white; padding: 20px; border-radius: 10px;'>" +
        "<h2 style='margin: 0;'>🏍️ VEHICLE STARTED</h2>" +
        "<p style='margin: 5px 0; opacity: 0.9;'>" + timestamp + "</p>" +
        "</div>" +
        "<div style='background: #f8f9fa; padding: 15px; margin-top: 10px; border-radius: 8px;'>" +
        "<h3 style='color: #667eea; margin-top: 0;'>📍 Location</h3>" +
        "<p style='margin: 5px 0;'>" + address + "</p>" +
        "<p style='margin-top: 10px;'><a href='" + mapLink + "' " +
        "style='color: #667eea; text-decoration: none;'>📍 View on Map</a></p>" +
        "</div>" +
        "<table style='width: 100%; margin-top: 10px;'>" +
        "<tr><td style='padding: 10px; background: #e8f5e9; border-radius: 5px;'>" +
        "<strong>🔥 Ignition:</strong> ON</td></tr>" +
        "<tr><td style='padding: 10px; background: #e3f2fd; border-radius: 5px;'>" +
        "<strong>📏 Odometer:</strong> " + odometer + "</td></tr>" +
        "</table>" +
        "</body></html>"
    
    mailActions.sendHtmlMail("user@example.com", "🏍️ Vehicle Started", emailBody)
    logInfo("vehicle", "Ignition ON notification sent")
end

rule "Vehicle Ignition OFF Notification"
when
    Item Vehicle_Ignition changed from ON to OFF
then
    val position = Vehicle_Position.state.toString
    val address = Vehicle_Address.state.toString
    val odometer = Vehicle_Odometer.state
    val coords = position.split(",")
    val mapLink = "https://www.google.com/maps?q=" + coords.get(0) + "," + coords.get(1)
    
    val dateFormat = new java.text.SimpleDateFormat("dd/MM HH:mm")
    val timestamp = dateFormat.format(new java.util.Date())
    
    val mailActions = getActions("mail","mail:smtp:samplesmtp")
    
    val emailBody = "<html><body style='font-family: Arial, sans-serif;'>" +
        "<div style='background: linear-gradient(135deg, #f093fb 0%, #f5576c 100%); " +
        "color: white; padding: 20px; border-radius: 10px;'>" +
        "<h2 style='margin: 0;'>🏍️ VEHICLE PARKED</h2>" +
        "<p style='margin: 5px 0; opacity: 0.9;'>" + timestamp + "</p>" +
        "</div>" +
        "<div style='background: #f8f9fa; padding: 15px; margin-top: 10px; border-radius: 8px;'>" +
        "<h3 style='color: #f5576c; margin-top: 0;'>📍 Parked Location</h3>" +
        "<p style='margin: 5px 0;'>" + address + "</p>" +
        "<p style='margin-top: 10px;'><a href='" + mapLink + "' " +
        "style='color: #f5576c; text-decoration: none;'>📍 View on Map</a></p>" +
        "</div>" +
        "</body></html>"
    
    mailActions.sendHtmlMail("user@example.com", "🏍️ Vehicle Parked", emailBody)
    logInfo("vehicle", "Ignition OFF notification sent")
end
```

### Trip Distance Tracking

Use the `distance` channel to track trip segments:

```openhab
// Items
Number:Length Trip_Distance "Trip Distance [%.1f km]" {unit="km"}
Number:Length Vehicle_Distance "Distance" {channel="traccar:device:myserver:car1:distance"}

// Rule
rule "Accumulate Trip Distance"
when
    Item Vehicle_Distance received update
then
    val increment = (Vehicle_Distance.state as QuantityType<Length>).toUnit("m").doubleValue()
    
    if (increment > 0) {
        val current = (Trip_Distance.state as QuantityType<Length>).toUnit("m").doubleValue()
        Trip_Distance.postUpdate(new QuantityType((current + increment), "m"))
        
        logInfo("Trip", "Added {} m to trip (total: {} km)", 
            increment, (current + increment) / 1000.0)
    }
end

rule "Reset Trip on Arrival"
when
    Item Vehicle_GeofenceEvent changed to "geofenceEnter"
then
    if ((Vehicle_GeofenceId.state as Number).intValue() == 1) {
        val finalDistance = (Trip_Distance.state as QuantityType<Length>).toUnit("km").doubleValue()
        logInfo("Trip", "Trip completed: {} km", finalDistance)
        
        Trip_Distance.postUpdate(new QuantityType(0, "m"))
    }
end
```

### Real-Time Movement Monitoring

Webhooks deliver position updates every 1-2 seconds during movement:

```openhab
rule "Real-Time Speed Monitoring"
when
    Item Vehicle_Speed received update
then
    val speed = (Vehicle_Speed.state as QuantityType<Speed>).toUnit("km/h").doubleValue()
    
    if (speed > 120) {
        sendNotification("admin@example.com", 
            String::format("Vehicle speeding: %.0f km/h!", speed))
        logWarn("Speed", "Excessive speed detected: {} km/h", speed)
    }
end

rule "Track GPS Quality During Movement"
when
    Item Vehicle_Motion changed to ON
then
    createTimer(now.plusSeconds(5), [ |
        val accuracy = (Vehicle_Accuracy.state as QuantityType<Length>).toUnit("m").doubleValue()
        val satellites = Vehicle_GpsSatellites.state as Number
        val gpsValid = Vehicle_GpsValid.state == ON
        
        logInfo("GPS", "Movement started - GPS quality check:")
        logInfo("GPS", "  Valid: {}", gpsValid)
        logInfo("GPS", "  Accuracy: {} m", accuracy)
        logInfo("GPS", "  Satellites: {}", satellites)
        
        if (!gpsValid || accuracy > 50) {
            sendNotification("admin@example.com", "Poor GPS quality during movement")
        }
    ])
end
```

### Activity-Based Automation

For OSMand/Traccar Client apps:

```openhab
rule "Detect Driver Left Vehicle"
when
    Item Phone_Activity changed from "in_vehicle" to "walking"
then
    logInfo("Activity", "Driver exited vehicle and is walking")
    
    // Wait 2 minutes to confirm
    createTimer(now.plusMinutes(2), [ |
        if (Phone_Activity.state.toString() == "walking") {
            // Driver has been walking for 2 minutes
            logInfo("Activity", "Driver confirmed away from vehicle")
            Vehicle_Status_Text.postUpdate("Driver away")
            
            // Lock vehicle if supported
            Vehicle_Lock.sendCommand(ON)
        }
    ])
end

rule "Detect Arrival Home on Foot"
when
    Item Phone_GeofenceEvent changed to "geofenceEnter"
then
    val activity = Phone_Activity.state.toString()
    val geofenceId = (Phone_GeofenceId.state as Number).intValue()
    
    if (geofenceId == 1 && activity == "walking") {
        logInfo("Arrival", "Arrived home on foot - enable walking mode")
        // Turn on pathway lights instead of garage
        Pathway_Lights.sendCommand(ON)
        Front_Door.sendCommand(UNLOCK)
    } else if (geofenceId == 1 && activity == "in_vehicle") {
        logInfo("Arrival", "Arrived home by vehicle - open garage")
        Garage_Door.sendCommand(ON)
    }
end
```

### Multi-Device Proximity Detection

Track multiple vehicles/phones:

```openhab
rule "Family Members Home"
when
    Item Person1_GeofenceEvent received update or
    Item Person2_GeofenceEvent received update or
    Item Car_GeofenceEvent received update
then
    val person1Home = Person1_GeofenceName.state.toString() == "Home"
    val person2Home = Person2_GeofenceName.state.toString() == "Home"
    val carHome = Car_GeofenceName.state.toString() == "Home"
    
    val countHome = (person1Home ? 1 : 0) + (person2Home ? 1 : 0) + (carHome ? 1 : 0)
    
    logInfo("Presence", "Devices at home: {}", countHome)
    
    if (countHome == 0) {
        logInfo("Presence", "Nobody home - enable away mode")
        House_Mode.postUpdate("away")
        Climate_Away.sendCommand(ON)
        Security_Armed.sendCommand(ON)
    } else if (countHome >= 1) {
        logInfo("Presence", "Someone home - disable away mode")
        House_Mode.postUpdate("home")
        Climate_Away.sendCommand(OFF)
    }
end
```

### Engine Hours Maintenance Tracking

Track maintenance intervals:

```openhab
// Items
Number:Time Vehicle_Hours "Engine Hours [%.1f h]" 
    {channel="traccar:device:myserver:car1:hours", unit="h"}
Number:Time Last_Service_Hours "Last Service At [%.1f h]"
Number:Time Next_Service_Hours "Next Service At [%.1f h]"

// Rule
rule "Check Maintenance Due"
when
    Item Vehicle_Hours received update
then
    val currentHours = (Vehicle_Hours.state as QuantityType<Time>).toUnit("h").doubleValue()
    val lastService = (Last_Service_Hours.state as QuantityType<Time>).toUnit("h").doubleValue()
    val hoursSinceService = currentHours - lastService
    
    if (hoursSinceService >= 50) {  // Service every 50 hours
        logWarn("Maintenance", "Service due! Hours since last: {}", hoursSinceService)
        sendNotification("admin@example.com", 
            String::format("Vehicle maintenance due: %.1f hours since service", hoursSinceService))
        
        Maintenance_Due.postUpdate(ON)
        
        // Calculate next service
        Next_Service_Hours.postUpdate(new QuantityType(currentHours + 50, "h"))
    }
end

rule "Record Service Completed"
when
    Item Service_Completed_Button received command ON
then
    val currentHours = Vehicle_Hours.state as QuantityType<Time>
    Last_Service_Hours.postUpdate(currentHours)
    Next_Service_Hours.postUpdate(currentHours.add(new QuantityType(50, "h")))
    Maintenance_Due.postUpdate(OFF)
    
    logInfo("Maintenance", "Service recorded at {} hours", currentHours)
    sendNotification("admin@example.com", "Vehicle service logged")
end
```

### Event Code Monitoring

Track device-specific events (Teltonika example):

```openhab
rule "Monitor Vehicle Events"
when
    Item Vehicle_Event received update
then
    val eventCode = (Vehicle_Event.state as DecimalType).intValue()
    
    // Teltonika FMM920 event codes
    switch(eventCode) {
        case 10828: logInfo("Vehicle", "Ignition OFF")
        case 10829: logInfo("Vehicle", "Ignition ON")
        case 10831: logInfo("Vehicle", "Movement detected")
        case 10832: logInfo("Vehicle", "Harsh acceleration")
        case 10833: logInfo("Vehicle", "Harsh braking")
        case 10834: logInfo("Vehicle", "Harsh cornering")
        default: logInfo("Vehicle", "Event code: {}", eventCode)
    }
    
    // Alert on harsh driving
    if (eventCode >= 10832 && eventCode <= 10834) {
        sendNotification("admin@example.com", 
            String::format("Harsh driving detected! Event: %d", eventCode))
    }
end
```

### GPS Quality Alerting

Monitor GPS reliability:

```openhab
rule "GPS Quality Alert"
when
    Item Vehicle_Accuracy received update or
    Item Vehicle_GpsSatellites received update or
    Item Vehicle_GpsValid received update
then
    val accuracy = (Vehicle_Accuracy.state as QuantityType<Length>).toUnit("m").doubleValue()
    val satellites = (Vehicle_GpsSatellites.state as DecimalType).intValue()
    val gpsValid = Vehicle_GpsValid.state == ON
    
    var String quality = "Unknown"
    var Boolean alert = false
    
    if (!gpsValid) {
        quality = "No GPS Fix"
        alert = true
    } else if (accuracy <= 5) {
        quality = "Excellent"
    } else if (accuracy <= 15) {
        quality = "Good"
    } else if (accuracy <= 50) {
        quality = "Fair"
    } else {
        quality = "Poor"
        alert = true
    }
    
    GPS_Quality_Text.postUpdate(quality)
    
    if (alert && Vehicle_Motion.state == ON) {
        logWarn("GPS", "Poor GPS quality during movement: {} m accuracy, {} satellites", 
            accuracy, satellites)
        sendNotification("admin@example.com", 
            String::format("Poor GPS: %s (%.0f m, %d sats)", quality, accuracy, satellites))
    }
end
```

## Protocol-Specific Channel Availability

Not all channels are supported by every device protocol. Availability depends on what data your GPS tracker sends to Traccar.

### Common Protocol Capabilities

| Protocol | Position | Altitude | Valid | Distance | Odometer | Hours | Event | Activity | Satellites | GSM | Battery |
|----------|----------|----------|-------|----------|----------|-------|-------|----------|------------|-----|---------|
| **Teltonika** | ✅ | ✅ | ✅ | ✅ | ✅ (`totalDistance`) | ✅ | ✅ | ❌ | ✅ (`sat`) | ❌ | ❌ |
| **OSMand** | ✅ | ✅ | ✅ | ✅ | ✅ (`odometer`) | ✅ | ❌ | ✅ | ❌ | ❌ | ✅ |
| **H02** | ✅ | ✅ | ✅ | ✅ | ✅ | ❌ | ❌ | ❌ | ✅ | ✅ | ✅ |
| **GT06** | ✅ | ✅ | ✅ | ✅ | ✅ | ❌ | ❌ | ❌ | ✅ | ✅ | ✅ |
| **Traccar Client** | ✅ | ✅ | ✅ | ✅ | ✅ | ❌ | ❌ | ✅ | ❌ | ❌ | ✅ |

### Channel Details by Protocol

#### Teltonika (Industrial GPS Trackers)
**Example devices**: FMM920, FMB920, FMT100

**Supported channels**:
- All position/navigation channels (position, altitude, speed, course, accuracy, valid)
- Distance tracking (odometer via `totalDistance`, distance)
- Engine hours (hours in milliseconds)
- GPS satellites (sat attribute)
- Device events (event codes like 10828, 10829, 10831)
- Protocol identification

**Not supported**:
- Activity recognition (hardware limitation)
- Battery level (wired devices)
- GSM signal (not reported by most models)

**Odometer notes**: 
- Uses `totalDistance` attribute
- Binding automatically falls back to this if `odometer` is not present
- Reported in meters, auto-converted to km with `unit="km"` in item definition

**Example webhook attributes**:
```json
{
  "sat": 8,
  "event": 10831,
  "distance": 0.0,
  "totalDistance": 33279520.98,
  "motion": false,
  "hours": 861239290,
  "priority": 0
}
```

#### OSMand / Traccar Client (Mobile Apps)
**Example apps**: Traccar Client (Android/iOS), OSMand with Traccar plugin

**Supported channels**:
- All position/navigation channels
- Distance tracking (odometer attribute)
- Activity recognition (walking, in_vehicle, still, on_bicycle, on_foot)
- Battery level
- Protocol identification

**Not supported**:
- Engine hours (not applicable to phones)
- GPS satellites (not exposed by apps)
- GSM signal (not exposed by apps)
- Device events (app limitation)

**Activity recognition**: Powered by Android/iOS motion APIs, updates automatically:
- `walking` - Pedestrian movement
- `in_vehicle` - Driving/passenger in car
- `still` - Not moving
- `on_bicycle` - Cycling
- `on_foot` - Walking/running (variant of walking)
- `running` - Fast pedestrian movement

**Example webhook attributes**:
```json
{
  "batteryLevel": 85.0,
  "distance": 15.3,
  "odometer": 179924.0,
  "motion": true,
  "activity": "walking"
}
```

#### H02 / GT06 (Chinese Hardware Trackers)
**Example devices**: TK103, GT06N, Concox GT06

**Supported channels**:
- All position/navigation channels
- Distance tracking
- GPS satellites
- GSM signal strength
- Battery level
- Protocol identification

**Not supported**:
- Engine hours (not measured)
- Activity recognition (hardware limitation)
- Device events (protocol limitation)

### Protocol Attribute Mapping

The binding handles protocol differences automatically:

| Channel | Teltonika | OSMand | H02/GT06 | Fallback Behavior |
|---------|-----------|--------|----------|-------------------|
| `odometer` | `totalDistance` | `odometer` | `odometer` | Checks `odometer` first, then `totalDistance` |
| `hours` | `hours` (ms) | `hours` (ms) | ❌ | NULL if not present |
| `gpsSatellites` | `sat` | ❌ | `sat` | NULL if not present |
| `activity` | ❌ | `activity` | ❌ | NULL if not present |
| `event` | `event` | ❌ | ❌ | NULL if not present |

### Checking Your Device's Attributes

To see what data your specific device sends:

#### Via Traccar API:
```bash
curl -u "username:password" \
  "https://your-traccar-server/api/positions?deviceId=1" | python3 -m json.tool
```

Look for the `attributes` object:
```json
{
  "attributes": {
    "sat": 8,
    "motion": true,
    "distance": 0.0,
    "totalDistance": 33279520.98,
    "hours": 861239290,
    "event": 10831
  }
}
```

#### Via OpenHAB Webhook Monitor:
Run the webhook monitor script (see Webhook Configuration section) and observe what attributes appear in incoming webhooks during device movement.

#### Via OpenHAB Logs:
```bash
tail -f /var/log/openhab/openhab.log | grep "attributes"
```

### Understanding NULL States

If a channel shows `NULL`, it means your device protocol doesn't provide that data. **This is normal and expected.**

Common NULL channels by protocol:
- **Teltonika devices**: `activity`, `batteryLevel`, `gsmSignal` will be NULL
- **OSMand apps**: `gpsSatellites`, `gsmSignal`, `hours`, `event` will be NULL  
- **H02/GT06**: `hours`, `activity`, `event` will be NULL

The binding gracefully handles missing attributes - channels simply remain NULL without errors.

## Troubleshooting

### Things show OFFLINE

**Symptoms**: Bridge or device things show OFFLINE status

**Solutions**:
1. Verify Traccar URL is accessible from openHAB server:
   ```bash
   curl -u "user:pass" "https://your-traccar-server/api/devices"
   ```
2. Check username/password are correct
3. Verify firewall allows outbound HTTPS connections
4. Enable debug logging to see connection errors:
   ```
   openhab> log:set DEBUG org.openhab.binding.traccar
   ```

### No position updates

**Symptoms**: Position channels don't update, last update timestamp frozen

**Solutions**:
1. Ensure `refreshInterval` ≥ 10 seconds in bridge configuration
2. Check device is online in Traccar web interface
3. Verify device has reported position recently in Traccar
4. Check OpenHAB logs for errors:
   ```bash
   tail -f /var/log/openhab/openhab.log | grep traccar
   ```
5. Test Traccar API manually:
   ```bash
   curl -u "user:pass" "https://your-traccar-server/api/positions?deviceId=1"
   ```

### Webhooks not working

**Symptoms**: Geofence events delayed, no real-time position updates

**Solutions**:

1. **Test webhook endpoint locally**:
   ```bash
   curl -X POST http://localhost:8090/webhook \
     -H "Content-Type: application/json" \
     -d '{"position":{"deviceId":1}}'
   ```
   Should return `200 OK`

2. **Check OpenHAB webhook server**:
   ```bash
   tail -f /var/log/openhab/openhab.log | grep webhook
   ```
   Should see: `Webhook server started on port 8090`

3. **Verify firewall allows incoming connections**:
   ```bash
   sudo ufw status
   sudo ufw allow 8090/tcp  # If blocked
   ```

4. **Test from Traccar server** (if self-hosted):
   ```bash
   curl -X POST http://OPENHAB_IP:8090/webhook \
     -H "Content-Type: application/json" \
     -d '{"position":{"deviceId":1}}'
   ```

5. **Check Traccar configuration** (`traccar.xml`):
   ```xml
   <entry key='forward.enable'>true</entry>
   <entry key='forward.url'>http://OPENHAB_IP:8090/webhook</entry>
   <entry key='forward.type'>json</entry>
   ```

6. **Restart Traccar** after configuration changes:
   ```bash
   sudo systemctl restart traccar
   ```

7. **Use webhook monitor** to debug (see Webhook Configuration section)

### Odometer shows huge numbers or wrong units

**Symptoms**: Odometer displays `33279520 m` instead of `33,279.5 km`

**Solution**: Add `unit="km"` metadata to item definition:
```openhab
Number:Length Vehicle_Odometer "Odometer [%.1f km]" 
    {channel="traccar:device:myserver:car1:odometer", unit="km"}
```

OpenHAB will automatically convert meters (from Traccar) to kilometers for display.

**How it works**:
1. Traccar sends: `33279520.98` meters
2. Binding stores in base unit (meters): `QuantityType(33279520.98, SIUnits.METRE)`
3. Item metadata `unit="km"` tells OpenHAB to convert for display
4. UI shows: `33,279.5 km`

### Some channels always show NULL

**Symptoms**: Channels like `activity`, `hours`, `event` never have values

**This is normal!** Not all channels are supported by every protocol.

**Common NULL channels**:
- **Teltonika devices**: `activity`, `batteryLevel` (wired devices don't have battery)
- **OSMand apps**: `gpsSatellites`, `hours`, `event` (apps don't expose this data)
- **All protocols**: `activity` only works with OSMand/Traccar Client apps

**Solutions**:
1. Check "Protocol-Specific Channel Availability" section above
2. Verify your device sends this data:
   ```bash
   curl -u "user:pass" "https://your-traccar/api/positions?deviceId=1" | grep attributes
   ```
3. Only use channels that your device protocol supports

### GPS Valid shows OFF but position updates

**This is normal for some situations:**
- Device is indoors (parking garage, covered area)
- Device uses cell tower triangulation instead of GPS
- Device has lost GPS lock but still reports last-known position

**The `valid` channel indicates**:
- `ON` - GPS has current satellite fix, position is accurate
- `OFF` - Position is estimated/cached, may be inaccurate

**Solution**: Use GPS Valid status to determine position reliability:
```openhab
rule "Check GPS Quality"
when
    Item Vehicle_GpsValid changed to OFF
then
    logWarn("GPS", "Vehicle position is estimated - GPS fix lost")
    // Don't trigger automations based on position
end
```

### Activity recognition not working

**Symptoms**: `activity` channel always NULL

**Requirements**:
- Only works with **OSMand** or **Traccar Client** mobile apps
- Does NOT work with hardware GPS trackers (Teltonika, H02, GT06, etc.)
- Requires device motion sensors (accelerometer, gyroscope)

**Solutions**:
1. Verify you're using OSMand or Traccar Client app
2. Check app permissions allow motion sensor access
3. Enable activity recognition in app settings
4. Hardware trackers will never support this - use `motion` channel instead

### Engine hours showing wrong values

**Symptoms**: Hours displays `861239290` instead of `239.2 h`

**Cause**: Traccar sends engine hours in **milliseconds**, binding must convert to hours.

**Solution**: This should be automatic. If not working:
1. Verify channel is defined correctly in items:
   ```openhab
   Number:Time Vehicle_Hours "Engine Hours [%.1f h]" 
       {channel="traccar:device:myserver:car1:hours", unit="h"}
   ```
2. Check binding logs for conversion:
   ```bash
   tail -f /var/log/openhab/openhab.log | grep "Engine hours"
   ```
   Should see: `Engine hours: 861239290.0 ms = 239.23 hours`

### Webhook monitor shows data but openHAB doesn't update

**Symptoms**: Webhook monitor receives data, but channels don't update

**Solutions**:
1. **Check device ID matches** between Traccar and openHAB thing configuration
2. **Verify thing is ONLINE**:
   ```bash
   curl http://localhost:8080/rest/things/traccar:device:myserver:car1
   ```
3. **Enable debug logging**:
   ```
   openhab> log:set DEBUG org.openhab.binding.traccar
   ```
   Should see: `Processing webhook position update` or `Processing webhook event type: geofenceEnter`
4. **Check JSON structure** - webhook must contain `position` and `device` objects

### Getting more help

**Enable full debug logging**:
```
openhab> log:set TRACE org.openhab.binding.traccar
```

**Collect diagnostic information**:
```bash
# Check bridge status
curl http://localhost:8080/rest/things/traccar:server:myserver

# Check device status  
curl http://localhost:8080/rest/things/traccar:device:myserver:car1

# Check item states
curl http://localhost:8080/rest/items/Vehicle_Position
curl http://localhost:8080/rest/items/Vehicle_Odometer

# Test Traccar API
curl -u "user:pass" "https://your-traccar/api/devices"
curl -u "user:pass" "https://your-traccar/api/positions?deviceId=1"

# Check webhook endpoint
curl -X POST http://localhost:8090/webhook -d '{"test":true}'

# View recent logs
tail -n 100 /var/log/openhab/openhab.log | grep -i traccar
```

## Support

- **Author**: Nanna Agesen
- **Email**: Nanna@agesen.dk
- **GitHub**: [@Prinsessen](https://github.com/Prinsessen)
- **More Examples**: See [EXAMPLES.md](EXAMPLES.md)

## License

Eclipse Public License 2.0 (EPL-2.0)
