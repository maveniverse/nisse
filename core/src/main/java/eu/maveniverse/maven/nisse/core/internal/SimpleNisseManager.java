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
import eu.maveniverse.maven.nisse.core.NisseManager;
import eu.maveniverse.maven.nisse.core.NisseSession;
import eu.maveniverse.maven.nisse.core.PropertySource;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

@Singleton
@Named
class SimpleNisseManager implements NisseManager {
    private final List<PropertySource> sources;
    private final ConcurrentHashMap<String, NisseSession> sessions;

    @Inject
    public SimpleNisseManager(List<PropertySource> sources) {
        this.sources = requireNonNull(sources, "sources");
        this.sessions = new ConcurrentHashMap<>();
    }

    @Override
    public NisseSession createSession(String sessionId, NisseConfiguration configuration) {
        requireNonNull(sessionId, "sessionId");
        requireNonNull(configuration, "configuration");
        if (sessions.containsKey(sessionId)) {
            throw new IllegalStateException("Nisse session " + sessionId + " already exists");
        }
        HashMap<String, String> properties = new HashMap<>();
        for (PropertySource source : this.sources) {
            properties.putAll(source.getProperties(configuration));
        }
        NisseSession session = SimpleNisseSession.create(configuration, Collections.unmodifiableMap(properties));
        sessions.put(sessionId, session);
        return session;
    }

    @Override
    public NisseSession retrieveSession(String sessionId) {
        requireNonNull(sessionId, "sessionId");
        if (sessions.containsKey(sessionId)) {
            return sessions.get(sessionId);
        }
        throw new IllegalStateException("Nisse session " + sessionId + " does not exist");
    }

    @Override
    public NisseSession finishSession(String sessionId) {
        requireNonNull(sessionId, "sessionId");
        if (sessions.containsKey(sessionId)) {
            return sessions.remove(sessionId);
        }
        throw new IllegalStateException("Nisse session " + sessionId + " does not exist");
    }
}
