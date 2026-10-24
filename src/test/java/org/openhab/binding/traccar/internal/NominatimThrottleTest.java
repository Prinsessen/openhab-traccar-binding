/*
 * Copyright (c) 2025-2026 Nanna Agesen
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.openhab.binding.traccar.internal;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

/**
 * The shared Nominatim budget: five devices once asked five times a second and kept going after a
 * 429 (10 279 refusals on 2026-10-06, 575 in an hour on 2026-10-24).
 *
 * @author Nanna Agesen - Initial contribution
 */
public class NominatimThrottleTest {

    @Test
    public void devicesShareOneRequestPerSecond() {
        NominatimThrottle t = new NominatimThrottle();
        assertEquals(0, t.reserve(10_000), "the first request goes at once");
        assertEquals(1000, t.reserve(10_000), "a second device at the same moment waits a second");
        assertEquals(2000, t.reserve(10_000), "a third waits two");
        assertEquals(0, t.reserve(20_000), "later on, no wait");
    }

    @Test
    public void aTooManyRequestsPausesEveryoneAndDoubles() {
        NominatimThrottle t = new NominatimThrottle();
        t.reserve(0);
        assertEquals(60_000, t.onResponse(429, 0, 0), "the first 429 starts a 60 s pause");
        assertEquals(-1, t.reserve(30_000), "nothing is sent during the pause");
        assertEquals(0, t.onResponse(429, 30_000, 0), "a 429 inside a pause does not start a new one (one warning)");
        assertTrue(t.reserve(200_000) >= 0, "after the pause requests go again");
        assertEquals(240_000, t.onResponse(429, 200_000, 0), "the pauses double: 60, 120, 240 s");
    }

    @Test
    public void anOkResetsThePause() {
        NominatimThrottle t = new NominatimThrottle();
        t.onResponse(429, 0, 0);
        t.onResponse(200, 100_000, 0);
        assertEquals(60_000, t.onResponse(429, 200_000, 0), "after a 200 the next 429 starts at 60 s again");
    }

    @Test
    public void retryAfterIsHonouredAndThePauseIsCapped() {
        NominatimThrottle t = new NominatimThrottle();
        assertEquals(300_000, t.onResponse(429, 0, 300), "Retry-After 300 s beats 60 s");
        NominatimThrottle u = new NominatimThrottle();
        long now = 0;
        for (int i = 0; i < 10; i++) {
            u.onResponse(429, now, 0);
            now += NominatimThrottle.MAX_PAUSE_MS + 1;
        }
        assertEquals(NominatimThrottle.MAX_PAUSE_MS, u.onResponse(429, now, 0), "never more than an hour");
    }
}
