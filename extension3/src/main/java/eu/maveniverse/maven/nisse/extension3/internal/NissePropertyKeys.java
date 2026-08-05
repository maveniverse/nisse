/*
 * Copyright (c) 2023-2024 Maveniverse Org.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 */
package eu.maveniverse.maven.nisse.extension3.internal;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.inject.Named;
import javax.inject.Singleton;

/**
 * Shared registry of nisse-managed property keys and their values.
 * <p>
 * Populated by {@link NisseConfigurationProcessor} during CLI initialization (before the
 * Maven session exists). Read by {@link NisseModelVersionProcessor} during model validation
 * to decide whether a {@code ${nisse.*}} expression is valid in a {@code <version>} element.
 * <p>
 * This decouples property validation from the {@code MavenSession} lifecycle, enabling
 * properties provided by <em>any</em> nisse source (file, export-subst, maven-user.properties)
 * to be accepted — not just those produced by the jgit source from a live git repository.
 */
@Singleton
@Named
final class NissePropertyKeys {
    private final ConcurrentHashMap<String, String> properties = new ConcurrentHashMap<>();

    /**
     * Register a batch of nisse property keys with their values.
     */
    void putAll(Map<String, String> props) {
        properties.putAll(props);
    }

    /**
     * Returns {@code true} if the given key was registered.
     */
    boolean containsKey(String key) {
        return properties.containsKey(key);
    }

    /**
     * Returns the value associated with the given key, or {@code null}.
     */
    String get(String key) {
        return properties.get(key);
    }

    /**
     * Returns an unmodifiable snapshot of all registered properties.
     */
    Map<String, String> getAll() {
        return Collections.unmodifiableMap(properties);
    }
}
