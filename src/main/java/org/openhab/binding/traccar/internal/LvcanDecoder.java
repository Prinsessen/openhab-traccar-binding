/*
 * Copyright (c) 2010-2026 Contributors to the openHAB project
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.openhab.binding.traccar.internal;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import javax.measure.Unit;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.core.library.types.DateTimeType;
import org.openhab.core.library.types.OnOffType;
import org.openhab.core.library.types.OpenClosedType;
import org.openhab.core.library.types.QuantityType;
import org.openhab.core.library.types.StringType;
import org.openhab.core.library.unit.SIUnits;
import org.openhab.core.library.unit.Units;
import org.openhab.core.types.State;

/**
 * Decodes the values and state flags a Teltonika CAN adapter (ALL-CAN300 / LV-CAN200, "LVCAN")
 * hands an FMx6-family tracker (FMC650 and relatives), as Traccar passes them on: one attribute
 * {@code io<AVL id>} per I/O element, numbers as doubles.
 *
 * <p>
 * Pure: attributes in, channel states out. The handler decides whether a record is applied at all
 * (only with {@code canAdapter=lvcan}, and never a record older than the newest one applied).
 *
 * <p>
 * The AVL ids and bit numbers are Teltonika's, not the car's: the adapter's program number selects
 * which car it reads, and it reports every car with the same ids. Which of them a car actually sends
 * differs per car. Tested on one car so far (an electric van); see the README for which fields are
 * confirmed and which are only documented.
 *
 * <p>
 * Rules that the decoding keeps:
 * <ul>
 * <li>A field that is not in the record changes nothing. With the tracker's ignition off it sends
 * records without any LVCAN field, and a value can also stop arriving while the car is busy (the
 * battery level was absent for thirty minutes at the end of a charge). Absent is never zero.</li>
 * <li>Each value carries its own "seen" time - the time of the last record that carried it,
 * changed or not. Range, odometer, speed and pedal only come while the car is ready to drive; the
 * battery level and the flags also come while it charges.</li>
 * <li>Flag words are up to 64 bits: read as {@code long}, never through an int.</li>
 * </ul>
 *
 * @author Nanna Agesen - Initial contribution
 */
@NonNullByDefault
public class LvcanDecoder {

    /** Channel ids inside the {@code can} channel group. */
    public static final String BATTERY_LEVEL = "batteryLevel";
    public static final String RANGE = "range";
    public static final String ODOMETER = "odometer";
    public static final String SPEED = "speed";
    public static final String PEDAL = "pedal";
    public static final String SEEN_SUFFIX = "Seen";
    public static final String LAST_DATA = "lastData";
    public static final String GEAR = "gear";
    public static final String CAN1_LINK = "can1Link";
    public static final String CAN2_LINK = "can2Link";
    public static final String SECURITY_FLAGS = "securityFlags";
    public static final String CONTROL_FLAGS = "controlFlags";
    public static final String INDICATOR_FLAGS = "indicatorFlags";
    public static final String UNMAPPED_IO = "unmappedIo";

    private enum Kind {
        PERCENT,
        METRES,
        KMH
    }

    private record Value(String channel, int avlId, Kind kind) {
    }

    /** FMx6 LVCAN values (Teltonika "FMC650 Data Sending Parameters ID", CAN adapter section). */
    private static final List<Value> VALUES = List.of( //
            new Value(BATTERY_LEVEL, 142, Kind.PERCENT), // Battery Level Percent (the HV battery of an EV)
            new Value(RANGE, 526, Kind.METRES), // Vehicles Range On Battery
            new Value(ODOMETER, 36, Kind.METRES), // Total Mileage
            new Value(SPEED, 30, Kind.KMH), // Vehicle Speed
            new Value(PEDAL, 31, Kind.PERCENT)); // Accelerator Pedal Position

    private static final int DOOR_STATUS = 143;
    private static final Map<String, Integer> DOORS = orderedMap( //
            "doorFrontLeft", 0x0100, "doorFrontRight", 0x0200, "doorRearLeft", 0x0400, "doorRearRight", 0x0800,
            "hood", 0x1000, "trunk", 0x2000);

    private enum Table {
        SECURITY,
        CONTROL,
        INDICATOR
    }

    private record Flag(String channel, Table table, int bit) {
    }

    /** P4 tables: separate I/O elements, off by default in the Configurator. */
    private static final int P4_SECURITY = 12710;
    private static final int P4_CONTROL = 12711;
    private static final int P4_INDICATOR = 12712;

    /** Legacy (non-P4) tables: Security State Flags and Control State Flags P2. */
    private static final int LEGACY_SECURITY = 47;
    private static final int LEGACY_CONTROL = 38;

