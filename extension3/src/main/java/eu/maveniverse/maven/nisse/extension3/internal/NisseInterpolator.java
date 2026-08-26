/*
 * Copyright (c) 2023-2024 Maveniverse Org.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 */
package eu.maveniverse.maven.nisse.extension3.internal;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.UnaryOperator;

/**
 * Property value interpolator adapted from Maven 4's
 * {@code org.apache.maven.impl.model.DefaultInterpolator}.
 * <p>
 * Supports {@code ${name}} variable references with:
 * <ul>
 *   <li>Default values: {@code ${var:-default}}</li>
 *   <li>Alternative values: {@code ${var:+alternative}}</li>
 *   <li>Nested interpolation: {@code ${foo.${bar}}}</li>
 *   <li>Escape handling: {@code \${literal}}</li>
 *   <li>Cycle detection for recursive references</li>
 * </ul>
 * <p>
 * Original source:
 * <a href="https://github.com/apache/maven/blob/master/impl/maven-impl/src/main/java/org/apache/maven/impl/model/DefaultInterpolator.java">
 * Apache Maven DefaultInterpolator</a>, licensed under Apache License 2.0.
 */
final class NisseInterpolator {

    private static final char ESCAPE_CHAR = '\\';
    private static final String DELIM_START = "${";
    private static final String DELIM_STOP = "}";
    private static final String MARKER = "$__";

    private NisseInterpolator() {}

    /**
     * Performs property variable substitution on the specified value.
     * <p>
     * If the specified value contains the syntax {@code ${<prop-name>}},
     * where {@code <prop-name>} refers to a property in {@code configProps}
     * or resolvable via {@code callback}, then the corresponding value is
     * substituted.  Multiple and nested variable placeholders are supported;
     * nested placeholders are resolved from innermost to outermost.
     *
     * @param val            the string on which to perform substitution
     * @param currentKey     the key of the property being evaluated (cycle detection)
     * @param cycleMap       set of variable references for cycle detection (may be {@code null})
     * @param configProps    configuration properties for lookups (may be {@code null})
     * @param callback       callback for external lookups (may be {@code null})
     * @param defaultsToEmptyString if {@code true}, unresolvable variables become empty strings;
     *                              otherwise they are left as-is
     * @return the value after substitution
     * @throws IllegalArgumentException on recursive variable references
     */
    public static String substVars(
            String val,
            String currentKey,
            Set<String> cycleMap,
            Map<String, String> configProps,
            UnaryOperator<String> callback,
            boolean defaultsToEmptyString) {
        return unescape(doSubstVars(val, currentKey, cycleMap, configProps, callback, defaultsToEmptyString));
    }

    private static String doSubstVars(
            String val,
            String currentKey,
            Set<String> cycleMap,
            Map<String, String> configProps,
            UnaryOperator<String> callback,
            boolean defaultsToEmptyString) {
        if (val == null || val.isEmpty()) {
            return val;
        }
        if (cycleMap == null) {
            cycleMap = new HashSet<>();
        }

        // Put the current key in the cycle map.
        if (currentKey != null) {
            cycleMap.add(currentKey);
        }

        // Assume we have a value that is something like:
        // "leading ${foo.${bar}} middle ${baz} trailing"

        // Find the first ending '}' variable delimiter, which
        // will correspond to the first deepest nested variable
        // placeholder.
        int startDelim;
        int stopDelim = -1;
        do {
            stopDelim = val.indexOf(DELIM_STOP, stopDelim + 1);
            while (stopDelim > 0 && val.charAt(stopDelim - 1) == ESCAPE_CHAR) {
                stopDelim = val.indexOf(DELIM_STOP, stopDelim + 1);
            }

            // Find the matching starting "${" variable delimiter
            // by looping until we find a start delimiter that is
            // greater than the stop delimiter we have found.
            startDelim = val.indexOf(DELIM_START);
            while (stopDelim >= 0) {
                int idx = val.indexOf(DELIM_START, startDelim + DELIM_START.length());
                if ((idx < 0) || (idx > stopDelim)) {
                    break;
                } else if (idx < stopDelim) {
                    startDelim = idx;
                }
            }
        } while (startDelim >= 0 && stopDelim >= 0 && stopDelim < startDelim + DELIM_START.length());

        // If we do not have a start or stop delimiter, then just
        // return the existing value.
        if ((startDelim < 0) || (stopDelim < 0)) {
            cycleMap.remove(currentKey);
            return val;
        }

        // At this point, we have found a variable placeholder so
        // we must perform a variable substitution on it.
        // Using the start and stop delimiter indices, extract
        // the first, deepest nested variable placeholder.
        String variable = val.substring(startDelim + DELIM_START.length(), stopDelim);

        String substValue = processSubstitution(variable, cycleMap, configProps, callback, defaultsToEmptyString);

        // Append the leading characters, the substituted value of
        // the variable, and the trailing characters to get the new
        // value.
        val = val.substring(0, startDelim) + substValue + val.substring(stopDelim + DELIM_STOP.length());

        // Now perform substitution again, since there could still
        // be substitutions to make.
        val = doSubstVars(val, currentKey, cycleMap, configProps, callback, defaultsToEmptyString);

        cycleMap.remove(currentKey);

        // Return the value.
        return val;
    }

