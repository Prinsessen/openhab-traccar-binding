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

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.api.ContentResponse;
import org.eclipse.jetty.client.util.StringContentProvider;
import org.eclipse.jetty.http.HttpMethod;
import org.eclipse.jetty.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

/**
 * The {@link TraccarApiClient} communicates with the Traccar REST API.
 *
 * @author Nanna Agesen - Initial contribution
 */
@NonNullByDefault
public class TraccarApiClient {

    private final Logger logger = LoggerFactory.getLogger(TraccarApiClient.class);
    private final Gson gson = new Gson();

    private final HttpClient httpClient;
    private final String baseUrl;
    private final String authHeader;

    public TraccarApiClient(HttpClient httpClient, String baseUrl, String username, String password) {
        this.httpClient = httpClient;
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;

        String credentials = username + ":" + password;
        this.authHeader = "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Authenticate with the Traccar server.
     *
     * @return true if authentication was successful
     */
    public boolean authenticate() {
        try {
            ContentResponse response = httpClient.newRequest(baseUrl + "/api/server").method(HttpMethod.GET)
                    .header("Authorization", authHeader).timeout(10, TimeUnit.SECONDS).send();

            if (response.getStatus() == HttpStatus.OK_200) {
                logger.debug("Successfully authenticated with Traccar server");
                return true;
            } else {
                logger.warn("Authentication failed with status: {}", response.getStatus());
                return false;
            }
        } catch (InterruptedException | TimeoutException | ExecutionException e) {
            logger.warn("Failed to authenticate: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Get all devices from the Traccar server.
     *
     * @return list of devices
     */
    public @Nullable List<Map<String, Object>> getDevices() {
        try {
            ContentResponse response = httpClient.newRequest(baseUrl + "/api/devices").method(HttpMethod.GET)
                    .header("Authorization", authHeader).timeout(10, TimeUnit.SECONDS).send();

            if (response.getStatus() == HttpStatus.OK_200) {
                String content = response.getContentAsString();
                return gson.fromJson(content, new TypeToken<List<Map<String, Object>>>() {
                }.getType());
            } else {
                logger.warn("Failed to get devices, status: {}", response.getStatus());
                return null;
            }
        } catch (InterruptedException | TimeoutException | ExecutionException e) {
            logger.debug("Failed to get devices: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Get the latest position for a specific device.
     *
     * @param deviceId the device ID
     * @return position data
     */
    public @Nullable Map<String, Object> getLatestPosition(int deviceId) {
        try {
            ContentResponse response = httpClient.newRequest(baseUrl + "/api/positions").method(HttpMethod.GET)
                    .param("deviceId", String.valueOf(deviceId)).header("Authorization", authHeader)
                    .timeout(10, TimeUnit.SECONDS).send();

            if (response.getStatus() == HttpStatus.OK_200) {
                String content = response.getContentAsString();
                List<Map<String, Object>> positions = gson.fromJson(content,
                        new TypeToken<List<Map<String, Object>>>() {
                        }.getType());

                if (positions != null && !positions.isEmpty()) {
                    return positions.get(0); // Return the first (latest) position
                }
            } else {
                logger.debug("Failed to get position for device {}, status: {}", deviceId, response.getStatus());
            }
        } catch (InterruptedException | TimeoutException | ExecutionException e) {
            logger.debug("Failed to get position for device {}: {}", deviceId, e.getMessage());
        }
        return null;
    }

    /**
     * Get geofences for a specific device.
     *
     * @param deviceId the device ID
     * @return list of geofences
     */
    public @Nullable List<Map<String, Object>> getGeofences(int deviceId) {
        try {
            ContentResponse response = httpClient.newRequest(baseUrl + "/api/geofences").method(HttpMethod.GET)
                    .param("deviceId", String.valueOf(deviceId)).header("Authorization", authHeader)
                    .timeout(10, TimeUnit.SECONDS).send();

            if (response.getStatus() == HttpStatus.OK_200) {
                String content = response.getContentAsString();
                return gson.fromJson(content, new TypeToken<List<Map<String, Object>>>() {
                }.getType());
            } else {
                logger.debug("Failed to get geofences for device {}, status: {}", deviceId, response.getStatus());
            }
        } catch (InterruptedException | TimeoutException | ExecutionException e) {
            logger.debug("Failed to get geofences for device {}: {}", deviceId, e.getMessage());
        }
        return null;
    }

    /**
     * Get device information including status.
     *
     * @param deviceId the device ID
     * @return device data
     */
    public @Nullable Map<String, Object> getDevice(int deviceId) {
        try {
            ContentResponse response = httpClient.newRequest(baseUrl + "/api/devices/" + deviceId)
                    .method(HttpMethod.GET).header("Authorization", authHeader).timeout(10, TimeUnit.SECONDS).send();

            if (response.getStatus() == HttpStatus.OK_200) {
                String content = response.getContentAsString();
                return gson.fromJson(content, new TypeToken<Map<String, Object>>() {
                }.getType());
            } else {
                logger.debug("Failed to get device {}, status: {}", deviceId, response.getStatus());
            }
        } catch (InterruptedException | TimeoutException | ExecutionException e) {
            logger.debug("Failed to get device {}: {}", deviceId, e.getMessage());
        }
        return null;
    }
    /**
     * Sends a custom (text) command to a device through Traccar.
     *
     * @return the HTTP status: 200 = delivered to a connected tracker, 202 = queued by Traccar until the
     *         tracker next connects, anything else = not sent (the body is logged at debug level)
     */
    public int sendCustomCommand(int deviceId, String data) {
        try {
            Map<String, Object> body = Map.of("deviceId", deviceId, "type", "custom", "attributes",
                    Map.of("data", data));
            ContentResponse response = httpClient.newRequest(baseUrl + "/api/commands/send").method(HttpMethod.POST)
                    .header("Authorization", authHeader).header("Accept", "application/json")
                    .content(new StringContentProvider(gson.toJson(body)), "application/json")
                    .timeout(15, TimeUnit.SECONDS).send();
            int status = response.getStatus();
            if (status != HttpStatus.OK_200 && status != HttpStatus.ACCEPTED_202) {
                logger.debug("Command to device {} not sent, status {}: {}", deviceId, status,
                        response.getContentAsString());
            }
            return status;
        } catch (InterruptedException | TimeoutException | ExecutionException e) {
            logger.debug("Failed to send command to device {}: {}", deviceId, e.getMessage());
            return -1;
        }
    }

    /**
     * The tracker's replies (Traccar {@code commandResult} events) for a device since a moment, oldest
     * first, each as the event map Traccar returns (attributes.result holds the text, eventTime when).
     */
    public List<Map<String, Object>> getCommandResults(int deviceId, Instant since) {
        try {
            ContentResponse response = httpClient.newRequest(baseUrl + "/api/reports/events").method(HttpMethod.GET)
                    .param("deviceId", String.valueOf(deviceId)).param("type", "commandResult")
                    .param("from", since.toString()).param("to", Instant.now().plusSeconds(60).toString())
                    .header("Authorization", authHeader).header("Accept", "application/json")
                    .timeout(15, TimeUnit.SECONDS).send();
            if (response.getStatus() == HttpStatus.OK_200) {
                List<Map<String, Object>> events = gson.fromJson(response.getContentAsString(),
                        new TypeToken<List<Map<String, Object>>>() {
                        }.getType());
                return events != null ? events : new ArrayList<>();
            }
            logger.debug("Command results for device {}: status {}", deviceId, response.getStatus());
        } catch (InterruptedException | TimeoutException | ExecutionException e) {
            logger.debug("Failed to read command results for device {}: {}", deviceId, e.getMessage());
        }
        return new ArrayList<>();
    }
}