    private static final String WORK_MODE = "workMode";

    private static final List<Flag> FLAGS_P4 = List.of( //
            new Flag("ignition", Table.SECURITY, 8), //
            new Flag("keyInserted", Table.SECURITY, 9), //
            new Flag("ready", Table.SECURITY, 13), //
            new Flag(WORK_MODE, Table.SECURITY, 15), //
            new Flag("handbrake", Table.SECURITY, 18), //
            new Flag("footbrake", Table.SECURITY, 19), //
            new Flag("hazardSwitch", Table.SECURITY, 21), //
            new Flag("chargeCable", Table.SECURITY, 28), //
            new Flag("charging", Table.SECURITY, 29), //
            new Flag("electricMotor", Table.SECURITY, 30), // "electric engine working": drive delivered now, follows the pedal on a bZ4X
            new Flag("closedByRemote", Table.SECURITY, 31), //
            new Flag("locked", Table.SECURITY, 32), //
            new Flag("remoteClose", Table.SECURITY, 35), //
            new Flag("remoteOpen", Table.SECURITY, 36), //
            new Flag("adapterSleep", Table.SECURITY, 39), //
            new Flag("remoteClose3x", Table.SECURITY, 40), //
            new Flag("gearPark", Table.SECURITY, 41), //
            new Flag("gearReverse", Table.SECURITY, 42), //
            new Flag("gearNeutral", Table.SECURITY, 43), //
            new Flag("gearDrive", Table.SECURITY, 44), //
            new Flag("sidelights", Table.CONTROL, 0), //
            new Flag("dippedBeam", Table.CONTROL, 1), //
            new Flag("fullBeam", Table.CONTROL, 2), //
            new Flag("rearFog", Table.CONTROL, 3), //
            new Flag("frontFog", Table.CONTROL, 4), //
            new Flag("airConditioning", Table.CONTROL, 8), //
            new Flag("beltDriver", Table.CONTROL, 12), //
            new Flag("beltPassenger", Table.CONTROL, 13), //
            new Flag("passengerPresent", Table.CONTROL, 17), //
            new Flag("lampAbs", Table.INDICATOR, 1), //
            new Flag("lampEsp", Table.INDICATOR, 2), //
            new Flag("espOff", Table.INDICATOR, 3), //
            new Flag("lampBrake", Table.INDICATOR, 8), //
            new Flag("lampAirbag", Table.INDICATOR, 9), //
            new Flag("lampSteering", Table.INDICATOR, 10), //
            new Flag("lampTyre", Table.INDICATOR, 13));

    /** The legacy tables carry fewer signals; the ones not listed here stay as they were. */
    private static final List<Flag> FLAGS_LEGACY = List.of( //
            new Flag("hazardSwitch", Table.SECURITY, 9), //
            new Flag("electricMotor", Table.SECURITY, 17), //
            new Flag("charging", Table.SECURITY, 18), //
            new Flag("chargeCable", Table.SECURITY, 19), //
            new Flag(WORK_MODE, Table.SECURITY, 20), //
            new Flag("keyInserted", Table.SECURITY, 24), //
            new Flag("ignition", Table.SECURITY, 25), //
            new Flag("locked", Table.SECURITY, 28), //
            new Flag("closedByRemote", Table.SECURITY, 29), //
            new Flag("gearPark", Table.SECURITY, 32), //
            new Flag("gearNeutral", Table.SECURITY, 34), //
            new Flag("gearDrive", Table.SECURITY, 35), //
            new Flag("handbrake", Table.SECURITY, 36), //
            new Flag("footbrake", Table.SECURITY, 37), //
            new Flag("gearReverse", Table.SECURITY, 39), //
            new Flag("lampBrake", Table.CONTROL, 3), //
            new Flag("lampAirbag", Table.CONTROL, 5), //
            new Flag("lampSteering", Table.CONTROL, 6), //
            new Flag("lampEsp", Table.CONTROL, 7), //
            new Flag("lampTyre", Table.CONTROL, 10), //
            new Flag("lampAbs", Table.CONTROL, 13), //
            new Flag("sidelights", Table.CONTROL, 20), //
            new Flag("dippedBeam", Table.CONTROL, 21), //
            new Flag("fullBeam", Table.CONTROL, 22), //
            new Flag("frontFog", Table.CONTROL, 23), //
            new Flag("ready", Table.CONTROL, 24), //
            new Flag("airConditioning", Table.CONTROL, 28), //
            new Flag("rearFog", Table.CONTROL, 29), //
            new Flag("beltPassenger", Table.CONTROL, 30), //
            new Flag("beltDriver", Table.CONTROL, 31));

