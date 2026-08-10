/*
 * Copyright (c) 2023-2024 Maveniverse Org.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 */
package eu.maveniverse.maven.nisse.core.simple;

import static java.util.Objects.requireNonNull;

import eu.maveniverse.maven.nisse.core.NisseConfiguration;
import eu.maveniverse.maven.nisse.core.NisseManager;
import eu.maveniverse.maven.nisse.core.PropertySource;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.function.BiFunction;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
@Named
public class SimpleNisseManager implements NisseManager {

    /**
     * The name of the low-priority fallback properties file, expected under the {@code .mvn/}
     * directory of the session root. Properties from this file are loaded before any
     * {@link PropertySource} so that source-resolved values (e.g. from JGit) take precedence.
     * <p>
     * The primary use case is source-archive builds (no {@code .git} directory) combined with
     * git {@code export-subst}: placeholders like {@code $Format:%(describe:tags=true)$} in
     * this file are expanded by {@code git archive}, providing fallback values when JGit
     * cannot resolve them.
     */
    static final String NISSE_PROPERTIES_FILE = ".mvn/nisse.properties";

    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final List<PropertySource> sources;

    @Inject
    public SimpleNisseManager(List<PropertySource> sources) {
        this.sources = requireNonNull(sources, "sources");
    }

    @Override
    public Map<String, String> createProperties(NisseConfiguration configuration) {
        requireNonNull(configuration, "configuration");
        BiFunction<PropertySource, String, List<String>> propertyKeyNamingStrategy =
                configuration.propertyKeyNamingStrategy();
        HashMap<String, String> properties = new HashMap<>();

        // Load .mvn/nisse.properties as a low-priority fallback: these values are
        // placed first so that PropertySource results (e.g. JGit) overwrite them.
        loadFallbackProperties(configuration, properties);

        for (PropertySource source : this.sources) {
            if (configuration.isPropertySourceActive(source)) {
                source.getProperties(configuration).forEach((key, value) -> {
                    for (String translated : propertyKeyNamingStrategy.apply(source, key)) {
                        properties.put(translated, value);
                    }
                });
            }
        }
        return properties;
    }

    /**
     * Loads properties from {@code .mvn/nisse.properties} relative to the session root directory.
     * Values that look like unexpanded git {@code export-subst} placeholders
     * ({@code $Format:…$}) are silently skipped.
     */
    private void loadFallbackProperties(NisseConfiguration configuration, Map<String, String> target) {
        Path nissePropertiesPath = configuration.getSessionRootDirectory().resolve(NISSE_PROPERTIES_FILE);
        if (!Files.isRegularFile(nissePropertiesPath)) {
            return;
        }
        try (InputStream in = Files.newInputStream(nissePropertiesPath)) {
            Properties props = new Properties();
            props.load(in);
            int loaded = 0;
            int skipped = 0;
            for (String key : props.stringPropertyNames()) {
                String value = props.getProperty(key);
                if (isUnexpandedPlaceholder(value)) {
                    skipped++;
                    logger.debug("Skipping unexpanded export-subst placeholder: {}={}", key, value);
                } else {
                    target.put(key, value);
                    loaded++;
                }
            }
            logger.debug(
                    "Loaded {} fallback properties from {} ({} unexpanded placeholders skipped)",
                    loaded,
                    nissePropertiesPath,
                    skipped);
        } catch (IOException e) {
            logger.warn("Failed to read fallback properties from {}: {}", nissePropertiesPath, e.getMessage());
        }
    }

    /**
     * Returns {@code true} if the value looks like an unexpanded git {@code export-subst}
     * placeholder, i.e. it matches the pattern {@code $Format:…$}.
     */
    static boolean isUnexpandedPlaceholder(String value) {
        return value != null && value.startsWith("$Format:") && value.endsWith("$");
    }
}
