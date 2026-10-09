# Changelog

All notable changes to the Traccar binding will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added
- **Teltonika CAN adapter support** (ALL-CAN300 / LV-CAN200 on an FMx6 tracker
  such as the FMC650): with the new device parameter `canAdapter=lvcan` the
  thing gets a `can` channel group - traction battery level, range, odometer,
  speed, pedal, doors, and the security, control and indicator flags (ignition,
  ready, charging cable, charging, locked, gear, lights, belts, warning lamps),
  each value with its own `...Seen` time. Tested on a Toyota bZ4X; the README
  marks every channel tested or untested.
- Device parameters `deviceFamily`, `canAdapter`, `obdDongle` and
  `gearParkWhenOff`. The defaults decode exactly as before.
- The first unit tests (`LvcanDecoderTest`).

### Fixed
- **Records out of order no longer move a device backwards.** A record older
  than the newest one applied is skipped, for every device and every channel.
  Teltonika trackers upload their buffer after a cold boot, and the records of
  one webhook batch are handled on parallel threads: `lastUpdate` went
  backwards, and a car with a CAN adapter read "unlocked, gear N" after its
  "locked, P" record. The order check and the updates of a record now happen
  under one lock. A device clock more than 10 minutes ahead of Traccar's server
  clock does not move the bar, so a wrong clock cannot block later records.

### Changed
- io30-io48 are read as OBD-II data only when the thing has no CAN adapter
  (and `obdDongle` is not `none`): on a CAN adapter the same numbers carry other
  values.
- With `deviceFamily=fmx6`, `batteryLevel` is not filled: Traccar reads it from
  AVL 113, which is Service Distance on that family.

## [1.1.1] - 2026-10-02

### Fixed
- The bundled `examples/vehicle_tracker.html` now uses documentation values
  like the rest of the examples. 1.1.0's documentation pass covered the
  repository, but this file ships *inside* the JAR, and the artifact published
  as 1.1.0 had been built before that pass - so the download still carried the
  old sample names while the source tree did not. If you installed 1.1.0, this
  is the only difference.
- Corrected the stated openHAB compatibility. This binding is built against the
  5.2 add-ons reactor and has only ever been run there. `docs/INDEX.md` and the
  1.1.0 release notes also claimed 4.x, which was never tested and generally
  will not resolve on a 4.x core.
- Beacon examples use one consistent set of kit names throughout, instead of
  two.

### Changed
- The release asset is named for its version,
  `org.openhab.binding.traccar-1.1.1.jar`, rather than carrying the build
  reactor's `5.2.0-SNAPSHOT` name as 1.0.0 and 1.1.0 did. Two downloads called
  the same thing cannot be told apart, which is how the stale 1.1.0 artifact
  went unnoticed. Install it exactly as before - drop it into
  `/usr/share/openhab/addons/`; openHAB does not care what the file is called.

## [1.1.0] - 2026-10-02

### Added
- `raw-attributes` channel on the device thing: every attribute Traccar decoded,
  sorted, as `name=value` pairs. The named channels cover about thirty
  attributes and anything else was never looked at, so this is how a new
  peripheral's signals are identified — from what actually arrives rather than
  from a protocol table. Advanced, read-only, truncated past 4000 characters.

  Added while preparing for a CAN adapter, where the need is sharp: AVL IO
  numbers mean different things per peripheral. `io38` in this binding is
  OBD-II vehicle speed from a Bluetooth dongle, while AVL ID 38 on a CAN
  adapter is Control State Flags — the same number, two unrelated meanings. The
  only safe way to map a new device is to look at what it sends.

### Changed
- **The default Traccar server URL is now `https://traccar.example.com`.** It
  used to be a real private server, both in `TraccarServerConfiguration` and as
  the `<default>` in `thing-types.xml`, so every installation offered a
  stranger's host as its suggested value. Existing things are unaffected; only
  the suggestion changes.

### Fixed
- **Beacon name persistence** (from 2026-01-21, never released): beacon names
  now survive an openHAB restart. They are stored in Thing properties and
  restored, so `beacon-name` keeps showing the last known name even when the
  beacon is out of range or absent from a webhook update.
- Documentation and examples now use documentation values throughout: a
  documentation street address, a city-centre coordinate pair, `350000000000000`
  as the sample device id and `aabbcc112233`-style beacon MACs from the locally
  administered range. Nothing in the examples is copyable as a working
  identifier any more, and nobody has to wonder whether a sample value belongs
  to someone.
- Removed `FMM920_current.txt`, one tracker's own configuration export.
  Useful to its owner, to nobody else.

### Security
- Webhook payload logging moved from INFO to DEBUG. The body is the complete
  Traccar payload and, for a phone-tracked device, includes the device's
  `notificationTokens` — a Firebase push credential — together with its name and
  exact coordinates. At INFO this was written to `openhab.log` on every webhook,
  which for an OsmAnd device is every few seconds. Anyone running this binding
  with webhooks enabled should assume their logs contain those tokens.

## [1.0.0] - 2026-01-17

### Added
- Initial release of Traccar binding for openHAB
- Traccar server bridge thing for connecting to Traccar GPS tracking servers
- Device thing for individual GPS tracked vehicles/devices
- Real-time position tracking with GPS coordinates, speed, and direction
- Comprehensive position channels:
  - `position` - GPS coordinates (latitude, longitude, altitude)
  - `speed` - Configurable speed units (km/h, mph, knots)
  - `course` - Direction/heading in degrees
  - `accuracy` - GPS accuracy in meters
  - `address` - Street address from reverse geocoding
  - `lastUpdate` - Timestamp of last update
- Device status monitoring:
  - `status` - Online/offline/unknown status
  - `batteryLevel` - Battery percentage
  - `motion` - Movement detection
  - `odometer` - Total distance traveled
- Signal strength and connectivity monitoring:
  - `gpsSatellites` - Number of GPS satellites used for positioning
  - `gsmSignal` - GSM/cellular signal strength (percentage)
- Geofencing support with webhooks:
  - `geofenceEvent` - Entry/exit events
  - `geofenceId` - Numeric geofence identifier
  - `geofenceName` - Human-readable geofence name
- Automatic device discovery from Traccar server
- Dual update mechanism:
  - Configurable polling interval (minimum 10 seconds)
  - Webhook support for instant geofence notifications
- Speed unit conversion from Traccar's native knots to km/h, mph, or knots
- Comprehensive documentation with examples
- Support for multiple Traccar servers simultaneously

### Features
- HTTP/HTTPS support for Traccar server connections
- Secure credential handling
- Configurable webhook port (1024-65535)
- Efficient API usage with hybrid polling/webhook approach
- Full support for Traccar 5.x and 6.x
- Compatible with 200+ GPS protocols and 2000+ device models

### Technical Details
- Built for openHAB 5.x
- Uses Jetty for webhook server
- OSGi bundle architecture
- Implements AbstractThingHandlerDiscoveryService for automatic discovery
- RESTful API integration with Traccar

### Author
- Nanna Agesen (Nanna@agesen.dk / @Prinsessen)

## Planned Features
- Support for Traccar commands (send commands to devices)
- Driver behavior analysis channels
- Maintenance tracking and alerts
- Fuel level monitoring (for supported devices)
- Enhanced filtering options for position updates
- Configurable geofence event retention
