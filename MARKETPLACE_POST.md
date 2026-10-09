# Traccar GPS Tracking Binding

This binding integrates a [Traccar](https://www.traccar.org/) server with openHAB: live position, speed and direction for every tracked vehicle or phone, geofence automation, reverse-geocoded street addresses, and -- where the hardware provides it -- the car's own data from a Teltonika CAN adapter, OBD-II engine data and BLE beacon tracking.

Traccar itself speaks over 200 GPS protocols and supports more than 2000 devices, so anything it can see, openHAB can now react to.

## New in 1.2.0

- **CAN adapter support** -- a car with a Teltonika ALL-CAN300 or LV-CAN200 on an FMx6 tracker (FMC650 and relatives) reports its own battery level, range, odometer, speed, doors, locks, gear, lights and warning lamps. Switched on per device with `canAdapter=lvcan`; every other device keeps exactly the channels it had. Tested on a Toyota bZ4X, and the README marks every channel tested or untested.
- **Records out of order no longer move a device backwards** -- for every device, not only cars: Teltonika trackers upload their buffer after a cold boot, and one webhook batch arrives on parallel threads.
- **A thing that lost its channels gets them back** -- replacing the JAR could rebuild a `.things` thing before the channel types existed.
- **Device profiles** -- `deviceFamily`, `canAdapter`, `obdDongle`: the same Teltonika AVL id means different things on different hardware, so a thing says what it is. The defaults decode exactly as 1.1.x did.

## Features

- **Real-time position** -- coordinates, altitude, speed in km/h, mph or knots, course, accuracy, fix validity and the device's protocol
- **Reverse geocoding** -- optional Nominatim lookup giving a formatted street address rather than a pair of numbers
- **Dual update mechanism** -- webhook push for immediate updates, with API polling as a safety net, so a missed webhook costs latency rather than data
- **GPS noise filtering** -- a stationary device stops jittering across the map
- **Distance tracking** -- per-trip and total distance, plus the device's own odometer where it reports one
- **Geofencing** -- enter and exit events with the geofence's id and name, ready to drive rules
- **Device status** -- online/offline, battery level, charge state, GSM signal, operator, satellites in view, HDOP and PDOP
- **CAN bus data** -- traction battery level, range, odometer, speed and pedal, each with the time it was last seen; ignition, ready, gear, charging cable, charging, central locking, doors, trunk, hood, lights, seat belts and warning lamps -- from a Teltonika CAN adapter on an FMx6 tracker
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
    Thing device car "Car" [ deviceId=4 ]
}
```

`deviceId` is Traccar's own **numeric** device id -- a small integer, not the
tracker's IMEI. The IMEI is the device's *identifier* in Traccar (`uniqueId`),
which is what you type in when registering it; the id is what Traccar assigns
afterwards. Log in and open `/api/devices` to see both:

```json
[ { "id": 4, "uniqueId": "350000000000000", "name": "Car" } ]
```

Use the `id`. A 15-digit IMEI will not fit in the integer this parameter takes.

### traccar.items

```
Location             Car_Position       "Position"              { channel="traccar:device:gpsserver:car:position" }
Number:Speed         Car_Speed          "Speed [%.0f %unit%]"   { channel="traccar:device:gpsserver:car:speed" }
String               Car_Address        "Address [%s]"          { channel="traccar:device:gpsserver:car:address" }
Switch               Car_Motion         "Moving"                { channel="traccar:device:gpsserver:car:motion" }
Number:Dimensionless Car_Battery        "Battery [%.0f %%]"     { channel="traccar:device:gpsserver:car:batteryLevel" }
String               Car_Geofence_Event "Geofence [%s]"         { channel="traccar:device:gpsserver:car:geofenceEvent" }
String               Car_Raw            "Raw attributes [%s]"   { channel="traccar:device:gpsserver:car:raw-attributes" }
```

### A rule on arriving home

```javascript
rules.JSRule({
  name: 'Car arrived',
  triggers: [triggers.ItemStateChangeTrigger('Car_Geofence_Event', undefined, 'geofenceEnter')],
  execute: () => { /* open the gate, start the charger, turn on the lights */ }
});
```

### A car with a CAN adapter

```
Thing device car "Car" [ deviceId=4, deviceFamily="fmx6", canAdapter="lvcan", gearParkWhenOff=true ]
```

```
Number:Dimensionless Car_SoC      "Battery [%.0f %%]"           { channel="traccar:device:gpsserver:car:can#batteryLevel" }
DateTime             Car_SoC_Seen "Battery read [%1$tH:%1$tM]"  { channel="traccar:device:gpsserver:car:can#batteryLevelSeen" }
Number:Length        Car_Range    "Range [%.0f km]"             { channel="traccar:device:gpsserver:car:can#range", unit="km" }
Switch               Car_Locked   "Locked [%s]"                 { channel="traccar:device:gpsserver:car:can#locked" }
```

A CAN value keeps its last state when the car stops sending it, so check its
`...Seen` time before a rule acts on it. The
[CAN adapter example](https://github.com/Prinsessen/openhab-traccar-binding/blob/main/docs/can-adapter-example.md)
has the tracker setup, items, a sitemap and JavaScript rules: stop charging at
a target, plug-in reminders, a night check and warning lamps.

## Security note

Webhook payload logging is at DEBUG, not INFO. The payload is everything Traccar
knows about the position **and the device**, which for a phone-tracked device
includes its push-notification tokens alongside its name and exact coordinates.
If you have been running an earlier build with webhooks enabled, assume your
`openhab.log` contains those tokens and let it rotate out.

## Resources

- **Download JAR (v1.2.0):** [org.openhab.binding.traccar-1.2.0.jar](https://github.com/Prinsessen/openhab-traccar-binding/releases/download/v1.2.0/org.openhab.binding.traccar-1.2.0.jar)
- **Source code:** [github.com/Prinsessen/openhab-traccar-binding](https://github.com/Prinsessen/openhab-traccar-binding)
- **Worked examples:** [EXAMPLES.md](https://github.com/Prinsessen/openhab-traccar-binding/blob/main/EXAMPLES.md) — geofencing, presence, speed, battery, multi-vehicle, and finding out what a new device sends
- **Full documentation:** [README.md](https://github.com/Prinsessen/openhab-traccar-binding/blob/main/README.md)
- **BLE beacon quick start:** [docs/beacon-quickstart.md](https://github.com/Prinsessen/openhab-traccar-binding/blob/main/docs/beacon-quickstart.md)
- **CAN adapter example:** [docs/can-adapter-example.md](https://github.com/Prinsessen/openhab-traccar-binding/blob/main/docs/can-adapter-example.md)
- **OBD-II guide:** [OBD-II_IMPLEMENTATION.md](https://github.com/Prinsessen/openhab-traccar-binding/blob/main/OBD-II_IMPLEMENTATION.md)
- **Changelog:** [CHANGELOG.md](https://github.com/Prinsessen/openhab-traccar-binding/blob/main/CHANGELOG.md)
- **License:** EPL-2.0
