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
import org.apache.maven.SessionScoped;
import org.apache.maven.api.Session;
import org.apache.maven.api.di.Inject;
import org.apache.maven.api.di.Named;
import org.apache.maven.api.di.Priority;
import org.apache.maven.api.services.ModelBuilderRequest;
import org.apache.maven.api.services.model.ModelVersionProcessor;

@SessionScoped
@Named
@Priority(200)
final class NisseModelVersionProcessor implements ModelVersionProcessor {
    private final Session session;
    private final NisseListener.InlinedKeys inlinedKeys;

    @Inject
    public NisseModelVersionProcessor(Session session) {
        this.session = requireNonNull(session, "session");
        inlinedKeys = session.getData().computeIfAbsent(NisseListener.INLINED_KEYS, NisseListener.InlinedKeys::new);
    }

    @Override
    public boolean isValidProperty(String property) {
        boolean valid = property.startsWith(NisseConfiguration.PROPERTY_PREFIX)
                && session.getUserProperties().containsKey(property);
        if (valid) {
            inlinedKeys.addKey(property);
        }
        return valid;
    }

    @Override
    public void overwriteModelProperties(Properties modelProperties, ModelBuilderRequest request) {
        for (String inlinedKey : inlinedKeys.getKeys()) {
            modelProperties.setProperty(inlinedKey, session.getUserProperties().get(inlinedKey));
        }
    }
}
