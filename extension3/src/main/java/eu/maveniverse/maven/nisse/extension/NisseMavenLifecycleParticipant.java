/*
 * Copyright (c) 2023-2024 Maveniverse Org.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 */
package eu.maveniverse.maven.nisse.extension;

import javax.inject.Named;
import org.apache.maven.AbstractMavenLifecycleParticipant;
import org.apache.maven.MavenExecutionException;
import org.apache.maven.SessionScoped;
import org.apache.maven.execution.MavenSession;
import org.codehaus.plexus.util.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@SessionScoped
@Named
final class NisseMavenLifecycleParticipant extends AbstractMavenLifecycleParticipant {
    private final Logger logger = LoggerFactory.getLogger(getClass());

    @Override
    public void afterSessionStart(MavenSession session) throws MavenExecutionException {
        logger.info(
                "Nisse {} is here! - afterSessionStart: {}",
                NisseMavenLifecycleParticipant.class.getPackage().getImplementationVersion(),
                StringUtils.capitalise("ff"));
        super.afterSessionStart(session);
    }

    @Override
    public void afterProjectsRead(MavenSession session) throws MavenExecutionException {
        logger.info(
                "Nisse {} is here! - afterProjectsRead",
                NisseMavenLifecycleParticipant.class.getPackage().getImplementationVersion());
        super.afterProjectsRead(session);
    }

    @Override
    public void afterSessionEnd(MavenSession session) throws MavenExecutionException {
        super.afterSessionEnd(session);
    }
}
