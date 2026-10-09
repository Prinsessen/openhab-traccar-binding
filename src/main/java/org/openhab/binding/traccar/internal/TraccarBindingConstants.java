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

import java.util.Set;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.core.thing.ThingTypeUID;
import org.openhab.core.thing.type.ChannelGroupTypeUID;

/**
 * The {@link TraccarBindingConstants} class defines common constants, which are
 * used across the whole binding.
 *
 * @author Nanna Agesen - Initial contribution
 */
@NonNullByDefault
public class TraccarBindingConstants {

    private static final String BINDING_ID = "traccar";

    // List of all Thing Type UIDs
    public static final ThingTypeUID THING_TYPE_SERVER = new ThingTypeUID(BINDING_ID, "server");
    public static final ThingTypeUID THING_TYPE_DEVICE = new ThingTypeUID(BINDING_ID, "device");

    public static final Set<ThingTypeUID> SUPPORTED_THING_TYPES_UIDS = Set.of(THING_TYPE_SERVER, THING_TYPE_DEVICE);

    // CAN adapter channels: a group added to the thing only with canAdapter=lvcan
    public static final String CHANNEL_GROUP_CAN = "can";
    public static final ChannelGroupTypeUID CHANNEL_GROUP_TYPE_CAN = new ChannelGroupTypeUID(BINDING_ID, "can");

    // List of all Channel ids
    public static final String CHANNEL_GEOFENCE_EVENT = "geofenceEvent";
    public static final String CHANNEL_GEOFENCE_ID = "geofenceId";
    public static final String CHANNEL_POSITION = "position";
    public static final String CHANNEL_LAST_UPDATE = "lastUpdate";
    public static final String CHANNEL_STATUS = "status";
    public static final String CHANNEL_SPEED = "speed";
    public static final String CHANNEL_GEOFENCE_NAME = "geofenceName";
    public static final String CHANNEL_COURSE = "course";
    public static final String CHANNEL_ACCURACY = "accuracy";
    public static final String CHANNEL_BATTERY_LEVEL = "batteryLevel";
    public static final String CHANNEL_ODOMETER = "odometer";
    public static final String CHANNEL_TOTAL_DISTANCE = "totalDistance";
    public static final String CHANNEL_MOTION = "motion";
    public static final String CHANNEL_ADDRESS = "address";
    public static final String CHANNEL_GPS_SAT = "gpsSatellites";
    public static final String CHANNEL_GSM_SIGNAL = "gsmSignal";
    public static final String CHANNEL_HOURS = "hours";
    public static final String CHANNEL_EVENT = "event";
    public static final String CHANNEL_ALTITUDE = "altitude";
    public static final String CHANNEL_VALID = "valid";
    public static final String CHANNEL_DISTANCE = "distance";
    public static final String CHANNEL_ACTIVITY = "activity";
    public static final String CHANNEL_PROTOCOL = "protocol";

    // OBD-II Channels (Teltonika FMM920 with Bluetooth OBD-II dongle)
    public static final String CHANNEL_OBD_DTC_COUNT = "obdDtcCount";
    public static final String CHANNEL_OBD_ENGINE_LOAD = "obdEngineLoad";
    public static final String CHANNEL_OBD_COOLANT_TEMP = "obdCoolantTemp";
    public static final String CHANNEL_OBD_SHORT_FUEL_TRIM = "obdShortFuelTrim";
    public static final String CHANNEL_OBD_FUEL_PRESSURE = "obdFuelPressure";
    public static final String CHANNEL_OBD_RPM = "obdRpm";
    public static final String CHANNEL_OBD_RPM_REPORTED = "obdRpmReported";
    public static final String CHANNEL_OBD_SPEED = "obdSpeed";
    public static final String CHANNEL_OBD_FUEL_LEVEL = "obdFuelLevel";
    public static final String CHANNEL_OBD_OEM_ODOMETER = "obdOemOdometer";
    public static final String CHANNEL_IGNITION = "ignition";

    // Additional Teltonika/GPS Channels
    public static final String CHANNEL_PDOP = "pdop";
    public static final String CHANNEL_HDOP = "hdop";
    public static final String CHANNEL_POWER = "power";
    public static final String CHANNEL_BATTERY = "battery";
    public static final String CHANNEL_OPERATOR = "operator";
    public static final String CHANNEL_VIN = "vin";

    // Additional Teltonika IO Channels (experimental/unknown)
    public static final String CHANNEL_IO42 = "io42";
    public static final String CHANNEL_IO49 = "io49";
    public static final String CHANNEL_IO51 = "io51";
    public static final String CHANNEL_TRIP_DISTANCE = "tripDistance";
    public static final String CHANNEL_EVENT_CODE = "eventCode";

    // OBD-II Trip Meters from ECU
    public static final String CHANNEL_IO199 = "io199";
    public static final String CHANNEL_IO205 = "io205";
    public static final String CHANNEL_IO389 = "io389";

