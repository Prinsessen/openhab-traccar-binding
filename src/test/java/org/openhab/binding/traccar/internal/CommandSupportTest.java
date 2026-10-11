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

import java.util.Map;

import org.junit.jupiter.api.Test;

/**
 * @author Nanna Agesen - Initial contribution
 */
public class CommandSupportTest {

    private static final String ALLOW = "^(getparam|getver|getstatus|lvcangetinfo)\\b|^setparam (100|101|105|1000|10[012]00):\\d+(;(100|101|105|1000|10[012]00):\\d+)*$";

    @Test
    public void allowListDefaultsToNothing() {
        assertFalse(CommandSupport.isAllowed(null, "getver"));
        assertFalse(CommandSupport.isAllowed("", "getver"));
        assertFalse(CommandSupport.isAllowed("   ", "getver"));
        assertFalse(CommandSupport.isAllowed(".*", ""), "an empty command is never sent");
    }

    @Test
    public void allowListMatchesWhatItSays() {
        assertTrue(CommandSupport.isAllowed(ALLOW, "getver"));
        assertTrue(CommandSupport.isAllowed(ALLOW, "getparam 105;101"));
        assertTrue(CommandSupport.isAllowed(ALLOW, "setparam 105:12300"));
        assertTrue(CommandSupport.isAllowed(ALLOW, "setparam 10000:60;10100:60;10200:60"));
        assertFalse(CommandSupport.isAllowed(ALLOW, "setparam 2001:evil"), "server address is not in the list");
        assertFalse(CommandSupport.isAllowed(ALLOW, "cpureset"));
        assertFalse(CommandSupport.isAllowed(ALLOW, "setparam 105:12300;2001:x"), "one foreign id refuses the whole command");
        assertTrue(CommandSupport.isAllowed(".*", "anything at all"));
        assertFalse(CommandSupport.isAllowed("(unclosed", "getver"), "a broken expression allows nothing");
    }

    @Test
    public void setparamReply() {
        Map<Integer, Long> r = CommandSupport.parseParameterReply("New value 105:12300;");
        assertEquals(Map.of(105, 12300L), r);
        r = CommandSupport.parseParameterReply("New value 10000:60;10100:60;10200:60;");
        assertEquals(3, r.size());
        assertEquals(60L, r.get(10200));
    }

    @Test
    public void getparamReply() {
        Map<Integer, Long> r = CommandSupport.parseParameterReply("Param ID:45440 Value:2;45441:5;45635:1;45970:0;105:13700");
        assertEquals(5, r.size());
        assertEquals(2L, r.get(45440));
        assertEquals(13700L, r.get(105));
        assertEquals(0L, r.get(45970));
    }

    @Test
    public void repliesThatConfirmNothing() {
        assertTrue(CommandSupport.parseParameterReply("Param ID:53040 doesn't exist").isEmpty());
        assertTrue(CommandSupport.parseParameterReply("Ver:03.01.01.Rev.15 GPS:AXN_5.10.21 Hw:FMC650").isEmpty());
        assertTrue(CommandSupport.parseParameterReply(null).isEmpty());
        assertTrue(CommandSupport.parseParameterReply("").isEmpty());
    }

    @Test
    public void setparamIdOfACommand() {
        assertEquals(105, CommandSupport.setparamId("setparam 105:12300"));
        assertEquals(10000, CommandSupport.setparamId("setparam 10000:60;10100:60"));
        assertEquals(-1, CommandSupport.setparamId("getparam 105"));
        assertEquals(-1, CommandSupport.setparamId("getver"));
    }
}
