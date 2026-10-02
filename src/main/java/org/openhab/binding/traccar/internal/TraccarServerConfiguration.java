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
 * Configuration class for Traccar Server Bridge.
 *
 * @author Nanna Agesen - Initial contribution
 */
public class TraccarServerConfiguration {

    public String url = "https://traccar.example.com";
    public String username = "";
    public String password = "";
    public int refreshInterval = 60;
    public int webhookPort = 8090;
    public String speedUnit = "kmh";
    public double speedThreshold = 2.0; // km/h - speeds below this are shown as 0

    // Nominatim reverse geocoding settings
    public boolean useNominatim = false;
    public String nominatimUrl = "https://nominatim.openstreetmap.org";
    public String nominatimLanguage = "en";
    public int geocodingCacheDistance = 50;
}
