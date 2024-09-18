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
import eu.maveniverse.maven.nisse.core.internal.SimpleNisseConfiguration;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Properties;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import org.apache.maven.cli.CliRequest;
import org.apache.maven.cli.configuration.ConfigurationProcessor;
import org.apache.maven.cli.configuration.SettingsXmlConfigurationProcessor;
import org.apache.maven.rtinfo.RuntimeInformation;
import org.eclipse.aether.util.version.GenericVersionScheme;
import org.eclipse.aether.version.InvalidVersionSpecificationException;
import org.eclipse.aether.version.Version;
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
    private final RuntimeInformation runtimeInformation;

    @Inject
    public NisseConfigurationProcessor(
            NisseManager nisseManager,
            SettingsXmlConfigurationProcessor settingsXmlConfigurationProcessor,
            RuntimeInformation runtimeInformation) {
        this.nisseManager = requireNonNull(nisseManager, "nisseManager");
        this.settingsXmlConfigurationProcessor =
                requireNonNull(settingsXmlConfigurationProcessor, "settingsXmlConfigurationProcessor");
        this.runtimeInformation = requireNonNull(runtimeInformation, "runtimeInformation");
    }

    @Override
    public void process(CliRequest request) throws Exception {
        settingsXmlConfigurationProcessor.process(request);

        // Broadening support: Maven versions pre 3.9.2 had no means to configure resources
        // from project. In those case we "backport" session.rootDirectory ONLY
        // CliRequest.multiModuleProjectDirectory -> session.rootDirectory
        if (needsTrick()) {
            String sessionRootDirectory =
                    request.getMultiModuleProjectDirectory().getAbsolutePath();
            Properties userProperties = request.getUserProperties();
            for (String key : userProperties.stringPropertyNames()) {
                String value = userProperties.getProperty(key);
                if (value != null && value.contains("${session.rootDirectory}")) {
                    value = value.replace("${session.rootDirectory}", sessionRootDirectory);
                }
                userProperties.setProperty(key, value);
            }
        }

        // create properties and push what we got into CLI user properties
        Properties userProperties = request.getUserProperties();
        NisseConfiguration configuration = SimpleNisseConfiguration.builder()
                .withSystemProperties(request.getSystemProperties())
                .withUserProperties(request.getUserProperties())
                .withCurrentWorkingDirectory(Paths.get(request.getWorkingDirectory()))
                .build();
        Map<String, String> nisseProperties = nisseManager.createProperties(configuration);
        logger.info("Nisse injecting {} properties into User Properties", nisseProperties.size());
        nisseProperties.forEach((k, v) -> {
            if (!userProperties.containsKey(k)) {
                request.getUserProperties().setProperty(k, v);
            }
        });
    }

    private boolean needsTrick() {
        try {
            GenericVersionScheme versionScheme = new GenericVersionScheme();
            Version notNeeds = versionScheme.parseVersion("3.9.2");
            Version currentMvn = versionScheme.parseVersion(runtimeInformation.getMavenVersion());
            return notNeeds.compareTo(currentMvn) > -1;
        } catch (InvalidVersionSpecificationException e) {
            throw new RuntimeException(e);
        }
    }
}
