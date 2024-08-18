/*
 * Copyright (c) 2023-2024 Maveniverse Org.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 */
package eu.maveniverse.maven.nisse.core;

import java.util.Map;
import java.util.concurrent.ConcurrentMap;

public interface NisseSession {
    /**
     * Returns the session configuration, never {@code null}.
     */
    NisseConfiguration getConfiguration();

    /**
     * Mutable session data map, never {@code null}.
     */
    ConcurrentMap<String, Object> getData();

    /**
     * Returns map of Nisse properties.
     */
    Map<String, String> getAllProperties();
}