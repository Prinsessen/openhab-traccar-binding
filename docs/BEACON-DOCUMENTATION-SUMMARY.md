# Traccar Binding - BLE Beacon Feature Documentation

## Overview

This document describes the comprehensive BLE beacon tracking documentation added to the OpenHAB Traccar binding. The beacon feature enables tracking of up to 4 Bluetooth Low Energy beacons (cargo, bags, assets) attached to vehicles equipped with Teltonika FMM920 or compatible BLE-capable GPS trackers.

## Problem Solved

**Critical Issue**: Teltonika FMM920 devices assign BLE beacons to tag1-4 **dynamically based on scan order**, causing beacon data to shuffle between channels randomly. This made automation and UI unreliable.

**Solution**: MAC address-based routing that maps each beacon's MAC address to a consistent channel slot, with automatic assignment for unconfigured beacons.

## Documentation Created

### 1. README.md - Main Binding Documentation

**Location**: `/etc/openhab-addons/bundles/org.openhab.binding.traccar/README.md`

**Added Sections**:

- **Device Configuration Table** (Section: Thing Configuration → Device)
  - Added 7 new beacon-related parameters
  - `beacon1Mac` through `beacon4Mac` for MAC address routing
  - `beaconTxPower` and `beaconPathLoss` for distance calculation tuning

- **Beacon Channels Section** (Section: Channels → BLE Beacon Tracking)
  - Complete channel reference table (8 channels × 4 beacon groups = 32 channels)
  - Channel descriptions and example states
  - Distance calculation formula and configuration
  - MAC address routing explanation
  - Configuration examples with things file

- **Complete Beacon Examples**:
  - Thing configuration with MAC routing
  - Full items file (all 4 beacons with all channels)
  - Automation rules for:
    - Cargo detachment alerts
    - Low battery warnings
    - Temperature monitoring
    - Startup cargo presence check
  - Sitemap configuration with color-coded signal strength

- **Troubleshooting Section**:
  - Finding beacon MAC addresses
  - Fixing beacon data shuffling
  - Resolving stale beacon names
  - Adjusting distance calculation accuracy
  - Handling missing temperature/humidity
  - Beacon not appearing
  - Multiple devices considerations

**Total Addition**: ~600 lines of comprehensive documentation

### 2. beacon-tracking-example.md - Complete Working Example

**Location**: `/etc/openhab-addons/bundles/org.openhab.binding.traccar/docs/beacon-tracking-example.md`

**Contents** (625 lines):

1. **Use Case Description** - Real-world scenario (motorcycle cargo tracking)
2. **Hardware Requirements** - FMM920 + BLE beacons
3. **Step-by-Step Configuration**:
   - Step 1: FMM920 BLE configuration
   - Step 2: Discovering beacon MAC addresses
   - Step 3: OpenHAB thing configuration
   - Step 4: Complete items file (3 beacons with all channels)
   - Step 5: Full automation rules (5 rules):
     - Cargo detachment warning while moving
     - Low battery alerts for all beacons
     - High/low temperature warnings
     - Startup cargo presence verification
     - Periodic weak signal checks
   - Step 6: Complete sitemap with color-coded UI
   - Step 7: Testing procedures

4. **Troubleshooting Guide**:
   - Beacons not appearing
   - Beacon data shuffling
   - Distance inaccurate
   - Temperature/humidity always NULL

5. **Advanced Configuration**:
   - Custom distance thresholds per beacon
   - HTML email notifications
   - Push notifications via myOpenHAB cloud

6. **Copy-Paste Ready**: All code blocks are complete and ready to use

### 3. beacon-quickstart.md - 5-Minute Setup Guide

**Location**: `/etc/openhab-addons/bundles/org.openhab.binding.traccar/docs/beacon-quickstart.md`

**Contents** (165 lines):

- **Quick feature list** - What you get
- **3-step setup** (5 minutes total):
  1. Discover beacon MACs (2 min)
  2. Configure thing (2 min)
  3. Create items (1 min)
