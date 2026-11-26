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
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

/**
 * The webhook receiver against a real socket: what Traccar sends, what it gets back, and what reaches the
 * server handler. Written for the move to Jetty 12 (openHAB 5.3), where the receiver had to be rewritten:
 * the same tests pass on the Jetty 9 receiver (openHAB 5.2) and on the Jetty 12 one, so both answer alike.
 *
 * @author Nanna Agesen - Initial contribution
 */
@NonNullByDefault
class TraccarWebhookServerTest {

    private static final String POSITION = "{\"position\":{\"deviceId\":7,\"latitude\":57.09,\"longitude\":9.52},"
            + "\"device\":{\"id\":7,\"name\":\"Test\"}}";
    private static final String EVENT = "{\"event\":{\"type\":\"geofenceEnter\",\"deviceId\":7,\"geofenceId\":1},"
            + "\"device\":{\"id\":7,\"name\":\"Test\"}}";

    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private TraccarServerHandler handler = mock(TraccarServerHandler.class);
    private @Nullable TraccarWebhookServer server;
    private int port;

    @BeforeEach
    void start() throws Exception {
        try (ServerSocket socket = new ServerSocket(0)) {
            port = socket.getLocalPort();
        }
        handler = mock(TraccarServerHandler.class);
        TraccarWebhookServer s = new TraccarWebhookServer(handler, port);
        s.start();
        server = s;
    }

    @AfterEach
    void stop() {
        TraccarWebhookServer s = server;
        if (s != null) {
            s.stop();
        }
    }

    private HttpResponse<String> send(String method, String pathAndQuery, @Nullable String body)
            throws IOException, InterruptedException {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + pathAndQuery))
                .timeout(Duration.ofSeconds(10));
        if (body != null) {
            b.header("Content-Type", "application/json").method(method,
                    HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
        } else {
            b.method(method, HttpRequest.BodyPublishers.noBody());
        }
        return client.send(b.build(), HttpResponse.BodyHandlers.ofString());
    }

    @SuppressWarnings("unchecked")
    private static boolean isEvent(@Nullable Map<String, Object> data, String type) {
        return data != null && data.get("event") instanceof Map<?, ?> e && type.equals(((Map<String, Object>) e).get("type"));
    }

    @Test
    void postIsAnsweredOkAndHandedToTheHandler() throws Exception {
        HttpResponse<String> r = send("POST", "/webhook", POSITION);
        assertEquals(200, r.statusCode());
        assertEquals("{\"status\":\"ok\"}", r.body());
        assertTrue(r.headers().firstValue("Content-Type").orElse("").startsWith("application/json"),
                r.headers().toString());
        verify(handler, timeout(5000)).handleWebhookEvent(argThat(d -> d != null && d.get("position") instanceof Map));
    }

    @Test
    void getCarriesTheJsonAsAParameter() throws Exception {
        HttpResponse<String> r = send("GET", "/webhook?json=" + URLEncoder.encode(EVENT, StandardCharsets.UTF_8),
                null);
        assertEquals(200, r.statusCode());
        verify(handler, timeout(5000)).handleWebhookEvent(argThat(d -> isEvent(d, "geofenceEnter")));
    }

    @Test
    void getWithoutJsonIsOkAndHandsNothingOn() throws Exception {
        assertEquals(200, send("GET", "/webhook", null).statusCode());
        Thread.sleep(300);
        verify(handler, never()).handleWebhookEvent(any());
    }

    @Test
    void anotherPathIsNotFound() throws Exception {
        assertEquals(404, send("POST", "/other", POSITION).statusCode());
        Thread.sleep(300);
        verify(handler, never()).handleWebhookEvent(any());
    }

    @Test
    void anotherMethodIsNotAllowed() throws Exception {
        assertEquals(405, send("PUT", "/webhook", POSITION).statusCode());
        Thread.sleep(300);
        verify(handler, never()).handleWebhookEvent(any());
    }

    @Test
    void brokenJsonIsAServerError() throws Exception {
        assertEquals(500, send("POST", "/webhook", "{not json").statusCode());
        Thread.sleep(300);
        verify(handler, never()).handleWebhookEvent(any());
    }

    @Test
    void webhooksAreHandledInArrivalOrder() throws Exception {
        String exit = EVENT.replace("geofenceEnter", "geofenceExit");
        assertEquals(200, send("POST", "/webhook", exit).statusCode());
        assertEquals(200, send("POST", "/webhook", EVENT).statusCode());
        InOrder order = inOrder(handler);
        order.verify(handler, timeout(5000)).handleWebhookEvent(argThat(d -> isEvent(d, "geofenceExit")));
        order.verify(handler, timeout(5000)).handleWebhookEvent(argThat(d -> isEvent(d, "geofenceEnter")));
    }

    @Test
    void stoppedWorkerAsksTraccarToRetry() throws Exception {
        TraccarWebhookServer s = server;
        assertNotNull(s);
        // the worker gone, the socket still open: what a full queue answers too
        java.lang.reflect.Field f = TraccarWebhookServer.class.getDeclaredField("worker");
        f.setAccessible(true);
        java.util.concurrent.ThreadPoolExecutor w = (java.util.concurrent.ThreadPoolExecutor) f.get(s);
        w.shutdownNow();
        assertEquals(503, send("POST", "/webhook", POSITION).statusCode());
    }
}
