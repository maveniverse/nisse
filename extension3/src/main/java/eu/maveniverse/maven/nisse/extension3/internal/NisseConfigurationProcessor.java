/*
 * Copyright (c) 2023-2024 Maveniverse Org.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 */
package eu.maveniverse.maven.nisse.extension3.internal;

import static java.util.Objects.requireNonNull;

import eu.maveniverse.maven.nisse.core.PropertyKeyManager;
import java.util.HashMap;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import org.apache.maven.cli.CliRequest;
import org.apache.maven.cli.configuration.ConfigurationProcessor;
import org.apache.maven.cli.configuration.SettingsXmlConfigurationProcessor;
import org.eclipse.sisu.Priority;

@Singleton
@Named
@Priority(200)
public class NisseConfigurationProcessor implements ConfigurationProcessor {
    private final PropertyKeyManager propertyKeyManager;
    private final SettingsXmlConfigurationProcessor settingsXmlConfigurationProcessor;

    @Inject
    public NisseConfigurationProcessor(
            PropertyKeyManager propertyKeyManager,
            SettingsXmlConfigurationProcessor settingsXmlConfigurationProcessor) {
        this.propertyKeyManager = requireNonNull(propertyKeyManager, "propertyKeyManager");
        this.settingsXmlConfigurationProcessor =
                requireNonNull(settingsXmlConfigurationProcessor, "settingsXmlConfigurationProcessor");
    }

    @Override
    public void process(CliRequest request) throws Exception {
        settingsXmlConfigurationProcessor.process(request);

        // push what we have into user properties
        propertyKeyManager
                .allKeys(request.getUserProperties().entrySet().stream()
                        .collect(Collectors.toMap(
                                e -> String.valueOf(e.getKey()),
                                e -> String.valueOf(e.getValue()),
                                (prev, next) -> next,
                                HashMap::new)))
                .forEach(v ->
                        v.getValue().ifPresent(s -> request.getUserProperties().setProperty(v.getKey(), s)));
    }
}
