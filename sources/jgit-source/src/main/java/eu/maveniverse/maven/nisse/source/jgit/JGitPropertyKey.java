/*
 * Copyright (c) 2023-2024 Maveniverse Org.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 */
package eu.maveniverse.maven.nisse.source.jgit;

import eu.maveniverse.maven.nisse.core.PropertyKey;
import java.util.Optional;

public class JGitPropertyKey extends PropertyKey {
    public JGitPropertyKey(JGitPropertyKeySource source, String key) {
        super(source, key);
    }

    @Override
    public Optional<String> getValue() {
        return Optional.ofNullable(((JGitPropertyKeySource) getSource()).getValue(getKey()));
    }
}
