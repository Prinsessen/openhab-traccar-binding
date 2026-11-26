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
import java.util.Map;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jetty.http.HttpHeader;
import org.eclipse.jetty.http.HttpStatus;
import org.eclipse.jetty.io.Content;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Response;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.util.Callback;
import org.openhab.core.common.NamedThreadFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

/**
 * The {@link TraccarWebhookServer} listens for webhook events from Traccar.
 *
 * @author Nanna Agesen - Initial contribution
 */
public class TraccarWebhookServer {

    private final Logger logger = LoggerFactory.getLogger(TraccarWebhookServer.class);
    private final Gson gson = new Gson();

    private final TraccarServerHandler serverHandler;
    private final int port;
    private @Nullable Server server;

    /**
     * Webhooks are answered at once and handled afterwards, one at a time, in arrival order.
     *
     * Before, a webhook was answered only when its handling was done - and the handling includes a
     * Nominatim lookup, rate-limited to one a second with 10 s timeouts. A batch of buffered
     * positions (68 pending in Traccar's log) then queued up behind each other's lookups and
     * Traccar gave up: thousands of "forwarding failed - Read timed out" a day, positions only
     * retried, events (geofence enter/exit) lost. And the requests ran on parallel threads, so a
     * geofenceExit and a geofenceEnter one second apart could be applied in the wrong order -
     * a motorcycle at home read "exited" and the garage door did not open.
     */
    private static final int QUEUE_CAPACITY = 2000;
    private @Nullable ThreadPoolExecutor worker;

    public TraccarWebhookServer(TraccarServerHandler serverHandler, int port) {
        this.serverHandler = serverHandler;
        this.port = port;
    }

    /**
     * Start the webhook server.
     */
    public void start() throws Exception {
        worker = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>(QUEUE_CAPACITY),
                new NamedThreadFactory("traccar-webhook"));
        server = new Server(port);
        server.setHandler(new WebhookHandler());
        server.start();
        logger.info("Traccar webhook server started on port {}", port);
    }

    /**
     * Stop the webhook server.
     */
    public void stop() {
        ThreadPoolExecutor localWorker = worker;
        if (localWorker != null) {
            localWorker.shutdownNow();
            worker = null;
        }
        Server localServer = server;
        if (localServer != null) {
            try {
                localServer.stop();
                logger.info("Traccar webhook server stopped");
            } catch (Exception e) {
                logger.warn("Error stopping webhook server: {}", e.getMessage());
            }
        }
    }

    /**
     * Jetty 12 core handler (openHAB 5.3). Jetty 9's servlet-style {@code AbstractHandler} and
     * {@code javax.servlet} are gone; the answers are the same as before (held by
     * {@code TraccarWebhookServerTest}): 200 with {"status":"ok"}, 404 for another path, 405 for another
     * method, 503 when the queue is full or stopped, 500 when the body cannot be read.
     */
    @NonNullByDefault({})
    private class WebhookHandler extends Handler.Abstract {
        @Override
        public boolean handle(Request request, Response response, Callback callback) {
            if (!"/webhook".equals(Request.getPathInContext(request))) {
                return status(response, callback, HttpStatus.NOT_FOUND_404);
            }

            // Accept both POST and GET methods (Traccar uses different methods for events vs positions)
            String method = request.getMethod();
            if (!"POST".equals(method) && !"GET".equals(method)) {
                return status(response, callback, HttpStatus.METHOD_NOT_ALLOWED_405);
            }

            try {
                Map<String, Object> eventData = null;

                // For POST requests, read JSON from body
                if ("POST".equals(method)) {
                    String body = Content.Source.asString(request, StandardCharsets.UTF_8);
                    /*
                     * DEBUG, not INFO, and this is a security fix rather than
                     * tidiness. The body is the whole Traccar payload, and for
                     * a phone-tracked device that includes the device's
                     * notificationTokens - a Firebase push credential - along
                     * with its name and exact coordinates. At INFO it was
                     * written to openhab.log on every webhook, which for an
                     * OsmAnd device is every few seconds.
                     */
                    logger.debug("Received webhook (POST): {}", body);
                    eventData = gson.fromJson(body, new TypeToken<Map<String, Object>>() {
                    }.getType());
                }
                // For GET requests, Traccar sends JSON as 'json' parameter
                else {
                    String jsonParam = Request.extractQueryParameters(request).getValue("json");
                    if (jsonParam != null && !jsonParam.isEmpty()) {
                        logger.debug("Received webhook (GET): {}", jsonParam); // see above: carries credentials
                        eventData = gson.fromJson(jsonParam, new TypeToken<Map<String, Object>>() {
                        }.getType());
                    } else {
                        logger.debug("GET request with no json parameter");
                    }
                }

                if (eventData != null) {
                    Object eventObj = eventData.get("event");
                    if (eventObj instanceof Map<?, ?>) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> event = (Map<String, Object>) eventObj;
                        logger.debug("Queueing webhook event type: {}", event.get("type"));
                    } else {
                        logger.debug("Queueing webhook position update");
                    }
                    ThreadPoolExecutor localWorker = worker;
                    final Map<String, Object> data = eventData;
                    try {
                        if (localWorker == null) {
                            throw new RejectedExecutionException("webhook worker not running");
                        }
                        localWorker.execute(() -> {
                            try {
                                serverHandler.handleWebhookEvent(data);
                            } catch (RuntimeException e) {
                                logger.warn("Failed to process webhook: {}", e.getMessage());
                            }
                        });
                    } catch (RejectedExecutionException e) {
                        // full or stopped: say so, and Traccar's forward.retry sends positions again
                        logger.warn("Webhook queue full or stopped ({}), asking Traccar to retry", e.getMessage());
                        return status(response, callback, HttpStatus.SERVICE_UNAVAILABLE_503);
                    }
                }

                response.setStatus(HttpStatus.OK_200);
                response.getHeaders().put(HttpHeader.CONTENT_TYPE, "application/json");
                Content.Sink.write(response, true, "{\"status\":\"ok\"}", callback);
                return true;
            } catch (Exception e) {
                logger.warn("Failed to process webhook: {}", e.getMessage());
                return status(response, callback, HttpStatus.INTERNAL_SERVER_ERROR_500);
            }
        }

        /** An answer without a body, as the Jetty 9 receiver gave. */
        private boolean status(Response response, Callback callback, int code) {
            response.setStatus(code);
            callback.succeeded();
            return true;
        }
    }
}