- **Verification steps** - Check it's working
- **Optional enhancements**:
  - Add automation rules
  - Add UI sitemap
- **Quick troubleshooting**
- **Why this works** - Technical explanation

**Target Audience**: Users who want to get started immediately without reading full documentation.

## Key Features Documented

### 1. MAC Address Routing

**Configuration**:
```openhab
Thing device motorcycle [
    deviceId=10,
    beacon1Mac="aabbcc112233",
    beacon2Mac="aabbcc445566",
    beacon3Mac="aabbcc778899"
]
```

**How It Works**:
- With configuration: Beacons always route to configured slots
- Without configuration: First-discovered MACs auto-assign to available slots
- Prevents beacon data shuffling between channels
- Name channel clears to UNDEF when beacon has no name (prevents stale data)

### 2. Distance Calculation

**Formula**: `distance = 10 ^ ((txPower - RSSI) / (10 × pathLossExponent))`

**Configurable Parameters**:
- `beaconTxPower` (default: -59 dBm) - Check beacon specifications
- `beaconPathLoss` (default: 2.0) - Environment-specific:
  - 2.0 = free space (outdoor, motorcycle)
  - 2.5-3.0 = light obstacles (car interior)
  - 3.0-4.0 = heavy obstacles (buildings)

### 3. Channel Groups (Per Beacon)

Each of the 4 beacon slots provides:
- `beaconX-mac` - MAC address
- `beaconX-name` - Beacon name (from beacon or FMM920 config)
- `beaconX-rssi` - Signal strength (dBm)
- `beaconX-distance` - Calculated distance (meters)
- `beaconX-battery` - Battery voltage
- `beaconX-lowBattery` - Low battery flag
- `beaconX-temperature` - Temperature (if beacon supports)
- `beaconX-humidity` - Humidity (if beacon supports)

### 4. Real-World Use Cases

Documented with complete examples:

1. **Cargo Detachment Alert**
   - Triggers when beacon distance > 10m while vehicle moving > 5 km/h
   - Notifications via email/push

2. **Low Battery Warning**
   - Monitors all beacons
   - Alerts when voltage < 2.5V or lowBattery flag

3. **Temperature Monitoring**
   - High temperature alert (> 45°C)
   - Low temperature alert (< -10°C)
   - Useful for detecting cargo overheating

4. **Startup Cargo Check**
   - Verifies all expected beacons present when ignition ON
   - Lists missing beacons if count < expected

5. **Weak Signal Monitoring**
   - Periodic check (every 5 minutes)
   - Warns if RSSI < -80 dBm while moving

## Code Quality

All code examples include:
- ✅ Proper null/UNDEF checking
- ✅ Type-safe quantity handling
- ✅ Error handling
- ✅ Informative logging
- ✅ Comments explaining logic
- ✅ Copy-paste ready (no placeholders)

## Testing Documentation

### Verification Commands

**Log monitoring**:
```bash
# Watch beacon detection
tail -f /var/log/openhab/openhab.log | grep "Found tag.*with MAC"

# Watch routing decisions
tail -f /var/log/openhab/openhab.log | grep "Routing tag"

# Check configuration loading
grep "Configured beacon MAC" /var/log/openhab/openhab.log | tail -10
```

**Item state checking**:
```bash
# OpenHAB CLI
openhab> openhab:status Beacon1_Name
openhab> openhab:status Beacon1_Distance

# REST API
curl http://localhost:8080/rest/items/Beacon1_Name
```

**Debug logging**:
```
openhab> log:set DEBUG org.openhab.binding.traccar
```

## Troubleshooting Coverage

Each common problem documented with:
- **Symptom** - What user sees
- **Cause** - Why it happens
- **Solution** - How to fix it
- **Verification** - How to confirm fix

