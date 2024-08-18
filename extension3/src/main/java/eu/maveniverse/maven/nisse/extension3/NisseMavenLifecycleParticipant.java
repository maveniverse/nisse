/*
 * Copyright (c) 2023-2024 Maveniverse Org.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 */
package eu.maveniverse.maven.nisse.extension3;

import static java.util.Objects.requireNonNull;

import eu.maveniverse.maven.nisse.core.NisseConfiguration;
import eu.maveniverse.maven.nisse.core.NisseManager;
import javax.inject.Inject;
import javax.inject.Named;
import org.apache.maven.AbstractMavenLifecycleParticipant;
import org.apache.maven.SessionScoped;
import org.apache.maven.execution.MavenSession;

@SessionScoped
@Named
public final class NisseMavenLifecycleParticipant extends AbstractMavenLifecycleParticipant {
    private final NisseManager nisseManager;

    @Inject
    public NisseMavenLifecycleParticipant(NisseManager nisseManager) {
        this.nisseManager = requireNonNull(nisseManager, "propertyKeyManager");
    }

    @Override
    public void afterSessionEnd(MavenSession session) {
        nisseManager.finishSession(
                session.getRequest().getUserProperties().getProperty(NisseConfiguration.PROPERTY_PREFIX + "sessionId"));
    }
}
