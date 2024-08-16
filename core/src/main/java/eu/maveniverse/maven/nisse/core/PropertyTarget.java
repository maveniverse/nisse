/*
 * Copyright (c) 2023-2024 Maveniverse Org.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 */
package eu.maveniverse.maven.nisse.core;

public enum PropertyTarget {
    /**
     * Pushes to Java System properties (and also Maven System properties).
     */
    JAVA_SYSTEM_PROPERTIES,
    /**
     * Pushes to Maven System properties.
     */
    MAVEN_SYSTEM_PROPERTIES,
    /**
     * Pushes into Project properties.
     */
    PROJECT_PROPERTIES,
    /**
     * Pushes into user properties.
     */
    MAVEN_USER_PROPERTIES
}