    /** Byte 0 of the security word: two bits per bus. The two formats order the values differently. */
    private static final String[] LINK_P4 = { "connected, no data", "connected, data", "not connected, needed",
            "not connected, not needed" };
    private static final String[] LINK_LEGACY = { "not connected, not needed", "connected, no data",
            "not connected, needed", "connected, data" };

    /**
     * The tracker's own I/O elements (FMx6) and adapter housekeeping ids, so {@link #UNMAPPED_IO} lists
     * only what is new. Ids only - nothing here is a value.
     */
    private static final Set<Integer> NOT_CAN = Set.of(12, 13, 22, 24, 39, 50, 51, 68, 71, 155, 178, 200, 205, 206,
            216, 218, 219, 220, 221, 226, 236, 237, 238, 250, 524, 529, 1148, 1161);

    private static final Set<Integer> MAPPED = new HashSet<>();
    static {
        VALUES.forEach(v -> MAPPED.add(v.avlId()));
        MAPPED.addAll(Set.of(DOOR_STATUS, P4_SECURITY, P4_CONTROL, P4_INDICATOR, LEGACY_SECURITY, LEGACY_CONTROL));
    }

    private LvcanDecoder() {
    }

    /** Every channel id of the {@code can} group this decoder can write. */
    public static List<String> channelIds() {
        List<String> ids = new ArrayList<>();
        for (Value v : VALUES) {
            ids.add(v.channel());
            ids.add(v.channel() + SEEN_SUFFIX);
        }
        ids.addAll(DOORS.keySet());
        for (Flag f : FLAGS_P4) {
            ids.add(f.channel());
        }
        ids.addAll(List.of(GEAR, CAN1_LINK, CAN2_LINK, LAST_DATA, SECURITY_FLAGS, CONTROL_FLAGS, INDICATOR_FLAGS,
                UNMAPPED_IO));
        return ids;
    }

    /**
     * Decodes one record.
     *
     * @param attributes the record's attributes as Traccar delivers them
     * @param recordTime the record's device time; it becomes the "seen" and "last data" time
     * @param gearParkWhenOff the car is parked whenever it is not ready: no gear bit reads P, and a record
     *            without speed or pedal reads 0 for them - right for cars that engage P themselves when
     *            switched off, wrong for a manual gearbox
     * @return channel id (inside the {@code can} group) to state; empty when the record has no LVCAN field
     */
    public static Map<String, State> decode(Map<String, Object> attributes, ZonedDateTime recordTime,
            boolean gearParkWhenOff) {
        Map<String, State> out = new LinkedHashMap<>();
        DateTimeType seen = new DateTimeType(recordTime);
        boolean any = false;

        for (Value v : VALUES) {
            Double raw = number(attributes.get("io" + v.avlId()));
            if (raw == null) {
                continue;
            }
            if (v.kind() == Kind.PERCENT && (raw < 0 || raw > 100)) {
                continue; // not a percentage - leave the channel as it was
            }
            Unit<?> unit = switch (v.kind()) {
                case PERCENT -> Units.PERCENT;
                case METRES -> SIUnits.METRE;
                case KMH -> SIUnits.KILOMETRE_PER_HOUR;
            };
            out.put(v.channel(), new QuantityType<>(raw, unit));
            out.put(v.channel() + SEEN_SUFFIX, seen);
            any = true;
        }

        Double doors = number(attributes.get("io" + DOOR_STATUS));
        if (doors != null) {
            long d = Math.round(doors);
            DOORS.forEach((channel, mask) -> out.put(channel, (d & mask) != 0 ? OpenClosedType.OPEN
                    : OpenClosedType.CLOSED));
            any = true;
        }

        boolean p4 = attributes.containsKey("io" + P4_SECURITY);
        Map<Table, Long> words = new LinkedHashMap<>();
        putWord(words, Table.SECURITY, attributes.get("io" + (p4 ? P4_SECURITY : LEGACY_SECURITY)));
        putWord(words, Table.CONTROL, attributes.get("io" + (p4 ? P4_CONTROL : LEGACY_CONTROL)));
        if (p4) {
            putWord(words, Table.INDICATOR, attributes.get("io" + P4_INDICATOR));
        }

        for (Map.Entry<Table, Long> w : words.entrySet()) {
            String channel = switch (w.getKey()) {
                case SECURITY -> SECURITY_FLAGS;
                case CONTROL -> CONTROL_FLAGS;
                case INDICATOR -> INDICATOR_FLAGS;
            };
            out.put(channel, new StringType(hexWithBits(w.getValue(), w.getKey() == Table.CONTROL ? 8 : 12)));
            any = true;
        }

        for (Flag f : p4 ? FLAGS_P4 : FLAGS_LEGACY) {
            Long word = words.get(f.table());
            if (word == null) {
                continue;
            }
            boolean on = bit(word, f.bit());
            if (WORK_MODE.equals(f.channel())) {
                out.put(f.channel(), new StringType(on ? "company" : "private"));
            } else {
                out.put(f.channel(), OnOffType.from(on));
            }
        }

        Long security = words.get(Table.SECURITY);
        if (security != null) {
            String[] link = p4 ? LINK_P4 : LINK_LEGACY;
            out.put(CAN1_LINK, new StringType(link[(int) (security & 3)]));
            out.put(CAN2_LINK, new StringType(link[(int) ((security >> 2) & 3)]));

            String gear;
            if (p4) {
                gear = bit(security, 41) ? "P" : bit(security, 42) ? "R" : bit(security, 43) ? "N"
                        : bit(security, 44) ? "D" : "-";
            } else {
                gear = bit(security, 32) ? "P" : bit(security, 39) ? "R" : bit(security, 34) ? "N"
                        : bit(security, 35) ? "D" : "-";
            }
            Long control = words.get(Table.CONTROL);
            boolean ready = p4 ? bit(security, 13) : control != null && bit(control, 24);
            if ("-".equals(gear) && gearParkWhenOff && !ready) {
                gear = "P";
            }
            out.put(GEAR, new StringType(gear));

            // Parked when switched off: the car sends speed and pedal only while ready, so the last
            // values before switch-off (2 km/h rolling into the parking space) would stand for the
            // whole stop. Not ready and parked means standing still with the foot off the pedal.
            if (gearParkWhenOff && !ready) {
                for (Value v : VALUES) {
                    if ((SPEED.equals(v.channel()) || PEDAL.equals(v.channel()))
                            && number(attributes.get("io" + v.avlId())) == null) {
                        Unit<?> unit = SPEED.equals(v.channel()) ? SIUnits.KILOMETRE_PER_HOUR : Units.PERCENT;
                        out.put(v.channel(), new QuantityType<>(0, unit));
                        out.put(v.channel() + SEEN_SUFFIX, seen);
                    }
                }
            }
        }

        if (!any) {
            return Map.of();
        }

        out.put(LAST_DATA, seen);
        out.put(UNMAPPED_IO, new StringType(unmapped(attributes)));
        return out;
    }

