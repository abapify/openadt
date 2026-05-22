package org.openadt.core;

import com.sap.adt.destinations.model.AdtDestinationDataFactory;
import com.sap.adt.destinations.model.IDestinationData;
import com.sap.adt.destinations.model.IDestinationDataWritable;
import com.sap.adt.destinations.model.ISystemConfiguration;
import com.sap.adt.destinations.model.internal.SystemConfigurationWritable;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Maps Eclipse {@code .destination.properties} into {@link IDestinationData} without
 * calling {@code AdtDestinationDataFactory.createDestinationData(InputStream)} (that
 * path pulls Eclipse {@code InstanceScope} preferences).
 */
public final class EclipseDestinationLoader {
    public IDestinationData load(Path destinationPropertiesFile) throws IOException {
        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(destinationPropertiesFile)) {
            properties.load(input);
        }
        return map(properties);
    }

    IDestinationData map(Properties properties) {
        String id = require(properties, "id");
        IDestinationDataWritable data = AdtDestinationDataFactory.newDestinationData(id);
        data.setId(id);
        setIfPresent(properties, "user", data::setUser);
        setIfPresent(properties, "client", data::setClient);
        setIfPresent(properties, "language", data::setLanguage);

        String configurationName = properties.getProperty("systemConfiguration", id);
        data.setSystemConfiguration(createSystemConfiguration(id, configurationName, properties));
        data.setSystemConfigurationName(configurationName);
        return data.getReadOnlyClone();
    }

    private static ISystemConfiguration createSystemConfiguration(
        String destinationId,
        String configurationName,
        Properties properties
    ) {
        // getWritable() clones; mutate SystemConfigurationWritable directly (same as SDK factory).
        SystemConfigurationWritable config = new SystemConfigurationWritable(configurationName);
        setIfPresent(properties, "description", config::setDescription);
        setIfPresent(properties, "systemId", config::setSystemId);
        setIfPresent(properties, "client", config::setPreferredClient);
        setIfPresent(properties, "language", config::setPreferredLanguage);
        setIfPresent(properties, "user", config::setPreferredUser);
        setIfPresent(properties, "messageServer", config::setMessageServer);
        setIfPresent(properties, "group", config::setGroup);
        setIfPresent(properties, "partnerName", config::setPartnerName);

        String messageServerService = properties.getProperty("messageServerService");
        if (messageServerService != null && !messageServerService.isBlank()) {
            config.setMessageServerService(messageServerService.trim());
        }

        String sso = properties.getProperty("SSOEnabled", "1");
        config.setSSOEnabled("1".equals(sso));
        config.setSNCType(resolveSncType(properties.getProperty("SNCType")));
        setIfPresent(properties, "router", config::setRouter);
        setIfPresent(properties, "server", config::setServer);
        setIfPresent(properties, "systemNumber", config::setSystemNumber);
        setIfPresent(properties, "gatewayServer", config::setGatewayServer);
        setIfPresent(properties, "gatewayServerService", config::setGatewayServerService);
        return config;
    }

    /**
     * Same codes as {@code SystemConfigurationWritable.setSystemConfigurationAttribute}
     * and {@link ISystemConfiguration.SNCType#getByCode(int)}.
     */
    static ISystemConfiguration.SNCType resolveSncType(String sncType) {
        if (sncType == null || sncType.isBlank()) {
            return ISystemConfiguration.SNCType.SNC_DEFAULT;
        }
        try {
            return ISystemConfiguration.SNCType.getByCode(Integer.parseInt(sncType.trim()));
        } catch (IllegalArgumentException ex) {
            return ISystemConfiguration.SNCType.SNC_UNAVAILABLE;
        }
    }

    private static String require(Properties properties, String key) {
        String value = properties.getProperty(key);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Missing property: " + key);
        }
        return value.trim();
    }

    private static void setIfPresent(Properties properties, String key, java.util.function.Consumer<String> setter) {
        String value = properties.getProperty(key);
        if (value != null && !value.isBlank()) {
            setter.accept(value.trim());
        }
    }
}
