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
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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

    /** Regex that matches {@code ${name}} variable references. */
    private static final Pattern VARIABLE_PATTERN = Pattern.compile("\\$\\{([^}]+)}");

    /**
     * Loads {@code nisse.*} properties from {@code .mvn/maven-user.properties} into
     * user properties.  This bridges the gap between Maven 3 (which ignores the file)
     * and Maven 4 (which auto-loads <em>and interpolates</em> it).
     * <p>
     * Values undergo the same interpolation that Maven 4 performs at load time:
     * {@code ${session.rootDirectory}}, system properties and already-set user
     * properties are resolved; unresolvable expressions are left as-is.
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

        // Build the interpolation context — same sources Maven 4 uses in
        // BaseParser.populateUserProperties():
        //   1. session.rootDirectory / session.topDirectory  (path placeholders)
        //   2. system properties                             (user.home, etc.)
        //   3. user properties                               (-D flags)
        Map<String, String> interpolationContext = new HashMap<>();
        for (String key : systemProperties.stringPropertyNames()) {
            interpolationContext.put(key, systemProperties.getProperty(key));
        }
        for (String key : userProperties.stringPropertyNames()) {
            interpolationContext.put(key, userProperties.getProperty(key));
        }
        String rootDir = sessionRoot.toString();
        interpolationContext.put("session.rootDirectory", rootDir);
        interpolationContext.put("session.topDirectory", rootDir);
        interpolationContext.put(
                "maven.project.conf", sessionRoot.resolve(".mvn").toString());

        try (InputStream in = Files.newInputStream(mavenUserPropsPath)) {
            Properties props = new Properties();
            props.load(in);
            int loaded = 0;
            for (String key : props.stringPropertyNames()) {
                if (!key.startsWith(NisseConfiguration.PROPERTY_PREFIX)) {
                    continue;
                }
                String value = props.getProperty(key);
                if (value == null || value.trim().isEmpty()) {
                    continue;
                }
                if (isUnexpandedPlaceholder(value)) {
                    continue;
                }
                value = interpolate(value, interpolationContext);
                if (!userProperties.containsKey(key)) {
                    userProperties.setProperty(key, value);
                    loaded++;
                }
            }
            if (loaded > 0) {
                logger.debug("Loaded {} nisse properties from {} (Maven 3 compatibility)", loaded, mavenUserPropsPath);
            }
        } catch (IOException e) {
            logger.warn("Failed to read maven-user.properties from {}: {}", mavenUserPropsPath, e.getMessage());
        }
    }

    /**
     * Interpolates {@code ${name}} references in {@code value} against the given
     * context map.  Mirrors Maven 4's {@code DefaultInterpolator.substVars()}:
     * resolvable variables are replaced; unresolvable ones are left as-is.
     */
    static String interpolate(String value, Map<String, String> context) {
        if (value == null || !value.contains("${")) {
            return value;
        }
        Matcher matcher = VARIABLE_PATTERN.matcher(value);
        StringBuilder sb = new StringBuilder();
        int last = 0;
        while (matcher.find()) {
            sb.append(value, last, matcher.start());
            String varName = matcher.group(1);
            String resolved = context.get(varName);
            if (resolved != null) {
                sb.append(resolved);
            } else {
                // leave unresolvable expression as-is (matches Maven 4 behaviour)
                sb.append(matcher.group());
            }
            last = matcher.end();
        }
        sb.append(value, last, value.length());
        return sb.toString();
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
