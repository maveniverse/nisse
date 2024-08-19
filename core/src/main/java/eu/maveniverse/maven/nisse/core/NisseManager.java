/*
 * Copyright (c) 2023-2024 Maveniverse Org.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 */
package eu.maveniverse.maven.nisse.core;

import java.util.Map;

/**
 * Nisse manager, that manages sources.
 */
public interface NisseManager {
    /**
     * Creates "final" map of properties contributed from {@link PropertySource} flattened (by source priorities).
     * Keys are also "namespaced" with prefix {@code "nisse.$source."}. Never returns {@code null}.
     */
    Map<String, String> createProperties(NisseConfiguration configuration);
}
