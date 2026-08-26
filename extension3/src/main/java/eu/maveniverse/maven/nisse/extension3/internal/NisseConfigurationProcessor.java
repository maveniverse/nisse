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
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Properties;
import java.util.function.UnaryOperator;
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

        // Maven 3 does NOT auto-load .mvn/maven-user.properties (unlike Maven 4).
        // Load nisse-prefixed properties from that file so that export-subst values
        // (expanded by `git archive`) are available to ModelVersionProcessor even
        // when the jgit source is inactive (no .git directory in source archives).
        // Values are interpolated to match Maven 4 behaviour (session.rootDirectory,
        // system properties, user properties).
        loadMavenUserProperties(
                request.getMultiModuleProjectDirectory().toPath(),
                request.getSystemProperties(),
                request.getUserProperties());
    }

    /**
     * Loads {@code nisse.*} properties from {@code .mvn/maven-user.properties} into
     * user properties.  This bridges the gap between Maven 3 (which ignores the file)
     * and Maven 4 (which auto-loads <em>and interpolates</em> it).
     * <p>
     * Uses {@link MavenPropertiesLoader} (adapted from Maven 4's property loading
     * infrastructure) which supports {@code ${includes}} directives,
     * {@code maven.override.*} prefixes, value trimming, UTF-8 loading, and full
     * variable substitution via {@link NisseInterpolator} (default values, alternative
     * values, nested interpolation, escape handling, cycle detection).
     * <p>
     * Only keys with the {@code nisse.} prefix are considered; unexpanded git
     * {@code export-subst} placeholders ({@code $Format:…$}) are silently skipped.
     * Properties already present in {@code userProperties} (from {@code -D} flags or
     * from nisse's own property sources) are never overwritten.
     */
    private void loadMavenUserProperties(Path sessionRoot, Properties systemProperties, Properties userProperties) {
        Path mavenUserPropsPath = sessionRoot.resolve(".mvn").resolve("maven-user.properties");
        if (!Files.isRegularFile(mavenUserPropsPath)) {
            return;
        }

        try {
            String rootDir = sessionRoot.toString();
            // Callback for external variable resolution — same sources Maven 4 uses
            // in BaseParser.populateUserProperties() via MavenPropertiesLoader
            UnaryOperator<String> callback = key -> {
                if ("session.rootDirectory".equals(key) || "session.topDirectory".equals(key)) {
                    return rootDir;
                }
                if ("maven.project.conf".equals(key)) {
                    return sessionRoot.resolve(".mvn").toString();
                }
                String v = userProperties.getProperty(key);
                if (v == null) {
                    v = systemProperties.getProperty(key);
                }
                return v;
            };

            Properties loaded = new Properties();
            MavenPropertiesLoader.loadProperties(loaded, mavenUserPropsPath, callback, false);

            int count = 0;
            for (String key : loaded.stringPropertyNames()) {
                if (!key.startsWith(NisseConfiguration.PROPERTY_PREFIX)) {
                    continue;
                }
                String value = loaded.getProperty(key);
                if (value == null || value.trim().isEmpty()) {
                    continue;
                }
                if (isUnexpandedPlaceholder(value)) {
                    continue;
                }
                if (!userProperties.containsKey(key)) {
                    userProperties.setProperty(key, value);
                    count++;
                }
            }
            if (count > 0) {
                logger.debug("Loaded {} nisse properties from {} (Maven 3 compatibility)", count, mavenUserPropsPath);
            }
        } catch (IOException e) {
            logger.warn("Failed to read maven-user.properties from {}: {}", mavenUserPropsPath, e.getMessage());
        }
    }

    /**
     * Returns {@code true} if the value looks like an unexpanded placeholder:
     * either a git {@code export-subst} pattern ({@code $Format:…$}) or a Maven
     * interpolation expression ({@code ${…}}).
     */
    private static boolean isUnexpandedPlaceholder(String value) {
        if (value == null) {
            return false;
        }
        if (value.startsWith("$Format:") && value.endsWith("$")) {
            return true;
        }
        if (value.startsWith("${") && value.endsWith("}")) {
            return true;
        }
        return false;
    }
}
