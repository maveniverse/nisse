/*
 * Copyright (c) 2023-2026 Maveniverse Org.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 */
package eu.maveniverse.gradle.nisse.plugin;

import java.util.Map;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.provider.Provider;

/**
 * Nisse plugin that creates value source with Nisse properties.
 * <p>
 * Registers two extensions:
 * <ul>
 *   <li>{@code nisseConfig} — DSL for configuring property sources (jgit, os)</li>
 *   <li>{@code nisse} — resolved {@code Map<String, String>} of discovered properties
 *       (available after project evaluation)</li>
 * </ul>
 */
public class NissePlugin implements Plugin<Project> {

    @Override
    public void apply(Project target) {
        NisseExtension extension = target.getExtensions().create("nisseConfig", NisseExtension.class);

        // Defer creation of the nisse properties map until after the build script has been
        // evaluated, so that the nisseConfig { ... } DSL block has been processed.
        target.afterEvaluate(project -> {
            Provider<Map<String, String>> provider = project.getProviders().of(NisseValueSource.class, p -> {
                p.getParameters().getCwd().set(project.getProjectDir());
                p.getParameters().getRoot().set(project.getRootDir());
                p.getParameters().getUserProperties().set(extension.toUserProperties());
            });

            Map<String, String> properties = provider.get();
            project.getExtensions().add("nisse", properties);

            project.getTasks().register("nisseDump", t -> {
                t.doLast(a -> {
                    System.out.println("Nisse dump:");
                    properties.forEach((key, value) -> System.out.println(key + "=" + value));
                });
            });
        });
    }
}
