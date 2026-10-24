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

import org.eclipse.jdt.annotation.NonNullByDefault;

/**
 * The request budget for Nominatim, shared by every {@link NominatimGeocoder} in the binding.
 * <p>
 * OSM's usage policy allows one request per second per client. Each device used to keep its own
 * one-second limit, so five devices could ask five times a second; and a 429 ("Too Many Requests")
 * was followed by the next request at once. Both kept the binding rate-limited for long stretches.
 * Now there is one limit for all devices, and a 429 or 503 pauses every request: 60 s, doubled at
 * each further 429 up to an hour, or the server's Retry-After if that is longer. A 200 resets it.
 *
 * @author Nanna Agesen - Initial contribution
 */
@NonNullByDefault
final class NominatimThrottle {

    static final long MIN_INTERVAL_MS = 1000;
    static final long FIRST_PAUSE_MS = 60_000;
    static final long MAX_PAUSE_MS = 3_600_000;

    private long lastRequestMs = Long.MIN_VALUE / 2;
    private long pausedUntilMs = 0;
    private long pauseMs = 0;

    /**
     * Reserve the next request slot.
     *
     * @param nowMs the current time in milliseconds
     * @return -1 while paused after a 429 (send nothing), otherwise the milliseconds to wait before sending
     */
    synchronized long reserve(long nowMs) {
        if (nowMs < pausedUntilMs) {
            return -1;
        }
        long slot = Math.max(nowMs, lastRequestMs + MIN_INTERVAL_MS);
        lastRequestMs = slot;
        return slot - nowMs;
    }

    /**
     * Account for the server's answer.
     *
     * @param status the HTTP status
     * @param nowMs the current time in milliseconds
     * @param retryAfterSec the Retry-After header in seconds, or 0 if there was none
     * @return the length of a pause that this answer STARTED in milliseconds, 0 otherwise (so the caller
     *         warns once per pause, not once per request)
     */
    synchronized long onResponse(int status, long nowMs, long retryAfterSec) {
        if (status == 429 || status == 503) {
            pauseMs = pauseMs == 0 ? FIRST_PAUSE_MS : Math.min(MAX_PAUSE_MS, pauseMs * 2);
            long pause = Math.max(pauseMs, retryAfterSec * 1000);
            boolean started = nowMs >= pausedUntilMs;
            pausedUntilMs = Math.max(pausedUntilMs, nowMs + pause);
            return started ? pause : 0;
        }
        if (status == 200) {
            pauseMs = 0;
        }
        return 0;
    }
}
