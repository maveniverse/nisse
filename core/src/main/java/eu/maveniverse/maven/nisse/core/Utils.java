/*
 * Copyright (c) 2023-2024 Maveniverse Org.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 */
package eu.maveniverse.maven.nisse.core;

public class Utils {
    /**
     * Helper method to "mavenize" property value.
     */
    public static String mavenizeValue(String value) {
        if (value == null || value.trim().isEmpty()) {
            return "true";
        }
        return value;
    }
}
