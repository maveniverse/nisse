/*
 * Copyright (c) 2023-2024 Maveniverse Org.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 */
package eu.maveniverse.maven.nisse.core.internal;

import static java.util.Objects.requireNonNull;

import eu.maveniverse.maven.nisse.core.PropertyKey;
import eu.maveniverse.maven.nisse.core.PropertyKeyManager;
import eu.maveniverse.maven.nisse.core.PropertyKeySource;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

@Singleton
@Named
class DefaultPropertyKeyManager implements PropertyKeyManager {
    private final Map<String, PropertyKeySource> sources;

    @Inject
    public DefaultPropertyKeyManager(Map<String, PropertyKeySource> sources) {
        this.sources = requireNonNull(sources, "sources");
    }

    @Override
    public Collection<PropertyKey> allKeys() {
        return sources.values().stream().flatMap(s -> s.providedKeys().stream()).collect(Collectors.toList());
    }

    @Override
    public Optional<PropertyKey> lookup(String key) {
        requireNonNull(key, "key");
        return allKeys().stream().filter(k -> key.equals(k.getKey())).findFirst();
    }
}
