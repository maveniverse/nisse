/*
 * Copyright (c) 2023-2024 Maveniverse Org.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 */
package eu.maveniverse.maven.nisse.core.internal;

import static java.util.Objects.requireNonNull;

import eu.maveniverse.maven.nisse.core.NisseSession;
import eu.maveniverse.maven.nisse.core.PropertyKeyManager;
import eu.maveniverse.maven.nisse.core.PropertySource;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

@Singleton
@Named
class DefaultPropertyKeyManager implements PropertyKeyManager {
    private final List<PropertySource> sources;

    @Inject
    public DefaultPropertyKeyManager(List<PropertySource> sources) {
        this.sources = requireNonNull(sources, "sources");
    }

    @Override
    public Map<String, String> allProperties(NisseSession session) {
        return sources.stream()
                .flatMap(s -> s.getProperties(session.getConfiguration()).entrySet().stream())
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }
}
