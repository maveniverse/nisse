/*
 * Copyright (c) 2023-2024 Maveniverse Org.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 */
package eu.maveniverse.maven.nisse.core.internal;

import static java.util.Objects.requireNonNull;

import eu.maveniverse.maven.nisse.core.NisseConfiguration;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Collectors;

public final class SimpleNisseConfiguration implements NisseConfiguration {
    private final Map<String, String> systemProperties;
    private final Map<String, String> userProperties;
    private final Map<String, String> configuration;
    private final Path currentWorkingDirectory;

    private SimpleNisseConfiguration(
            Map<String, String> systemProperties,
            Map<String, String> userProperties,
            Map<String, String> configuration,
            Path currentWorkingDirectory) {
        this.systemProperties = requireNonNull(systemProperties, "systemProperties");
        this.userProperties = requireNonNull(userProperties, "userProperties");
        this.configuration = requireNonNull(configuration, "configuration");
        this.currentWorkingDirectory = requireNonNull(currentWorkingDirectory);
    }

    @Override
    public Map<String, String> getSystemProperties() {
        return systemProperties;
    }

    @Override
    public Map<String, String> getUserProperties() {
        return userProperties;
    }

    @Override
    public Map<String, String> getConfiguration() {
        return configuration;
    }

    @Override
    public Path getCurrentWorkingDirectory() {
        return currentWorkingDirectory;
    }

    public static Builder builder() {
        return new Builder().withJavaSystemProperties().withCurrentWorkingDirectory();
    }

    public static class Builder {
        private Map<String, String> systemProperties = new HashMap<>();
        private Map<String, String> userProperties = new HashMap<>();
        private Path currentWorkingDirectory = Paths.get("").toAbsolutePath();

        public SimpleNisseConfiguration build() {
            HashMap<String, String> configuration = new HashMap<>();
            configuration.putAll(systemProperties);
            configuration.putAll(userProperties);
            return new SimpleNisseConfiguration(
                    Collections.unmodifiableMap(systemProperties),
                    Collections.unmodifiableMap(userProperties),
                    Collections.unmodifiableMap(configuration),
                    currentWorkingDirectory);
        }

        public Builder withJavaSystemProperties() {
            return withSystemProperties(System.getProperties().stringPropertyNames().stream()
                    .collect(Collectors.toMap(k -> k, System::getProperty)));
        }

        public Builder withCurrentWorkingDirectory() {
            return withCurrentWorkingDirectory(Paths.get("").toAbsolutePath());
        }

        public Builder withSystemProperties(Map<String, String> systemProperties) {
            if (systemProperties != null) {
                this.systemProperties = new HashMap<>(systemProperties);
            } else {
                this.systemProperties = new HashMap<>();
            }
            return this;
        }

        public Builder withSystemProperties(Properties systemProperties) {
            return withSystemProperties(toMap(systemProperties));
        }

        public Builder withUserProperties(Map<String, String> userProperties) {
            if (userProperties != null) {
                this.userProperties = new HashMap<>(userProperties);
            } else {
                this.userProperties = new HashMap<>();
            }
            return this;
        }

        public Builder withUserProperties(Properties userProperties) {
            return withUserProperties(toMap(userProperties));
        }

        public Builder withCurrentWorkingDirectory(Path currentWorkingDirectory) {
            if (currentWorkingDirectory != null) {
                this.currentWorkingDirectory = currentWorkingDirectory.toAbsolutePath();
            } else {
                this.currentWorkingDirectory = Paths.get("");
            }
            return this;
        }
    }

    private static Map<String, String> toMap(Properties properties) {
        if (properties != null) {
            return properties.entrySet().stream()
                    .collect(Collectors.toMap(
                            e -> String.valueOf(e.getKey()),
                            e -> String.valueOf(e.getValue()),
                            (prev, next) -> next,
                            HashMap::new));
        } else {
            return null;
        }
    }
}
