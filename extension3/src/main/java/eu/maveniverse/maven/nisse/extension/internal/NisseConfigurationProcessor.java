/*
 * Copyright (c) 2023-2024 Maveniverse Org.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 */
package eu.maveniverse.maven.nisse.extension.internal;

import static eu.maveniverse.maven.nisse.core.Nisse.PROPERTY_PREFIX;
import static java.util.Objects.requireNonNull;

import eu.maveniverse.maven.nisse.core.PropertyKey;
import eu.maveniverse.maven.nisse.core.PropertyKeyManager;
import java.util.Optional;
import java.util.Properties;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Provider;
import javax.inject.Singleton;
import org.apache.maven.cli.CliRequest;
import org.apache.maven.cli.configuration.ConfigurationProcessor;
import org.apache.maven.cli.configuration.SettingsXmlConfigurationProcessor;
import org.eclipse.sisu.Priority;

@Singleton
@Named
@Priority(200)
public class NisseConfigurationProcessor implements ConfigurationProcessor {
    private final Provider<PropertyKeyManager> propertyKeyManagerProvider;
    private final SettingsXmlConfigurationProcessor settingsXmlConfigurationProcessor;

    @Inject
    public NisseConfigurationProcessor(
            Provider<PropertyKeyManager> propertyKeyManagerProvider,
            SettingsXmlConfigurationProcessor settingsXmlConfigurationProcessor) {
        this.propertyKeyManagerProvider = requireNonNull(propertyKeyManagerProvider, "propertyKeyManagerProvider");
        this.settingsXmlConfigurationProcessor =
                requireNonNull(settingsXmlConfigurationProcessor, "settingsXmlConfigurationProcessor");
    }

    @Override
    public void process(CliRequest request) throws Exception {
        settingsXmlConfigurationProcessor.process(request);

        // push what is needed
        Properties userProperties = request.getUserProperties();
        PropertyKeyManager propertyKeyManager = propertyKeyManagerProvider.get();
        for (PropertyKey propertyKey : propertyKeyManager.allKeys()) {
            Optional<String> value = propertyKey.getValue();
            value.ifPresent(s -> userProperties.setProperty(PROPERTY_PREFIX + propertyKey.getKey(), s));
        }
    }
}
