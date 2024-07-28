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

import java.util.Arrays;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.modbus.handler.ModbusEndpointThingHandler;
import org.openhab.core.io.transport.modbus.AsyncModbusFailure;
import org.openhab.core.io.transport.modbus.AsyncModbusReadResult;
import org.openhab.core.io.transport.modbus.ModbusCommunicationInterface;
import org.openhab.core.io.transport.modbus.ModbusReadFunctionCode;
import org.openhab.core.io.transport.modbus.ModbusReadRequestBlueprint;
import org.openhab.core.io.transport.modbus.ModbusRegisterArray;
import org.openhab.core.io.transport.modbus.ValueBuffer;
import org.openhab.core.library.types.QuantityType;
import org.openhab.core.library.unit.Units;
import org.openhab.core.thing.Bridge;
import org.openhab.core.thing.Channel;
import org.openhab.core.thing.ChannelUID;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingStatus;
import org.openhab.core.thing.ThingStatusDetail;
import org.openhab.core.thing.binding.BaseThingHandler;
import org.openhab.core.thing.binding.ThingHandler;
import org.openhab.core.thing.binding.builder.ChannelBuilder;
import org.openhab.core.thing.binding.builder.ThingBuilder;
import org.openhab.core.thing.type.ChannelTypeUID;
import org.openhab.core.types.Command;
import org.openhab.core.types.RefreshType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link ModbusFlexbatchHandler} is responsible for handling commands, which are
 * sent to one of the channels.
 *
 * @author Markus Barchfeld - Initial contribution
 */
@NonNullByDefault
public class ModbusFlexbatchHandler extends BaseThingHandler {
    public enum ReadStatus {
        NOT_RECEIVED,
        READ_SUCCESS,
        READ_FAILED
    }

    private final Logger logger = LoggerFactory.getLogger(ModbusFlexbatchHandler.class);

    private ReadStatus dataRead = ReadStatus.NOT_RECEIVED;
    protected volatile @Nullable ModbusCommunicationInterface comms = null;

    public ModbusFlexbatchHandler(Thing thing) {
        super(thing);
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        // if (CHANNEL_1.equals(channelUID.getId())) {
        if (command instanceof RefreshType) {
            // TODO: handle data refresh
        }

        // TODO: handle command

        // Note: if communication with thing fails for some reason,
        // indicate that by setting the status with detail information:
        // updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR,
        // "Could not control device at IP address x.x.x.x");
        // }
    }

    @Override
    public void initialize() {
        ModbusFlexbatchConfiguration config = getConfigAs(ModbusFlexbatchConfiguration.class);

        // TODO: Initialize the handler.
        // The framework requires you to return from this method quickly, i.e. any network access must be done in
        // the background initialization below.
        // Also, before leaving this method a thing status from one of ONLINE, OFFLINE or UNKNOWN must be set. This
        // might already be the real thing status in case you can decide it directly.
        // In case you can not decide the thing status directly (e.g. for long running connection handshake using WAN
        // access or similar) you should set status UNKNOWN here and then decide the real status asynchronously in the
        // background.

        // set the thing status to UNKNOWN temporarily and let the background task decide for the real status.
        // the framework is then able to reuse the resources from the thing handler initialization.
        // we set this upfront to reliably check status updates in unit tests.
        updateStatus(ThingStatus.UNKNOWN);

        if (thing.getChannels().size() > 0) {
            final ThingBuilder thingChannelRemovalBuilder = editThing();
            thing.getChannels().stream().forEach(channel -> {
                thingChannelRemovalBuilder.withoutChannel(channel.getUID());
            });
            updateThing(thingChannelRemovalBuilder.build());

        }

        scheduler.shutdown();
        try {
            scheduler.awaitTermination(10, TimeUnit.SECONDS);
        } catch (InterruptedException ie) {
            // TODO handle
        }

        final ThingBuilder thingBuilder = editThing();
        Stream<Integer> deviceIds = Arrays.asList(config.deviceIds.split(",")).stream()
                .map(s -> Integer.valueOf(s).intValue());

        deviceIds.forEach(deviceId -> {

            ChannelUID activePower = new ChannelUID(thing.getUID(), "Messwerte", "ActivePower-" + deviceId);

            Channel channel = ChannelBuilder.create(activePower).withDescription("The active Channel")
                    .withLabel("ActivePower " + deviceId).withType(new ChannelTypeUID("modbus", "active-power"))
                    .withAcceptedItemType("Number").build();

            thingBuilder.withChannel(channel);
            scheduler.execute(() -> {
                // E3DCConfiguration localConfig = getConfigAs(E3DCConfiguration.class);
                // config = localConfig;
                ModbusCommunicationInterface localComms = connectEndpoint();
                if (localComms != null) {
                    ModbusReadRequestBlueprint dataRequest = new ModbusReadRequestBlueprint(deviceId,
                            ModbusReadFunctionCode.READ_MULTIPLE_REGISTERS, 23316, 8, 3);

                    localComms.registerRegularPoll(dataRequest, 1000, 1000, this::handleDataResult,
                            this::handleDataFailure);
                } // else state handling performed in connectEndPoint function
            });
        });

        updateThing(thingBuilder.build());

        // These logging types should be primarily used by bindings
        // logger.trace("Example trace message");
        // logger.debug("Example debug message");
        // logger.warn("Example warn message");
        //
        // Logging to INFO should be avoided normally.
        // See https://www.openhab.org/docs/developer/guidelines.html#f-logging

        // Note: When initialization can NOT be done set the status with more details for further
        // analysis. See also class ThingStatusDetail for all available status details.
        // Add a description to give user information to understand why thing does not work as expected. E.g.
        // updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR,
        // "Can not access device as username and/or password are invalid");
    }

