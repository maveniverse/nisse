/*
 * Copyright (c) 2023-2024 Maveniverse Org.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 */
package eu.maveniverse.maven.nisse.extension3.internal;

import static java.util.Objects.requireNonNull;

import eu.maveniverse.maven.nisse.core.NisseConfiguration;
import eu.maveniverse.maven.nisse.core.NisseManager;
import eu.maveniverse.maven.nisse.core.NisseSession;
import eu.maveniverse.maven.nisse.core.internal.SimpleNisseConfiguration;
import java.nio.file.Paths;
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
    private final NisseManager nisseManager;
    private final SettingsXmlConfigurationProcessor settingsXmlConfigurationProcessor;

    @Inject
    public NisseConfigurationProcessor(
            NisseManager nisseManager, SettingsXmlConfigurationProcessor settingsXmlConfigurationProcessor) {
        this.nisseManager = requireNonNull(nisseManager, "propertyKeyManager");
        this.settingsXmlConfigurationProcessor =
                requireNonNull(settingsXmlConfigurationProcessor, "settingsXmlConfigurationProcessor");
    }

    @Override
    public void process(CliRequest request) throws Exception {
        settingsXmlConfigurationProcessor.process(request);

        // push what we have into user properties
        NisseConfiguration configuration = SimpleNisseConfiguration.builder()
                .withSystemProperties(request.getSystemProperties())
                .withUserProperties(request.getUserProperties())
                .withCurrentWorkingDirectory(Paths.get(request.getWorkingDirectory()))
                .build();
        String sessionId = "n-" + request.hashCode() + "-" + System.nanoTime();
        NisseSession session = nisseManager.createSession(sessionId, configuration);
        session.getAllProperties().forEach((k, v) -> request.getUserProperties().setProperty(k, v));
        request.getUserProperties().setProperty(NisseConfiguration.PROPERTY_PREFIX + "sessionId", sessionId);
    }
}
