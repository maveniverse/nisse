/*
 * Copyright (c) 2023-2024 Maveniverse Org.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 */
package eu.maveniverse.maven.nisse.extension3.internal;

import static java.util.Objects.requireNonNull;

import eu.maveniverse.maven.nisse.core.NisseSession;
import java.util.Optional;
import java.util.Properties;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Provider;
import javax.inject.Singleton;
import org.apache.maven.model.building.ModelBuildingRequest;
import org.apache.maven.model.interpolation.ModelVersionProcessor;
import org.eclipse.sisu.Priority;

@Singleton
@Named
@Priority(200)
class NisseModelVersionProcessor implements ModelVersionProcessor {
    private final Provider<NisseSession> nisseSessionProvider;

    @Inject
    public NisseModelVersionProcessor(Provider<NisseSession> nisseSessionProvider) {
        this.nisseSessionProvider = requireNonNull(nisseSessionProvider, "nisseSessionProvider");
    }

    @Override
    public boolean isValidProperty(String property) {
        NisseSession nisseSession = nisseSessionProvider.get();
        Optional<PropertyKey> propertyKey = nisseSession.getSessionPropertyKeys().stream()
                .filter(p -> property.equals(p.getKey()))
                .findFirst();
        propertyKey.ifPresent(nisseSession::addSessionMandatoryPropertyKey);
        return propertyKey.isPresent();
    }

    @Override
    public void overwriteModelProperties(Properties modelProperties, ModelBuildingRequest request) {
        NisseSession nisseSession = nisseSessionProvider.get();
        for (PropertyKey mandatoryKey : nisseSession.getSessionMandatoryPropertyKeys()) {
            if (mandatoryKey.getValue().isPresent()) {
                modelProperties.setProperty(
                        mandatoryKey.getKey(), mandatoryKey.getValue().get());
            } else {
                throw new IllegalStateException("Mandatory property " + mandatoryKey.getKey() + " have no value");
            }
        }
    }
}
