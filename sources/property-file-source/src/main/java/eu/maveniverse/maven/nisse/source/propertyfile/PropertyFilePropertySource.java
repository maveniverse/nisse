/*
 * Copyright (c) 2023-2024 Maveniverse Org.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 */
package eu.maveniverse.maven.nisse.source.propertyfile;

import static eu.maveniverse.maven.nisse.core.Utils.mavenizeValue;

import eu.maveniverse.maven.nisse.core.NisseConfiguration;
import eu.maveniverse.maven.nisse.core.PropertySource;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import javax.inject.Named;
import javax.inject.Singleton;

/**
 * A simple property key source that accepts a Java Properties file name, and will load up properties from it.
 */
@Singleton
@Named(PropertyFilePropertySource.NAME)
public class PropertyFilePropertySource implements PropertySource {
    public static final String NAME = "property-file";

    public static final String FILE_NAME = NisseConfiguration.PROPERTY_PREFIX + NAME + ".name";

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public Map<String, String> getProperties(NisseConfiguration configuration) {
        String propertyFile = configuration.getConfiguration().get(FILE_NAME);
        HashMap<String, String> properties = new HashMap<>();
        if (propertyFile != null) {
            Path propertyFilePath = configuration.getCurrentWorkingDirectory().resolve(propertyFile);
            if (Files.isRegularFile(propertyFilePath)) {
                try (InputStream stream = Files.newInputStream(propertyFilePath)) {
                    Properties prop = new Properties();
                    prop.load(stream);
                    for (final String key : prop.stringPropertyNames()) {
                        properties.put(key, mavenizeValue(prop.getProperty(key)));
                    }
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            }
        }
        return Collections.unmodifiableMap(properties);
    }
}
