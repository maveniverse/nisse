/*
 * Copyright (c) 2023-2024 Maveniverse Org.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 */
package eu.maveniverse.maven.nisse.source.jgit;

import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Named presets for dynamic version configuration. Each preset provides sensible defaults
 * for the individual dynamic version properties, reducing configuration from multiple
 * boolean/string properties down to a single property.
 * <p>
 * Individual properties always override preset defaults, so the full flexibility is
 * preserved for advanced users.
 *
 * @see JGitPropertySource
 */
enum DynamicVersionPreset {

    /**
     * Development builds (current default behavior): increment patch, append build number, append SNAPSHOT.
     */
    SNAPSHOT,

    /**
     * CI builds: increment patch, append build number, append branch name, no SNAPSHOT.
     */
    CI,

    /**
     * Release builds: exact tag version, no increment, no qualifiers.
     */
    RELEASE,

    /**
     * Local development: like snapshot, plus append DIRTY qualifier on uncommitted changes.
     */
    DIRTY;

    private static final String INCREASE_PATCH_VERSION = "nisse.source.jgit.increasePatchVersion";
    private static final String VERSION_INCREMENT = "nisse.source.jgit.versionIncrement";
    private static final String APPEND_BUILD_NUMBER = "nisse.source.jgit.appendBuildNumber";
    private static final String APPEND_SNAPSHOT = "nisse.source.jgit.appendSnapshot";
    private static final String APPEND_BRANCH_NAME = "nisse.source.jgit.appendBranchName";
    private static final String APPEND_DIRTY = "nisse.source.jgit.appendDirty";

    /**
     * Returns the default property values for this preset.
     *
     * @return an unmodifiable map of property keys to their default values
     */
    Map<String, String> defaults() {
        Map<String, String> map = new HashMap<>();
        switch (this) {
            case SNAPSHOT:
                map.put(INCREASE_PATCH_VERSION, Boolean.TRUE.toString());
                map.put(APPEND_BUILD_NUMBER, Boolean.TRUE.toString());
                map.put(APPEND_SNAPSHOT, Boolean.TRUE.toString());
                map.put(APPEND_BRANCH_NAME, Boolean.FALSE.toString());
                map.put(APPEND_DIRTY, Boolean.FALSE.toString());
                break;
            case CI:
                map.put(INCREASE_PATCH_VERSION, Boolean.TRUE.toString());
                map.put(APPEND_BUILD_NUMBER, Boolean.TRUE.toString());
                map.put(APPEND_SNAPSHOT, Boolean.FALSE.toString());
                map.put(APPEND_BRANCH_NAME, Boolean.TRUE.toString());
                map.put(APPEND_DIRTY, Boolean.FALSE.toString());
                break;
            case RELEASE:
                map.put(VERSION_INCREMENT, "none");
                map.put(APPEND_BUILD_NUMBER, Boolean.FALSE.toString());
                map.put(APPEND_SNAPSHOT, Boolean.FALSE.toString());
                map.put(APPEND_BRANCH_NAME, Boolean.FALSE.toString());
                map.put(APPEND_DIRTY, Boolean.FALSE.toString());
                break;
            case DIRTY:
                map.put(INCREASE_PATCH_VERSION, Boolean.TRUE.toString());
                map.put(APPEND_BUILD_NUMBER, Boolean.TRUE.toString());
                map.put(APPEND_SNAPSHOT, Boolean.TRUE.toString());
                map.put(APPEND_BRANCH_NAME, Boolean.FALSE.toString());
                map.put(APPEND_DIRTY, Boolean.TRUE.toString());
                break;
            default:
                break;
        }
        return Collections.unmodifiableMap(map);
    }

    /**
     * Parses a preset name (case-insensitive, trimmed). Returns {@code null} for unknown values.
     *
     * @param name the preset name to parse
     * @return the matching preset, or {@code null} if the name is {@code null}, empty, or unknown
     */
    static DynamicVersionPreset fromString(String name) {
        if (name == null || name.trim().isEmpty()) {
            return null;
        }
        try {
            return valueOf(name.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
