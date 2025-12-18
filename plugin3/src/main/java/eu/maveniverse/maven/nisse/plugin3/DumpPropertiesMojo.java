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
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Properties;
import javax.inject.Inject;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Nisse dump-properties Mojo that dumps created properties to output.
 * Is mostly usable as some diagnostic/setup check.
 */
@Mojo(name = "dump-properties", threadSafe = true)
public class DumpPropertiesMojo extends AbstractMojo {
    private final Logger logger = LoggerFactory.getLogger(getClass());

    @Inject
    private MavenSession mavenSession;

    @Inject
    private NisseManager nisseManager;

    /**
     * If set, the dump will write out {@link java.util.Properties} into this file, otherwise the dump goes out
     * to logger.
     */
    @Parameter(property = "nisse.output")
    private File output;

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
            if (output == null) {
                logger.info("Dumping {} properties", properties.size());
                properties.forEach((k, v) -> logger.info("{}={}", k, v));
            } else {
                logger.info("Dumping {} properties to {}", properties.size(), output);
                try (OutputStream outputStream = Files.newOutputStream(output.toPath())) {
                    Properties props = new Properties();
                    props.putAll(properties);
                    props.store(outputStream, null);
                }
            }
        } catch (IOException e) {
            throw new MojoExecutionException("Error while creating Nisse configuration", e);
        }
    }
}
