/*
 * Copyright (c) 2023-2026 Maveniverse Org.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 */
package eu.maveniverse.gradle.nisse.plugin;

import org.gradle.api.Plugin;
import org.gradle.api.Project;

/**
 * Nisse plugin that crates value source with Nisse properties.
 */
public class NissePlugin implements Plugin<Project> {

    @Override
    public void apply(Project target) {
        target.getProviders().of(NisseValueSource.class, p -> {
            p.getParameters().cwd().set(target.getProjectDir().toPath());
            p.getParameters().root().set(target.getRootDir().toPath());
        });
    }
}
