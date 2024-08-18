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
import eu.maveniverse.maven.nisse.core.NisseSession;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class SimpleNisseSession implements NisseSession {
    private final NisseConfiguration configuration;
    private final ConcurrentMap<String, Object> data;
    private final Map<String, String> properties;

    private SimpleNisseSession(
            NisseConfiguration configuration, ConcurrentMap<String, Object> data, Map<String, String> properties) {
        this.configuration = requireNonNull(configuration, "configuration");
        this.data = requireNonNull(data, "data");
        this.properties = requireNonNull(properties, "properties");
    }

    @Override
    public NisseConfiguration getConfiguration() {
        return configuration;
    }

    @Override
    public ConcurrentMap<String, Object> getData() {
        return data;
    }

    @Override
    public Map<String, String> getAllProperties() {
        return properties;
    }

    public static SimpleNisseSession create(NisseConfiguration configuration, Map<String, String> properties) {
        requireNonNull(configuration, "configuration");
        return new SimpleNisseSession(configuration, new ConcurrentHashMap<>(), properties);
    }
}
