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
import eu.maveniverse.maven.nisse.core.PropertyKeyNamingStrategies;
import eu.maveniverse.maven.nisse.core.PropertySource;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.function.BiFunction;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class SimpleNisseConfiguration implements NisseConfiguration {
    private final Map<String, String> systemProperties;
    private final Map<String, String> userProperties;
    private final Map<String, String> configuration;
    private final Path currentWorkingDirectory;
    private final Path sessionRootDirectory;
    private final BiFunction<PropertySource, String, List<String>> propertyKeyNamingStrategy;

    private SimpleNisseConfiguration(
            Map<String, String> systemProperties,
            Map<String, String> userProperties,
            Map<String, String> configuration,
            Path currentWorkingDirectory,
            Path sessionRootDirectory,
            BiFunction<PropertySource, String, List<String>> propertyKeyNamingStrategy) {
        this.systemProperties = requireNonNull(systemProperties, "systemProperties");
        this.userProperties = requireNonNull(userProperties, "userProperties");
        this.configuration = requireNonNull(configuration, "configuration");
        this.currentWorkingDirectory = requireNonNull(currentWorkingDirectory);
        this.sessionRootDirectory = requireNonNull(sessionRootDirectory);
        this.propertyKeyNamingStrategy = requireNonNull(propertyKeyNamingStrategy, "propertyKeyNamingStrategy");
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

    @Override
    public Path getSessionRootDirectory() {
        return sessionRootDirectory;
    }

    @Override
    public boolean isPropertySourceActive(PropertySource source) {
        requireNonNull(source, "source");
        String key = SOURCE_PREFIX + source.getName() + ".active";
        return Boolean.parseBoolean(getConfiguration().getOrDefault(key, "true"));
    }

    @Override
    public Collection<String> getInlinedPropertyKeys() {
        String key = SOURCE_PREFIX + "inlinedKeys";
        String value = getConfiguration().get(key);
        if (value != null) {
            return Stream.of(value.split(",", -1))
                    .filter(s -> !s.trim().isEmpty())
                    .collect(Collectors.toList());
        }
        return Collections.emptyList();
    }

    @Override
    public BiFunction<PropertySource, String, List<String>> propertyKeyNamingStrategy() {
        return propertyKeyNamingStrategy;
    }

    public static Builder builder() {
        return new Builder().withJavaSystemProperties().withCurrentWorkingDirectory();
    }

    public static class Builder {
        private Map<String, String> systemProperties = new HashMap<>();
        private Map<String, String> userProperties = new HashMap<>();
        private Path currentWorkingDirectory = Paths.get("").toAbsolutePath();
        private Path sessionRootDirectory = Paths.get("").toAbsolutePath();
        private final List<BiFunction<PropertySource, String, List<String>>> propertyKeyNamingStrategies =
                new ArrayList<>();
        private BiFunction<PropertySource, String, List<String>> propertyKeyNamingStrategy = null;
        private boolean detectCompatOsDetector = true; // default behaviour

        public SimpleNisseConfiguration build() {
            HashMap<String, String> configuration = new HashMap<>(systemProperties);
            // Broadening support: Maven versions pre 3.9.2 had no means to configure resources
            // from project. In those case we "backport" session.rootDirectory ONLY
            // CliRequest.multiModuleProjectDirectory -> session.rootDirectory
            for (String key : userProperties.keySet()) {
                String value = userProperties.get(key);
                if (value != null && value.contains("${session.rootDirectory}")) {
                    value = value.replace("${session.rootDirectory}", sessionRootDirectory.toString());
                }
                configuration.put(key, value);
            }

            // if caller provided naming strategy, use it as-is, otherwise configure from list (or just default it)
            if (propertyKeyNamingStrategy == null) {
                // defaults if none present
                if (propertyKeyNamingStrategies.isEmpty()) {
                    propertyKeyNamingStrategies.add(PropertyKeyNamingStrategies.defaultStrategy());
                }
                // compat; if enabled
                if (detectCompatOsDetector && Boolean.parseBoolean(configuration.get(COMPAT_OS_DETECTOR))) {
                    propertyKeyNamingStrategies.add(PropertyKeyNamingStrategies.osDetector());
                }

                // combine all using fork()
                this.propertyKeyNamingStrategy = PropertyKeyNamingStrategies.fork(propertyKeyNamingStrategies);
            }

            return new SimpleNisseConfiguration(
                    Collections.unmodifiableMap(systemProperties),
                    Collections.unmodifiableMap(userProperties),
                    Collections.unmodifiableMap(configuration),
                    currentWorkingDirectory,
                    sessionRootDirectory,
                    propertyKeyNamingStrategy);
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

        public Builder withSessionRootDirectory(Path sessionRootDirectory) {
            if (sessionRootDirectory != null) {
                this.sessionRootDirectory = sessionRootDirectory.toAbsolutePath();
            } else {
                this.sessionRootDirectory = Paths.get("");
            }
            return this;
        }

        /**
         * Whether "OS detector compat detection" should be enabled or disabled. By default, is enabled.
         *
         * @see NisseConfiguration#COMPAT_OS_DETECTOR
         */
        public Builder withDetectCompatOsDetector(boolean detectCompatOsDetector) {
            this.detectCompatOsDetector = detectCompatOsDetector;
            return this;
        }

        /**
         * The "roll your own" strategy to use. If user calls this method with non-null strategy, it will be used
         * by Nisse "as is", and strategies added by {@link #combinePropertyKeyNamingStrategy(BiFunction)}, if any,
         * will be completely ignored.
         * 
         * @see #combinePropertyKeyNamingStrategy(BiFunction) 
         */
        public Builder withPropertyKeyNamingStrategy(
                BiFunction<PropertySource, String, List<String>> propertyKeyNamingStrategy) {
            this.propertyKeyNamingStrategy = propertyKeyNamingStrategy;
            return this;
        }

        /**
         * Here user may add multiple strategies that will be combined using {@link PropertyKeyNamingStrategies#fork(List)}.
         * The {@link PropertyKeyNamingStrategies#defaultStrategy()} is <em>not appended to end of this list</em>.
         * Hence, user needs to append it if required.
         * This method is no-op if invoked with {@code null}.
         *
         * @see PropertyKeyNamingStrategies#fork(List)
         * @see #withPropertyKeyNamingStrategy(BiFunction) 
         */
        public Builder combinePropertyKeyNamingStrategy(
                BiFunction<PropertySource, String, List<String>> propertyKeyNamingStrategy) {
            if (propertyKeyNamingStrategy != null) {
                this.propertyKeyNamingStrategies.add(propertyKeyNamingStrategy);
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