    private static String processSubstitution(
            String variable,
            Set<String> cycleMap,
            Map<String, String> configProps,
            UnaryOperator<String> callback,
            boolean defaultsToEmptyString) {

        // Process chained operators from left to right
        int startIdx = 0;
        String substValue = null;

        while (startIdx < variable.length()) {
            int idx1 = variable.indexOf(":-", startIdx);
            int idx2 = variable.indexOf(":+", startIdx);
            int idx = idx1 >= 0 ? idx2 >= 0 ? Math.min(idx1, idx2) : idx1 : idx2;

            if (idx < 0) {
                // No more operators, process the final variable
                if (substValue == null) {
                    String currentVar = variable.substring(startIdx);
                    substValue = resolveVariable(currentVar, cycleMap, configProps, callback, defaultsToEmptyString);
                }
                break;
            }

            // Get the current variable part before the operator
            String varPart = variable.substring(startIdx, idx);
            if (substValue == null) {
                substValue = resolveVariable(varPart, cycleMap, configProps, callback, defaultsToEmptyString);
            }

            // Find the end of the current operator's value
            int nextIdx1 = variable.indexOf(":-", idx + 2);
            int nextIdx2 = variable.indexOf(":+", idx + 2);
            int nextIdx = nextIdx1 >= 0 ? nextIdx2 >= 0 ? Math.min(nextIdx1, nextIdx2) : nextIdx1 : nextIdx2;

            String op = variable.substring(idx, idx + 2);
            String opValue = variable.substring(idx + 2, nextIdx >= 0 ? nextIdx : variable.length());

            // Process the operator value through substitution if it contains variables
            String processedOpValue =
                    doSubstVars(opValue, variable, cycleMap, configProps, callback, defaultsToEmptyString);

            // Apply the operator
            if (":+".equals(op)) {
                if (substValue != null && !substValue.isEmpty()) {
                    substValue = processedOpValue;
                    // Skip any remaining operators since we've made a decision
                    break;
                }
            } else if (":-".equals(op)) {
                if (substValue == null || substValue.isEmpty()) {
                    substValue = processedOpValue;
                    // Skip any remaining operators since we've made a decision
                    break;
                }
            } else {
                throw new IllegalArgumentException("Bad substitution operator in: ${" + variable + "}");
            }

            startIdx = nextIdx >= 0 ? nextIdx : variable.length();
        }

        if (substValue == null) {
            if (defaultsToEmptyString) {
                substValue = "";
            } else {
                substValue = MARKER + "{" + variable + "}";
            }
        }

        return substValue;
    }

    private static String resolveVariable(
            String variable,
            Set<String> cycleMap,
            Map<String, String> configProps,
            UnaryOperator<String> callback,
            boolean defaultsToEmptyString) {

        // Verify that this is not a recursive variable reference
        if (!cycleMap.add(variable)) {
            throw new IllegalArgumentException("recursive variable reference: " + variable);
        }

        String substValue = null;
        // Try configuration properties first
        if (configProps != null) {
            substValue = configProps.get(variable);
        }
        if (substValue == null && !variable.isEmpty() && callback != null) {
            String s1 = callback.apply(variable);
            substValue = doSubstVars(s1, variable, cycleMap, configProps, callback, defaultsToEmptyString);
        }

        // Remove the variable from cycle map
        cycleMap.remove(variable);
        return substValue;
    }

    /**
     * Performs substitution on all entries in the given map, mirroring
     * {@code DefaultInterpolator.interpolate(Map, UnaryOperator)}.
     */
    public static void substituteVars(Map<String, String> map, UnaryOperator<String> callback) {
        Map<String, String> snapshot = new HashMap<>(map);
        for (String name : new ArrayList<>(map.keySet())) {
            String value = map.get(name);
            map.put(name, substVars(value, name, null, snapshot, callback, false));
        }
    }

    /**
     * Escapes special characters in the given string to prevent unwanted interpolation.
     */
    public static String escape(String val) {
        if (val == null || val.isEmpty()) {
            return val;
        }
        return val.replace("$", MARKER);
    }

    /**
     * Unescapes previously escaped characters in the given string.
     */
    public static String unescape(String val) {
        if (val == null || val.isEmpty()) {
            return val;
        }
        // Fast path: if the string contains neither the escape marker ($__)
        // nor the escape char (\), there is nothing to unescape.
        if (val.indexOf(MARKER.charAt(0)) < 0 && val.indexOf(ESCAPE_CHAR) < 0) {
            return val;
        }
        if (val.contains(MARKER)) {
            val = val.replace(MARKER, "$");
        }
        int escape = val.indexOf(ESCAPE_CHAR);
        while (escape >= 0 && escape < val.length() - 1) {
            char c = val.charAt(escape + 1);
            if (c == '{' || c == '}') {
                val = val.substring(0, escape) + val.substring(escape + 1);
            }
            escape = val.indexOf(ESCAPE_CHAR, escape + 1);
        }
        return val;
    }
}
