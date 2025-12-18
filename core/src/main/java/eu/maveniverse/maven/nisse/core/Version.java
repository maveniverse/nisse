/*
 * Copyright (c) 2023-2024 Maveniverse Org.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 */
package eu.maveniverse.maven.nisse.core;

import static java.util.stream.Collectors.toMap;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

public final class Version {
    private Version() {}

    public static String version() {
        return loadClasspathProperties("eu.maveniverse.maven.nisse", "core").getOrDefault("version", "<unknown>");
    }

    public static Map<String, String> loadClasspathProperties(String groupId, String artifactId) {
        String resource = "/META-INF/maven/" + groupId + "/" + artifactId + "/pom.properties";
        final Properties props = new Properties();
        try (InputStream is = Version.class.getResourceAsStream(resource)) {
            if (is != null) {
                props.load(is);
            }
        } catch (IOException e) {
            // fall through
        }
        return props.entrySet().stream()
                .collect(toMap(
                        e -> String.valueOf(e.getKey()),
                        e -> String.valueOf(e.getValue()),
                        (prev, next) -> next,
                        HashMap::new));
    }
}
