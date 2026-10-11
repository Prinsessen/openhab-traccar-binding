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

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jetty.client.HttpClient;
import org.openhab.core.thing.Bridge;
import org.openhab.core.thing.ChannelUID;
import org.openhab.core.thing.ThingStatus;
import org.openhab.core.thing.ThingStatusDetail;
import org.openhab.core.thing.binding.BaseBridgeHandler;
import org.openhab.core.thing.binding.ThingHandlerService;
import org.openhab.core.types.Command;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link TraccarServerHandler} handles communication with the Traccar server
 * and manages device discovery.
 *
 * @author Nanna Agesen - Initial contribution
 */
@NonNullByDefault
public class TraccarServerHandler extends BaseBridgeHandler {

    private final Logger logger = LoggerFactory.getLogger(TraccarServerHandler.class);
    private final HttpClient httpClient;

    private @Nullable TraccarServerConfiguration config;
    private @Nullable ScheduledFuture<?> pollingJob;
    private @Nullable TraccarApiClient apiClient;
    private @Nullable TraccarWebhookServer webhookServer;

    public TraccarServerHandler(Bridge bridge, HttpClient httpClient) {
        super(bridge);
        this.httpClient = httpClient;
    }

    @Override
    public void initialize() {
        config = getConfigAs(TraccarServerConfiguration.class);

        if (config.url.isEmpty() || config.username.isEmpty() || config.password.isEmpty()) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR,
                    "Server URL, username, and password must be configured");
            return;
        }

        // Initialize API client
        apiClient = new TraccarApiClient(httpClient, config.url, config.username, config.password);

        // Start webhook server for geofence events
        try {
            webhookServer = new TraccarWebhookServer(this, config.webhookPort);
            webhookServer.start();
            logger.info("Traccar webhook server started on port {}", config.webhookPort);
        } catch (Exception e) {
            logger.error("Failed to start webhook server: {}", e.getMessage());
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR,
                    "Failed to start webhook server: " + e.getMessage());
            return;
        }

        updateStatus(ThingStatus.UNKNOWN);

        // Start polling for device positions
        scheduler.execute(this::connect);
    }

    private void connect() {
        try {
            TraccarApiClient client = apiClient;
            if (client != null && client.authenticate()) {
                updateStatus(ThingStatus.ONLINE);
                startPolling();
            } else {
                updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR, "Authentication failed");
            }
        } catch (Exception e) {
            logger.error("Failed to connect to Traccar server: {}", e.getMessage());
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR,
                    "Connection failed: " + e.getMessage());
        }
    }

    private void startPolling() {
        TraccarServerConfiguration configuration = config;
        if (configuration != null) {
            pollingJob = scheduler.scheduleWithFixedDelay(this::poll, 0, configuration.refreshInterval,
                    TimeUnit.SECONDS);
        }
    }

    private void poll() {
        try {
            TraccarApiClient client = apiClient;
            if (client != null) {
                // Update device positions for all child things
                getThing().getThings().forEach(thing -> {
                    if (thing.getHandler() instanceof TraccarDeviceHandler deviceHandler) {
                        deviceHandler.updatePosition();
                    }
                });
            }
        } catch (Exception e) {
            logger.debug("Polling error: {}", e.getMessage());
        }
    }

    @Override
    public void dispose() {
        ScheduledFuture<?> job = pollingJob;
        if (job != null) {
            job.cancel(true);
            pollingJob = null;
        }

        TraccarWebhookServer webhook = webhookServer;
        if (webhook != null) {
            try {
                webhook.stop();
            } catch (Exception e) {
                logger.warn("Error stopping webhook server: {}", e.getMessage());
            }
            webhookServer = null;
        }

        super.dispose();
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        // Bridge doesn't handle commands
    }

    @Override
    public Collection<Class<? extends ThingHandlerService>> getServices() {
        return Set.of(TraccarDiscoveryService.class);
    }

    public @Nullable TraccarApiClient getApiClient() {
        return apiClient;
    }

    public void handleWebhookEvent(Map<String, Object> eventData) {
        // Extract event data - Traccar sends nested structure
        Object eventObj = eventData.get("event");
        final Map<String, Object> event;
        if (eventObj instanceof Map<?, ?>) {
            @SuppressWarnings("unchecked")
            Map<String, Object> eventMap = (Map<String, Object>) eventObj;
            event = eventMap;
        } else {
            event = null;
        }

        // Extract device ID from event or top-level data or position data
        Integer deviceId = null;
        if (event != null && event.get("deviceId") instanceof Number) {
            deviceId = ((Number) event.get("deviceId")).intValue();
        } else if (eventData.get("deviceId") instanceof Number) {
            deviceId = ((Number) eventData.get("deviceId")).intValue();
        } else {
            // Try to get deviceId from position object (for position-only forwards)
            Object positionObj = eventData.get("position");
            if (positionObj instanceof Map<?, ?>) {
                @SuppressWarnings("unchecked")
                Map<String, Object> positionMap = (Map<String, Object>) positionObj;
                if (positionMap.get("deviceId") instanceof Number) {
                    deviceId = ((Number) positionMap.get("deviceId")).intValue();
                }
            }
        }

        if (deviceId == null) {
            logger.debug("No device ID in webhook event");
            return;
        }

        final Integer finalDeviceId = deviceId;
        getThing().getThings().forEach(thing -> {
            if (thing.getHandler() instanceof TraccarDeviceHandler deviceHandler) {
                TraccarDeviceConfiguration deviceConfig = deviceHandler.getConfiguration();
                if (deviceConfig != null && deviceConfig.deviceId == finalDeviceId) {
                    logger.debug("Processing webhook for device {}", finalDeviceId);

                    // Handle position updates from webhook
                    Object positionObj = eventData.get("position");
                    if (positionObj instanceof Map<?, ?>) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> positionMap = (Map<String, Object>) positionObj;
                        logger.debug("Updating position from webhook");
                        deviceHandler.updatePositionFromWebhook(positionMap);
                    }

                    // Handle device status updates from webhook
                    Object deviceObj = eventData.get("device");
                    if (deviceObj instanceof Map<?, ?>) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> deviceMap = (Map<String, Object>) deviceObj;
                        Object statusObj = deviceMap.get("status");
                        if (statusObj instanceof String status) {
                            logger.debug("Updating device status from webhook: {}", status);
                            deviceHandler.updateStatus(status);
                        }
                    }

                    // Handle geofence events
                    if (event != null) {
                        Object eventTypeObj = event.get("type");
                        if (eventTypeObj instanceof String eventType) {
                            if ("geofenceEnter".equals(eventType) || "geofenceExit".equals(eventType)) {
                                logger.debug("Processing geofence event: {}", eventType);
                                deviceHandler.handleGeofenceEvent(eventData);
                            } else if ("commandResult".equals(eventType)) {
                                // the tracker's reply to a command - the webhook is the fast path, the
                                // device handler's poll the fallback
                                deviceHandler.handleCommandResult(eventData);
                            }
                        }
                    }
                }
            }
        });
    }

    /**
     * Get the configured speed unit (kmh, mph, or knots)
     */
    public String getSpeedUnit() {
        TraccarServerConfiguration cfg = config;
        return cfg != null ? cfg.speedUnit : "kmh";
    }

    /**
     * Get the server configuration (for accessing geocoding settings)
     */
    public @Nullable TraccarServerConfiguration getConfiguration() {
        return config;
    }
}
