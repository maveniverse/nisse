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
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
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
    private final NissePropertyKeys nissePropertyKeys;

    /**
     * Keys accepted by {@link #isValidProperty} that could not be registered with the
     * {@link NissePropertyInliner} because the session was not yet available.
     * They are flushed into the inliner at the start of {@link #overwriteModelProperties}.
     */
    private final Set<String> deferredInlinedKeys = ConcurrentHashMap.newKeySet();

    @Inject
    public NisseModelVersionProcessor(
            Provider<MavenSession> sessionProvider, NissePropertyInliner inliner, NissePropertyKeys nissePropertyKeys) {
        this.sessionProvider = requireNonNull(sessionProvider, "sessionProvider");
        this.inliner = requireNonNull(inliner, "inliner");
        this.nissePropertyKeys = requireNonNull(nissePropertyKeys, "nissePropertyKeys");
    }

    @Override
    public boolean isValidProperty(String property) {
        if (!property.startsWith(NisseConfiguration.PROPERTY_PREFIX)) {
            return false;
        }
        // Check the session-independent property registry first. This covers properties
        // from any nisse source (file, export-subst, maven-user.properties) even when the
        // jgit source was skipped (e.g. no .git directory in a source archive).
        boolean valid = nissePropertyKeys.containsKey(property);
        if (!valid) {
            // Fallback: check session user properties (covers -D flags set after
            // NisseConfigurationProcessor ran, or other late-bound properties).
            try {
                MavenSession session = sessionProvider.get();
                valid = session.getRequest().getUserProperties().containsKey(property);
            } catch (Exception e) {
                // session not available yet during early model validation
                logger.debug("NisseModelVersionProcessor.isValidProperty: session not available for {}", property);
            }
        }
        if (valid) {
            try {
                inliner.inlinedKeys(sessionProvider.get()).add(property);
            } catch (Exception e) {
                // Session not available yet; park the key so overwriteModelProperties can
                // flush it into the inliner once the session exists.
                deferredInlinedKeys.add(property);
                logger.debug("NisseModelVersionProcessor.isValidProperty: deferring inlining for {}", property);
            }
        }
        return valid;
    }

    @Override
    public void overwriteModelProperties(Properties modelProperties, ModelBuildingRequest request) {
        try {
            MavenSession session = this.sessionProvider.get();

            // Flush any keys that were deferred because the session was unavailable
            // during isValidProperty.
            if (!deferredInlinedKeys.isEmpty()) {
                inliner.inlinedKeys(session).addAll(deferredInlinedKeys);
                deferredInlinedKeys.clear();
            }

            for (String inlinedKey : inliner.inlinedKeys(session)) {
                String value = session.getRequest().getUserProperties().getProperty(inlinedKey);
                if (value == null) {
                    // Fallback: the property may have been provided by a nisse source
                    // (e.g. file/export-subst) without going through session user properties.
                    value = nissePropertyKeys.get(inlinedKey);
                }
                if (value != null) {
                    modelProperties.setProperty(inlinedKey, value);
                }
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