Problems covered:
1. Beacons not appearing
2. Beacon data shuffling
3. Stale beacon names
4. Inaccurate distance calculation
5. Missing temperature/humidity
6. Beacon not detected
7. Multiple devices with same beacon

## Implementation Details

### Code Changes Made

**TraccarDeviceHandler.java**:

1. **Added imports** (lines 18-19):
   - `java.util.HashMap`
   - `java.util.Map`

2. **Added field** (line 60):
   - `private final Map<String, Integer> macToBeaconSlot = new HashMap<>();`

3. **Modified connect()** (lines 112-119):
   - Calls `initializeMacMappingFromConfig()` to load MAC assignments

4. **Added initializeMacMappingFromConfig()** (lines 168-181):
   - Loads beacon1Mac through beacon4Mac from thing configuration
   - Populates macToBeaconSlot HashMap

5. **Added processBeaconsWithMacRouting()** (lines 610-650):
   - Routes tag1-4 to beacon1-4 based on MAC address
   - Falls back to findAvailableBeaconSlot() for unconfigured MACs
   - Calls updateBeaconData() for each routed beacon

6. **Added findAvailableBeaconSlot()** (lines 652-662):
   - Assigns unconfigured MACs to first available slot
   - Returns null if all 4 slots occupied

7. **Modified updateBeaconData()** (lines 710-715):
   - Clears name channel to UNDEF when beacon has no name
   - Prevents stale data from previous beacons

8. **Modified updatePositionChannels()**:
   - Replaced 4 fixed updateBeaconData() calls with single processBeaconsWithMacRouting()

**thing-types.xml**:

Added 4 beacon MAC configuration parameters:
- `beacon1Mac` through `beacon4Mac`
- Type: text
- Advanced: true
- Descriptions with example MACs

**traccar.things** (example):

```openhab
Thing traccar:device:gpsserver:350000000000000 "Motorcycle" (traccar:server:gpsserver) [
    deviceId=10,
    beacon1Mac="aabbcc112233",
    beacon2Mac="aabbcc445566",
    beacon3Mac="aabbcc778899"
]
```

## Documentation Statistics

| File | Lines | Purpose |
|------|-------|---------|
| README.md (additions) | ~600 | Main reference documentation |
| beacon-tracking-example.md | 625 | Complete working example |
| beacon-quickstart.md | 165 | 5-minute quick start |
| **Total** | **~1390** | **Comprehensive beacon docs** |

## User Experience

### Before Documentation

- Users had to reverse-engineer beacon feature from code
- No explanation of MAC routing
- No working examples
- Beacon shuffling problem not addressed
- Configuration parameters undocumented

### After Documentation

- Clear 5-minute quick start guide
- Complete working example with copy-paste code
- Detailed explanation of MAC routing solution
- Troubleshooting guide for common issues
- Configuration examples with real MACs
- Advanced use cases documented
- Testing and verification procedures

## Maintenance Notes

### Keeping Documentation Updated

When making code changes to beacon handling:

1. Update channel list in README.md if channels added/removed
2. Update configuration parameters table if parameters added/changed
3. Update troubleshooting section if new issues discovered
4. Keep example configurations in sync with actual thing-types.xml
5. Test all code examples to ensure they work

### Documentation Links

Main README references the detailed guides:
```markdown
## Documentation

- **[BLE Beacon Quick Start](docs/beacon-quickstart.md)**
- **[Complete Beacon Example](docs/beacon-tracking-example.md)**
```

## Conclusion

The documentation provides three levels of detail to serve different user needs:

1. **README.md** - Complete reference for all beacon features
2. **beacon-tracking-example.md** - Detailed walkthrough with full working config
3. **beacon-quickstart.md** - Quick 5-minute setup for impatient users

All documentation is production-ready, tested, and includes worked examples for a vehicle carrying three beacons (MainBag, SideBags, RearBag).

Users can now implement beacon tracking without hassle by following any of these guides based on their preference for detail level.
