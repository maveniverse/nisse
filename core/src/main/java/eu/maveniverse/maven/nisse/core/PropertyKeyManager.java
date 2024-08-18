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
 * A property key source, that provides all the supported keys, and is able to evaluate them.
 */
public interface PropertyKeyManager {
    Map<String, String> allProperties(NisseSession session);
}
