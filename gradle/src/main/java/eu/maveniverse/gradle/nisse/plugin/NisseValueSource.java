/*
 * Copyright (c) 2023-2026 Maveniverse Org.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 */
package eu.maveniverse.gradle.nisse.plugin;

import eu.maveniverse.maven.nisse.core.NisseConfiguration;
import eu.maveniverse.maven.nisse.core.simple.SimpleNisseConfiguration;
import eu.maveniverse.maven.nisse.core.simple.SimpleNisseManager;
import eu.maveniverse.maven.nisse.source.jgit.JGitPropertySource;
import eu.maveniverse.maven.nisse.source.osdetector.OsDetectorPropertySource;
import org.gradle.api.provider.ValueSource;
import org.jspecify.annotations.Nullable;

import java.util.Arrays;
import java.util.Map;

/**
 * Values source for Nisse properties.
 */
public abstract class NisseValueSource implements ValueSource<Map<String, String>, NisseValueSourceParam> {
    @Override
    public @Nullable Map<String, String> obtain() {
        NisseConfiguration configuration = SimpleNisseConfiguration.builder()
                .withSystemProperties(System.getProperties())
                .withCurrentWorkingDirectory(getParameters().cwd().get())
                .withSessionRootDirectory(getParameters().root().get())
                .build();
        return new SimpleNisseManager(
                Arrays.asList(new JGitPropertySource(),
                        new OsDetectorPropertySource())).createProperties(configuration);
    }
}
