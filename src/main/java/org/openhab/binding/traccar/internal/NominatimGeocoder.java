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

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

/**
 * Nominatim reverse geocoding service with caching and rate limiting.
 *
 * @author Nanna Agesen - Initial contribution
 */
@NonNullByDefault
public class NominatimGeocoder {

    private final Logger logger = LoggerFactory.getLogger(NominatimGeocoder.class);
    private final Gson gson = new Gson();

    private final String serverUrl;
    private final String language;
    private final int cacheDistance;

    // Cache structure: "lat,lon" -> CachedAddress
    private final Map<String, CachedAddress> addressCache = new ConcurrentHashMap<>();

    // OSM's limit (1 request/s) is per client, not per device: one budget for every geocoder in the
    // binding, with a pause after a 429 (NominatimThrottle).
    private static final NominatimThrottle THROTTLE = new NominatimThrottle();
    private static final String USER_AGENT = "openHAB-Traccar-Binding (+https://github.com/Prinsessen/openhab-traccar-binding)";

    /**
     * Cached address entry with coordinates
     */
    private static class CachedAddress {
        final String address;
        final double latitude;
        final double longitude;
        final long timestamp;

        CachedAddress(String address, double latitude, double longitude) {
            this.address = address;
            this.latitude = latitude;
            this.longitude = longitude;
            this.timestamp = System.currentTimeMillis();
        }
    }

    /**
     * Create a Nominatim geocoder instance
     *
     * @param serverUrl Base URL of Nominatim server (e.g., "https://nominatim.openstreetmap.org")
     * @param language Language code for address results (e.g., "en", "da", "de")
     * @param cacheDistance Minimum distance in meters to trigger new geocoding request
     */
    public NominatimGeocoder(String serverUrl, String language, int cacheDistance) {
        this.serverUrl = serverUrl.endsWith("/") ? serverUrl.substring(0, serverUrl.length() - 1) : serverUrl;
        this.language = language;
        this.cacheDistance = cacheDistance;
    }

    /**
     * Get address for coordinates using reverse geocoding
     *
     * @param latitude Latitude coordinate
     * @param longitude Longitude coordinate
     * @return Address string, or null if geocoding fails
     */
    public @Nullable String getAddress(double latitude, double longitude) {
        // Check cache first
        CachedAddress cached = findInCache(latitude, longitude);
        if (cached != null) {
            logger.debug("Using cached address for {},{}: {}", latitude, longitude, cached.address);
            return cached.address;
        }

        // Rate limiting, shared by all devices; nothing is sent while paused after a 429
        long wait = THROTTLE.reserve(System.currentTimeMillis());
        if (wait < 0) {
            logger.debug("Nominatim paused after a 429 - no request for {},{}", latitude, longitude);
            return null;
        }
        if (wait > 0) {
            try {
                logger.debug("Rate limiting: waiting {}ms", wait);
                Thread.sleep(wait);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return null;
            }
        }

        // Make API request
        try {
            String address = reverseGeocode(latitude, longitude);
            if (address != null) {
                // Cache the result
                String cacheKey = String.format(Locale.ROOT, "%.6f,%.6f", latitude, longitude);
                addressCache.put(cacheKey, new CachedAddress(address, latitude, longitude));
                logger.debug("Geocoded {},{} -> {}", latitude, longitude, address);
            }
            return address;
        } catch (IOException e) {
            logger.warn("Nominatim geocoding failed for {},{}: {}", latitude, longitude, e.getMessage());
            return null;
        }
    }

    /**
     * Find cached address within cache distance
     */
    private @Nullable CachedAddress findInCache(double latitude, double longitude) {
        for (CachedAddress cached : addressCache.values()) {
            double distance = calculateDistance(latitude, longitude, cached.latitude, cached.longitude);
            if (distance <= cacheDistance) {
                // Cache hit within distance threshold
                return cached;
            }
        }
        return null;
    }

