/*
 * Copyright (c) 2023-2024 Maveniverse Org.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 */
package eu.maveniverse.maven.nisse.core;

import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

public interface NisseConfiguration {
    /**
     * The default Nisse prefix.
     */
    String PROPERTY_PREFIX = "nisse.";

    /**
     * The default Nisse source config prefix.
     */
    String SOURCE_PREFIX = PROPERTY_PREFIX + "source.";

    /**
     * Key to use for detecting is "os-detector compatibility" requested. Value of this key is parsed as
     * boolean string.
     */
    String COMPAT_OS_DETECTOR = PROPERTY_PREFIX + "compat.osDetector";

    /**
     * Returns immutable map of session effective system properties, never {@code null}.
     */
    Map<String, String> getSystemProperties();

    /**
     * Returns immutable map of session effective user properties, never {@code null}.
     */
    Map<String, String> getUserProperties();

    /**
     * Returns immutable map of session effective configuration, never {@code null}.
     * <p>
     * Note: in Maven this is systemProperties + userProperties flattened in this order.
     */
    Map<String, String> getConfiguration();

    /**
     * Returns the path that should be considered "current working directory", never {@code null}.
     * <p>
     * Note: in Maven this may not be same as "user.dir" system property!
     */
    Path getCurrentWorkingDirectory();

    /**
     * Returns the path that should be considered "session root directory", never {@code null}.
     * <p>
     * Note: this is to support older Maven versions than 3.9.2.
     */
    Path getSessionRootDirectory();

    /**
     * Returns {@code true} if property source is active (by default they are active). To disable a source use
     * {@code "nisse.source.$source.active=false"} property (defaults to {@code true}).
     */
    boolean isPropertySourceActive(PropertySource source);

    /**
     * Returns {@code true} if property source is active (by default they are active). To disable a source use
     * {@code "nisse.source.inlinedKeys=key1,key2"} property (defaults to empty collection).
     */
    Collection<String> getInlinedPropertyKeys();

    /**
     * Returns the {@link PropertyKeyNamingStrategies} to apply to published properties.
     */
    BiFunction<PropertySource, String, List<String>> propertyKeyNamingStrategy();
}
