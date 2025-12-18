/*
 * Copyright (c) 2023-2024 Maveniverse Org.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 */
package eu.maveniverse.maven.nisse.plugin3;

import eu.maveniverse.maven.nisse.core.NisseConfiguration;
import eu.maveniverse.maven.nisse.core.NisseManager;
import eu.maveniverse.maven.nisse.core.PropertyKeyNamingStrategies;
import eu.maveniverse.maven.nisse.core.simple.SimpleNisseConfiguration;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.Map;
import javax.inject.Inject;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Nisse inject-properties Mojo that injects created properties into project.
 */
@Mojo(name = "inject-properties", threadSafe = true)
public class InjectPropertiesMojo extends AbstractMojo {
    private final Logger logger = LoggerFactory.getLogger(getClass());

    @Inject
    private MavenProject mavenProject;

    @Inject
    private MavenSession mavenSession;

    @Inject
    private NisseManager nisseManager;

    /**
     * Diagnostic utility, if {@code true}, it will dump to log all the properties it injects into project.
     */
    @Parameter(property = "nisse.dump", defaultValue = "false")
    private boolean dump;

    @Override
    public void execute() throws MojoExecutionException {
        try {
            NisseConfiguration configuration = SimpleNisseConfiguration.builder()
                    .withSystemProperties(mavenSession.getSystemProperties())
                    .withUserProperties(mavenSession.getUserProperties())
                    .withCurrentWorkingDirectory(
                            Paths.get(mavenSession.getRequest().getBaseDirectory()))
                    .withSessionRootDirectory(mavenSession
                            .getRequest()
                            .getMultiModuleProjectDirectory()
                            .toPath())
                    .combinePropertyKeyNamingStrategy(PropertyKeyNamingStrategies.translated(
                            PropertyKeyNamingStrategies.translationTableFromPropertiesFile(mavenSession
                                    .getRequest()
                                    .getMultiModuleProjectDirectory()
                                    .toPath()
                                    .resolve(".mvn")
                                    .resolve("nisse-translation.properties")),
                            PropertyKeyNamingStrategies.sourcePrefixed(),
                            PropertyKeyNamingStrategies.defaultStrategy()))
                    .build();
            Map<String, String> properties = nisseManager.createProperties(configuration);
            if (dump) {
                logger.info("Dumping {} properties", properties.size());
                properties.forEach((k, v) -> logger.info("{}={}", k, v));
            }
            logger.info("Injecting {} properties into project", properties.size());
            properties.forEach((k, v) -> mavenProject.getProperties().setProperty(k, v));
        } catch (IOException e) {
            throw new MojoExecutionException("Error while creating Nisse configuration", e);
        }
    }
}
