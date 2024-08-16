/*
 * Copyright (c) 2023-2024 Maveniverse Org.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 */
package eu.maveniverse.maven.nisse.core;

import static java.util.Objects.requireNonNull;

import java.util.Optional;

/**
 * A simple property key that is either already known, or is 'cheap' to calculate, and it can be prepared ahead of time.
 * It must have a value.
 */
public class KnownPropertyKey extends PropertyKey {
    private final String value;

    public KnownPropertyKey(PropertyKeySource source, String key, String value) {
        super(source, key);
        this.value = requireNonNull(value, "value");
    }

    @Override
    public Optional<String> getValue() {
        return Optional.of(value);
    }
}
