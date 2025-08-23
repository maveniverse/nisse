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
import eu.maveniverse.maven.nisse.core.PropertyKeyNamingStrategies;
import eu.maveniverse.maven.nisse.core.Version;
import eu.maveniverse.maven.nisse.core.simple.SimpleNisseConfiguration;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Properties;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import org.apache.maven.cli.CliRequest;
import org.apache.maven.cli.configuration.ConfigurationProcessor;
import org.apache.maven.cli.configuration.SettingsXmlConfigurationProcessor;
import org.eclipse.sisu.Priority;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
@Named
@Priority(200)
final class NisseConfigurationProcessor implements ConfigurationProcessor {
    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final NisseManager nisseManager;
    private final SettingsXmlConfigurationProcessor settingsXmlConfigurationProcessor;

    @Inject
    public NisseConfigurationProcessor(
            NisseManager nisseManager, SettingsXmlConfigurationProcessor settingsXmlConfigurationProcessor) {
        this.nisseManager = requireNonNull(nisseManager, "nisseManager");
        this.settingsXmlConfigurationProcessor =
                requireNonNull(settingsXmlConfigurationProcessor, "settingsXmlConfigurationProcessor");
    }

    @Override
    public void process(CliRequest request) throws Exception {
        settingsXmlConfigurationProcessor.process(request);

        logger.info("Maveniverse Nisse {} loaded", Version.version());

        // create properties and push what we got into CLI user properties
        Properties userProperties = request.getUserProperties();
        NisseConfiguration configuration = SimpleNisseConfiguration.builder()
                .withSystemProperties(request.getSystemProperties())
                .withUserProperties(request.getUserProperties())
                .withCurrentWorkingDirectory(Paths.get(request.getWorkingDirectory()))
                .withSessionRootDirectory(
                        request.getMultiModuleProjectDirectory().toPath())
                .combinePropertyKeyNamingStrategy(PropertyKeyNamingStrategies.translated(
                        PropertyKeyNamingStrategies.translationTableFromPropertiesFile(
                                request.getMultiModuleProjectDirectory()
                                        .toPath()
                                        .resolve(".mvn")
                                        .resolve("nisse-translation.properties")),
                        PropertyKeyNamingStrategies.sourcePrefixed(),
                        PropertyKeyNamingStrategies.defaultStrategy()))
                .build();
        Map<String, String> nisseProperties = nisseManager.createProperties(configuration);
        logger.info("Nisse injecting {} properties into User Properties", nisseProperties.size());
        if (Boolean.parseBoolean(request.getUserProperties().getProperty("nisse.dump", "false"))) {
            nisseProperties.forEach((k, v) -> logger.info("{}={}", k, v));
        }
        nisseProperties.forEach((k, v) -> {
            if (!userProperties.containsKey(k)) {
                request.getUserProperties().setProperty(k, v);
            }
        });
    }
}
