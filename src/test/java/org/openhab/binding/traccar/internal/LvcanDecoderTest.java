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

import static org.junit.jupiter.api.Assertions.*;

import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.Map;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.junit.jupiter.api.Test;
import org.openhab.core.library.types.DateTimeType;
import org.openhab.core.library.types.OnOffType;
import org.openhab.core.library.types.OpenClosedType;
import org.openhab.core.library.types.QuantityType;
import org.openhab.core.library.types.StringType;
import org.openhab.core.library.unit.SIUnits;
import org.openhab.core.library.unit.Units;
import org.openhab.core.types.State;

/**
 * Records as Traccar delivers them from an FMx6 tracker with an ALL-CAN300, cut down to the CAN
 * fields. The flag words are the ones read on the first car; every other value is made up.
 *
 * @author Nanna Agesen - Initial contribution
 */
@NonNullByDefault
public class LvcanDecoderTest {

    private static final ZonedDateTime T = ZonedDateTime.parse("2026-01-01T12:00:00Z");

    private static Map<String, Object> record(Object... pairs) {
        Map<String, Object> m = new HashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            m.put((String) pairs[i], pairs[i + 1]);
        }
        return m;
    }

    private static Map<String, State> decode(Object... pairs) {
        return LvcanDecoder.decode(record(pairs), T, false);
    }

    @Test
    public void valuesInBaseUnitsWithSeenTimes() {
        Map<String, State> s = decode("io36", 12345000.0, "io526", 217000.0, "io30", 37.0, "io31", 12.0, "io143",
                0.0);
        assertEquals(new QuantityType<>(12345000.0, SIUnits.METRE), s.get("odometer"));
        assertEquals(new QuantityType<>(217000.0, SIUnits.METRE), s.get("range"));
        assertEquals(new QuantityType<>(37.0, SIUnits.KILOMETRE_PER_HOUR), s.get("speed"));
        assertEquals(new QuantityType<>(12.0, Units.PERCENT), s.get("pedal"));
        assertEquals(new DateTimeType(T), s.get("odometerSeen"));
        assertEquals(new DateTimeType(T), s.get("rangeSeen"));
        assertEquals(new DateTimeType(T), s.get("lastData"));
        for (String door : new String[] { "doorFrontLeft", "doorFrontRight", "doorRearLeft", "doorRearRight", "hood",
                "trunk" }) {
            assertEquals(OpenClosedType.CLOSED, s.get(door), door);
        }
        assertNull(s.get("batteryLevel"), "a value not in the record is not touched");
    }

    @Test
    public void doorBits() {
        Map<String, State> s = decode("io143", (double) (0x0100 | 0x2000));
        assertEquals(OpenClosedType.OPEN, s.get("doorFrontLeft"));
        assertEquals(OpenClosedType.OPEN, s.get("trunk"));
        assertEquals(OpenClosedType.CLOSED, s.get("doorFrontRight"));
        assertEquals(OpenClosedType.CLOSED, s.get("hood"));
    }

    @Test
    public void p4SecurityWordFromTheFirstRecord() {
        // 0xCA339: CAN1 data, CAN2 needed; ignition, key, ready, company, handbrake, footbrake
        Map<String, State> s = decode("io12710", 828217.0);
        assertEquals(new StringType("connected, data"), s.get("can1Link"));
        assertEquals(new StringType("not connected, needed"), s.get("can2Link"));
        assertEquals(OnOffType.ON, s.get("ignition"));
        assertEquals(OnOffType.ON, s.get("keyInserted"));
        assertEquals(OnOffType.ON, s.get("ready"));
        assertEquals(new StringType("company"), s.get("workMode"));
        assertEquals(OnOffType.ON, s.get("handbrake"));
        assertEquals(OnOffType.ON, s.get("footbrake"));
        assertEquals(OnOffType.OFF, s.get("locked"));
        assertEquals(OnOffType.OFF, s.get("charging"));
        assertEquals(new StringType("-"), s.get("gear"));
        assertEquals(new StringType("0x0000000CA339  bits 0,3,4,5,8,9,13,15,18,19"), s.get("securityFlags"));
    }

    @Test
    public void p4ControlAndIndicatorWords() {
        Map<String, State> s = decode("io12710", 0.0, "io12711", 3.0, "io12712", 512.0);
        assertEquals(OnOffType.ON, s.get("sidelights"));
        assertEquals(OnOffType.ON, s.get("dippedBeam"));
        assertEquals(OnOffType.OFF, s.get("fullBeam"));
        assertEquals(OnOffType.ON, s.get("lampAirbag"));
        assertEquals(OnOffType.OFF, s.get("lampAbs"));
    }

    @Test
    public void bitsAbove32AreReadAsLong() {
        // locked (32) and gear D (44): past an int, exact in a double
        double word = (double) ((1L << 32) | (1L << 44));
        Map<String, State> s = decode("io12710", word);
        assertEquals(OnOffType.ON, s.get("locked"));
        assertEquals(OnOffType.ON, s.get("gearDrive"));
        assertEquals(new StringType("D"), s.get("gear"));
    }

    @Test
    public void legacyWordsWhenThereIsNoP4() {
        // security 0x300310000B: CAN1 data, CAN2 needed; company, key, ignition, handbrake, footbrake
        // control 0x1300000: sidelights, dipped beam, ready
        Map<String, State> s = decode("io47", (double) 0x300310000BL, "io38", (double) 0x1300000L);
        assertEquals(new StringType("connected, data"), s.get("can1Link"));
        assertEquals(new StringType("not connected, needed"), s.get("can2Link"));
        assertEquals(OnOffType.ON, s.get("keyInserted"));
        assertEquals(OnOffType.ON, s.get("ignition"));
        assertEquals(OnOffType.ON, s.get("handbrake"));
        assertEquals(OnOffType.ON, s.get("footbrake"));
        assertEquals(new StringType("company"), s.get("workMode"));
        assertEquals(OnOffType.ON, s.get("sidelights"));
        assertEquals(OnOffType.ON, s.get("dippedBeam"));
        assertEquals(OnOffType.ON, s.get("ready"));
        assertNull(s.get("passengerPresent"), "not in the legacy tables: left as it was");
    }

    @Test
    public void p4WinsWhenBothFormatsArrive() {
        // P4 says ignition on; the legacy word is all zero
        Map<String, State> s = decode("io12710", 256.0, "io47", 0.0);
        assertEquals(OnOffType.ON, s.get("ignition"));
    }

    @Test
    public void gearParkWhenOffIsAnOption() {
        Map<String, Object> off = record("io12710", 0.0); // no gear bit, not ready
        assertEquals(new StringType("-"), LvcanDecoder.decode(off, T, false).get("gear"));
        assertEquals(new StringType("P"), LvcanDecoder.decode(off, T, true).get("gear"));

        Map<String, Object> ready = record("io12710", (double) (1L << 13)); // ready, still no gear bit
        assertEquals(new StringType("-"), LvcanDecoder.decode(ready, T, true).get("gear"));
    }

    @Test
    public void batteryLevelAndItsSeenTime() {
        Map<String, State> s = decode("io142", 64.0);
        assertEquals(new QuantityType<>(64.0, Units.PERCENT), s.get("batteryLevel"));
        assertEquals(new DateTimeType(T), s.get("batteryLevelSeen"));
    }

    @Test
    public void aRecordWithoutBatteryLevelLeavesItAndItsSeenTimeAlone() {
        // the slow end of a charge: flags and doors keep coming, the battery level does not
        Map<String, State> s = decode("io12710", 0.0, "io143", 0.0);
        assertNull(s.get("batteryLevel"));
        assertNull(s.get("batteryLevelSeen"));
        assertEquals(new DateTimeType(T), s.get("lastData"));
        assertEquals(OnOffType.OFF, s.get("charging"));
    }

    @Test
    public void noLvcanFieldMeansNoChange() {
        assertTrue(decode("io24", 0.0, "io200", 0.0, "power", 12.6).isEmpty());
    }

    @Test
    public void aBatteryLevelOutsideZeroToHundredIsIgnored() {
        Map<String, State> s = decode("io142", 255.0, "io143", 0.0);
        assertNull(s.get("batteryLevel"));
        assertNull(s.get("batteryLevelSeen"));
    }

    @Test
    public void numbersAsStringsAreRead() {
        assertEquals(new QuantityType<>(50.0, Units.PERCENT), decode("io142", "50").get("batteryLevel"));
    }

    @Test
    public void unmappedListsOnlyWhatIsNew() {
        Map<String, State> s = decode("io142", 50.0, "io1161", 350000000000000.0, "io9999", 5.0, "io216", 1.0);
        assertEquals(new StringType("io9999=5.0"), s.get("unmappedIo"));
        assertEquals(new StringType("none"), decode("io142", 50.0).get("unmappedIo"));
    }

    @Test
    public void everyDecodedChannelIsDeclared() {
        Map<String, State> s = decode("io142", 1.0, "io526", 1.0, "io36", 1.0, "io30", 1.0, "io31", 1.0, "io143",
                0.0, "io12710", 0.0, "io12711", 0.0, "io12712", 0.0, "io9999", 1.0);
        assertTrue(LvcanDecoder.channelIds().containsAll(s.keySet()), s.keySet().toString());
    }
}
