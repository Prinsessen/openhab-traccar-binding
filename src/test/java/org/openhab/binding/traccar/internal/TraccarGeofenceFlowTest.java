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
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.lang.reflect.Field;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.openhab.core.library.types.DecimalType;
import org.openhab.core.library.types.StringType;
import org.openhab.core.thing.Bridge;
import org.openhab.core.thing.ChannelUID;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingUID;
import org.openhab.core.thing.binding.ThingHandlerCallback;
import org.openhab.core.thing.type.ThingTypeRegistry;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

/**
 * A geofence event all the way from Traccar's POST to the device's channels: the real webhook receiver, the real
 * server handler and the real device handler, only openHAB's callback mocked. Until this test the path was proven
 * only by hand - on openHAB 5.3 by a user's curl (2026-11-26); the receiver test stops at the server handler.
 * The same test runs on main (openHAB 5.0-5.2, Jetty 9) and on the openhab-5.3 branch (Jetty 12).
 *
 * @author Nanna Agesen - Initial contribution
 */
@NonNullByDefault
class TraccarGeofenceFlowTest {

    private static final int DEVICE_ID = 7;
    private static final ThingUID DEVICE_UID = new ThingUID("traccar", "device", "server", "dev7");

    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final ThingHandlerCallback callback = mock(ThingHandlerCallback.class);
    private TraccarServerHandler server = mock(TraccarServerHandler.class);
    private @Nullable TraccarWebhookServer receiver;
    private int port;

    private static String event(String type, int deviceId, String time, int geofenceId, String name) {
        return "{\"event\":{\"type\":\"" + type + "\",\"deviceId\":" + deviceId + ",\"geofenceId\":" + geofenceId
                + ",\"eventTime\":\"" + time + "\"},\"geofence\":{\"id\":" + geofenceId + ",\"name\":\"" + name
                + "\"}}";
    }

    private static ChannelUID channel(String id) {
        return new ChannelUID(DEVICE_UID, id);
    }

    @BeforeEach
    void setUp() throws Exception {
        Thing deviceThing = mock(Thing.class);
        when(deviceThing.getUID()).thenReturn(DEVICE_UID);
        TraccarDeviceHandler device = new TraccarDeviceHandler(deviceThing, mock(ThingTypeRegistry.class));
        device.setCallback(callback);
        TraccarDeviceConfiguration config = new TraccarDeviceConfiguration();
        config.deviceId = DEVICE_ID;
        Field f = TraccarDeviceHandler.class.getDeclaredField("config"); // set by initialize() in openHAB
        f.setAccessible(true);
        f.set(device, config);
        when(deviceThing.getHandler()).thenReturn(device);

        Bridge bridge = mock(Bridge.class);
        when(bridge.getThings()).thenReturn(List.of(deviceThing));
        server = new TraccarServerHandler(bridge, mock(org.eclipse.jetty.client.HttpClient.class));

        try (ServerSocket socket = new ServerSocket(0)) {
            port = socket.getLocalPort();
        }
        TraccarWebhookServer r = new TraccarWebhookServer(server, port);
        r.start();
        receiver = r;
    }

    @AfterEach
    void tearDown() {
        TraccarWebhookServer r = receiver;
        if (r != null) {
            r.stop();
        }
    }

    private void webhook(String body) {
        @Nullable
        Map<String, Object> data = new Gson().fromJson(body, new TypeToken<Map<String, Object>>() {
        }.getType());
        if (data == null) {
            fail("not JSON: " + body);
            return;
        }
        server.handleWebhookEvent(data);
    }

    @Test
    void aPostedGeofenceEnterReachesTheChannels() throws Exception {
        // what a user's curl sent on openHAB 5.3, over a real socket
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/webhook"))
                .timeout(Duration.ofSeconds(10)).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(
                        event("geofenceEnter", DEVICE_ID, "2026-11-26T18:00:00.000+00:00", 1, "Test"),
                        StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode());

        verify(callback, timeout(5000)).stateUpdated(channel("geofenceEvent"), new StringType("geofenceEnter"));
        verify(callback).stateUpdated(channel("geofenceName"), new StringType("Test"));
        verify(callback).stateUpdated(channel("geofenceId"), new DecimalType(1));
    }

    @Test
    void nameAndIdAreSetBeforeTheEvent() {
        // rules trigger on geofenceEvent and read geofenceName/Id in the same moment
        webhook(event("geofenceExit", DEVICE_ID, "2026-11-26T18:00:00.000+00:00", 4, "Home"));
        InOrder order = inOrder(callback);
        order.verify(callback).stateUpdated(channel("geofenceId"), new DecimalType(4));
        order.verify(callback).stateUpdated(channel("geofenceName"), new StringType("Home"));
        order.verify(callback).stateUpdated(channel("geofenceEvent"), new StringType("geofenceExit"));
    }

    @Test
    void anOlderEventArrivingLateIsSkipped() {
        // Traccar forwards each event as its own request: an exit and an enter a second apart can arrive swapped
        webhook(event("geofenceEnter", DEVICE_ID, "2026-11-26T18:00:02.000+00:00", 4, "Home"));
        webhook(event("geofenceExit", DEVICE_ID, "2026-11-26T18:00:01.000+00:00", 4, "Home"));
        verify(callback).stateUpdated(channel("geofenceEvent"), new StringType("geofenceEnter"));
        verify(callback, never()).stateUpdated(channel("geofenceEvent"), new StringType("geofenceExit"));
    }

    @Test
    void anEventForAnotherDeviceChangesNothing() {
        webhook(event("geofenceEnter", DEVICE_ID + 1, "2026-11-26T18:00:00.000+00:00", 4, "Home"));
        verify(callback, never()).stateUpdated(any(), any());
    }
}
