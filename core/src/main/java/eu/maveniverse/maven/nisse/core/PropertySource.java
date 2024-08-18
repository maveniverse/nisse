/*
 * Copyright (c) 2023-2024 Maveniverse Org.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 */
package eu.maveniverse.maven.nisse.core;

import java.util.Map;

/**
 * A property source, that provides all the supported properties.
 */
public interface PropertySource {
    /**
     * Returns the name of the property source, never {@code null}.
     */
    String getName();

    /**
     * Returns a map of provided properties, never {@code null}.
     * <p>
     * The returned map should not contain {@code null} keys and {@code null} values, they should be
     * "mavenized", in a way Apache Maven does: empty values should be replaced with {@code "true"} string.
     */
    Map<String, String> getProperties(NisseConfiguration configuration);
}
