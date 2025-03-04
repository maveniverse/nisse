/*
 * Copyright (c) 2023-2024 Maveniverse Org.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 */
package eu.maveniverse.maven.nisse.source.mvn;

import eu.maveniverse.maven.nisse.core.NisseConfiguration;
import eu.maveniverse.maven.nisse.core.PropertySource;
import java.io.InputStream;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import javax.inject.Named;
import javax.inject.Singleton;

/**
 * mvn source: gives access to current Maven version, uses {@code "maven.version"} property.
 */
@Singleton
@Named(MvnPropertySource.NAME)
public class MvnPropertySource implements PropertySource {
    public static final String NAME = "mvn";

    private static final String VERSION = "version";
    private static final String VERSION_MAJOR = "major";
    private static final String VERSION_MINOR = "minor";
    private static final String VERSION_MAJOR_MINOR = "majorMinor";
    private static final String VERSION_PATCH = "patch";
    private static final String VERSION_QUALIFIER = "qualifier";

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public Map<String, String> getProperties(NisseConfiguration configuration) {
        String mavenVersion = configuration.getConfiguration().get("maven.version");
        HashMap<String, String> result = new HashMap<>();
        if (mavenVersion != null) {
            result.put(VERSION, mavenVersion);
            if (mavenVersion.contains("-")) {
                int qIdx = mavenVersion.indexOf('-');
                result.put(VERSION_QUALIFIER, mavenVersion.substring(qIdx + 1));
                mavenVersion = mavenVersion.substring(0, qIdx);
            }
            String[] elems = mavenVersion.split("\\.");
            result.put(VERSION_MAJOR, elems[0]);
            result.put(VERSION_MINOR, elems[1]);
            result.put(VERSION_MAJOR_MINOR, elems[0] + "." + elems[1]);
            result.put(VERSION_PATCH, elems[2]);
        }
        Properties mavenBuildProperties = getMavenBuildProperties(configuration);
        if (!mavenBuildProperties.isEmpty()) {
            mavenBuildProperties.stringPropertyNames().forEach(k -> result.put(k, mavenBuildProperties.getProperty(k)));
        }
        return Collections.unmodifiableMap(result);
    }

    private static Properties getMavenBuildProperties(NisseConfiguration configuration) {
        String mavenHome = configuration.getConfiguration().get("maven.home");
        String mavenVersion = configuration.getConfiguration().get("maven.version");
        Properties properties = new Properties();
        try {
            if (mavenHome != null && mavenVersion != null) {
                Path mavenCoreJarPath = Paths.get(mavenHome).resolve("lib/maven-core-" + mavenVersion + ".jar");
                if (Files.isRegularFile(mavenCoreJarPath)) {
                    try (FileSystem fs = FileSystems.newFileSystem(mavenCoreJarPath, (ClassLoader) null);
                            InputStream input =
                                    Files.newInputStream(fs.getPath("/org/apache/maven/messages/build.properties"))) {
                        properties.load(input);
                    }
                }
            }
        } catch (Exception e) {
            // ignore
        }
        return properties;
    }
}
