# Traccar Binding - BLE Beacon Documentation Index

## Quick Access

| Document | Purpose | Audience | Lines |
|----------|---------|----------|-------|
| **[beacon-quickstart.md](beacon-quickstart.md)** | 5-minute setup guide | End users (quick start) | 190 |
| **[beacon-tracking-example.md](beacon-tracking-example.md)** | Complete working configuration | End users (detailed) | 625 |
| **[can-adapter-example.md](can-adapter-example.md)** | CAN adapter: complete example with JS rules | End users (cars with a CAN adapter) | 401 |
| **[DEVELOPER-REFERENCE.md](DEVELOPER-REFERENCE.md)** | Technical reference card | Developers | 317 |
| **[BEACON-DOCUMENTATION-SUMMARY.md](BEACON-DOCUMENTATION-SUMMARY.md)** | Implementation overview | Maintainers | 351 |
| **[../README.md](../README.md)** | Main binding documentation | All users | 1818 |
| **[../MARKETPLACE_POST.md](../MARKETPLACE_POST.md)** | Community forum post | Maintainers (publishing) | 102 |

## Documentation Hierarchy

```
Traccar Binding Docs
├── README.md (Main reference - all features)
│   ├── Channels → BLE Beacon Tracking section (~600 lines)
│   ├── Device Configuration with beacon parameters
│   ├── Complete beacon examples (things, items, rules, sitemap)
│   └── Troubleshooting guide
│
├── docs/beacon-quickstart.md (5-minute quick start)
│   ├── 3-step setup process
│   ├── Minimal working example
│   └── Quick troubleshooting
│
├── docs/beacon-tracking-example.md (Complete example)
│   ├── Full 7-step walkthrough
│   ├── Copy-paste ready configuration
│   ├── 5 automation rules
│   ├── Sitemap with color coding
│   └── Advanced configurations
│
├── docs/DEVELOPER-REFERENCE.md (Developer guide)
│   ├── Architecture overview
│   ├── Key classes and methods
│   ├── Data flow diagrams
│   ├── Testing procedures
│   └── Common issues and solutions
│
└── docs/BEACON-DOCUMENTATION-SUMMARY.md (Meta documentation)
    ├── What was implemented
    ├── Documentation statistics
    ├── Code changes summary
    └── Maintenance notes
```

## Use Cases → Documentation Mapping

### "I want to track bags on my motorcycle"
→ Start with **beacon-quickstart.md** (5 minutes)

### "I need a complete working example"
→ Use **beacon-tracking-example.md** (step-by-step guide)

### "What channels are available?"
→ Check **README.md** → Channels → BLE Beacon Tracking

### "How do I configure MAC routing?"
→ See **README.md** → MAC Address Routing section

### "Beacon data keeps shuffling"
→ Troubleshooting in **README.md** or **beacon-tracking-example.md**

### "I'm developing/modifying the binding"
→ Read **DEVELOPER-REFERENCE.md**

### "What was implemented and why?"
→ See **BEACON-DOCUMENTATION-SUMMARY.md**

## Feature Coverage

### Documented Features

✅ MAC address routing (prevents beacon shuffling)  
✅ Distance calculation with tunable parameters  
✅ All 8 channels per beacon (×4 beacon groups = 32 channels)  
✅ Automatic beacon assignment for unconfigured MACs  
✅ Stale data handling (name clears to UNDEF)  
✅ Temperature/humidity support  
✅ Low battery detection  
✅ Real-world automation examples:
  - Cargo detachment alerts
  - Battery warnings
  - Temperature monitoring
  - Startup presence check
  - Weak signal detection

### Configuration Examples

✅ Thing configuration with MAC routing  
✅ Complete items file (all channels)  
✅ Automation rules (5 examples)  
✅ Sitemap with color-coded UI  
✅ Testing procedures  
✅ Troubleshooting steps  

## Documentation Statistics

| Metric | Value |
|--------|-------|
| Total documentation lines | 3,301 |
| README beacon section | ~600 lines |
| Beacon-specific docs | 1,483 lines |
| Code examples | 50+ blocks |
| Configuration examples | 15+ |
| Troubleshooting topics | 7 |
| Automation rule examples | 5 |

## Learning Path

### For End Users

1. **Start here**: beacon-quickstart.md (5 min)
2. **If you want more**: beacon-tracking-example.md (30 min)
3. **Reference**: README.md → BLE Beacon Tracking section
4. **Problems?**: Troubleshooting sections in any doc

### For Developers

1. **Understand feature**: BEACON-DOCUMENTATION-SUMMARY.md
2. **Technical details**: DEVELOPER-REFERENCE.md
3. **Code reference**: TraccarDeviceHandler.java
4. **User perspective**: beacon-tracking-example.md (see what users expect)

### For Maintainers

1. **Implementation overview**: BEACON-DOCUMENTATION-SUMMARY.md
2. **Keep docs synced**: Check "Maintenance Notes" section
3. **User feedback**: Review examples in beacon-tracking-example.md
4. **Code quality**: Follow patterns in DEVELOPER-REFERENCE.md

## File Locations

```
openhab-addons/bundles/org.openhab.binding.traccar/
├── README.md (main documentation)
├── docs/
│   ├── beacon-quickstart.md
│   ├── beacon-tracking-example.md
│   ├── DEVELOPER-REFERENCE.md
│   ├── BEACON-DOCUMENTATION-SUMMARY.md
│   └── INDEX.md (this file)
├── src/main/java/.../TraccarDeviceHandler.java
└── src/main/resources/OH-INF/thing/thing-types.xml
```

## Key Concepts

### MAC Address Routing
Teltonika FMM920 assigns beacons to tag1-4 dynamically. The binding maps each beacon's MAC address to a consistent channel slot (beacon1-4), preventing data shuffling.

### Distance Calculation
Uses log-distance path loss model with configurable Tx power and path loss exponent. Users can tune for their environment (outdoor vs indoor).

### Automatic Assignment
Unconfigured beacon MACs automatically assign to first available slot and remain consistent. Configuration is optional but recommended for labeled beacons.

### Stale Data Prevention
Name channel explicitly clears to UNDEF when beacon doesn't send a name, preventing confusion from old data.

## Version Information

- **Binding Version**: 5.2.0-SNAPSHOT
- **Documentation Created**: January 20, 2026
- **Author**: Nanna Agesen <Nanna@agesen.dk>
- **OpenHAB Version**: 5.x (built against the 5.2 add-ons reactor; not tried on 4.x)

## Support & Contributing

- **Issues**: Report via GitHub or community forum
- **Questions**: nanna@agesen.dk or OpenHAB community
- **Contributions**: Follow patterns in existing documentation
- **Testing**: Use examples in beacon-tracking-example.md

## Related Documentation

- **Main README**: Complete binding features beyond beacons
- **Traccar Documentation**: https://www.traccar.org/documentation/
- **Teltonika FMM920 Manual**: BLE beacon configuration
- **OpenHAB Units**: JSR-385 measurement types

## Recent Updates

- **2026-01-20**: Complete beacon documentation added (1,483 lines)
  - Quick start guide
  - Complete working example
  - Developer reference
  - Implementation summary
  - Main README beacon section

## Next Steps

After reading appropriate documentation:

1. Configure FMM920 for BLE scanning
2. Discover your beacon MAC addresses
3. Follow quick start or complete example
4. Test with your setup
5. Customize automation rules
6. Report any issues or improvements

---

**Pro Tip**: Most users should start with beacon-quickstart.md and upgrade to beacon-tracking-example.md only if they need detailed automation examples. README.md is the comprehensive reference for all features.
