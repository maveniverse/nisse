/*
 * Copyright (c) 2023-2026 Maveniverse Org.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 */
package eu.maveniverse.gradle.nisse.plugin;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.eclipse.jgit.api.Git;
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
                tasks.register("showOsName") {
                    doLast {
                        print 'Nisse was here: ' + project.nisse['nisse.os.name']
                    }
                }
                """);
        BuildResult result = runner().withArguments("showOsName").run();
        String output = result.getOutput();
        int idx = output.indexOf("Nisse was here: ");
        assertTrue(idx >= 0, "Expected 'Nisse was here: ' in output: " + output);
        String value = output.substring(idx + "Nisse was here: ".length())
                .lines()
                .findFirst()
                .orElse("");
        assertTrue(!value.isEmpty() && !value.equals("null"), "Expected non-empty os.name but got: '" + value + "'");
    }

    /**
     * Minimal setup example: just apply the plugin and all sources (jgit, os-detector) are active by default.
     * The nisse extension is available as a Map of discovered properties.
     */
    @Test
    void minimalSetup() throws IOException {
        writeFile("settings.gradle", "rootProject.name = \"test\"");
        writeFile("build.gradle", """
                plugins {
                    id("eu.maveniverse.gradle.plugins.nisse-gradle-plugin")
                }
                // Access nisse properties via the 'nisse' extension (a Map<String, String>)
                tasks.register("showVersion") {
                    doLast {
                        println "OS: ${project.nisse['nisse.os.name']}"
                        println "Arch: ${project.nisse['nisse.os.arch']}"
                    }
                }
                """);
        BuildResult result = runner().withArguments("showVersion").run();
        String output = result.getOutput();
        assertTrue(output.contains("OS: "), "Expected OS property in output: " + output);
        assertTrue(output.contains("Arch: "), "Expected Arch property in output: " + output);
    }

    /**
     * Configuration example: deactivate the jgit source via the nisseConfig DSL and verify
     * that jgit properties are absent while os-detector properties remain available.
     */
    @Test
    void configureDeactivateSource() throws IOException {
        writeFile("settings.gradle", "rootProject.name = \"test\"");
        writeFile("build.gradle", """
                plugins {
                    id("eu.maveniverse.gradle.plugins.nisse-gradle-plugin")
                }
                // Use the nisseConfig DSL to deactivate the jgit source
                nisseConfig {
                    jgit {
                        active = false
                    }
                }
                tasks.register("checkProperties") {
                    doLast {
                        def props = project.nisse
                        // os-detector properties should still be present
                        println "os.name=" + props['nisse.os.name']
                        // jgit properties should be absent since jgit source was deactivated
                        println "jgit.commit=" + props.getOrDefault('nisse.jgit.commit', 'ABSENT')
                    }
                }
                """);
        BuildResult result = runner().withArguments("checkProperties").run();
        String output = result.getOutput();
        // os-detector properties should be present
        assertTrue(output.contains("os.name="), "Expected os.name in output: " + output);
        assertFalse(output.contains("os.name=null"), "os.name should not be null: " + output);
        // jgit properties should be absent
        assertTrue(output.contains("jgit.commit=ABSENT"), "Expected jgit.commit to be ABSENT: " + output);
    }

    /**
     * Configuration example: configure jgit source options via the nisseConfig DSL.
     */
    @Test
    void configureJgitViaDsl() throws IOException {
        writeFile("settings.gradle", "rootProject.name = \"test\"");
        writeFile("build.gradle", """
                plugins {
                    id("eu.maveniverse.gradle.plugins.nisse-gradle-plugin")
                }
                // Configure jgit source via nisseConfig DSL
                nisseConfig {
                    jgit {
                        dynamicVersion = true
                        appendSnapshot = false
                        appendDirty = true
                        dirtyQualifier = 'WIP'
                        shortCommitIdLength = 10
                    }
                }
                tasks.register("showNisse") {
                    doLast {
                        def props = project.nisse
                        println "os.name=" + props['nisse.os.name']
                        // jgit properties should be present (source is active)
                        props.findAll { it.key.startsWith('nisse.jgit.') }.each {
                            println it.key + '=' + it.value
                        }
                    }
                }
                """);
        BuildResult result = runner().withArguments("showNisse").run();
        String output = result.getOutput();
        assertTrue(output.contains("os.name="), "Expected os.name in output: " + output);
    }

    /**
     * Configuration example: enable counting version via the nisseConfig DSL.
     * Counting version walks the commit history and derives a version from commit message
     * directives ([major], [minor], [patch]). Commits without a directive increment the
     * commit count.
     */
    @Test
    void configureCountingVersion() throws Exception {
        // Set up a git repo with commits that exercise counting version directives
        try (Git git = Git.init().setDirectory(projectDir.toFile()).call()) {
            writeFile("settings.gradle", "rootProject.name = \"test\"");
            writeFile("build.gradle", """
                    plugins {
                        id("eu.maveniverse.gradle.plugins.nisse-gradle-plugin")
                    }
                    nisseConfig {
                        jgit {
                            countingVersion = true
                        }
                    }
                    tasks.register("printCountingVersion") {
                        doLast {
                            println "countingVersion=" + project.nisse['nisse.jgit.countingVersion']
                        }
                    }
                    """);
            git.add().addFilepattern(".").call();
            git.commit().setMessage("initial commit").call(); // commitCount=1 -> 0.0.0-1
            git.commit().setAllowEmpty(true).setMessage("[major] first major").call(); // major=1, reset
            git.commit().setAllowEmpty(true).setMessage("some work").call(); // commitCount=1
            git.commit().setAllowEmpty(true).setMessage("[minor] add feature").call(); // minor=1, reset
            git.commit().setAllowEmpty(true).setMessage("fix typo").call(); // commitCount=1
            git.commit().setAllowEmpty(true).setMessage("another fix").call(); // commitCount=2 -> 1.1.0-2
        }

        BuildResult result = runner().withArguments("printCountingVersion").run();
        String output = result.getOutput();
        assertTrue(output.contains("countingVersion=1.1.0-2"), "Expected counting version 1.1.0-2 but got: " + output);
    }

    /**
     * Configuration example: counting version with a custom pattern and start values.
     */
    @Test
    void configureCountingVersionCustomPattern() throws Exception {
        try (Git git = Git.init().setDirectory(projectDir.toFile()).call()) {
            writeFile("settings.gradle", "rootProject.name = \"test\"");
            writeFile("build.gradle", """
                    plugins {
                        id("eu.maveniverse.gradle.plugins.nisse-gradle-plugin")
                    }
                    nisseConfig {
                        jgit {
                            countingVersion = true
                            countingStartMajor = 2
                            countingStartMinor = 0
                            countingStartPatch = 0
                            countingPattern = '%M.%m.%p(.%c)'
                        }
                    }
                    tasks.register("printCountingVersion") {
                        doLast {
                            println "countingVersion=" + project.nisse['nisse.jgit.countingVersion']
                        }
                    }
                    """);
            git.add().addFilepattern(".").call();
            git.commit().setMessage("initial").call(); // commitCount=1
            git.commit().setAllowEmpty(true).setMessage("[minor] feature").call(); // minor=1, reset
            git.commit().setAllowEmpty(true).setMessage("tweak").call(); // commitCount=1 -> 2.1.0.1
        }

        BuildResult result = runner().withArguments("printCountingVersion").run();
        String output = result.getOutput();
        assertTrue(output.contains("countingVersion=2.1.0.1"), "Expected counting version 2.1.0.1 but got: " + output);
    }

    /**
     * Configuration example: use counting version from Git history as the project version.
     * This is the recommended way to derive a project version from Git with Nisse.
     */
    @Test
    void configureUseInProjectVersion() throws Exception {
        try (Git git = Git.init().setDirectory(projectDir.toFile()).call()) {
            writeFile("settings.gradle", "rootProject.name = \"test\"");
            writeFile("build.gradle", """
                    plugins {
                        id("eu.maveniverse.gradle.plugins.nisse-gradle-plugin")
                    }
                    nisseConfig {
                        jgit {
                            countingVersion = true
                        }
                    }
                    afterEvaluate {
                        version = project.nisse['nisse.jgit.countingVersion']
                    }
                    tasks.register("showProjectVersion") {
                        doLast {
                            println "project.version=" + project.version
                        }
                    }
                    """);
            git.add().addFilepattern(".").call();
            git.commit().setMessage("[major] initial release").call(); // major=1
            git.commit().setAllowEmpty(true).setMessage("[minor] add feature").call(); // minor=1
            git.commit().setAllowEmpty(true).setMessage("bugfix").call(); // commitCount=1 -> 1.1.0-1
        }

        BuildResult result = runner().withArguments("showProjectVersion").run();
        String output = result.getOutput();
        assertTrue(output.contains("project.version=1.1.0-1"), "Expected project.version=1.1.0-1 but got: " + output);
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
