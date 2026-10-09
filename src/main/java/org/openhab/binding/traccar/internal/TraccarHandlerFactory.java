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

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jetty.client.HttpClient;
import org.openhab.core.io.net.http.HttpClientFactory;
import org.openhab.core.thing.Bridge;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingTypeUID;
import org.openhab.core.thing.binding.BaseThingHandlerFactory;
import org.openhab.core.thing.binding.ThingHandler;
import org.openhab.core.thing.binding.ThingHandlerFactory;
import org.openhab.core.thing.type.ThingTypeRegistry;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

/**
 * The {@link TraccarHandlerFactory} is responsible for creating things and thing
 * handlers.
 *
 * @author Nanna Agesen - Initial contribution
 */
@NonNullByDefault
@Component(configurationPid = "binding.traccar", service = ThingHandlerFactory.class)
public class TraccarHandlerFactory extends BaseThingHandlerFactory {

    private final HttpClient httpClient;
    private final ThingTypeRegistry thingTypeRegistry;

    @Activate
    public TraccarHandlerFactory(@Reference HttpClientFactory httpClientFactory,
            @Reference ThingTypeRegistry thingTypeRegistry) {
        this.httpClient = httpClientFactory.getCommonHttpClient();
        this.thingTypeRegistry = thingTypeRegistry;
    }

    @Override
    public boolean supportsThingType(ThingTypeUID thingTypeUID) {
        return SUPPORTED_THING_TYPES_UIDS.contains(thingTypeUID);
    }

    @Override
    protected @Nullable ThingHandler createHandler(Thing thing) {
        ThingTypeUID thingTypeUID = thing.getThingTypeUID();

        if (THING_TYPE_SERVER.equals(thingTypeUID)) {
            return new TraccarServerHandler((Bridge) thing, httpClient);
        } else if (THING_TYPE_DEVICE.equals(thingTypeUID)) {
            return new TraccarDeviceHandler(thing, thingTypeRegistry);
        }

        return null;
    }
}
