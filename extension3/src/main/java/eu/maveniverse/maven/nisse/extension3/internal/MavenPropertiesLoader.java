/*
 * Copyright (c) 2023-2024 Maveniverse Org.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 */
/*
 * Adapted from Apache Maven 4's org.apache.maven.cling.props.MavenPropertiesLoader.
 * Original source: https://github.com/apache/maven, licensed under Apache License 2.0.
 * Modified for Java 8 compatibility and to use NisseInterpolator.
 */
package eu.maveniverse.maven.nisse.extension3.internal;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;

/**
 * Loads and interpolates Maven properties files.
 * <p>
 * Adapted from Maven 4's {@code org.apache.maven.cling.props.MavenPropertiesLoader}.
 * Supports {@code ${includes}} directives, {@code maven.override.*} prefix,
 * value trimming, and full variable substitution via {@link NisseInterpolator}.
 */
final class MavenPropertiesLoader {

    static final String INCLUDES_PROPERTY = "${includes}";

    static final String OVERRIDE_PREFIX = "maven.override.";

    private MavenPropertiesLoader() {}

    static void loadProperties(
            java.util.Properties properties, Path path, UnaryOperator<String> callback, boolean escape)
            throws IOException {
        MavenProperties sp = new MavenProperties(false);
        if (Files.exists(path)) {
            sp.load(path);
        }
        properties.forEach(
                (k, v) -> sp.put(k.toString(), escape ? NisseInterpolator.escape(v.toString()) : v.toString()));
        loadIncludes(path, sp, callback);
        substitute(sp, callback);
        sp.forEach(properties::setProperty);
    }

    static void substitute(MavenProperties props, UnaryOperator<String> callback) {
        for (Enumeration<?> e = props.propertyNames(); e.hasMoreElements(); ) {
            String name = (String) e.nextElement();
            String value = props.getProperty(name);
            if (value == null) {
                if (callback != null) {
                    value = callback.apply(name);
                }
            }
            if (name.startsWith(OVERRIDE_PREFIX)) {
                String overrideName = name.substring(OVERRIDE_PREFIX.length());
                props.put(overrideName, substVars(value, name, props, callback));
            } else {
                props.put(name, substVars(value, name, props, callback));
            }
        }
        props.keySet().removeIf(k -> k.startsWith(OVERRIDE_PREFIX));
    }

    private static MavenProperties loadPropertiesFile(Path path, boolean failIfNotFound, UnaryOperator<String> callback)
            throws IOException {
        MavenProperties configProps = new MavenProperties(null, false);
        if (Files.exists(path) || failIfNotFound) {
            configProps.load(path);
            loadIncludes(path, configProps, callback);
            trimValues(configProps);
        }
        return configProps;
    }

    private static void loadIncludes(Path configProp, MavenProperties configProps, UnaryOperator<String> callback)
            throws IOException {
        String includes = configProps.get(INCLUDES_PROPERTY);
        if (includes != null) {
            includes = substVars(includes, INCLUDES_PROPERTY, configProps, callback);
            for (String location : parseIncludeLocations(includes)) {
                boolean mandatory = true;
                if (location.startsWith("?")) {
                    mandatory = false;
                    location = location.substring(1);
                }
                Path path = configProp.resolveSibling(location);
                final MavenProperties parentProps = configProps;
                MavenProperties props = loadPropertiesFile(path, mandatory, s -> {
                    String v = callback != null ? callback.apply(s) : null;
                    return v != null ? v : parentProps.getProperty(s);
                });
                configProps.putAll(props);
            }
        }
        configProps.remove(INCLUDES_PROPERTY);
    }

    private static void trimValues(MavenProperties configProps) {
        configProps.replaceAll((k, v) -> v.trim());
    }

    /**
     * Parses a comma-separated list of include locations.
     * Supports quoted paths ({@code "path"}) and optional markers ({@code ?}).
     * Equivalent to the original {@code StringTokenizer}-based parsing.
     */
    private static List<String> parseIncludeLocations(String includes) {
        List<String> locations = new ArrayList<>();
        boolean optional = false;
        boolean inQuote = false;
        StringBuilder tokBuf = new StringBuilder();
        boolean tokStarted = false;

        for (int i = 0; i < includes.length(); i++) {
            char c = includes.charAt(i);
            if (c == '"') {
                inQuote = !inQuote;
            } else if (c == ',' && !inQuote) {
                if (tokStarted) {
                    String loc = tokBuf.toString().trim();
                    locations.add(optional ? "?" + loc : loc);
                    tokBuf.setLength(0);
                    tokStarted = false;
                    optional = false;
                }
            } else if (c == '?' && !inQuote && !tokStarted) {
                optional = true;
            } else {
                tokStarted = true;
                tokBuf.append(c);
            }
        }
        if (tokStarted) {
            String loc = tokBuf.toString().trim();
            locations.add(optional ? "?" + loc : loc);
        }
        return locations;
    }

    static String substVars(String value, String name, Map<String, String> props, UnaryOperator<String> callback) {
        return NisseInterpolator.substVars(value, name, null, props, callback, false);
    }
}
