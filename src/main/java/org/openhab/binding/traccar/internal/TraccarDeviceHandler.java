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

import static org.openhab.binding.traccar.internal.TraccarBindingConstants.*;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import javax.measure.Unit;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.core.library.types.DateTimeType;
import org.openhab.core.library.types.DecimalType;
import org.openhab.core.library.types.OnOffType;
import org.openhab.core.library.types.OpenClosedType;
import org.openhab.core.library.types.PointType;
import org.openhab.core.library.types.QuantityType;
import org.openhab.core.library.types.StringType;
import org.openhab.core.library.unit.ImperialUnits;
import org.openhab.core.library.unit.MetricPrefix;
import org.openhab.core.library.unit.SIUnits;
import org.openhab.core.library.unit.Units;
import org.openhab.core.library.unit.MetricPrefix;
import org.openhab.core.thing.Bridge;
import org.openhab.core.thing.Channel;
import org.openhab.core.thing.ChannelGroupUID;
import org.openhab.core.thing.ChannelUID;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingStatus;
import org.openhab.core.thing.ThingStatusDetail;
import org.openhab.core.thing.binding.BaseThingHandler;
import org.openhab.core.thing.binding.BridgeHandler;
import org.openhab.core.thing.binding.ThingHandlerCallback;
import org.openhab.core.thing.binding.builder.ChannelBuilder;
import org.openhab.core.thing.binding.builder.ThingBuilder;
import org.openhab.core.thing.type.ChannelDefinition;
import org.openhab.core.thing.type.ThingType;
import org.openhab.core.thing.type.ThingTypeRegistry;
import org.openhab.core.types.Command;
import org.openhab.core.types.RefreshType;
import org.openhab.core.types.State;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link TraccarDeviceHandler} handles commands for Traccar tracked devices
 * and updates their position and geofence status.
 *
 * @author Nanna Agesen - Initial contribution
 */
@NonNullByDefault
public class TraccarDeviceHandler extends BaseThingHandler {

    private final Logger logger = LoggerFactory.getLogger(TraccarDeviceHandler.class);

    private @Nullable TraccarDeviceConfiguration config;
    private final Map<String, Integer> macToBeaconSlot = new HashMap<>();
    private final Map<Integer, String> beaconSlotToName = new HashMap<>();
    private @Nullable NominatimGeocoder geocoder;

    /**
     * Device time of the newest record applied. Records do not always arrive in order - after a cold
     * boot a Teltonika uploads its buffer, and with a long send period they come in batches - and an
     * older record must not overwrite a newer state: the last update time went backwards, and a CAN
     * car read "unlocked, gear N" after its "locked, P" record.
     */
    private @Nullable ZonedDateTime newestRecord;

    /**
     * The records of one webhook batch are handled on parallel threads: the order check and the
     * channel updates of a record happen under this lock, or an older record that passed the check
     * first is applied last.
     */
    private final Object recordLock = new Object();

    /**
     * Event time of the newest geofence event applied. Traccar forwards each event as its own HTTP
     * request, so two events a second apart (a geofenceExit and a geofenceEnter as the GPS crosses
     * the fence) can arrive in either order; the older one must not win.
     */
    private @Nullable ZonedDateTime newestGeofenceEvent;

    /** A device clock this far ahead of Traccar's server clock is not trusted to move the bar. */
    private static final long MAX_CLOCK_AHEAD_MINUTES = 10;

    /**
     * Traccar's id of the newest position applied. The same position reaches the binding twice when
     * Traccar forwards both the position and an event that carries it (an alarm, a geofence event), and
     * the API poll can hand over a position the webhook already delivered.
     */
    private long lastPositionId = -1;

    // ---- commands to the tracker (Command channel, Low Voltage Level channel) ----
    private static final DateTimeFormatter HHMMSS = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final int PARAM_LOW_VOLTAGE_LEVEL = 105;
    private static final long RESULT_POLL_SECONDS = 20;
    private static final long SAFETY_NET_CHECK_MINUTES = 10;
    private @Nullable ScheduledFuture<?> resultPoll;
    private @Nullable ScheduledFuture<?> safetyNet;
    private @Nullable Instant commandSentAt;
    private @Nullable Instant lastResultAt;
    /** The Low Voltage Level the tracker last confirmed (mV), or -1 before the first reply. */
    private long lowVoltageConfirmedMv = -1;
    /** When a level below the normal one was confirmed; null while at the normal level or unknown. */
    private @Nullable Instant lowVoltageLoweredSince;
    /** The level (mV) last sent through the channel and not yet confirmed, or -1. */
    private long lowVoltagePendingMv = -1;

    private final ThingTypeRegistry thingTypeRegistry;

    public TraccarDeviceHandler(Thing thing, ThingTypeRegistry thingTypeRegistry) {
        super(thing);
        this.thingTypeRegistry = thingTypeRegistry;
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        if (command instanceof RefreshType) {
            updatePosition();
            return;
        }
        String channel = channelUID.getId();
        if (CHANNEL_COMMAND_SEND.equals(channel)) {
            String text = command.toString().trim();
            if (!CommandSupport.isAllowed(profile().allowedCommands, text)) {
                updateState(CHANNEL_COMMAND_STATUS, new StringType("refused: not in allowedCommands - " + text));
                logger.warn("Device {}: command refused, not in allowedCommands: {}", profile().deviceId, text);
                return;
            }
            sendCommandToTracker(text, false);
        } else if (CHANNEL_LOW_VOLTAGE_LEVEL.equals(channel)) {
            long mv = toMillivolts(command);
            if (mv <= 0) {
                logger.warn("Device {}: Low Voltage Level command not understood: {}", profile().deviceId, command);
                return;
            }
            updateState(CHANNEL_LOW_VOLTAGE_STATUS, new StringType(mv + " mV sent " + now() + ", waiting for the tracker"));
            lowVoltagePendingMv = mv;
            sendCommandToTracker("setparam " + PARAM_LOW_VOLTAGE_LEVEL + ":" + mv, true);
        }
    }

