# Traccar GPS Tracking Binding

This binding integrates a [Traccar](https://www.traccar.org/) server with openHAB: live position, speed and direction for every tracked vehicle or phone, geofence automation, reverse-geocoded street addresses, and -- where the hardware provides it -- OBD-II engine data and BLE beacon tracking.

Traccar itself speaks over 200 GPS protocols and supports more than 2000 devices, so anything it can see, openHAB can now react to.

## Features

- **Real-time position** -- coordinates, altitude, speed in km/h, mph or knots, course, accuracy, fix validity and the device's protocol
- **Reverse geocoding** -- optional Nominatim lookup giving a formatted street address rather than a pair of numbers
- **Dual update mechanism** -- webhook push for immediate updates, with API polling as a safety net, so a missed webhook costs latency rather than data
- **GPS noise filtering** -- a stationary device stops jittering across the map
- **Distance tracking** -- per-trip and total distance, plus the device's own odometer where it reports one
- **Geofencing** -- enter and exit events with the geofence's id and name, ready to drive rules
- **Device status** -- online/offline, battery level, charge state, GSM signal, operator, satellites in view, HDOP and PDOP
- **OBD-II data** -- engine RPM, coolant temperature, engine load, fuel level and pressure, DTC count and the vehicle's own odometer, from a paired Bluetooth OBD dongle
- **BLE beacon tracking** -- RSSI, distance, temperature, humidity, magnet, motion, pitch and roll from beacons seen by a Teltonika FMM920
- **Raw attributes** -- one diagnostic channel carrying every attribute the server decoded, named or not

## Why the raw-attributes channel exists

A binding can only surface what it names, and this one names about thirty attributes out of however many a given device sends. Anything else arrives and is never looked at.

That is a problem the first time you fit an unfamiliar peripheral, because Teltonika's AVL IO numbers are **per-peripheral**: `io38` from a Bluetooth OBD dongle is vehicle speed, while AVL ID 38 from a CAN adapter is Control State Flags. The same number, two unrelated meanings, and no protocol table will tell you which one your device is using.

So `raw-attributes` shows the lot, sorted, as `name=value` pairs. Fit the hardware, read the channel, find out what actually arrives, and only then decide what deserves a channel of its own. It is an advanced channel, read-only, and truncated past 4000 characters so one chatty device cannot fill an item.

## Requirements

- A Traccar server, cloud or self-hosted, reachable from openHAB
- An account on it with API access
- For webhook push: a port openHAB can listen on, and Traccar configured to forward to it
- openHAB 5.x. Built against the 5.2 add-ons reactor and running there; it has
  not been tried on 4.x, and an add-on built against 5.2 generally will not
  resolve on an older core

## Supported Things

- **`server`** (Bridge) -- a Traccar server instance
- **`device`** -- one tracked vehicle, phone or asset on that server

## Quick Start

### traccar.things

```
Bridge traccar:server:gpsserver "Traccar Server" [
    url="https://your-traccar-server",
    username="you@example.com",
    password="...",
    refreshInterval=30,
    webhookPort=8088
] {
    Thing device car "Car" [ deviceId="350000000000000" ]
}
```

`deviceId` is the device's unique id in Traccar -- usually the tracker's IMEI, or whatever identifier the app registered with.

### traccar.items

```
Location Car_Position  "Position"            { channel="traccar:device:gpsserver:car:position" }
Number:Speed Car_Speed "Speed [%.0f %unit%]" { channel="traccar:device:gpsserver:car:speed" }
String   Car_Address   "Address [%s]"        { channel="traccar:device:gpsserver:car:address" }
Switch   Car_Motion    "Moving"              { channel="traccar:device:gpsserver:car:motion" }
Number   Car_Battery   "Battery [%d %%]"     { channel="traccar:device:gpsserver:car:battery-level" }
String   Car_Raw       "Raw attributes [%s]" { channel="traccar:device:gpsserver:car:raw-attributes" }
```

### A rule on arriving home

```javascript
rules.JSRule({
  name: 'Car arrived',
  triggers: [triggers.ItemStateChangeTrigger('Car_Geofence_Event', undefined, 'enter')],
  execute: () => { /* open the gate, start the charger, turn on the lights */ }
});
```

## Security note

Webhook payload logging is at DEBUG, not INFO. The payload is everything Traccar
knows about the position **and the device**, which for a phone-tracked device
includes its push-notification tokens alongside its name and exact coordinates.
If you have been running an earlier build with webhooks enabled, assume your
`openhab.log` contains those tokens and let it rotate out.

## Resources

- **Download JAR (v1.1.1):** [org.openhab.binding.traccar-1.1.1.jar](https://github.com/Prinsessen/openhab-traccar-binding/releases/download/v1.1.1/org.openhab.binding.traccar-1.1.1.jar)
- **Source code:** [github.com/Prinsessen/openhab-traccar-binding](https://github.com/Prinsessen/openhab-traccar-binding)
- **Worked examples:** [EXAMPLES.md](https://github.com/Prinsessen/openhab-traccar-binding/blob/main/EXAMPLES.md) — geofencing, presence, speed, battery, multi-vehicle, and finding out what a new device sends
- **Full documentation:** [README.md](https://github.com/Prinsessen/openhab-traccar-binding/blob/main/README.md)
- **BLE beacon quick start:** [docs/beacon-quickstart.md](https://github.com/Prinsessen/openhab-traccar-binding/blob/main/docs/beacon-quickstart.md)
- **OBD-II guide:** [OBD-II_IMPLEMENTATION.md](https://github.com/Prinsessen/openhab-traccar-binding/blob/main/OBD-II_IMPLEMENTATION.md)
- **Changelog:** [CHANGELOG.md](https://github.com/Prinsessen/openhab-traccar-binding/blob/main/CHANGELOG.md)
- **License:** EPL-2.0
