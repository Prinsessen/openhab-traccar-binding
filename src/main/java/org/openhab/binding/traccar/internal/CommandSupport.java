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

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;

/**
 * The pure parts of sending a custom command to a tracker and reading its reply, kept free of
 * openHAB and HTTP so they can be tested: the allow-list check, and the parsing of a Teltonika
 * {@code setparam} / {@code getparam} reply into parameter id to value.
 *
 * <p>
 * Teltonika replies look like {@code New value 105:12300;10000:60;} to a setparam and
 * {@code Param ID:105 Value:12300;101:48;1000:30} to a getparam (the first pair spelled out, the rest
 * as id:value). A parameter that does not exist answers {@code Param ID:53040 doesn't exist}, which
 * parses to nothing.
 *
 * @author Nanna Agesen - Initial contribution
 */
@NonNullByDefault
public final class CommandSupport {

    private static final Pattern PAIR = Pattern.compile("(\\d+):(-?\\d+)");
    private static final Pattern GETPARAM_FIRST = Pattern.compile("Param ID:(\\d+) Value:(-?\\d+)");

    private CommandSupport() {
    }

    /**
     * Whether a command text may be sent, by the thing's {@code allowedCommands} regular expression.
     * An empty or missing expression allows nothing - a device that has not been told what it may be
     * sent is not sent anything. An expression that does not compile allows nothing either.
     */
    public static boolean isAllowed(@Nullable String allowedCommands, String command) {
        if (allowedCommands == null || allowedCommands.isBlank() || command.isBlank()) {
            return false;
        }
        try {
            return Pattern.compile(allowedCommands.trim()).matcher(command.trim()).find();
        } catch (PatternSyntaxException e) {
            return false;
        }
    }

    /**
     * The parameter id:value pairs a Teltonika reply confirms, in the order they appear. Empty for a
     * reply that confirms nothing ("doesn't exist", an error text, or not a parameter reply at all).
     */
    public static Map<Integer, Long> parseParameterReply(@Nullable String reply) {
        Map<Integer, Long> out = new LinkedHashMap<>();
        if (reply == null) {
            return out;
        }
        String text = reply.trim();
        Matcher first = GETPARAM_FIRST.matcher(text);
        int from = 0;
        if (first.find()) {
            out.put(Integer.parseInt(first.group(1)), Long.parseLong(first.group(2)));
            from = first.end();
        } else if (text.startsWith("New value")) {
            from = "New value".length();
        } else {
            return out;
        }
        Matcher m = PAIR.matcher(text);
        m.region(from, text.length());
        while (m.find()) {
            out.put(Integer.parseInt(m.group(1)), Long.parseLong(m.group(2)));
        }
        return out;
    }

    /**
     * The parameter id a {@code setparam id:value} command addresses first, or -1 for any other command.
     */
    public static int setparamId(String command) {
        String c = command.trim();
        if (!c.startsWith("setparam ")) {
            return -1;
        }
        Matcher m = PAIR.matcher(c);
        return m.find() ? Integer.parseInt(m.group(1)) : -1;
    }
}
