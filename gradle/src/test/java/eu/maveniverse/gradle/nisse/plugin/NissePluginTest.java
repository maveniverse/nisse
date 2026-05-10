/*
 * Copyright (c) 2023-2026 Maveniverse Org.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 */
package eu.maveniverse.gradle.nisse.plugin;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class NissePluginTest {

    @TempDir
    private Path projectDir;

    @Test
    void nisseDump() throws IOException {
        writeFile("settings.gradle", "rootProject.name = \"test\"");
        writeFile("build.gradle", """
                        plugins {
                            id("eu.maveniverse.gradle.plugins.nisse-gradle-plugin")
                        }
                        """);
        BuildResult result = runner().withArguments("nisseDump").run();
        assertTrue(result.getOutput().contains("nisse.os.name="), result.getOutput());
    }

    @Test
    void nisseProjectProperties() throws IOException {
        writeFile("settings.gradle", "rootProject.name = \"test\"");
        writeFile("build.gradle", """
                plugins {
                    id("eu.maveniverse.gradle.plugins.nisse-gradle-plugin")
                }
                print 'Nisse was here: ' + project.properties.nisse['nisse.os.name']
                """);
        BuildResult result = runner().run();
        String output = result.getOutput();
        int idx = output.indexOf("Nisse was here: ");
        assertTrue(idx >= 0, "Expected 'Nisse was here: ' in output: " + output);
        String value = output.substring(idx + "Nisse was here: ".length())
                .lines()
                .findFirst()
                .orElse("");
        assertTrue(!value.isEmpty() && !value.equals("null"), "Expected non-empty os.name but got: '" + value + "'");
    }

    private GradleRunner runner() {
        return GradleRunner.create()
                .withProjectDir(projectDir.toFile())
                .withPluginClasspath()
                .withDebug(true);
    }

    private void writeFile(String filename, String content) throws IOException {
        Files.writeString(projectDir.resolve(filename), content);
    }
}
