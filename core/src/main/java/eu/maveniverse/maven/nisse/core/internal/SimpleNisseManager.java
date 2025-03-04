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
import eu.maveniverse.maven.nisse.core.PropertySource;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

@Singleton
@Named
class SimpleNisseManager implements NisseManager {
    private final List<PropertySource> sources;

    @Inject
    public SimpleNisseManager(List<PropertySource> sources) {
        this.sources = requireNonNull(sources, "sources");
    }

    @Override
    public Map<String, String> createProperties(NisseConfiguration configuration) {
        requireNonNull(configuration, "configuration");
        BiFunction<PropertySource, String, String> propertyKeyNamingStrategy =
                configuration.propertyKeyNamingStrategy();
        HashMap<String, String> properties = new HashMap<>();
        for (PropertySource source : this.sources) {
            if (configuration.isPropertySourceActive(source)) {
                source.getProperties(configuration)
                        .forEach((key, value) -> properties.put(propertyKeyNamingStrategy.apply(source, key), value));
            }
        }
        return properties;
    }
}
