/*
 * Copyright (c) 2023-2024 Maveniverse Org.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 */
package eu.maveniverse.maven.nisse.extension4.internal;

import static java.util.Objects.requireNonNull;

import eu.maveniverse.maven.nisse.core.NisseConfiguration;
import eu.maveniverse.maven.nisse.core.NisseManager;
import eu.maveniverse.maven.nisse.core.PropertyKeyNamingStrategies;
import eu.maveniverse.maven.nisse.core.Version;
import eu.maveniverse.maven.nisse.core.simple.SimpleNisseConfiguration;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.HashMap;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import org.apache.maven.api.ProtoSession;
import org.apache.maven.api.spi.PropertyContributor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
@Named
final class NissePropertyContributor implements PropertyContributor {
    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final NisseManager nisseManager;

    @Inject
    public NissePropertyContributor(NisseManager nisseManager) {
        this.nisseManager = requireNonNull(nisseManager, "nisseManager");
    }

    @Override
    public Map<String, String> contribute(ProtoSession protoSession) {
        try {
            logger.info("Maveniverse Nisse {} loaded", Version.version());
            // create properties and push what we got into CLI user properties
            NisseConfiguration configuration = SimpleNisseConfiguration.builder()
                    .withSystemProperties(protoSession.getSystemProperties())
                    .withUserProperties(protoSession.getUserProperties())
                    .withCurrentWorkingDirectory(protoSession.getTopDirectory())
                    .withSessionRootDirectory(protoSession.getRootDirectory())
                    .combinePropertyKeyNamingStrategy(PropertyKeyNamingStrategies.translated(
                            PropertyKeyNamingStrategies.translationTableFromPropertiesFile(protoSession
                                    .getRootDirectory()
                                    .resolve(".mvn")
                                    .resolve("nisse-translation.properties")),
                            PropertyKeyNamingStrategies.sourcePrefixed(),
                            PropertyKeyNamingStrategies.defaultStrategy()))
                    .build();
            Map<String, String> result = new HashMap<>(protoSession.getUserProperties());
            Map<String, String> nisseProperties = nisseManager.createProperties(configuration);
            logger.info("Nisse injecting {} properties into User Properties", nisseProperties.size());
            if (Boolean.parseBoolean(protoSession.getUserProperties().getOrDefault("nisse.dump", "false"))) {
                nisseProperties.forEach((k, v) -> logger.info("{}={}", k, v));
            }
            for (Map.Entry<String, String> entry : nisseProperties.entrySet()) {
                if (isUnexpandedPlaceholder(result.get(entry.getKey())) && !isUnexpandedPlaceholder(entry.getValue())) {
                    // we have expanded value
                    result.put(entry.getKey(), entry.getValue());
                } else {
                    // otherwise preserve possible user properties precedence (ie CLI -Dnisse.foo.bar=baz)
                    result.putIfAbsent(entry.getKey(), entry.getValue());
                }
            }
            nisseProperties.forEach(result::putIfAbsent);
            return result;
        } catch (IOException e) {
            throw new UncheckedIOException("Error while creating Nisse configuration", e);
        }
    }

    private static boolean isUnexpandedPlaceholder(String propertyValue) {
        if (propertyValue == null || propertyValue.trim().isEmpty()) {
            return false;
        }
        if (propertyValue.startsWith("${") && propertyValue.endsWith("}")) {
            // maven interpolation
            return true;
        }
        if (propertyValue.startsWith("$Format:") && propertyValue.endsWith("$")) {
            // git-subst
            return true;
        }
        return false;
    }
}