    /** A voltage command as mV: QuantityType in any electric-potential unit, or a plain number (mV above 100, else V). */
    private static long toMillivolts(Command command) {
        if (command instanceof QuantityType<?> q) {
            QuantityType<?> v = q.toUnit(Units.VOLT);
            return v != null ? Math.round(v.doubleValue() * 1000.0) : -1;
        }
        if (command instanceof DecimalType d) {
            double n = d.doubleValue();
            return Math.round(n > 100 ? n : n * 1000.0);
        }
        try {
            double n = Double.parseDouble(command.toString().replace("V", "").trim());
            return Math.round(n > 100 ? n : n * 1000.0);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private String now() {
        return ZonedDateTime.now().format(HHMMSS);
    }

    /**
     * Sends a text command through Traccar and starts asking for the reply. Replies also come through
     * the webhook (handleCommandResult) when Traccar forwards events; the poll is the fallback and stops
     * at the first reply newer than the command, or after commandResultTimeout.
     */
    private synchronized void sendCommandToTracker(String text, boolean lowVoltage) {
        TraccarApiClient api = apiClient();
        if (api == null) {
            updateState(CHANNEL_COMMAND_STATUS, new StringType("failed: no connection to Traccar"));
            return;
        }
        int status = api.sendCustomCommand(profile().deviceId, text);
        String when = now();
        String result = status == 200 ? "delivered " + when
                : status == 202 ? "queued " + when + " (tracker offline, Traccar delivers when it connects)"
                        : "failed: HTTP " + status + " " + when;
        updateState(CHANNEL_COMMAND_SEND, new StringType(text));
        updateState(CHANNEL_COMMAND_STATUS, new StringType(result));
        logger.info("Device {}: command \"{}\" {}", profile().deviceId, text, result);
        if (status != 200 && status != 202) {
            if (lowVoltage) {
                updateState(CHANNEL_LOW_VOLTAGE_STATUS, new StringType("send failed " + when + " (HTTP " + status + ")"));
            }
            return;
        }
        commandSentAt = Instant.now();
        startResultPoll();
    }

    private @Nullable TraccarApiClient apiClient() {
        Bridge bridge = getBridge();
        if (bridge != null && bridge.getHandler() instanceof TraccarServerHandler serverHandler) {
            return serverHandler.getApiClient();
        }
        return null;
    }

    private void startResultPoll() {
        int timeout = profile().commandResultTimeout;
        ScheduledFuture<?> running = resultPoll;
        if (running != null) {
            running.cancel(false);
        }
        if (timeout <= 0) {
            return;
        }
        Instant deadline = Instant.now().plusSeconds(timeout);
        resultPoll = scheduler.scheduleWithFixedDelay(() -> {
            try {
                if (pollCommandResult() || Instant.now().isAfter(deadline)) {
                    ScheduledFuture<?> me = resultPoll;
                    if (me != null) {
                        me.cancel(false);
                    }
                    resultPoll = null;
                }
            } catch (Exception e) {
                logger.debug("Device {}: result poll failed: {}", profile().deviceId, e.getMessage());
            }
        }, RESULT_POLL_SECONDS, RESULT_POLL_SECONDS, TimeUnit.SECONDS);
    }

    /** Asks Traccar for replies since the command was sent; true once one newer than the command is applied. */
    private boolean pollCommandResult() {
        TraccarApiClient api = apiClient();
        Instant sent = commandSentAt;
        if (api == null || sent == null) {
            return true;
        }
        boolean applied = false;
        for (Map<String, Object> event : api.getCommandResults(profile().deviceId, sent.minusSeconds(5))) {
            if (applyCommandResult(event)) {
                applied = true;
            }
        }
        return applied;
    }

    /** A Traccar commandResult event, from the webhook (event forwarding) or the poll. */
    public void handleCommandResult(Map<String, Object> eventData) {
        Object eventObj = eventData.get("event");
        if (eventObj instanceof Map<?, ?>) {
            @SuppressWarnings("unchecked")
            Map<String, Object> event = (Map<String, Object>) eventObj;
            applyCommandResult(event);
        }
    }

    /**
     * Applies one commandResult event: the result channels, and the Low Voltage Level when the reply
     * confirms parameter 105. Returns true when the event is newer than the last one applied.
     */
    private synchronized boolean applyCommandResult(Map<String, Object> event) {
        Object attributesObj = event.get("attributes");
        String text = "";
        if (attributesObj instanceof Map<?, ?> attributes) {
            Object result = attributes.get("result");
            text = result != null ? result.toString().trim() : "";
        }
        ZonedDateTime at = parseTime(event.get("eventTime"));
        if (at == null) {
            at = parseTime(event.get("serverTime"));
        }
        Instant when = at != null ? at.toInstant() : Instant.now();
        Instant last = lastResultAt;
        if (last != null && !when.isAfter(last)) {
            return false;
        }
        Instant sent = commandSentAt;
        if (sent != null && when.isBefore(sent.minusSeconds(5))) {
            return false; // an old reply the poll window still shows
        }
        lastResultAt = when;
        updateState(CHANNEL_COMMAND_RESULT, new StringType(text));
        updateState(CHANNEL_COMMAND_RESULT_TIME, new DateTimeType(when.atZone(ZoneId.systemDefault())));
        logger.info("Device {}: tracker replied \"{}\"", profile().deviceId, text);
        Long mv = CommandSupport.parseParameterReply(text).get(PARAM_LOW_VOLTAGE_LEVEL);
        if (mv != null) {
            confirmLowVoltage(mv, when);
        } else if (lowVoltagePendingMv > 0 && text.startsWith("Config same")) {
            // a Teltonika answers "Config same for all parameters" to a setparam that changes nothing -
            // the tracker runs with the value sent, so it counts as confirmed
            confirmLowVoltage(lowVoltagePendingMv, when);
        }
        return true;
    }

    private void confirmLowVoltage(long mv, Instant when) {
        lowVoltageConfirmedMv = mv;
        lowVoltagePendingMv = -1;
        updateState(CHANNEL_LOW_VOLTAGE_LEVEL, new QuantityType<>(mv, MetricPrefix.MILLI(Units.VOLT)));
        int normal = profile().lowVoltageLevelNormal;
        if (normal > 0 && mv < normal) {
            if (lowVoltageLoweredSince == null) {
                lowVoltageLoweredSince = when;
            }
        } else {
            lowVoltageLoweredSince = null;
        }
        updateState(CHANNEL_LOW_VOLTAGE_STATUS,
                new StringType(mv + " mV confirmed " + when.atZone(ZoneId.systemDefault()).format(HHMMSS)));
    }

    /**
     * The safety net: a Low Voltage Level below the normal one that has stood for lowVoltageRestoreHours
     * is put back, because a tracker that never sees its ignition go off never sleeps.
     */
    private void startSafetyNet() {
        ScheduledFuture<?> running = safetyNet;
        if (running != null) {
            running.cancel(false);
        }
        if (profile().lowVoltageLevelNormal <= 0) {
            return;
        }
        safetyNet = scheduler.scheduleWithFixedDelay(() -> {
            try {
                Instant since = lowVoltageLoweredSince;
                int normal = profile().lowVoltageLevelNormal;
                if (since != null && normal > 0
                        && Instant.now().isAfter(since.plusSeconds(profile().lowVoltageRestoreHours * 3600L))) {
                    logger.warn("Device {}: Low Voltage Level {} mV has stood for {} h - restoring {} mV",
                            profile().deviceId, lowVoltageConfirmedMv, profile().lowVoltageRestoreHours, normal);
                    updateState(CHANNEL_LOW_VOLTAGE_STATUS, new StringType(normal + " mV restored by the binding "
                            + now() + " after " + profile().lowVoltageRestoreHours + " h lowered, waiting for the tracker"));
                    lowVoltageLoweredSince = null; // one attempt per lowering; the reply sets it again if still low
                    lowVoltagePendingMv = normal;
                    sendCommandToTracker("setparam " + PARAM_LOW_VOLTAGE_LEVEL + ":" + normal, true);
                }
            } catch (Exception e) {
                logger.debug("Device {}: safety net failed: {}", profile().deviceId, e.getMessage());
            }
        }, SAFETY_NET_CHECK_MINUTES, SAFETY_NET_CHECK_MINUTES, TimeUnit.MINUTES);
    }

    @Override
    public void dispose() {
        ScheduledFuture<?> f = resultPoll;
        if (f != null) {
            f.cancel(true);
            resultPoll = null;
        }
        f = safetyNet;
        if (f != null) {
            f.cancel(true);
            safetyNet = null;
        }
        super.dispose();
    }

    @Override
    public void initialize() {
        config = getConfigAs(TraccarDeviceConfiguration.class);

        Bridge bridge = getBridge();
        if (bridge == null) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR, "No bridge configured");
            return;
        }

        BridgeHandler bridgeHandler = bridge.getHandler();
        if (!(bridgeHandler instanceof TraccarServerHandler)) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR, "Invalid bridge handler");
            return;
        }

        restoreLostChannels();
        updateCanChannels();

        updateStatus(ThingStatus.UNKNOWN);
        scheduler.execute(this::connect);
    }

    private TraccarDeviceConfiguration profile() {
        TraccarDeviceConfiguration configuration = config;
        return configuration != null ? configuration : new TraccarDeviceConfiguration();
    }

    /**
     * Puts back every channel the thing type defines that the thing does not have.
     *
     * <p>
     * When the binding's JAR is replaced, core can rebuild a thing from a .things file before this
     * bundle has registered its channel types; it then logs "Could not create channel ... channel type
     * ... could not be found" once per channel and creates the thing without them. State updates still
     * reach the items (they travel by link), so nothing looks wrong, but the thing shows no channels and
     * stays that way until the bundle is restarted. Seen on a motorcycle tracker after the third JAR
     * replace of an evening: all 101 channels gone. By the time the handler initializes, the channel
     * types exist, so the builders come from core's registry exactly as the thing type declares them.
     */
    private void restoreLostChannels() {
        ThingHandlerCallback callback = getCallback();
        ThingType thingType = thingTypeRegistry.getThingType(getThing().getThingTypeUID());
        if (callback == null || thingType == null) {
            return;
        }
        List<Channel> missing = new ArrayList<>();
        for (ChannelDefinition definition : thingType.getChannelDefinitions()) {
            ChannelUID uid = new ChannelUID(getThing().getUID(), definition.getId());
            if (getThing().getChannel(uid) != null) {
                continue;
            }
            try {
                ChannelBuilder builder = callback.createChannelBuilder(uid, definition.getChannelTypeUID());
                String label = definition.getLabel();
                if (label != null) {
                    builder.withLabel(label);
                }
                missing.add(builder.build());
            } catch (IllegalArgumentException e) {
                logger.debug("Device {}: channel {} not put back: {}", profile().deviceId, definition.getId(),
                        e.getMessage());
            }
        }
        if (!missing.isEmpty()) {
            ThingBuilder builder = editThing();
            missing.forEach(builder::withChannel);
            updateThing(builder.build());
            logger.info(
                    "Device {}: {} channel(s) put back that the thing had lost (a JAR replace can rebuild a .things thing before the channel types exist)",
                    profile().deviceId, missing.size());
        }
    }

    /**
     * Adds the {@code can} channel group with {@code canAdapter=lvcan}, and removes it without, so a
     * motorcycle never shows an HV battery. Adds only what is missing (a newer release may bring new
     * channels), and keeps channels already there and linked. The builders come from core's registry,
     * the same way for a thing from a .things file as for a managed one.
     */
    private void updateCanChannels() {
        ThingHandlerCallback callback = getCallback();
        if (callback == null) {
            return;
        }
        List<Channel> present = getThing().getChannelsOfGroup(CHANNEL_GROUP_CAN);
        if (!profile().hasCanAdapter()) {
            if (!present.isEmpty()) {
                updateThing(editThing().withoutChannels(present).build());
                logger.info("Device {}: {} CAN channel(s) removed (canAdapter is not lvcan)", profile().deviceId,
                        present.size());
            }
            return;
        }
        List<ChannelBuilder> builders;
        try {
            builders = callback.createChannelBuilders(new ChannelGroupUID(getThing().getUID(), CHANNEL_GROUP_CAN),
                    CHANNEL_GROUP_TYPE_CAN);
        } catch (RuntimeException e) {
            logger.warn("Device {}: CAN channels could not be created: {}", profile().deviceId, e.getMessage());
            return;
        }
        List<Channel> missing = new ArrayList<>();
        for (ChannelBuilder builder : builders) {
            Channel channel = builder.build();
            if (getThing().getChannel(channel.getUID()) == null) {
                missing.add(channel);
            }
        }
        if (!missing.isEmpty()) {
            // withChannel adds; withChannels would REPLACE every channel the thing has
            ThingBuilder builder = editThing();
            missing.forEach(builder::withChannel);
            updateThing(builder.build());
            logger.info("Device {}: {} CAN channel(s) added (canAdapter=lvcan)", profile().deviceId, missing.size());
        }
    }

    private void connect() {
        Bridge bridge = getBridge();
        if (bridge != null && bridge.getStatus() == ThingStatus.ONLINE) {
            // Initialize Nominatim geocoder from server configuration
            TraccarServerHandler serverHandler = (TraccarServerHandler) bridge.getHandler();
            if (serverHandler != null) {
                TraccarServerConfiguration serverConfig = serverHandler.getConfiguration();
                if (serverConfig.useNominatim) {
                    geocoder = new NominatimGeocoder(serverConfig.nominatimUrl, serverConfig.nominatimLanguage,
                            serverConfig.geocodingCacheDistance);
                    logger.info("Nominatim geocoding enabled for device {} (server: {}, language: {})", config.deviceId,
                            serverConfig.nominatimUrl, serverConfig.nominatimLanguage);
                }
            }

            // Initialize beacon MAC assignments from configuration
            logger.debug("ABOUT TO INITIALIZE BEACON MAC MAPPINGS FOR DEVICE {}",
                    config != null ? config.deviceId : "NULL");
            try {
                initializeMacMappingFromConfig();
                initializeBeaconNamesFromProperties();
                logger.debug("BEACON MAC INITIALIZATION COMPLETED");
            } catch (Exception e) {
                logger.error("===== EXCEPTION during beacon MAC initialization: {} =====", e.getMessage(), e);
            }

            updateStatus(ThingStatus.ONLINE);
            updatePosition();
            startSafetyNet();
        } else {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.BRIDGE_OFFLINE);
        }
    }

    public void updatePosition() {
        TraccarDeviceConfiguration configuration = config;
        if (configuration == null) {
            return;
        }

        Bridge bridge = getBridge();
        if (bridge == null) {
            return;
        }

        BridgeHandler bridgeHandler = bridge.getHandler();
        if (!(bridgeHandler instanceof TraccarServerHandler serverHandler)) {
            return;
        }

        TraccarApiClient apiClient = serverHandler.getApiClient();
        if (apiClient == null) {
            return;
        }

        try {
            // Get device info for status
            Map<String, Object> device = apiClient.getDevice(configuration.deviceId);
            if (device != null) {
                Object statusObj = device.get("status");
                if (statusObj instanceof String status) {
                    updateState(CHANNEL_STATUS, new StringType(status));
                }
            }

            // Get position data
            Map<String, Object> position = apiClient.getLatestPosition(configuration.deviceId);
            if (position != null) {
                updatePositionChannels(position);
            }
        } catch (Exception e) {
            logger.debug("Failed to update position for device {}: {}", configuration.deviceId, e.getMessage());
        }
    }

    /**
     * Initialize MAC to beacon slot mapping from thing configuration.
     * This ensures consistent beacon assignments across binding restarts.
     */
    private void initializeMacMappingFromConfig() {
        logger.debug("Initializing beacon MAC mappings from configuration");
        for (int slot = 1; slot <= 4; slot++) {
            String paramName = "beacon" + slot + "Mac";
            Object macObj = getThing().getConfiguration().get(paramName);
            logger.debug("Beacon slot {} config param '{}' = {}", slot, paramName, macObj);
            if (macObj instanceof String mac && !mac.isBlank()) {
                macToBeaconSlot.put(mac.toLowerCase().trim(), slot);
                logger.debug("Configured beacon MAC {} for slot {}", mac, slot);
            }
        }
        logger.debug("Beacon MAC mapping initialized with {} entries", macToBeaconSlot.size());
    }

    /**
     * Initialize beacon names from Thing properties.
     * Restores last known beacon names after binding restart.
     */
    private void initializeBeaconNamesFromProperties() {
        logger.debug("Initializing beacon names from Thing properties");
        for (int slot = 1; slot <= 4; slot++) {
            String propName = "beacon" + slot + "Name";
            String storedName = getThing().getProperties().get(propName);
            if (storedName != null && !storedName.isBlank()) {
                beaconSlotToName.put(slot, storedName);
                logger.debug("Restored beacon {} name: {}", slot, storedName);
                // Update channel with restored name
                updateState("beacon" + slot + "-name", new StringType(storedName));
            }
        }
        logger.debug("Beacon name restoration completed with {} entries", beaconSlotToName.size());
    }

    private void updatePositionChannels(Map<String, Object> position) {
        ZonedDateTime deviceTime = parseTime(position.get("deviceTime"));
        synchronized (recordLock) {
            if (position.get("id") instanceof Number idNumber) {
                long id = idNumber.longValue();
                if (id > 0 && id == lastPositionId) {
                    logger.debug("Device {}: position {} already applied, skipped", profile().deviceId, id);
                    return;
                }
                lastPositionId = id;
            }
            ZonedDateTime newest = newestRecord;
            if (deviceTime != null && newest != null && deviceTime.isBefore(newest)) {
                logger.debug("Device {}: record from {} is older than the newest applied ({}), skipped",
                        profile().deviceId, deviceTime, newest);
                return;
            }
            if (deviceTime != null) {
                ZonedDateTime serverTime = parseTime(position.get("serverTime"));
                if (serverTime == null || !deviceTime.isAfter(serverTime.plusMinutes(MAX_CLOCK_AHEAD_MINUTES))) {
                    newestRecord = deviceTime;
                }
            }
            applyRecord(position, deviceTime);
        }
    }

    private void applyRecord(Map<String, Object> position, @Nullable ZonedDateTime deviceTime) {
        // Update position (latitude, longitude, altitude)
        Object latObj = position.get("latitude");
        Object lonObj = position.get("longitude");
        if (latObj instanceof Number && lonObj instanceof Number) {
            double latitude = ((Number) latObj).doubleValue();
            double longitude = ((Number) lonObj).doubleValue();
            Object altObj = position.get("altitude");
            double altitude = altObj instanceof Number ? ((Number) altObj).doubleValue() : 0.0;

            PointType point = new PointType(new DecimalType(latitude), new DecimalType(longitude),
                    new DecimalType(altitude));
            updateState(CHANNEL_POSITION, point);
        }

        // Update speed (Traccar reports in knots, convert to configured unit)
        Object speedObj = position.get("speed");
        if (speedObj instanceof Number) {
            double speedKnots = ((Number) speedObj).doubleValue();
            double convertedSpeed;
            Unit<?> speedUnit;

            TraccarServerHandler bridge = (TraccarServerHandler) getBridge().getHandler();
            String speedUnitConfig = bridge != null ? bridge.getSpeedUnit() : "kmh";

            switch (speedUnitConfig) {
                case "mph":
                    convertedSpeed = speedKnots * 1.15078; // knots to mph
                    speedUnit = ImperialUnits.MILES_PER_HOUR;
                    break;
                case "knots":
                    convertedSpeed = speedKnots; // no conversion
                    speedUnit = Units.KNOT;
                    break;
                case "kmh":
                default:
                    convertedSpeed = speedKnots * 1.852; // knots to km/h
                    speedUnit = SIUnits.KILOMETRE_PER_HOUR;
                    break;
            }

            // Filter GPS noise: speeds below threshold are considered stationary
            // This prevents false motion detection from GPS signal drift and small movements
            TraccarServerConfiguration serverConfig = bridge != null ? bridge.getConfiguration() : null;
            double thresholdKmh = serverConfig != null ? serverConfig.speedThreshold : 2.0;
            double thresholdKnots = thresholdKmh / 1.852; // Convert km/h threshold to knots
            if (speedKnots < thresholdKnots) {
                convertedSpeed = 0;
            }

            updateState(CHANNEL_SPEED, new QuantityType<>(convertedSpeed, speedUnit));
        }

        // Update altitude (elevation)
        Object altObj = position.get("altitude");
        if (altObj instanceof Number) {
            double altitude = ((Number) altObj).doubleValue();
            updateState(CHANNEL_ALTITUDE, new QuantityType<>(altitude, SIUnits.METRE));
        }

        // Update GPS validity
        Object validObj = position.get("valid");
        if (validObj instanceof Boolean valid) {
            updateState(CHANNEL_VALID, OnOffType.from(valid));
        }

        // Update protocol
        Object protocolObj = position.get("protocol");
        String protocol = null;
        if (protocolObj != null) {
            protocol = protocolObj.toString();
            updateState(CHANNEL_PROTOCOL, new StringType(protocol));
        }

        // Update course (direction/heading)
        Object courseObj = position.get("course");
        if (courseObj instanceof Number) {
            double course = ((Number) courseObj).doubleValue();
            updateState(CHANNEL_COURSE, new QuantityType<>(course, Units.DEGREE_ANGLE));
        }

        // Update accuracy
        Object accuracyObj = position.get("accuracy");
        if (accuracyObj instanceof Number) {
            double accuracy = ((Number) accuracyObj).doubleValue();
            updateState(CHANNEL_ACCURACY, new QuantityType<>(accuracy, SIUnits.METRE));
        }

        // Update address (use Nominatim if enabled, otherwise use Traccar's address)
        String address = null;
        NominatimGeocoder currentGeocoder = geocoder;
        if (currentGeocoder != null && latObj instanceof Number && lonObj instanceof Number) {
            // Use Nominatim for reverse geocoding
            double latitude = ((Number) latObj).doubleValue();
            double longitude = ((Number) lonObj).doubleValue();
            address = currentGeocoder.getAddress(latitude, longitude);
            if (address != null) {
                logger.debug("Using Nominatim address for device {}: {}", config.deviceId, address);
            }
        }

        // Fall back to Traccar's address if Nominatim is disabled or failed
        if (address == null) {
            Object addressObj = position.get("address");
            if (addressObj instanceof String traccarAddress) {
                address = traccarAddress;
                logger.debug("Using Traccar address for device {}: {}", config.deviceId, address);
            }
        }

        // Update address channel
        if (address != null) {
            updateState(CHANNEL_ADDRESS, new StringType(address));
        }

        // Update attributes (batteryLevel, odometer, motion)
        Object attributesObj = position.get("attributes");
        if (attributesObj instanceof Map<?, ?>) {
            @SuppressWarnings("unchecked")
            Map<String, Object> attributes = (Map<String, Object>) attributesObj;

            /*
             * Everything, before anything is picked out of it.
             *
             * Placed first on purpose: the named reads below are the thirty-odd
             * attributes this binding happens to know, and a signal that has no
             * read here is simply never looked at. Fitting a new peripheral
             * means finding out what it actually sends, and this is the only
             * place that shows it.
             *
             * Sorted, so two readings can be diffed by eye.
             */
            StringBuilder raw = new StringBuilder();
            for (Map.Entry<String, Object> entry : new TreeMap<>(attributes).entrySet()) {
                if (raw.length() > 0) {
                    raw.append(", ");
                }
                raw.append(entry.getKey()).append('=').append(entry.getValue());
                if (raw.length() > RAW_ATTRIBUTES_MAX) {
                    raw.append(" …(truncated)");
                    break;
                }
            }
            updateState(CHANNEL_RAW_ATTRIBUTES, new StringType(raw.toString()));

            // Battery level. Traccar reads it from AVL 113, which is the internal battery on the FMB
            // family but Service Distance on the FMx6 family - so not there.
            Object batteryObj = profile().isFmx6() ? null : attributes.get("batteryLevel");
            if (batteryObj instanceof Number) {
                double battery = ((Number) batteryObj).doubleValue();
                updateState(CHANNEL_BATTERY_LEVEL, new QuantityType<>(battery, Units.PERCENT));
            }

            // Odometer (device-reported, mainly for OSMand)
            Object odometerObj = attributes.get("odometer");
            if (odometerObj instanceof Number) {
                double odometerMeters = ((Number) odometerObj).doubleValue();
                updateState(CHANNEL_ODOMETER, new QuantityType<>(odometerMeters, SIUnits.METRE));
            }

            // Total Distance (Traccar server cumulative distance, all protocols)
            Object totalDistanceObj = attributes.get("totalDistance");
            if (totalDistanceObj instanceof Number) {
                double totalDistanceMeters = ((Number) totalDistanceObj).doubleValue();
                updateState(CHANNEL_TOTAL_DISTANCE, new QuantityType<>(totalDistanceMeters, SIUnits.METRE));
            }

            // Motion detection
            Object motionObj = attributes.get("motion");
            if (motionObj instanceof Boolean motion) {
                updateState(CHANNEL_MOTION, OnOffType.from(motion));
            }

            // Engine hours (in milliseconds from Traccar)
            Object hoursObj = attributes.get("hours");
            if (hoursObj instanceof Number) {
                double hoursMs = ((Number) hoursObj).doubleValue();
                double hoursValue = hoursMs / 3600000.0; // Convert milliseconds to hours
                logger.debug("Engine hours: {} ms = {} hours", hoursMs, hoursValue);
                updateState(CHANNEL_HOURS, new QuantityType<>(hoursValue, Units.HOUR));
            }

            // Event code (device-specific)
            Object eventObj = attributes.get("event");
            if (eventObj instanceof Number) {
                int eventCode = ((Number) eventObj).intValue();
                updateState(CHANNEL_EVENT, new DecimalType(eventCode));
            }

            // Distance (incremental distance since last update)
            Object distanceObj = attributes.get("distance");
            if (distanceObj instanceof Number) {
                double distanceMeters = ((Number) distanceObj).doubleValue();
                updateState(CHANNEL_DISTANCE, new QuantityType<>(distanceMeters, SIUnits.METRE));
            }

            // Activity (OSMand-specific activity detection)
            Object activityObj = attributes.get("activity");
            if (activityObj != null) {
                updateState(CHANNEL_ACTIVITY, new StringType(activityObj.toString()));
            }

            // Ignition status
            Object ignitionObj = attributes.get("ignition");
            if (ignitionObj instanceof Boolean) {
                updateState(CHANNEL_IGNITION, OnOffType.from((Boolean) ignitionObj));
            }

            // Alarm: Traccar's one attribute for every scenario the tracker raises itself (Teltonika:
            // towing -> tow, unplug -> powerCut, jamming, crash -> accident, eco driving ->
            // hardAcceleration/hardBraking/hardCornering, overspeed, idle ...). Several at once come
            // comma-separated. The alarm is left on the channel so the last one can be read; the record's
            // device time goes to alarmTime, which changes on every alarm record and is what a rule
            // triggers on - "changed" on the alarm text alone would miss a second tow a minute later.
            // A record without an alarm leaves both as they are.
            Object alarmObj = attributes.get("alarm");
            if (alarmObj instanceof String alarm && !alarm.isBlank()) {
                updateState(CHANNEL_ALARM, new StringType(alarm.trim()));
                updateState(CHANNEL_ALARM_TIME,
                        new DateTimeType(deviceTime != null ? deviceTime : ZonedDateTime.now()));
                logger.info("Device {}: alarm {} ({})", profile().deviceId, alarm.trim(),
                        deviceTime != null ? deviceTime : "no device time");
            }

            // GPS Satellites
            Object satObj = attributes.get("sat");
            if (satObj instanceof Number) {
                int satellites = ((Number) satObj).intValue();
                updateState(CHANNEL_GPS_SAT, new DecimalType(satellites));
            }

            // GSM Signal Strength (RSSI)
            Object rssiObj = attributes.get("rssi");
            if (rssiObj instanceof Number) {
                double rssi = ((Number) rssiObj).doubleValue();
                double signalPercent;
                if (rssi >= 0 && rssi <= 5) {
                    // Teltonika bar scale: 0-5 bars, convert to percentage
                    signalPercent = (rssi / 5.0) * 100.0;
                } else if (rssi < 0) {
                    // dBm scale: -113 to -51 dBm
                    signalPercent = Math.max(0, Math.min(100, 2 * (rssi + 113)));
                } else {
                    signalPercent = rssi; // Already percentage
                }
                updateState(CHANNEL_GSM_SIGNAL, new QuantityType<>(signalPercent, Units.PERCENT));
            } else {
                // Try alternative attribute name "gsm"
                Object gsmObj = attributes.get("gsm");
                if (gsmObj instanceof Number) {
                    double gsm = ((Number) gsmObj).doubleValue();
                    updateState(CHANNEL_GSM_SIGNAL, new QuantityType<>(gsm, Units.PERCENT));
                }
            }

            // GPS Dilution of Precision (DOP) metrics
            Object pdopObj = attributes.get("pdop");
            if (pdopObj instanceof Number) {
                double pdop = ((Number) pdopObj).doubleValue();
                updateState(CHANNEL_PDOP, new DecimalType(pdop));
            }

            Object hdopObj = attributes.get("hdop");
            if (hdopObj instanceof Number) {
                double hdop = ((Number) hdopObj).doubleValue();
                updateState(CHANNEL_HDOP, new DecimalType(hdop));
            }

            // Power and Battery voltages
            Object powerObj = attributes.get("power");
            if (powerObj instanceof Number) {
                double powerVolts = ((Number) powerObj).doubleValue();
                updateState(CHANNEL_POWER, new QuantityType<>(powerVolts, Units.VOLT));
            }

            Object batteryObjVolt = attributes.get("battery");
            if (batteryObjVolt instanceof Number) {
                double batteryVolts = ((Number) batteryObjVolt).doubleValue();
                updateState(CHANNEL_BATTERY, new QuantityType<>(batteryVolts, Units.VOLT));
            }

            // Mobile operator code
            Object operatorObj = attributes.get("operator");
            if (operatorObj != null) {
                String operatorCode;
                if (operatorObj instanceof Number) {
                    // Convert to integer to remove decimal point (23801.0 -> 23801)
                    operatorCode = String.valueOf(((Number) operatorObj).intValue());
                } else {
                    operatorCode = operatorObj.toString();
                }
                updateState(CHANNEL_OPERATOR, new StringType(operatorCode));
            }

            // Vehicle Identification Number (VIN)
            Object vinObj = attributes.get("vin");
            if (vinObj != null) {
                updateState(CHANNEL_VIN, new StringType(vinObj.toString()));
            }

            // Trip distance (resets)
            Object tripDistanceObj = attributes.get("distance");
            if (tripDistanceObj instanceof Number) {
                double tripDist = ((Number) tripDistanceObj).doubleValue();
                updateState(CHANNEL_TRIP_DISTANCE, new QuantityType<>(tripDist, SIUnits.METRE));
            }

            // Teltonika event code (e.g., 36 = OBD-II data update)
            Object eventCodeObj = attributes.get("event");
            if (eventCodeObj instanceof Number) {
                int eventCode = ((Number) eventCodeObj).intValue();
                updateState(CHANNEL_EVENT_CODE, new DecimalType(eventCode));
            }

            // The io numbers below mean OBD-II data from a Bluetooth dongle. On a tracker with a CAN
            // adapter the same numbers carry the adapter's values (io30 speed, io36 mileage, io38 the
            // control flags), so they are read here only when the thing says a dongle is fitted.
            if (profile().readsObd()) {
                updateObdChannels(attributes);
            }

            // CAN adapter (ALL-CAN300 / LV-CAN200 on an FMx6 tracker)
            if (profile().hasCanAdapter()) {
                updateCanChannels(attributes, deviceTime);
            }

            // Process Bluetooth Beacon data (Teltonika FMM920 optional accessory)
            // Route beacons by MAC address to consistent slots
            processBeaconsWithMacRouting(attributes);
        }

        // Update last update time
        if (deviceTime != null) {
            updateState(CHANNEL_LAST_UPDATE, new DateTimeType(deviceTime));
        } else if (position.get("deviceTime") != null) {
            logger.debug("Failed to parse device time: {}", position.get("deviceTime"));
        }
    }

    private static @Nullable ZonedDateTime parseTime(@Nullable Object value) {
        if (value instanceof String text) {
            try {
                return ZonedDateTime.parse(text);
            } catch (Exception e) {
                return null;
            }
        }
        return null;
    }

    private void updateCanChannels(Map<String, Object> attributes, @Nullable ZonedDateTime deviceTime) {
        // the record is the newest applied: updatePositionChannels() skipped any older one
        Map<String, State> states = LvcanDecoder.decode(attributes,
                deviceTime != null ? deviceTime : ZonedDateTime.now(), profile().gearParkWhenOff);
        // empty when the record has no LVCAN field: every CAN channel stays as it was
        states.forEach((id, state) -> updateState(CHANNEL_GROUP_CAN + ChannelUID.CHANNEL_GROUP_SEPARATOR + id, state));
    }

    private void updateObdChannels(Map<String, Object> attributes) {
        // Additional Teltonika IO Channels (experimental/unknown purpose)
        // io42: Varies 84-94 (possibly intake air temperature or another sensor)
        Object io42Obj = attributes.get("io42");
        if (io42Obj instanceof Number) {
            int io42 = ((Number) io42Obj).intValue();
            updateState(CHANNEL_IO42, new DecimalType(io42));
        }

        // io49: Typically constant ~5816 (possibly battery voltage in mV)
        Object io49Obj = attributes.get("io49");
        if (io49Obj instanceof Number) {
            int io49 = ((Number) io49Obj).intValue();
            updateState(CHANNEL_IO49, new DecimalType(io49));
        }

        // io51: Varies 14000-14200 (possibly alternator voltage in mV)
        Object io51Obj = attributes.get("io51");
        if (io51Obj instanceof Number) {
            int io51 = ((Number) io51Obj).intValue();
            updateState(CHANNEL_IO51, new DecimalType(io51));
        }

        // OBD-II Trip Meters from ECU
        // io199: Trip odometer 1 (in meters, convert to km)
        Object io199Obj = attributes.get("io199");
        if (io199Obj instanceof Number) {
            double trip1Meters = ((Number) io199Obj).doubleValue();
            updateState(CHANNEL_IO199, new QuantityType<>(trip1Meters / 1000.0, MetricPrefix.KILO(SIUnits.METRE)));
        }

        // io205: Trip odometer 2 (in meters, convert to km)
        Object io205Obj = attributes.get("io205");
        if (io205Obj instanceof Number) {
            double trip2Meters = ((Number) io205Obj).doubleValue();
            updateState(CHANNEL_IO205, new QuantityType<>(trip2Meters / 1000.0, MetricPrefix.KILO(SIUnits.METRE)));
        }

        // io389: Total vehicle mileage from ECU (in meters, convert to km)
        Object io389Obj = attributes.get("io389");
        if (io389Obj instanceof Number) {
            double ecuOdometerMeters = ((Number) io389Obj).doubleValue();
            updateState(CHANNEL_IO389,
                    new QuantityType<>(ecuOdometerMeters / 1000.0, MetricPrefix.KILO(SIUnits.METRE)));
        }

        // OBD-II Data (Teltonika FMM920 with Bluetooth OBD-II dongle)
        // These channels are only populated when an OBD-II dongle is paired and ignition is ON

        // io30: Number of Diagnostic Trouble Codes (DTCs)
        Object io30Obj = attributes.get("io30");
        if (io30Obj instanceof Number) {
            int dtcCount = ((Number) io30Obj).intValue();
            updateState(CHANNEL_OBD_DTC_COUNT, new DecimalType(dtcCount));
        }

        // io31: Engine Load [%]
        Object io31Obj = attributes.get("io31");
        if (io31Obj instanceof Number) {
            double engineLoad = ((Number) io31Obj).doubleValue();
            updateState(CHANNEL_OBD_ENGINE_LOAD, new QuantityType<>(engineLoad, Units.PERCENT));
        }

        // io32: Coolant Temperature [°C]
        Object io32Obj = attributes.get("io32");
        if (io32Obj instanceof Number) {
            double coolantTemp = ((Number) io32Obj).doubleValue();
            updateState(CHANNEL_OBD_COOLANT_TEMP, new QuantityType<>(coolantTemp, SIUnits.CELSIUS));
        }

        // io33: Short Fuel Trim [%]
        Object io33Obj = attributes.get("io33");
        if (io33Obj instanceof Number) {
            double shortFuelTrim = ((Number) io33Obj).doubleValue();
            updateState(CHANNEL_OBD_SHORT_FUEL_TRIM, new QuantityType<>(shortFuelTrim, Units.PERCENT));
        }

        // io35: Fuel Pressure [kPa]
        Object io35Obj = attributes.get("io35");
        if (io35Obj instanceof Number) {
            double fuelPressureKpa = ((Number) io35Obj).doubleValue();
            // Convert kPa to Pa for OpenHAB (1 kPa = 1000 Pa)
            double fuelPressurePa = fuelPressureKpa * 1000;
            updateState(CHANNEL_OBD_FUEL_PRESSURE, new QuantityType<>(fuelPressurePa, SIUnits.PASCAL));
        }

        // io36: Engine RPM (actual RPM from OBD-II)
        // This appears to be the real RPM value that varies with engine speed
        Object io36Obj = attributes.get("io36");
        if (io36Obj instanceof Number) {
            int rpm = ((Number) io36Obj).intValue();
            updateState(CHANNEL_OBD_RPM, new DecimalType(rpm));
        }

        // io37: Engine RPM Reported (standard OBD-II PID, often shows 0)
        Object io37Obj = attributes.get("io37");
        if (io37Obj instanceof Number) {
            int rpmReported = ((Number) io37Obj).intValue();
            updateState(CHANNEL_OBD_RPM_REPORTED, new DecimalType(rpmReported));
        }

        // io38: Vehicle Speed from OBD-II [km/h]
        Object io38Obj = attributes.get("io38");
        if (io38Obj instanceof Number) {
            double obdSpeed = ((Number) io38Obj).doubleValue();
            updateState(CHANNEL_OBD_SPEED, new QuantityType<>(obdSpeed, SIUnits.KILOMETRE_PER_HOUR));
        }

        // io48: Fuel Level [%] - Inverted (100 - value) because bike reports fuel used, not remaining
        Object io48Obj = attributes.get("io48");
        if (io48Obj instanceof Number) {
            double fuelUsed = ((Number) io48Obj).doubleValue();
            double fuelRemaining = 100.0 - fuelUsed;
            updateState(CHANNEL_OBD_FUEL_LEVEL, new QuantityType<>(fuelRemaining, Units.PERCENT));
        }

        // OEM Odometer (actual vehicle odometer from CAN bus via OBD-II)
        // This is different from the generic "odometer" field - when OBD-II is active,
        // the odometer field contains the real vehicle odometer reading from CAN bus
        // Check if OBD-II data is present (indicated by io30+ attributes)
        if (io30Obj != null || io31Obj != null) {
            Object oemOdometerObj = attributes.get("odometer");
            if (oemOdometerObj instanceof Number) {
                double oemOdometerMeters = ((Number) oemOdometerObj).doubleValue();
                updateState(CHANNEL_OBD_OEM_ODOMETER, new QuantityType<>(oemOdometerMeters, SIUnits.METRE));
            }
        }
    }

    /**
     * Update beacon data for a specific beacon number.
     * Processes both basic telemetry (RSSI, MAC, Battery) and full sensor data
     * (Temperature, Humidity, Motion, Magnet, Pitch, Roll) when available.
     *
     * @param attributes Position attributes map from Traccar
     * @param beaconPrefix Attribute prefix ("tag1", "tag2", "tag3", or "tag4")
     * @param beaconNumber Beacon number (1-4)
     */
    /**
     * Process beacon data routing by MAC address.
     * Routes each tag1-4 to the correct beacon slot based on MAC configuration.
     */
    private void processBeaconsWithMacRouting(Map<String, Object> attributes) {
        logger.debug("Processing beacons with MAC routing");

        // Process each tag (tag1 through tag4) from the device
        for (int tagNum = 1; tagNum <= 4; tagNum++) {
            String tagPrefix = "tag" + tagNum;
            String macKey = tagPrefix + "Mac";
            Object macObj = attributes.get(macKey);

            if (macObj instanceof String mac && !mac.isBlank()) {
                String normalizedMac = mac.toLowerCase().trim();
                logger.debug("Found {} with MAC {}", tagPrefix, normalizedMac);

                // Look up which beacon slot this MAC should go to
                Integer beaconSlot = macToBeaconSlot.get(normalizedMac);

                if (beaconSlot != null) {
                    // Route to configured slot
                    String channelPrefix = "beacon" + beaconSlot;
                    logger.debug("Routing {} (MAC {}) to {}", tagPrefix, normalizedMac, channelPrefix);
                    updateBeaconData(attributes, tagPrefix, channelPrefix);
                } else {
                    // MAC not configured - find first available slot
                    logger.debug("MAC {} not configured, finding available slot", normalizedMac);
                    Integer availableSlot = findAvailableBeaconSlot(normalizedMac);
                    if (availableSlot != null) {
                        String channelPrefix = "beacon" + availableSlot;
                        logger.debug("Assigning {} (MAC {}) to available {}", tagPrefix, normalizedMac, channelPrefix);
                        updateBeaconData(attributes, tagPrefix, channelPrefix);
                    } else {
                        logger.debug("No available slot for {} (MAC {})", tagPrefix, normalizedMac);
                    }
                }
            }
        }
    }

    /**
     * Find an available beacon slot (1-4) that doesn't have a MAC assigned.
     */
    private @Nullable Integer findAvailableBeaconSlot(String mac) {
        for (int slot = 1; slot <= 4; slot++) {
            if (!macToBeaconSlot.containsValue(slot)) {
                // Temporarily assign this MAC to this slot for this update
                macToBeaconSlot.put(mac, slot);
                return slot;
            }
        }
        return null;
    }

    private void updateBeaconData(Map<String, Object> attributes, String beaconPrefix, String channelPrefix) {
        // Channel prefix: beacon1, beacon2, etc.

        // Get configuration parameters for distance calculation
        Object txPowerObj = getThing().getConfiguration().get("beaconTxPower");
        int txPower = (txPowerObj instanceof Number) ? ((Number) txPowerObj).intValue() : -59;

        Object pathLossObj = getThing().getConfiguration().get("beaconPathLoss");
        double pathLoss = (pathLossObj instanceof Number) ? ((Number) pathLossObj).doubleValue() : 2.0;

        // Basic telemetry (always check)
        Object rssi = attributes.get(beaconPrefix + "Rssi");
        if (rssi instanceof Number rssiValue) {
            int rssiInt = rssiValue.intValue();
            updateState(channelPrefix + "-rssi", new DecimalType(rssiInt));

            // Calculate distance from RSSI
            // Formula: distance = 10 ^ ((txPower - RSSI) / (10 * pathLossExponent))
            double exponent = (txPower - rssiInt) / (10.0 * pathLoss);
            double distanceMeters = Math.pow(10, exponent);
            updateState(channelPrefix + "-distance", new QuantityType<>(distanceMeters, SIUnits.METRE));
        }

        Object mac = attributes.get(beaconPrefix + "Mac");
        if (mac instanceof String macValue) {
            updateState(channelPrefix + "-mac", new StringType(macValue));
        }

        Object battery = attributes.get(beaconPrefix + "Battery");
        if (battery instanceof Number batteryValue) {
            updateState(channelPrefix + "-battery",
                    new QuantityType<>(batteryValue.doubleValue(), MetricPrefix.MILLI(Units.VOLT)));
        }

        Object lowBattery = attributes.get(beaconPrefix + "LowBattery");
        if (lowBattery instanceof Number lowBatteryValue) {
            updateState(channelPrefix + "-lowBattery", OnOffType.from(lowBatteryValue.intValue() != 0));
        }

        // Full sensor data (may not always be present)
        Object name = attributes.get(beaconPrefix + "Name");
        if (name instanceof String nameValue) {
            // Trim null characters from name
            String cleanName = nameValue.replaceAll("\\u0000", "").trim();
            if (!cleanName.isEmpty()) {
                // Extract beacon slot number from channelPrefix (e.g., "beacon1" -> 1)
                int beaconSlot = Integer.parseInt(channelPrefix.replace("beacon", ""));

                // Check if name has changed
                String previousName = beaconSlotToName.get(beaconSlot);
                if (previousName == null || !previousName.equals(cleanName)) {
                    logger.info("Beacon {} name updated: '{}' -> '{}'", beaconSlot,
                            previousName != null ? previousName : "<none>", cleanName);

                    // Store in memory
                    beaconSlotToName.put(beaconSlot, cleanName);

                    // Persist to Thing properties
                    Map<String, String> properties = editProperties();
                    properties.put("beacon" + beaconSlot + "Name", cleanName);
                    updateProperties(properties);
                }

                // Always update channel with latest name
                updateState(channelPrefix + "-name", new StringType(cleanName));
            }
        } else {
            // Name not in current webhook - use stored name if available
            int beaconSlot = Integer.parseInt(channelPrefix.replace("beacon", ""));
            String storedName = beaconSlotToName.get(beaconSlot);
            if (storedName != null) {
                updateState(channelPrefix + "-name", new StringType(storedName));
            }
        }

        Object temp = attributes.get(beaconPrefix + "Temp");
        if (temp instanceof Number tempValue) {
            // Temperature is in hundredths of °C
            double celsius = tempValue.doubleValue() / 100.0;
            updateState(channelPrefix + "-temperature", new QuantityType<>(celsius, SIUnits.CELSIUS));
        }

        Object humidity = attributes.get(beaconPrefix + "Humidity");
        if (humidity instanceof Number humidityValue) {
            updateState(channelPrefix + "-humidity", new QuantityType<>(humidityValue.doubleValue(), Units.PERCENT));
        }

        Object magnet = attributes.get(beaconPrefix + "Magnet");
        if (magnet instanceof Boolean magnetValue) {
            // Magnet sensor: false=CLOSED (magnet near), true=OPEN (magnet away)
            updateState(channelPrefix + "-magnet", magnetValue ? OpenClosedType.OPEN : OpenClosedType.CLOSED);
        }

        Object motion = attributes.get(beaconPrefix + "Motion");
        if (motion instanceof Boolean motionValue) {
            updateState(channelPrefix + "-motion", OnOffType.from(motionValue));
        }

        Object motionCount = attributes.get(beaconPrefix + "MotionCount");
        if (motionCount instanceof Number motionCountValue) {
            updateState(channelPrefix + "-motionCount", new DecimalType(motionCountValue.intValue()));
        }

        Object pitch = attributes.get(beaconPrefix + "Pitch");
        if (pitch instanceof Number pitchValue) {
            updateState(channelPrefix + "-pitch", new QuantityType<>(pitchValue.doubleValue(), Units.DEGREE_ANGLE));
        }

        Object roll = attributes.get(beaconPrefix + "AngleRoll");
        if (roll instanceof Number rollValue) {
            updateState(channelPrefix + "-roll", new QuantityType<>(rollValue.doubleValue(), Units.DEGREE_ANGLE));
        }
    }

    public void updatePositionFromWebhook(Map<String, Object> position) {
        logger.debug("Updating position from webhook for device {}", config != null ? config.deviceId : "unknown");
        updatePositionChannels(position);
    }

    public void updateStatus(String status) {
        logger.debug("Updating status from webhook for device {}: {}", config != null ? config.deviceId : "unknown",
                status);
        updateState(CHANNEL_STATUS, new StringType(status));
    }

    public void handleGeofenceEvent(Map<String, Object> eventData) {
        // Extract the event object - Traccar sends nested structure
        Object eventObj = eventData.get("event");
        if (!(eventObj instanceof Map<?, ?>)) {
            logger.debug("No event object in webhook data");
            return;
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> event = (Map<String, Object>) eventObj;

        ZonedDateTime eventTime = parseTime(event.get("eventTime"));
        synchronized (recordLock) {
            ZonedDateTime newest = newestGeofenceEvent;
            if (eventTime != null && newest != null && eventTime.isBefore(newest)) {
                logger.debug("Device {}: geofence event from {} is older than the newest applied ({}), skipped",
                        profile().deviceId, eventTime, newest);
                return;
            }
            if (eventTime != null) {
                newestGeofenceEvent = eventTime;
            }
        }

        // Handle geofence entry/exit events from webhook
        Object eventTypeObj = event.get("type");
        if (eventTypeObj instanceof String eventType) {
            updateState(CHANNEL_GEOFENCE_EVENT, new StringType(eventType));

            // Update geofence name if available
            Object geofenceObj = eventData.get("geofence");
            if (geofenceObj instanceof Map<?, ?>) {
                @SuppressWarnings("unchecked")
                Map<String, Object> geofence = (Map<String, Object>) geofenceObj;

                // Update geofence ID
                Object idObj = geofence.get("id");
                if (idObj instanceof Number geofenceId) {
                    updateState(CHANNEL_GEOFENCE_ID, new DecimalType(geofenceId.longValue()));
                }

                // Update geofence name
                Object nameObj = geofence.get("name");
                if (nameObj instanceof String geofenceName) {
                    updateState(CHANNEL_GEOFENCE_NAME, new StringType(geofenceName));
                    TraccarDeviceConfiguration configuration = config;
                    logger.info("Device {} {} geofence: {} (ID: {})",
                            configuration != null ? Integer.valueOf(configuration.deviceId) : "unknown", eventType,
                            geofenceName, idObj);
                }
            }

            // Also update position if included in the event
            Object positionObj = eventData.get("position");
            if (positionObj instanceof Map<?, ?>) {
                @SuppressWarnings("unchecked")
                Map<String, Object> position = (Map<String, Object>) positionObj;
                logger.debug("Updating position from geofence event");
                updatePositionChannels(position);
            }
        }
    }

    public @Nullable TraccarDeviceConfiguration getConfiguration() {
        return config;
    }
}
