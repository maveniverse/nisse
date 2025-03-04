/*
 * Copyright (c) 2023-2024 Maveniverse Org.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 */
package eu.maveniverse.maven.nisse.core;

import static java.util.Objects.requireNonNull;

import java.util.Map;
import java.util.function.BiFunction;

/**
 * Property key naming strategies.
 */
public interface PropertyKeyNamingStrategies extends BiFunction<PropertySource, String, String> {
    /**
     * The default naming strategy Nisse applied in existing releases so far.
     * <p>
     * It prefixes keys as {@code "nisse." + $source.name + "." + $key}.
     */
    static BiFunction<PropertySource, String, String> nisseDefault() {
        return sourcePrefixed().andThen(s -> NisseConfiguration.PROPERTY_PREFIX + s);
    }

    /**
     * Publishes properties as set by source. Based on sources being in use, this may cause key conflicts with
     * undefined behaviour.
     */
    static BiFunction<PropertySource, String, String> identity() {
        return (source, key) -> key;
    }

    /**
     * Prefixes property keys with specified static prefix. Based on sources being in use, this may cause key
     * conflicts with undefined behaviour.
     */
    static BiFunction<PropertySource, String, String> prefixed(String staticPrefix) {
        requireNonNull(staticPrefix, "staticPrefix");
        return (source, key) -> staticPrefix + key;
    }

    /**
     * Prefixes property keys with source name making them unique.
     */
    static BiFunction<PropertySource, String, String> sourcePrefixed() {
        return (source, key) -> source.getName() + "." + key;
    }

    /**
     * Translates properties using provided translation table, if key not found, uses fallback strategy.
     * <p>
     * This function applies {@code lookup} strategy and using result performs a lookup on provided {@code translation}
     * map. If result is non-null, will be used, otherwise {@code fallback} strategy is used.
     */
    static BiFunction<PropertySource, String, String> translated(
            Map<String, String> translation,
            BiFunction<PropertySource, String, String> lookup,
            BiFunction<PropertySource, String, String> fallback) {
        requireNonNull(translation, "translation");
        requireNonNull(lookup, "lookup");
        requireNonNull(fallback, "fallback");
        return (source, key) -> {
            String lookupKey = lookup.apply(source, key);
            String translated = translation.get(lookupKey);
            if (translated == null) {
                translated = fallback.apply(source, key);
            }
            return translated;
        };
    }
}
