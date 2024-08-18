/*
 * Copyright (c) 2023-2024 Maveniverse Org.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 */
package eu.maveniverse.maven.nisse.extension3;

import static java.util.Objects.requireNonNull;

import eu.maveniverse.maven.nisse.core.NisseSession;
import eu.maveniverse.maven.nisse.core.PropertyKeyManager;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import javax.inject.Inject;
import javax.inject.Named;
import org.apache.maven.AbstractMavenLifecycleParticipant;
import org.apache.maven.SessionScoped;
import org.apache.maven.execution.MavenSession;

@SessionScoped
@Named
public final class NisseMavenLifecycleParticipant extends AbstractMavenLifecycleParticipant implements NisseSession {
    private final PropertyKeyManager propertyKeyManager;
    private final List<PropertyKey> propertyKeys;
    private final CopyOnWriteArrayList<PropertyKey> mandatoryPropertyKeys;

    @Inject
    public NisseMavenLifecycleParticipant(PropertyKeyManager propertyKeyManager) {
        this.propertyKeyManager = requireNonNull(propertyKeyManager, "propertyKeyManager");
        this.propertyKeys = new ArrayList<>();
        this.mandatoryPropertyKeys = new CopyOnWriteArrayList<>();
    }

    @Override
    public void afterSessionStart(MavenSession session) {
        propertyKeys.addAll(
                propertyKeyManager.allKeys(session.getRepositorySession().getUserProperties()));
    }

    @Override
    public Collection<PropertyKey> getSessionPropertyKeys() {
        return Collections.unmodifiableList(propertyKeys);
    }

    @Override
    public Collection<PropertyKey> getSessionMandatoryPropertyKeys() {
        return Collections.unmodifiableList(mandatoryPropertyKeys);
    }

    @Override
    public void addSessionMandatoryPropertyKey(PropertyKey key) {
        requireNonNull(key, "key");
        mandatoryPropertyKeys.add(key);
    }
}
