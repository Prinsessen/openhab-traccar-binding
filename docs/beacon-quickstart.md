# BLE Beacon Tracking - Quick Start Guide

**5-Minute Setup** for tracking cargo beacons with Teltonika FMM920 and OpenHAB Traccar binding.

## What You Get

✅ Track up to 4 BLE beacons (bags, cargo, assets)  
✅ Real-time distance, signal strength, battery level  
✅ Alerts when cargo becomes detached  
✅ Temperature/humidity monitoring (if beacon supports it)  
✅ Automatic beacon-to-channel routing by MAC address  

## Prerequisites

- Teltonika FMM920 (or compatible BLE-enabled GPS tracker)
- BLE beacons (any brand: iBeacon, Eddystone, Teltonika EYE, etc.)
- OpenHAB with Traccar binding installed
- Traccar server running

## Step 1: Discover Beacon MACs (2 minutes)

After your FMM920 starts scanning beacons, monitor OpenHAB logs:

```bash
tail -f /var/log/openhab/openhab.log | grep "Found tag.*with MAC"
```

You'll see:
```
[DEBUG] Found tag1 with MAC aabbcc112233
[DEBUG] Found tag2 with MAC aabbcc445566
[DEBUG] Found tag3 with MAC aabbcc778899
```

**Write down these MACs** - you need them for the next step.

## Step 2: Configure Thing (2 minutes)

Edit `conf/things/traccar.things`:

```openhab
Bridge traccar:server:gpsserver "GPS Server" [
    url="https://your-traccar-server.com",
    username="your@email.com",
    password="yourpassword"
] {
    Thing device motorcycle "Motorcycle" [
        deviceId=10,                     // Your device ID from Traccar
        beacon1Mac="aabbcc112233",      // Replace with your MACs
        beacon2Mac="aabbcc445566",
        beacon3Mac="aabbcc778899"
    ]
}
```

**That's it!** The binding will now route beacons consistently by MAC address.

## Step 3: Create Items (1 minute)

Copy this to `conf/items/beacons.items`:

```openhab
// Beacon 1
String Beacon1_Name "Beacon 1 [%s]" 
    {channel="traccar:device:gpsserver:motorcycle:beacon1-name"}
Number:Length Beacon1_Distance "Distance [%.2f m]" 
    {channel="traccar:device:gpsserver:motorcycle:beacon1-distance"}
Number Beacon1_RSSI "Signal [%d dBm]" 
    {channel="traccar:device:gpsserver:motorcycle:beacon1-rssi"}
Number:ElectricPotential Beacon1_Battery "Battery [%.2f V]" 
    {channel="traccar:device:gpsserver:motorcycle:beacon1-battery"}

// Beacon 2
String Beacon2_Name "Beacon 2 [%s]" 
    {channel="traccar:device:gpsserver:motorcycle:beacon2-name"}
Number:Length Beacon2_Distance "Distance [%.2f m]" 
    {channel="traccar:device:gpsserver:motorcycle:beacon2-distance"}

// Beacon 3
String Beacon3_Name "Beacon 3 [%s]" 
    {channel="traccar:device:gpsserver:motorcycle:beacon3-name"}
Number:Length Beacon3_Distance "Distance [%.2f m]" 
    {channel="traccar:device:gpsserver:motorcycle:beacon3-distance"}
```

## Verify It's Working

Check item states:
```bash
# OpenHAB CLI
openhab> openhab:status Beacon1_Name
openhab> openhab:status Beacon1_Distance

# REST API
curl http://localhost:8080/rest/items/Beacon1_Name
```

Watch live updates:
```bash
tail -f /var/log/openhab/events.log | grep Beacon
```

## Next Steps (Optional)

### Add Automation

Create `conf/rules/beacons.rules`:

```openhab
rule "Cargo Detached Alert"
when
    Item Beacon1_Distance changed
then
    val distance = (Beacon1_Distance.state as QuantityType<Length>).toUnit("m").doubleValue()
    
    if (distance > 10.0) {
        logWarn("Cargo", "⚠️ Beacon 1 is {} meters away!", distance)
        sendNotification("you@example.com", "Cargo may be detached!")
    }
end

rule "Low Battery Alert"
when
    Item Beacon1_Battery changed
then
    val voltage = (Beacon1_Battery.state as QuantityType<ElectricPotential>).toUnit("V").doubleValue()
    
    if (voltage < 2.5) {
        logWarn("Battery", "Low battery on Beacon 1: {} V", voltage)
        sendNotification("you@example.com", "Replace beacon battery soon!")
    }
end
```

### Add UI (Sitemap)

Create `conf/sitemaps/beacons.sitemap`:

```openhab
sitemap beacons label="Cargo Tracking" {
    Frame label="Beacons" {
        Text item=Beacon1_Name icon="bag"
        Text item=Beacon1_Distance icon="distance"
            valuecolor=[Beacon1_Distance<2="green", Beacon1_Distance<5="orange", ="red"]
        Text item=Beacon1_RSSI icon="signal"
        Text item=Beacon1_Battery icon="battery"
        
        Text item=Beacon2_Name icon="bag"
        Text item=Beacon2_Distance icon="distance"
        
        Text item=Beacon3_Name icon="bag"
        Text item=Beacon3_Distance icon="distance"
    }
}
```

## Troubleshooting

**No beacon data?**
- Check FMM920 has BLE enabled in Teltonika Configurator
- Verify beacons are added to FMM920 beacon list
- Check logs: `grep "Found tag" /var/log/openhab/openhab.log | tail -20`

**Data shuffling between beacons?**
- Ensure you configured `beacon1Mac`, `beacon2Mac` in thing config
- MACs must be lowercase without colons: `aabbcc112233` ✓, not `AA:BB:CC:11:22:33` ✗

**Distance seems wrong?**
- Adjust thing config:
  ```openhab
  Thing device motorcycle [
      deviceId=10,
      beaconTxPower=-62,     // Try -59 to -65
      beaconPathLoss=2.5     // Try 2.0 (outdoor) to 4.0 (indoor)
  ]
  ```

## More Information

- **Full Documentation**: See `README.md` for complete channel list and advanced features
- **Complete Example**: See `docs/beacon-tracking-example.md` for full working configuration
- **Support**: Nanna Agesen <Nanna@agesen.dk>

## Why This Works

The FMM920 assigns beacons to `tag1-4` **randomly** based on scan order. Without MAC routing, your "MainBag" could appear as beacon1 one minute and beacon3 the next.

The binding's MAC routing feature maps each beacon's MAC address to a consistent slot (beacon1-4), so your UI and automation work reliably.

**No configuration needed** - it works automatically. But adding MAC configuration ensures specific beacons always go to specific slots (useful for labels and automation).
