/*
 * Copyright (c) 2023-2026 Maveniverse Org.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 */
package eu.maveniverse.gradle.nisse.plugin;

import java.util.HashMap;
import java.util.Map;
import javax.inject.Inject;
import org.gradle.api.Action;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Property;

/**
 * Gradle DSL extension for configuring Nisse.
 *
 * <pre>
 * nisseConfig {
 *     jgit {
 *         active = true
 *         dynamicVersion = true
 *         appendSnapshot = false
 *     }
 *     os {
 *         active = true
 *     }
 * }
 * </pre>
 */
public abstract class NisseExtension {

    private final JGitExtension jgit;
    private final OsExtension os;

    @Inject
    public NisseExtension(ObjectFactory objects) {
        this.jgit = objects.newInstance(JGitExtension.class);
        this.os = objects.newInstance(OsExtension.class);
    }

    /**
     * Returns the JGit source configuration.
     */
    public JGitExtension getJgit() {
        return jgit;
    }

    /**
     * Configures the JGit source.
     *
     * @param action the configuration action
     */
    public void jgit(Action<? super JGitExtension> action) {
        action.execute(jgit);
    }

    /**
     * Returns the OS source configuration.
     */
    public OsExtension getOs() {
        return os;
    }

    /**
     * Configures the OS source.
     *
     * @param action the configuration action
     */
    public void os(Action<? super OsExtension> action) {
        action.execute(os);
    }

    /**
     * Converts the DSL configuration into a flat map of Nisse configuration properties
     * (e.g. {@code nisse.source.jgit.dynamicVersion=true}).
     */
    Map<String, String> toUserProperties() {
        Map<String, String> props = new HashMap<>();
        jgit.contribute(props);
        os.contribute(props);
        return props;
    }

    /**
     * JGit source configuration.
     */
    public abstract static class JGitExtension {
        /** Whether the jgit source is active. Defaults to {@code true}. */
        public abstract Property<Boolean> getActive();

        /** Enable dynamic version computation from git tags. */
        public abstract Property<Boolean> getDynamicVersion();

        /** Enable counting version computation from git tags. */
        public abstract Property<Boolean> getCountingVersion();

        /** Length of the short commit id. */
        public abstract Property<Integer> getShortCommitIdLength();

        /** Whether to increase the patch version. */
        public abstract Property<Boolean> getIncreasePatchVersion();

        /** Whether to append the build number. */
        public abstract Property<Boolean> getAppendBuildNumber();

        /** Whether to append {@code -SNAPSHOT} qualifier. */
        public abstract Property<Boolean> getAppendSnapshot();

        /** Whether to append a dirty qualifier when the working tree is dirty. */
        public abstract Property<Boolean> getAppendDirty();

        /** The qualifier string to use when the working tree is dirty. */
        public abstract Property<String> getDirtyQualifier();

        /** Override the version to use (bypass git-based detection). */
        public abstract Property<String> getUseVersion();

        /** Pattern for extracting version hints from tag names. */
        public abstract Property<String> getVersionHintPattern();

        /** Date format to use ({@code git}, {@code iso}, {@code custom}). */
        public abstract Property<String> getDateFormat();

        /** Custom date format pattern (when dateFormat is {@code custom}). */
        public abstract Property<String> getDateFormatPattern();

        /** Counting version start major. */
        public abstract Property<Integer> getCountingStartMajor();

        /** Counting version start minor. */
        public abstract Property<Integer> getCountingStartMinor();

        /** Counting version start patch. */
        public abstract Property<Integer> getCountingStartPatch();

        /** Counting version match major pattern. */
        public abstract Property<String> getCountingMatchMajor();

        /** Counting version match minor pattern. */
        public abstract Property<String> getCountingMatchMinor();

        /** Counting version match patch pattern. */
        public abstract Property<String> getCountingMatchPatch();

        /** Counting version output pattern. */
        public abstract Property<String> getCountingPattern();

        void contribute(Map<String, String> props) {
            setIfPresent(props, "nisse.source.jgit.active", getActive());
            setIfPresent(props, "nisse.source.jgit.dynamicVersion", getDynamicVersion());
            setIfPresent(props, "nisse.source.jgit.countingVersion", getCountingVersion());
            setIfPresent(props, "nisse.source.jgit.shortCommitIdLength", getShortCommitIdLength());
            setIfPresent(props, "nisse.source.jgit.increasePatchVersion", getIncreasePatchVersion());
            setIfPresent(props, "nisse.source.jgit.appendBuildNumber", getAppendBuildNumber());
            setIfPresent(props, "nisse.source.jgit.appendSnapshot", getAppendSnapshot());
            setIfPresent(props, "nisse.source.jgit.appendDirty", getAppendDirty());
            setIfPresent(props, "nisse.source.jgit.dirtyQualifier", getDirtyQualifier());
            setIfPresent(props, "nisse.source.jgit.useVersion", getUseVersion());
            setIfPresent(props, "nisse.source.jgit.versionHintPattern", getVersionHintPattern());
            setIfPresent(props, "nisse.source.jgit.dateFormat", getDateFormat());
            setIfPresent(props, "nisse.source.jgit.dateFormat.pattern", getDateFormatPattern());
            setIfPresent(props, "nisse.source.jgit.countingVersion.startMajor", getCountingStartMajor());
            setIfPresent(props, "nisse.source.jgit.countingVersion.startMinor", getCountingStartMinor());
            setIfPresent(props, "nisse.source.jgit.countingVersion.startPatch", getCountingStartPatch());
            setIfPresent(props, "nisse.source.jgit.countingVersion.matchMajor", getCountingMatchMajor());
            setIfPresent(props, "nisse.source.jgit.countingVersion.matchMinor", getCountingMatchMinor());
            setIfPresent(props, "nisse.source.jgit.countingVersion.matchPatch", getCountingMatchPatch());
            setIfPresent(props, "nisse.source.jgit.countingVersion.pattern", getCountingPattern());
        }
    }

    /**
     * OS detector source configuration.
     */
    public abstract static class OsExtension {
        /** Whether the os source is active. Defaults to {@code true}. */
        public abstract Property<Boolean> getActive();

        void contribute(Map<String, String> props) {
            setIfPresent(props, "nisse.source.os.active", getActive());
        }
    }

    private static void setIfPresent(Map<String, String> props, String key, Property<?> property) {
        if (property.isPresent()) {
            props.put(key, property.get().toString());
        }
    }
}
