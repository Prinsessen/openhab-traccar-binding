# CAN Adapter - Complete Example

A working setup for a car with a Teltonika CAN adapter (ALL-CAN300 or LV-CAN200)
on an FMx6-family tracker (FMC650 and relatives): the thing, the items, a
sitemap, and JavaScript rules (openHAB's JS Scripting add-on, GraalJS) for the
things people actually do with the data.

Every name and value here is made up. The channel reference - every channel,
which ones are tested, and how the decoding behaves - is in the README under
[CAN Adapter](../README.md#can-adapter-teltonika-all-can300--lv-can200).

**Contents**

1. [Before you start: the tracker](#1-before-you-start-the-tracker)
2. [Thing](#2-thing)
3. [Items](#3-items)
4. [Sitemap](#4-sitemap)
5. [Freshness: when is a value current?](#5-freshness-when-is-a-value-current)
6. [Rule: stop charging at a target battery level](#6-rule-stop-charging-at-a-target-battery-level)
7. [Rule: plugged in but not charging](#7-rule-plugged-in-but-not-charging)
8. [Rule: evening reminder to plug in](#8-rule-evening-reminder-to-plug-in)
9. [Rule: night check - locked, doors shut, lights off](#9-rule-night-check---locked-doors-shut-lights-off)
10. [Rule: warning lamps](#10-rule-warning-lamps)
11. [Rule: the car's own speed, with GPS as fallback](#11-rule-the-cars-own-speed-with-gps-as-fallback)
12. [Troubleshooting](#12-troubleshooting)

---

## 1. Before you start: the tracker

The binding only decodes what the tracker sends. In the Teltonika Configurator:

- **CAN adapter port (COM1) baud rate: "Default".** With 115200 the adapter reads
  the car and the tracker never hears it.
- **Enable the LVCAN values** you want (battery level, range, mileage, speed,
  pedal, door status) and the **P4 state flags** - security (12710), control
  (12711), indicator (12712). The P4 flags are separate I/O elements and are
  off by default.
- **Ignition source.** The tracker only reads the car while its own ignition is
  on. On an electric car, power voltage alone is not enough: with the 12 V
  battery full the DC-DC converter can hold it at float (about 12.5-12.8 V) even
  while driving. Use power voltage + movement (parameter 101 = 48), or wire a
  digital input to a fuse that is live only while the car is on.
- **Never enable "Send data with 0, if ignition is off"**: it sends zeros, which
  read as a battery at 0 %.

The README's section "Setting up the tracker" has the rest (movement source,
the accelerometer's false Traccar alarm, constant I/O elements).

## 2. Thing

```openhab
Bridge traccar:server:myserver "Traccar" [
    url="https://traccar.example.com", username="user@example.com", password="secret" ] {

    Thing device car "Car" [
        deviceId=2,                 // Traccar's device id, not the IMEI
        deviceFamily="fmx6",        // FMC650 and relatives
        canAdapter="lvcan",         // adds the can# channel group
        gearParkWhenOff=true        // an EV or automatic: not ready = parked (P, 0 km/h)
    ]
}
```

With `canAdapter="lvcan"` the thing gets the `can` channel group when it
initializes - the log says `Device 2: 60 CAN channel(s) added (canAdapter=lvcan)`.
The OBD-II reading of io30-io48 is switched off for this thing, because the
adapter uses the same numbers for other values.

## 3. Items

The channels send base units: range and odometer arrive in **metres**, so give
those items `unit="km"`.

```openhab
Group gCar "Car"

// values, each with the time it was last seen
Number:Dimensionless Car_Battery        "Battery [%.0f %%]"            (gCar) { channel="traccar:device:myserver:car:can#batteryLevel", unit="%" }
DateTime             Car_Battery_Seen   "Battery read [%1$tH:%1$tM]"   (gCar) { channel="traccar:device:myserver:car:can#batteryLevelSeen" }
Number:Length        Car_Range          "Range [%.0f km]"              (gCar) { channel="traccar:device:myserver:car:can#range", unit="km" }
Number:Length        Car_Odometer       "Odometer [%.0f km]"           (gCar) { channel="traccar:device:myserver:car:can#odometer", unit="km" }
Number:Speed         Car_Speed          "Speed [%.0f km/h]"            (gCar) { channel="traccar:device:myserver:car:can#speed" }
DateTime             Car_Speed_Seen     "Speed read [%1$tH:%1$tM:%1$tS]" (gCar) { channel="traccar:device:myserver:car:can#speedSeen" }
DateTime             Car_CAN_LastData   "Last CAN data [%1$tH:%1$tM]"  (gCar) { channel="traccar:device:myserver:car:can#lastData" }

// state
Switch               Car_Ready          "Ready to drive [%s]"          (gCar) { channel="traccar:device:myserver:car:can#ready" }
String               Car_Gear           "Gear [%s]"                    (gCar) { channel="traccar:device:myserver:car:can#gear" }
Switch               Car_Locked         "Locked [%s]"                  (gCar) { channel="traccar:device:myserver:car:can#locked" }
Switch               Car_Cable          "Charging cable [%s]"          (gCar) { channel="traccar:device:myserver:car:can#chargeCable" }
Switch               Car_Charging       "Charging [%s]"                (gCar) { channel="traccar:device:myserver:car:can#charging" }

// doors
Group:Contact:OR(OPEN, CLOSED) gCarDoors "Doors [%s]" (gCar)
Contact Car_Door_FL "Front left [%s]"  (gCarDoors) { channel="traccar:device:myserver:car:can#doorFrontLeft" }
Contact Car_Door_FR "Front right [%s]" (gCarDoors) { channel="traccar:device:myserver:car:can#doorFrontRight" }
Contact Car_Door_RL "Rear left [%s]"   (gCarDoors) { channel="traccar:device:myserver:car:can#doorRearLeft" }
Contact Car_Door_RR "Rear right [%s]"  (gCarDoors) { channel="traccar:device:myserver:car:can#doorRearRight" }
Contact Car_Trunk   "Trunk [%s]"       (gCarDoors) { channel="traccar:device:myserver:car:can#trunk" }
Contact Car_Hood    "Hood [%s]"        (gCarDoors) { channel="traccar:device:myserver:car:can#hood" }

// lights
Switch Car_Sidelights "Sidelights [%s]"        (gCar) { channel="traccar:device:myserver:car:can#sidelights" }
Switch Car_DippedBeam "Dipped headlights [%s]" (gCar) { channel="traccar:device:myserver:car:can#dippedBeam" }

// warning lamps
Group:Switch:OR(ON, OFF) gCarLamps "Warning lamps [%s]" (gCar)
Switch Car_Lamp_Abs      "ABS [%s]"            (gCarLamps) { channel="traccar:device:myserver:car:can#lampAbs" }
Switch Car_Lamp_Esp      "ESP [%s]"            (gCarLamps) { channel="traccar:device:myserver:car:can#lampEsp" }
Switch Car_Lamp_Brake    "Brake system [%s]"   (gCarLamps) { channel="traccar:device:myserver:car:can#lampBrake" }
Switch Car_Lamp_Airbag   "Airbag [%s]"         (gCarLamps) { channel="traccar:device:myserver:car:can#lampAirbag" }
Switch Car_Lamp_Steering "Power steering [%s]" (gCarLamps) { channel="traccar:device:myserver:car:can#lampSteering" }
Switch Car_Lamp_Tyre     "Tyre pressure [%s]"  (gCarLamps) { channel="traccar:device:myserver:car:can#lampTyre" }

// for the rules below: something that can stop the charger, and a target
Switch               Charger_Allow      "Charger allowed [%s]"
Number:Dimensionless Car_Target_Battery "Charge to [%.0f %%]" { unit="%" }
```

`Charger_Allow` stands for whatever your charger binding offers to pause a
session (go-e, Easee, Zaptec, a smart plug ...).

## 4. Sitemap

```openhab
sitemap car label="Car" {
    Frame label="Battery" {
        Text item=Car_Battery valuecolor=[<20="red", <40="orange", >=40="green"]
        Text item=Car_Battery_Seen
        Text item=Car_Range
        Text item=Car_Cable
        Text item=Car_Charging visibility=[Car_Cable==ON]
        Setpoint item=Car_Target_Battery minValue=50 maxValue=100 step=5
    }
    Frame label="State" {
        Text item=Car_Ready
        Text item=Car_Gear
        Text item=Car_Speed visibility=[Car_Ready==ON]
        Text item=Car_Locked valuecolor=[ON="green", OFF="red"]
        Group item=gCarDoors valuecolor=[OPEN="red", CLOSED="green"]
        Group item=gCarLamps valuecolor=[ON="red", OFF="green"]
        Text item=Car_Odometer
        Text item=Car_CAN_LastData
    }
}
```

## 5. Freshness: when is a value current?

A CAN value keeps its last state when it stops arriving - the binding never
turns a missing value into 0 (`gearParkWhenOff` is the one, opt-in exception).
Range, odometer, speed and pedal only come while the car is ready to drive; the
battery level and the flags also come while it charges, and the battery level
can still pause for half an hour near the end of a charge. With the tracker's
ignition off nothing comes at all.

So before a rule acts on a value, it asks the value's own `...Seen` channel how
old it is. All rules below use this helper; put it at the top of the same file,
or in a module of your own.

```javascript
const { items, time } = require('openhab');

// minutes since a DateTime item, or Infinity when it has no time yet
function ageMinutes(name) {
  const item = items.getItem(name);
  if (item.isUninitialized) return Infinity;
  return time.Duration.between(time.toZDT(item), time.ZonedDateTime.now()).toMinutes();
}
```

How old is too old depends on the tracker's send period: with records sent every
10 s while driving and every 150 s while standing, a working link is never more
than about three minutes old. Five to ten minutes is a sensible limit.

## 6. Rule: stop charging at a target battery level

The car's own battery level, live during the charge, stops the charger at the
target. A stale reading never ends a session.

```javascript
const { rules, triggers, items, time } = require('openhab');

function ageMinutes(name) {
  const item = items.getItem(name);
  if (item.isUninitialized) return Infinity;
  return time.Duration.between(time.toZDT(item), time.ZonedDateTime.now()).toMinutes();
}

rules.JSRule({
  name: 'Car - stop charging at the target',
  triggers: [
    triggers.ItemStateChangeTrigger('Car_Battery'),
    triggers.ItemStateChangeTrigger('Car_Target_Battery')
  ],
  execute: () => {
    const battery = items.getItem('Car_Battery').numericState;
    const target = items.getItem('Car_Target_Battery').numericState;
    if (battery === null || target === null || target >= 100) return;
    if (ageMinutes('Car_Battery_Seen') > 10) return;          // not current: do nothing
    if (battery >= target && items.getItem('Charger_Allow').state === 'ON') {
      console.info('car: battery ' + battery + ' % reached the target ' + target + ' % - charger paused');
      items.getItem('Charger_Allow').sendCommand('OFF');
    }
  }
});
```

Turn `Charger_Allow` back on when the cable is pulled or the target is raised -
or leave that to the charger binding's own logic.

## 7. Rule: plugged in but not charging

A cable that is in while nothing flows for a quarter of an hour - a tripped
charger, a schedule that never started, a car that refused the session.

```javascript
const { rules, triggers, items, actions, time } = require('openhab');

let timer = null;

rules.JSRule({
  name: 'Car - plugged in but not charging',
  triggers: [
    triggers.ItemStateChangeTrigger('Car_Cable'),
    triggers.ItemStateChangeTrigger('Car_Charging')
  ],
  execute: () => {
    const plugged = items.getItem('Car_Cable').state === 'ON';
    const charging = items.getItem('Car_Charging').state === 'ON';
    if (timer !== null) { timer.cancel(); timer = null; }
    if (!plugged || charging) return;
    timer = actions.ScriptExecution.createTimer(time.ZonedDateTime.now().plusMinutes(15), () => {
      timer = null;
      if (items.getItem('Car_Cable').state === 'ON' && items.getItem('Car_Charging').state !== 'ON') {
        actions.notificationBuilder('The car is plugged in but has not charged for 15 minutes').send();
      }
    });
  }
});
```

The car's charging flag may switch off before the very end of a charge (on the
tested car it went off at 98 % while power still flowed). If your charger
binding reports its own state, prefer that for "charging" and keep the car's
flag for "cable".

## 8. Rule: evening reminder to plug in

At 21:00: battery below a threshold and no cable. Uses the battery level only
if it is from today.

```javascript
const { rules, triggers, items, actions, time } = require('openhab');

function ageMinutes(name) {
  const item = items.getItem(name);
  if (item.isUninitialized) return Infinity;
  return time.Duration.between(time.toZDT(item), time.ZonedDateTime.now()).toMinutes();
}

rules.JSRule({
  name: 'Car - evening plug-in reminder',
  triggers: [triggers.GenericCronTrigger('0 0 21 * * ?')],
  execute: () => {
    const battery = items.getItem('Car_Battery').numericState;
    if (battery === null || ageMinutes('Car_Battery_Seen') > 12 * 60) return;
    if (battery < 40 && items.getItem('Car_Cable').state !== 'ON') {
      actions.notificationBuilder('Car at ' + Math.round(battery) + ' % and not plugged in').send();
    }
  }
});
```

If the car is often away overnight, add a presence condition (a geofence
channel of the same thing, for example).

## 9. Rule: night check - locked, doors shut, lights off

At 23:00, only on current data: a car that has been asleep since the
afternoon reports its last state, which is usually right, but say so.

```javascript
const { rules, triggers, items, actions, time } = require('openhab');

function ageMinutes(name) {
  const item = items.getItem(name);
  if (item.isUninitialized) return Infinity;
  return time.Duration.between(time.toZDT(item), time.ZonedDateTime.now()).toMinutes();
}

rules.JSRule({
  name: 'Car - night check',
  triggers: [triggers.GenericCronTrigger('0 0 23 * * ?')],
  execute: () => {
    const problems = [];
    if (items.getItem('Car_Locked').state !== 'ON') problems.push('not locked');
    if (items.getItem('gCarDoors').state === 'OPEN') problems.push('a door, the trunk or the hood is open');
    if (items.getItem('Car_Sidelights').state === 'ON' || items.getItem('Car_DippedBeam').state === 'ON') {
      problems.push('lights on');
    }
    if (problems.length === 0) return;
    const age = ageMinutes('Car_CAN_LastData');
    const when = age === Infinity ? 'no CAN data yet' : 'as of ' + age + ' min ago';
    actions.notificationBuilder('Car: ' + problems.join(', ') + ' (' + when + ')').send();
  }
});
```

## 10. Rule: warning lamps

A warning lamp that stays lit. Lamps flash for about a second at start-up (the
lamp test), so wait before telling anyone.

```javascript
const { rules, triggers, items, actions, time } = require('openhab');

let timer = null;

rules.JSRule({
  name: 'Car - warning lamp',
  triggers: [triggers.ItemStateChangeTrigger('gCarLamps')],
  execute: (event) => {
    if (timer !== null) { timer.cancel(); timer = null; }
    if (event.newState !== 'ON') return;
    timer = actions.ScriptExecution.createTimer(time.ZonedDateTime.now().plusSeconds(30), () => {
      timer = null;
      const lit = items.getItem('gCarLamps').members.filter((i) => i.state === 'ON').map((i) => i.label);
      if (lit.length > 0) actions.notificationBuilder('Car warning lamp: ' + lit.join(', ')).send();
    });
  }
});
```

Only the airbag lamp's start-up test has been seen on the tested car; the other
lamps are decoded as Teltonika documents them.

## 11. Rule: the car's own speed, with GPS as fallback

One speed item for dashboards: the car's own while it is current, the tracker's
GPS speed otherwise. The same pattern works for any value that has a second
source - a manufacturer's cloud binding for battery level, for instance.

```javascript
const { rules, triggers, items, time } = require('openhab');

function ageMinutes(name) {
  const item = items.getItem(name);
  if (item.isUninitialized) return Infinity;
  return time.Duration.between(time.toZDT(item), time.ZonedDateTime.now()).toMinutes();
}

// Number:Speed Car_Speed_Best "Speed [%.0f km/h]"  - no channel; this rule fills it
// Number:Speed Car_GPS_Speed  "GPS speed"          { channel="traccar:device:myserver:car:speed" }

rules.JSRule({
  name: 'Car - best speed',
  triggers: [
    triggers.ItemStateChangeTrigger('Car_Speed'),
    triggers.ItemStateChangeTrigger('Car_Speed_Seen'),
    triggers.ItemStateChangeTrigger('Car_GPS_Speed'),
    triggers.GenericCronTrigger('0 * * * * ?')
  ],
  execute: () => {
    const fromCar = ageMinutes('Car_Speed_Seen') <= 5;
    const source = fromCar ? 'Car_Speed' : 'Car_GPS_Speed';
    const speed = items.getItem(source).numericState;
    if (speed !== null) items.getItem('Car_Speed_Best').postUpdate(speed + ' km/h');
  }
});
```

With `gearParkWhenOff=true` the car's speed is set to 0 as soon as a record
says the car is not ready, so a parked car does not keep the last speed it
reported while rolling into its space.

## 12. Troubleshooting

**No `can#` channels on the thing.** Check `canAdapter="lvcan"` and look for
`CAN channel(s) added` in the log after the thing initialized.

**Channels exist, but stay NULL.** The tracker sends no LVCAN fields. Link the
`raw-attributes` channel and look for `io142`, `io12710` and friends. None at
all: check the COM1 baud rate, the adapter's program number for your car, and
that the elements are enabled. Some but not others: the car does not send them -
the README marks what one car sends.

**Values freeze while driving.** The tracker's ignition went off - see the
ignition source in section 1. The `ignition` channel of the thing (not
`can#ignition`) shows the tracker's own view.

**Values arrive in bursts.** That is the tracker's send period, not the binding:
records are collected and sent every N seconds. Teltonika's defaults are 120 s;
lower the "Moving" send period if you want live speed.

**A value looks old.** Look at its `...Seen` channel. Range, odometer and speed
only come while the car is ready to drive; that is the car, not a fault.

**Range shows 217000.** The item is a plain `Number`. Make it `Number:Length`
with `unit="km"`.
