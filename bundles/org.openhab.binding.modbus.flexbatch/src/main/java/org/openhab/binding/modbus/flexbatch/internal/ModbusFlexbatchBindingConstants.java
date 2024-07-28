/**
 * Copyright (c) 2010-2024 Contributors to the openHAB project
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
package org.openhab.binding.modbus.flexbatch.internal;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.binding.modbus.ModbusBindingConstants;
import org.openhab.core.thing.ThingTypeUID;

/**
 * The {@link modbus.flexbatchBindingConstants} class defines common constants, which are
 * used across the whole binding.
 *
 * @author Markus Barchfeld - Initial contribution
 */
@NonNullByDefault
public class ModbusFlexbatchBindingConstants {
    private static final String BINDING_ID = ModbusBindingConstants.BINDING_ID;

    // List of all Thing Type UIDs
    public static final ThingTypeUID THING_TYPE_GENERIC_DEVICE = new ThingTypeUID(BINDING_ID, "flexbatch");

    // List of all Channel ids
    // channel ids will be generated: depending on your configuration ("flex") and for many devices ("batch")
}
