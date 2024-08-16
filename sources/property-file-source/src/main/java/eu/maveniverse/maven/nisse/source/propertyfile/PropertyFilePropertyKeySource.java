/*
 * Copyright (c) 2023-2024 Maveniverse Org.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 */
package eu.maveniverse.maven.nisse.source.propertyfile;

import eu.maveniverse.maven.nisse.core.KnownPropertyKey;
import eu.maveniverse.maven.nisse.core.PropertyKey;
import eu.maveniverse.maven.nisse.core.PropertyKeySource;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import org.eclipse.sisu.Nullable;

/**
 * A simple property key source that accepts a Java Properties file name, and will load up properties from it.
 */
@Singleton
@Named(PropertyFilePropertyKeySource.NAME)
public class PropertyFilePropertyKeySource implements PropertyKeySource {
    public static final String NAME = "property-file";

    private final List<PropertyKey> propertyKeys;

    @Inject
    public PropertyFilePropertyKeySource(@Nullable @Named("nisse.property-file.name") String propertyFile) {
        ArrayList<KnownPropertyKey> propertyKeys = new ArrayList<>();
        if (propertyFile != null) {
            Path propertyFilePath = Paths.get(propertyFile);
            if (Files.isRegularFile(propertyFilePath)) {
                try (InputStream stream = Files.newInputStream(propertyFilePath)) {
                    Properties properties = new Properties();
                    properties.load(stream);
                    for (final String key : properties.stringPropertyNames()) {
                        propertyKeys.add(new KnownPropertyKey(this, key, properties.getProperty(key)));
                    }
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            }
        }
        this.propertyKeys = Collections.unmodifiableList(propertyKeys);
    }

    @Override
    public Collection<PropertyKey> providedKeys() {
        return propertyKeys;
    }
}
