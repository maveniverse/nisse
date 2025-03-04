/*
 * Copyright (c) 2023-2024 Maveniverse Org.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 */
package eu.maveniverse.maven.nisse.core;

import static java.util.Objects.requireNonNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

/**
 * Property key naming strategies.
 */
public interface PropertyKeyNamingStrategies extends BiFunction<PropertySource, String, List<String>> {
    /**
     * The default naming strategy Nisse applied in existing releases so far.
     * <p>
     * It prefixes keys as {@code "nisse." + $source.name + "." + $key}.
     */
    static BiFunction<PropertySource, String, List<String>> nisseDefault() {
        return sourcePrefixed().andThen(ls -> ls.stream()
                .map(s -> NisseConfiguration.PROPERTY_PREFIX + s)
                .collect(Collectors.toList()));
    }

    /**
     * Publishes properties as set by source. Based on sources being in use, this may cause key conflicts with
     * undefined behaviour.
     */
    static BiFunction<PropertySource, String, List<String>> identity() {
        return (source, key) -> Collections.singletonList(key);
    }

    /**
     * Prefixes property keys with specified static prefix. Based on sources being in use, this may cause key
     * conflicts with undefined behaviour.
     */
    static BiFunction<PropertySource, String, List<String>> prefixed(String staticPrefix) {
        requireNonNull(staticPrefix, "staticPrefix");
        return (source, key) -> Collections.singletonList(staticPrefix + key);
    }

    /**
     * Prefixes property keys with source name making them unique.
     */
    static BiFunction<PropertySource, String, List<String>> sourcePrefixed() {
        return (source, key) -> Collections.singletonList(source.getName() + "." + key);
    }

    /**
     * Translates properties using provided translation table, if key not found, uses fallback strategy.
     * <p>
     * This function applies {@code lookup} strategy and using result performs a lookup on provided {@code translation}
     * map. If result is non-null, will be used, otherwise {@code fallback} strategy is used.
     */
    static BiFunction<PropertySource, String, List<String>> translated(
            Map<String, List<String>> translation,
            BiFunction<PropertySource, String, List<String>> lookup,
            BiFunction<PropertySource, String, List<String>> fallback) {
        requireNonNull(translation, "translation");
        requireNonNull(lookup, "lookup");
        requireNonNull(fallback, "fallback");
        return (source, key) -> {
            List<String> lookupKeys = lookup.apply(source, key);
            List<String> result = new ArrayList<>(lookupKeys.size());
            for (String lookupKey : lookupKeys) {
                List<String> translated = translation.get(lookupKey);
                if (translated != null) {
                    result.addAll(translated);
                }
            }
            if (result.isEmpty()) {
                result = fallback.apply(source, key);
            }
            return result;
        };
    }
}
