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

/**
 * Configuration class for Traccar Device.
 *
 * <p>
 * The same Teltonika AVL id means different things on different trackers and accessories (io38 is
 * vehicle speed from a Bluetooth OBD-II dongle, but the Control State Flags from a CAN adapter), so
 * a thing says what it is and the binding never guesses. The defaults reproduce the decoding of
 * every release before these parameters existed.
 *
 * @author Nanna Agesen - Initial contribution
 */
public class TraccarDeviceConfiguration {

    public static final String FAMILY_FMB = "fmb";
    public static final String FAMILY_FMX6 = "fmx6";
    public static final String CAN_NONE = "none";
    public static final String CAN_LVCAN = "lvcan";
    public static final String OBD_BLUETOOTH = "bluetooth";

    public int deviceId;

    /** {@code fmb} (FMB/FMM/FMC9xx family, default), {@code fmx6} (FMC650 and relatives), {@code phone}. */
    public String deviceFamily = FAMILY_FMB;

    /** {@code none} (default) or {@code lvcan}: an ALL-CAN300 / LV-CAN200 on the tracker. */
    public String canAdapter = CAN_NONE;

    /** {@code bluetooth} (default: io30-io48 read as OBD-II, as before) or {@code none}. */
    public String obdDongle = OBD_BLUETOOTH;

    /** With a CAN adapter: no gear bit while the car is not ready reads as P. */
    public boolean gearParkWhenOff = false;

    public boolean hasCanAdapter() {
        return CAN_LVCAN.equals(canAdapter);
    }

    /** The OBD-II block and a CAN adapter use the same io numbers: never both. */
    public boolean readsObd() {
        return OBD_BLUETOOTH.equals(obdDongle) && !hasCanAdapter();
    }

    public boolean isFmx6() {
        return FAMILY_FMX6.equals(deviceFamily);
    }
}
