/*
 * Copyright (c) 2023-2024 Maveniverse Org.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 */
package eu.maveniverse.maven.nisse.core;

import static java.util.Objects.requireNonNull;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

/**
 * Property key naming strategies.
 */
public interface PropertyKeyNamingStrategies extends BiFunction<PropertySource, String, List<String>> {
    /**
     * Forks multiple strategies: effect is that input key is mapped to each combined strategies and output is then
     * aggregated. If all strategy provides one output for one input, the effect of this strategy is {@code 1:N}.
     */
    static BiFunction<PropertySource, String, List<String>> fork(
            List<BiFunction<PropertySource, String, List<String>>> strategies) {
        requireNonNull(strategies, "strategies");
        if (strategies.isEmpty()) {
            throw new IllegalArgumentException("strategies must not be empty");
        } else if (strategies.size() == 1) {
            return strategies.get(0);
        } else {
            return (propertySource, s) -> {
                ArrayList<String> result = new ArrayList<>();
                for (BiFunction<PropertySource, String, List<String>> strategy : strategies) {
                    result.addAll(strategy.apply(propertySource, s));
                }
                return result;
            };
        }
    }

    /**
     * Shorthand for {@link #fork(List)}.
     */
    @SafeVarargs
    static BiFunction<PropertySource, String, List<String>> fork(
            BiFunction<PropertySource, String, List<String>>... strategies) {
        return fork(Arrays.asList(strategies));
    }

    /**
     * Pipes multiple strategies. Effect is that input key is applied to first combined strategy, then that result is
     * applied to second combined strategy and so on, accumulating all results. If all strategy provides one output
     * for one input, the effect of this strategy is {@code 1:1}. If strategy emits empty list for given key, key is
     * carried over to next strategy unchanged.
     */
    static BiFunction<PropertySource, String, List<String>> pipe(
            List<BiFunction<PropertySource, String, List<String>>> strategies) {
        requireNonNull(strategies, "strategies");
        if (strategies.isEmpty()) {
            throw new IllegalArgumentException("strategies must not be empty");
        } else if (strategies.size() == 1) {
            return strategies.get(0);
        } else {
            return (propertySource, key) -> {
                List<String> keys = Collections.singletonList(key);
                List<String> acc = new ArrayList<>();
                for (BiFunction<PropertySource, String, List<String>> strategy : strategies) {
                    for (String k : keys) {
                        List<String> a = strategy.apply(propertySource, k);
                        if (a.isEmpty()) {
                            acc.add(k);
                        } else {
                            acc.addAll(a);
                        }
                    }
                    keys = new ArrayList<>(acc);
                    acc = new ArrayList<>(keys.size());
                }
                return keys;
            };
        }
    }

    /**
     * Shorthand for {@link #pipe(List)}.
     */
    @SafeVarargs
    static BiFunction<PropertySource, String, List<String>> pipe(
            BiFunction<PropertySource, String, List<String>>... strategies) {
        return pipe(Arrays.asList(strategies));
    }

    // strategies

    /**
     * The default combined naming strategy Nisse applied in existing releases so far.
     * <p>
     * Is {@link #pipe(BiFunction[])} combined out of:
     * <ul>
     *     <li>{@link #sourcePrefixed()}</li>
     *     <li>{@link #nissePrefixed()}</li>
     * </ul>
     * It prefixes keys as {@code "nisse." + $source.name + "." + $key}.
     */
    static BiFunction<PropertySource, String, List<String>> defaultStrategy() {
        return pipe(sourcePrefixed(), nissePrefixed());
    }

    /**
     * The prefixing naming strategy Nisse applied in all existing releases so far.
     * <p>
     * It prefixes keys as {@code "nisse." + $key}.
     */
    static BiFunction<PropertySource, String, List<String>> nissePrefixed() {
        return prefixed(NisseConfiguration.PROPERTY_PREFIX);
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
     * map. If key is present, mapping is applied, otherwise {@code fallback} strategy is used.
     * <p>
     * The property key should be existing key that needs remapping, and value should be the comma separated keys that
     * existing key should be remapped to. If value is empty, key is unpublished. If value has one or more comma
     * separated values, the remapped key is published under provided keys. There is special keyword that can be
     * used as last list element, the {@code +fallback}, that if present, will append fallback keys as well.
     * <p>
     * Behaviour in short:
     * <ul>
     *     <li>if key is not in the map, fallback is applied</li>
     *     <li>if key is in map but value is empty, key is unpublished</li>
     *     <li>if key is in map with non-empty value, key is published under provided values</li>
     *     <li>if key is in map with non-empty value, and ends with {@code ,+fallback} the fallback is appended</li>
     * </ul>
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
                List<String> mappedKeys = new ArrayList<>();
                boolean keyMapped = translation.containsKey(lookupKey);
                if (keyMapped) {
                    List<String> translated = translation.get(lookupKey);
                    if (translated != null) {
                        mappedKeys.addAll(translated);
                    }
                    if (!mappedKeys.isEmpty() && "+fallback".equals(mappedKeys.get(mappedKeys.size() - 1))) {
                        mappedKeys.addAll(fallback.apply(source, key));
                    }
                } else {
                    mappedKeys.addAll(fallback.apply(source, key));
                }
                mappedKeys.removeAll(Collections.singleton("+fallback"));
                result.addAll(mappedKeys);
            }
            return result;
        };
    }

    /**
     * Loads up translation table from given properties file, if exists. Otherwise, returns empty map.
     */
    static Map<String, List<String>> translationTableFromPropertiesFile(Path properties) throws IOException {
        if (Files.exists(properties)) {
            Properties props = new Properties();
            try (InputStream inputStream = Files.newInputStream(properties)) {
                props.load(inputStream);
            }
            Map<String, List<String>> translation = new HashMap<>();
            for (String key : props.stringPropertyNames()) {
                List<String> values = Arrays.stream(props.getProperty(key).split(","))
                        .filter(s -> !s.trim().isEmpty())
                        .collect(Collectors.toList());
                translation.put(key, values);
            }
            return translation;
        }
        return Collections.emptyMap();
    }

    /**
     * Built-in strategy to provide full compatibility with <a href="https://github.com/trustin/os-maven-plugin">os-maven-plugin</a>
     * that inspired "os" source.
     */
    static BiFunction<PropertySource, String, List<String>> osDetector() {
        return (propertySource, key) -> {
            if ("os".equals(propertySource.getName())) {
                return Collections.singletonList("os.detected." + key);
            } else {
                return Collections.emptyList();
            }
        };
    }
}
