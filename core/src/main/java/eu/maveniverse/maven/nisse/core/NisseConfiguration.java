/*
 * Copyright (c) 2023-2024 Maveniverse Org.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 */
package eu.maveniverse.maven.nisse.core;

import java.nio.file.Path;
import java.util.Map;

public interface NisseConfiguration {
    /**
     * The default Nisse prefix.
     */
    String PROPERTY_PREFIX = "nisse.";

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
     */
    Map<String, String> getConfiguration();

    /**
     * Returns the path that should be considered "current working directory", never {@code null}.
     */
    Path getCurrentWorkingDirectory();
}
