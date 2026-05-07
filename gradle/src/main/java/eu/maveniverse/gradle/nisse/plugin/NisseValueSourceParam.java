/*
 * Copyright (c) 2023-2026 Maveniverse Org.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 */
package eu.maveniverse.gradle.nisse.plugin;

import java.io.File;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.ValueSourceParameters;

/**
 * Params for Nisse value source.
 */
public interface NisseValueSourceParam extends ValueSourceParameters {
    /**
     * Property for current working directory.
     *
     * @return the path of current working directory.
     */
    Property<File> getCwd();

    /**
     * Property for build root directory.
     *
     * @return the path of root directory.
     */
    Property<File> getRoot();
}
