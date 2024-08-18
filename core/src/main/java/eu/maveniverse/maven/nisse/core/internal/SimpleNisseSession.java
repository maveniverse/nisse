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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class SimpleNisseSession implements NisseSession {
    private final NisseConfiguration configuration;
    private final ConcurrentMap<String, Object> data;

    private SimpleNisseSession(NisseConfiguration configuration, ConcurrentMap<String, Object> data) {
        this.configuration = requireNonNull(configuration, "configuration");
        this.data = requireNonNull(data, "data");
    }

    @Override
    public NisseConfiguration getConfiguration() {
        return configuration;
    }

    @Override
    public ConcurrentMap<String, Object> getData() {
        return data;
    }

    public static SimpleNisseSession create(NisseConfiguration configuration) {
        requireNonNull(configuration, "configuration");
        return new SimpleNisseSession(configuration, new ConcurrentHashMap<>());
    }
}
