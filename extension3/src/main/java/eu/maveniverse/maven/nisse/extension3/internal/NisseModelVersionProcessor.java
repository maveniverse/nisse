/*
 * Copyright (c) 2023-2024 Maveniverse Org.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 */
package eu.maveniverse.maven.nisse.extension3.internal;

import static java.util.Objects.requireNonNull;

import eu.maveniverse.maven.nisse.core.NisseConfiguration;
import eu.maveniverse.maven.nisse.core.PropertyKeyNamingStrategies;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Provider;
import javax.inject.Singleton;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.building.ModelBuildingRequest;
import org.apache.maven.model.interpolation.ModelVersionProcessor;
import org.eclipse.sisu.Priority;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
@Named
@Priority(200)
final class NisseModelVersionProcessor implements ModelVersionProcessor {
    private final Logger logger = LoggerFactory.getLogger(NisseModelVersionProcessor.class);
    private final Provider<MavenSession> sessionProvider;
    private final NissePropertyInliner inliner;
    private volatile Map<String, List<String>> translationTable;

    @Inject
    public NisseModelVersionProcessor(Provider<MavenSession> sessionProvider, NissePropertyInliner inliner) {
        this.sessionProvider = requireNonNull(sessionProvider, "sessionProvider");
        this.inliner = requireNonNull(inliner, "inliner");
    }

    @Override
    public boolean isValidProperty(String property) {
        MavenSession session = this.sessionProvider.get();
        boolean valid = property.startsWith(NisseConfiguration.PROPERTY_PREFIX)
                && session.getRequest().getUserProperties().containsKey(property);
        if (valid) {
            inliner.inlinedKeys(session).add(property);
        } else if (property.startsWith(NisseConfiguration.PROPERTY_PREFIX)) {
            warnIfTranslated(session, property);
        }
        return valid;
    }

    private void warnIfTranslated(MavenSession session, String property) {
        try {
            Map<String, List<String>> table = loadTranslationTable(session);
            if (!table.isEmpty()) {
                String sourcePrefixedKey = property.substring(NisseConfiguration.PROPERTY_PREFIX.length());
                List<String> translatedTo = table.get(sourcePrefixedKey);
                if (translatedTo != null) {
                    List<String> targets = translatedTo.stream()
                            .map(String::trim)
                            .filter(k -> !"+fallback".equals(k))
                            .collect(Collectors.toList());
                    if (!targets.isEmpty()) {
                        String targetExprs =
                                targets.stream().map(k -> "${" + k + "}").collect(Collectors.joining(", "));
                        logger.warn(
                                "POM references {}, but this property was translated to {} via "
                                        + "nisse-translation.properties. Use {} instead, or remove the translation.",
                                "${" + property + "}",
                                targetExprs,
                                targetExprs);
                    }
                }
            }
        } catch (Exception e) {
            logger.debug("Failed to check translation table: {}", e.getMessage());
        }
    }

    private Map<String, List<String>> loadTranslationTable(MavenSession session) throws IOException {
        if (translationTable == null) {
            synchronized (this) {
                if (translationTable == null) {
                    Path translationFile = session.getRequest()
                            .getMultiModuleProjectDirectory()
                            .toPath()
                            .resolve(".mvn")
                            .resolve("nisse-translation.properties");
                    translationTable = PropertyKeyNamingStrategies.translationTableFromPropertiesFile(translationFile);
                }
            }
        }
        return translationTable;
    }

    @Override
    public void overwriteModelProperties(Properties modelProperties, ModelBuildingRequest request) {
        try {
            MavenSession session = this.sessionProvider.get();
            for (String inlinedKey : inliner.inlinedKeys(session)) {
                modelProperties.setProperty(
                        inlinedKey, session.getRequest().getUserProperties().getProperty(inlinedKey));
            }
        } catch (Exception e) {
            // ignore; this means we were invoked outside of session
            if (logger.isDebugEnabled()) {
                logger.warn("NisseModelVersionProcessor.overwriteModelProperties: failed, called out of session?", e);
            } else {
                logger.warn("NisseModelVersionProcessor.overwriteModelProperties: failed, called out of session?");
            }
        }
    }
}