    /**
     * Calculate distance between two coordinates using Haversine formula
     *
     * @return Distance in meters
     */
    private double calculateDistance(double lat1, double lon1, double lat2, double lon2) {
        final double R = 6371000; // Earth radius in meters
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) + Math.cos(Math.toRadians(lat1))
                * Math.cos(Math.toRadians(lat2)) * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return R * c;
    }

    /**
     * Make reverse geocoding request to Nominatim
     */
    private @Nullable String reverseGeocode(double latitude, double longitude) throws IOException {
        // Locale.ROOT: with a German or Danish JVM locale %.6f prints "51,123456", and Nominatim answers
        // 400 "Parameter 'lon' must be a number" - reported by a user on 2026-10-11
        String url = String.format(Locale.ROOT,
                "%s/reverse?format=json&lat=%.6f&lon=%.6f&accept-language=%s&zoom=18&addressdetails=1", serverUrl,
                latitude, longitude, URLEncoder.encode(language, StandardCharsets.UTF_8));

        logger.debug("Nominatim request: {}", url);

        try {
            // Use java.net.http.HttpClient for HTTP requests
            java.net.http.HttpClient client = java.net.http.HttpClient.newBuilder()
                    .connectTimeout(java.time.Duration.ofSeconds(10)).build();

            java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder().uri(java.net.URI.create(url))
                    .header("User-Agent", USER_AGENT).timeout(java.time.Duration.ofSeconds(10)).GET().build();

            java.net.http.HttpResponse<String> response = client.send(request,
                    java.net.http.HttpResponse.BodyHandlers.ofString());

            int status = response.statusCode();
            long retryAfter = response.headers().firstValue("Retry-After").map(v -> {
                try {
                    return Long.parseLong(v.trim());
                } catch (NumberFormatException e) {
                    return 0L;
                }
            }).orElse(0L);
            long pause = THROTTLE.onResponse(status, System.currentTimeMillis(), retryAfter);
            if (status == 200) {
                JsonObject json = gson.fromJson(response.body(), JsonObject.class);
                if (json != null && json.has("address")) {
                    return formatAddress(json.getAsJsonObject("address"));
                }
            } else if (status == 429 || status == 503) {
                if (pause > 0) {
                    logger.warn("Nominatim rate limit ({}) - geocoding paused for {} s for all devices", status,
                            pause / 1000);
                }
            } else {
                logger.warn("Nominatim returned status {}: {}", status, response.body());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Request interrupted", e);
        }

        return null;
    }

    /**
     * Format address components into readable string
     * Format: Street number, Postcode City, Province, Country
     */
    private @Nullable String formatAddress(JsonObject addressComponents) {
        StringBuilder formatted = new StringBuilder();

        // Street name and number
        String road = getComponent(addressComponents, "road");
        String houseNumber = getComponent(addressComponents, "house_number");
        if (road != null) {
            formatted.append(road);
            if (houseNumber != null) {
                formatted.append(" ").append(houseNumber);
            }
        }

        // Postal code
        String postcode = getComponent(addressComponents, "postcode");

        // City (try multiple fields in order of preference)
        String city = getComponent(addressComponents, "city");
        if (city == null) {
            city = getComponent(addressComponents, "town");
        }
        if (city == null) {
            city = getComponent(addressComponents, "village");
        }
        if (city == null) {
            city = getComponent(addressComponents, "municipality");
        }

        // Add postcode and city together
        if (postcode != null || city != null) {
            if (formatted.length() > 0) {
                formatted.append(", ");
            }
            if (postcode != null) {
                formatted.append(postcode);
                if (city != null) {
                    formatted.append(" ").append(city);
                }
            } else if (city != null) {
                formatted.append(city);
            }
        }

        // Province/State
        String province = getComponent(addressComponents, "state");
        if (province == null) {
            province = getComponent(addressComponents, "province");
        }
        if (province == null) {
            province = getComponent(addressComponents, "region");
        }

        if (province != null) {
            if (formatted.length() > 0) {
                formatted.append(", ");
            }
            formatted.append(province);
        }

        // Country
        String country = getComponent(addressComponents, "country");
        if (country != null) {
            if (formatted.length() > 0) {
                formatted.append(", ");
            }
            formatted.append(country);
        }

        return formatted.length() > 0 ? formatted.toString() : null;
    }

    /**
     * Get address component from JSON, returns null if not present
     */
    private @Nullable String getComponent(JsonObject addressComponents, String key) {
        if (addressComponents.has(key) && !addressComponents.get(key).isJsonNull()) {
            return addressComponents.get(key).getAsString();
        }
        return null;
    }

    /**
     * Clear the address cache
     */
    public void clearCache() {
        addressCache.clear();
        logger.debug("Address cache cleared");
    }

    /**
     * Get cache statistics
     */
    public String getCacheStats() {
        return String.format("Cache size: %d entries", addressCache.size());
    }
}