    /*
     * Every attribute Traccar decoded, sorted, as one string.
     *
     * The named reads in the handler take about thirty of them and the rest
     * were never looked at - not filtered, just never asked for. This channel
     * shows the lot, so a new peripheral's signals can be identified from what
     * ACTUALLY arrives instead of from a protocol table. That matters because
     * the ID space is per-peripheral: io38 here is OBD-II vehicle speed from a
     * Bluetooth OBD dongle, while AVL ID 38 on a CAN adapter is Control State
     * Flags. The same number, two unrelated meanings.
     *
     * It is how io42 came to be known at all - see the comment on it in the
     * handler: "Varies 84-94 (possibly intake air temperature)".
     */
    public static final String CHANNEL_RAW_ATTRIBUTES = "raw-attributes";

    /* A cap, so one chatty device cannot post a megabyte into an item. */
    public static final int RAW_ATTRIBUTES_MAX = 4000;

    // Bluetooth Beacon Channels (Teltonika FMM920 optional accessory)
    // Beacon 1
    public static final String CHANNEL_BEACON1_RSSI = "beacon1-rssi";
    public static final String CHANNEL_BEACON1_DISTANCE = "beacon1-distance";
    public static final String CHANNEL_BEACON1_MAC = "beacon1-mac";
    public static final String CHANNEL_BEACON1_BATTERY = "beacon1-battery";
    public static final String CHANNEL_BEACON1_LOW_BATTERY = "beacon1-lowBattery";
    public static final String CHANNEL_BEACON1_NAME = "beacon1-name";
    public static final String CHANNEL_BEACON1_TEMPERATURE = "beacon1-temperature";
    public static final String CHANNEL_BEACON1_HUMIDITY = "beacon1-humidity";
    public static final String CHANNEL_BEACON1_MAGNET = "beacon1-magnet";
    public static final String CHANNEL_BEACON1_MOTION = "beacon1-motion";
    public static final String CHANNEL_BEACON1_MOTION_COUNT = "beacon1-motionCount";
    public static final String CHANNEL_BEACON1_PITCH = "beacon1-pitch";
    public static final String CHANNEL_BEACON1_ROLL = "beacon1-roll";

    // Beacon 2
    public static final String CHANNEL_BEACON2_RSSI = "beacon2-rssi";
    public static final String CHANNEL_BEACON2_DISTANCE = "beacon2-distance";
    public static final String CHANNEL_BEACON2_MAC = "beacon2-mac";
    public static final String CHANNEL_BEACON2_BATTERY = "beacon2-battery";
    public static final String CHANNEL_BEACON2_LOW_BATTERY = "beacon2-lowBattery";
    public static final String CHANNEL_BEACON2_NAME = "beacon2-name";
    public static final String CHANNEL_BEACON2_TEMPERATURE = "beacon2-temperature";
    public static final String CHANNEL_BEACON2_HUMIDITY = "beacon2-humidity";
    public static final String CHANNEL_BEACON2_MAGNET = "beacon2-magnet";
    public static final String CHANNEL_BEACON2_MOTION = "beacon2-motion";
    public static final String CHANNEL_BEACON2_MOTION_COUNT = "beacon2-motionCount";
    public static final String CHANNEL_BEACON2_PITCH = "beacon2-pitch";
    public static final String CHANNEL_BEACON2_ROLL = "beacon2-roll";

    // Beacon 3
    public static final String CHANNEL_BEACON3_RSSI = "beacon3-rssi";
    public static final String CHANNEL_BEACON3_DISTANCE = "beacon3-distance";
    public static final String CHANNEL_BEACON3_MAC = "beacon3-mac";
    public static final String CHANNEL_BEACON3_BATTERY = "beacon3-battery";
    public static final String CHANNEL_BEACON3_LOW_BATTERY = "beacon3-lowBattery";
    public static final String CHANNEL_BEACON3_NAME = "beacon3-name";
    public static final String CHANNEL_BEACON3_TEMPERATURE = "beacon3-temperature";
    public static final String CHANNEL_BEACON3_HUMIDITY = "beacon3-humidity";
    public static final String CHANNEL_BEACON3_MAGNET = "beacon3-magnet";
    public static final String CHANNEL_BEACON3_MOTION = "beacon3-motion";
    public static final String CHANNEL_BEACON3_MOTION_COUNT = "beacon3-motionCount";
    public static final String CHANNEL_BEACON3_PITCH = "beacon3-pitch";
    public static final String CHANNEL_BEACON3_ROLL = "beacon3-roll";

    // Beacon 4
    public static final String CHANNEL_BEACON4_RSSI = "beacon4-rssi";
    public static final String CHANNEL_BEACON4_DISTANCE = "beacon4-distance";
    public static final String CHANNEL_BEACON4_MAC = "beacon4-mac";
    public static final String CHANNEL_BEACON4_BATTERY = "beacon4-battery";
    public static final String CHANNEL_BEACON4_LOW_BATTERY = "beacon4-lowBattery";
    public static final String CHANNEL_BEACON4_NAME = "beacon4-name";
    public static final String CHANNEL_BEACON4_TEMPERATURE = "beacon4-temperature";
    public static final String CHANNEL_BEACON4_HUMIDITY = "beacon4-humidity";
    public static final String CHANNEL_BEACON4_MAGNET = "beacon4-magnet";
    public static final String CHANNEL_BEACON4_MOTION = "beacon4-motion";
    public static final String CHANNEL_BEACON4_MOTION_COUNT = "beacon4-motionCount";
    public static final String CHANNEL_BEACON4_PITCH = "beacon4-pitch";
    public static final String CHANNEL_BEACON4_ROLL = "beacon4-roll";
}