    private static void putWord(Map<Table, Long> words, Table table, @Nullable Object value) {
        Double d = number(value);
        if (d != null && d >= 0) {
            words.put(table, Math.round(d)); // doubles are exact to 2^53, past every bit in these tables
        }
    }

    private static boolean bit(long word, int n) {
        return ((word >>> n) & 1L) == 1L;
    }

    /** "0x0000000CA339  bits 0,3,4,5,8,9" - what a walk-round is read off. */
    static String hexWithBits(long word, int digits) {
        StringBuilder bits = new StringBuilder();
        for (int n = 0; n < 64; n++) {
            if (bit(word, n)) {
                if (bits.length() > 0) {
                    bits.append(',');
                }
                bits.append(n);
            }
        }
        String hex = Long.toHexString(word).toUpperCase(Locale.ROOT);
        while (hex.length() < digits) {
            hex = "0" + hex;
        }
        return "0x" + hex + "  bits " + (bits.length() > 0 ? bits : "none");
    }

    private static String unmapped(Map<String, Object> attributes) {
        Map<Integer, Object> fresh = new TreeMap<>();
        for (Map.Entry<String, Object> e : attributes.entrySet()) {
            String key = e.getKey();
            if (key.length() > 2 && key.startsWith("io") && key.substring(2).chars().allMatch(Character::isDigit)) {
                int id = Integer.parseInt(key.substring(2));
                if (!MAPPED.contains(id) && !NOT_CAN.contains(id)) {
                    fresh.put(id, e.getValue());
                }
            }
        }
        if (fresh.isEmpty()) {
            return "none";
        }
        StringBuilder sb = new StringBuilder();
        fresh.forEach((id, value) -> {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append("io").append(id).append('=').append(value);
        });
        return sb.toString();
    }

    private static @Nullable Double number(@Nullable Object value) {
        if (value instanceof Number n) {
            return n.doubleValue();
        }
        if (value instanceof String s) {
            try {
                return Double.valueOf(s.trim());
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    private static Map<String, Integer> orderedMap(Object... pairs) {
        Map<String, Integer> m = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            m.put((String) pairs[i], (Integer) pairs[i + 1]);
        }
        return m;
    }
}
