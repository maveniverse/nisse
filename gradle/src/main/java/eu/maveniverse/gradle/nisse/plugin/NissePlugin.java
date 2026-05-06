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
import org.gradle.api.provider.Provider;

import java.util.Map;

/**
 * Nisse plugin that crates value source with Nisse properties.
 */
public class NissePlugin implements Plugin<Project> {

    @Override
    public void apply(Project target) {
        Provider<Map<String, String>> provider = target.getProviders().of(NisseValueSource.class, p -> {
            p.getParameters().getCwd().set(target.getProjectDir());
            p.getParameters().getRoot().set(target.getRootDir());
        });

        target.getExtensions().add("nisse", provider.get());

        target.getTasks().register("nisseDump", p -> {
            System.out.println("Nisse dump:");
            provider.get().forEach((key, value) -> System.out.println(key + "=" + value));
        });
    }
}