    void handleDataResult(AsyncModbusReadResult result) {
        if (dataRead != ReadStatus.READ_SUCCESS) {
            // update status only if bit switches
            dataRead = ReadStatus.READ_SUCCESS;
            updateStatus(ThingStatus.ONLINE);
        }
        // logger.debug("Got response for request: " + result.getRequest().toString());
        ModbusReadRequestBlueprint request = result.getRequest();

        int deviceId = request.getUnitID();
        Optional<ModbusRegisterArray> opt = result.getRegisters();

        byte[] bArray = opt.get().getBytes();

        ValueBuffer wrapper = ValueBuffer.wrap(bArray);
        long result1 = wrapper.getUInt32();

        QuantityType newCurrentPower = QuantityType.valueOf(result1 / 100, Units.WATT);
        ChannelUID channelUID = new ChannelUID(thing.getUID(), "Messwerte", "ActivePower-" + deviceId);
        updateState(channelUID, newCurrentPower);

        long result2 = wrapper.getUInt32();
        long result3 = wrapper.getUInt32();
    }

    void handleDataFailure(AsyncModbusFailure<ModbusReadRequestBlueprint> result) {
        if (dataRead != ReadStatus.READ_FAILED) {
            // update status only if bit switches
            dataRead = ReadStatus.READ_FAILED;
            updateStatus(ThingStatus.OFFLINE);
        }
    }

    /**
     * Get the endpoint handler from the bridge this handler is connected to
     * Checks that we're connected to the right type of bridge
     *
     * @return the endpoint handler or null if the bridge does not exist
     */
    private @Nullable ModbusEndpointThingHandler getEndpointThingHandler() {
        Bridge bridge = getBridge();
        if (bridge == null) {
            logger.debug("Bridge is null");
            return null;
        }
        if (bridge.getStatus() != ThingStatus.ONLINE) {
            logger.debug("Bridge is not online");
            return null;
        }

        ThingHandler handler = bridge.getHandler();
        if (handler == null) {
            logger.debug("Bridge handler is null");
            return null;
        }

        if (handler instanceof ModbusEndpointThingHandler thingHandler) {
            return thingHandler;
        } else {
            logger.debug("Unexpected bridge handler: {}", handler);
            return null;
        }
    }

    /**
     * Get a reference to the modbus endpoint
     */
    private @Nullable ModbusCommunicationInterface connectEndpoint() {
        if (comms != null) {
            return comms;
        }

        ModbusEndpointThingHandler slaveEndpointThingHandler = getEndpointThingHandler();
        if (slaveEndpointThingHandler == null) {
            @SuppressWarnings("null")
            String label = Optional.ofNullable(getBridge()).map(b -> b.getLabel()).orElse("<null>");
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.BRIDGE_OFFLINE,
                    String.format("Bridge '%s' is offline", label));
            return null;
        }

        comms = slaveEndpointThingHandler.getCommunicationInterface();

        if (comms == null) {
            @SuppressWarnings("null")
            String label = Optional.ofNullable(getBridge()).map(b -> b.getLabel()).orElse("<null>");
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.BRIDGE_OFFLINE,
                    String.format("Bridge '%s' not completely initialized", label));
            return null;
        } else {
            return comms;
        }
    }
}
