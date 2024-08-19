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
import java.util.Properties;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Provider;
import javax.inject.Singleton;
import org.apache.maven.api.Session;
import org.apache.maven.api.services.ModelBuilderRequest;
import org.apache.maven.api.services.model.ModelVersionProcessor;
import org.eclipse.sisu.Priority;

@Singleton
@Named
@Priority(200)
final class NisseModelVersionProcessor implements ModelVersionProcessor {
    private final Provider<Session> sessionProvider;
    private final NissePropertyInliner inliner;

    @Inject
    public NisseModelVersionProcessor(Provider<Session> sessionProvider, NissePropertyInliner inliner) {
        this.sessionProvider = requireNonNull(sessionProvider, "sessionProvider");
        this.inliner = requireNonNull(inliner, "inliner");
    }

    @Override
    public boolean isValidProperty(String property) {
        Session session = this.sessionProvider.get();
        boolean valid = property.startsWith(NisseConfiguration.PROPERTY_PREFIX)
                && session.getUserProperties().containsKey(property);
        if (valid) {
            inliner.inlinedKeys(session).add(property);
        }
        return valid;
    }

    @Override
    public void overwriteModelProperties(Properties modelProperties, ModelBuilderRequest request) {
        Session session = this.sessionProvider.get();
        for (String inlinedKey : inliner.inlinedKeys(session)) {
            modelProperties.setProperty(inlinedKey, session.getUserProperties().get(inlinedKey));
        }
    }
}
