/*
 * Copyright (c) 2023-2024 Maveniverse Org.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 */
package eu.maveniverse.maven.nisse.core;

import static java.util.Objects.requireNonNull;

import java.util.Objects;
import java.util.Optional;

/**
 * A property key originating from some source that can be evaluated. How it is evaluated (already has a value, is
 * "lazy", is caching values) is left to the implementation.
 */
public abstract class PropertyKey {
    private final PropertyKeySource source;
    private final String key;

    public PropertyKey(PropertyKeySource source, String key) {
        this.source = requireNonNull(source, "source");
        this.key = requireNonNull(key, "key");
    }

    public PropertyKeySource getSource() {
        return source;
    }

    public String getKey() {
        return key;
    }

    public abstract Optional<String> getValue();

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        PropertyKey that = (PropertyKey) o;
        return Objects.equals(source, that.source) && Objects.equals(key, that.key);
    }

    @Override
    public int hashCode() {
        return Objects.hash(source, key);
    }

    @Override
    public String toString() {
        return key;
    }
}
