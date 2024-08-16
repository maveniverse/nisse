/*
 * Copyright (c) 2023-2024 Maveniverse Org.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 */
package eu.maveniverse.maven.nisse.extension3.internal;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.impl.Installer;
import org.eclipse.aether.installation.InstallRequest;
import org.eclipse.aether.installation.InstallResult;
import org.eclipse.aether.installation.InstallationException;
import org.eclipse.aether.internal.impl.DefaultInstaller;
import org.eclipse.sisu.Priority;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
@Named
@Priority(200)
final class NisseInstaller implements Installer {
    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final DefaultInstaller installer;

    @Inject
    public NisseInstaller(DefaultInstaller installer) {
        this.installer = installer;
    }

    @Override
    public InstallResult install(RepositorySystemSession session, InstallRequest request) throws InstallationException {
        logger.info("install");
        return installer.install(session, request);
    }
}
