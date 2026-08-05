/*
 * Copyright (c) 2023-2024 Maveniverse Org.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 */
package eu.maveniverse.maven.nisse.extension3.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class NissePropertyKeysTest {

    @Test
    void emptyRegistryContainsNothing() {
        NissePropertyKeys keys = new NissePropertyKeys();
        assertFalse(keys.containsKey("nisse.jgit.dynamicVersion"));
        assertNull(keys.get("nisse.jgit.dynamicVersion"));
        assertTrue(keys.getAll().isEmpty());
    }

    @Test
    void putAllRegistersProperties() {
        NissePropertyKeys keys = new NissePropertyKeys();
        Map<String, String> props = new HashMap<>();
        props.put("nisse.jgit.dynamicVersion", "4.3.1");
        props.put("nisse.jgit.commit", "abc123");

        keys.putAll(props);

        assertTrue(keys.containsKey("nisse.jgit.dynamicVersion"));
        assertEquals("4.3.1", keys.get("nisse.jgit.dynamicVersion"));
        assertTrue(keys.containsKey("nisse.jgit.commit"));
        assertEquals("abc123", keys.get("nisse.jgit.commit"));
        assertFalse(keys.containsKey("nisse.jgit.nonexistent"));
    }

    @Test
    void getAllReturnsSnapshot() {
        NissePropertyKeys keys = new NissePropertyKeys();
        Map<String, String> props = new HashMap<>();
        props.put("nisse.os.name", "linux");
        keys.putAll(props);

        Map<String, String> all = keys.getAll();
        assertEquals(1, all.size());
        assertEquals("linux", all.get("nisse.os.name"));
    }

    @Test
    void putAllMergesWithExisting() {
        NissePropertyKeys keys = new NissePropertyKeys();
        Map<String, String> first = new HashMap<>();
        first.put("nisse.jgit.dynamicVersion", "1.0.0");
        keys.putAll(first);

        Map<String, String> second = new HashMap<>();
        second.put("nisse.os.name", "linux");
        second.put("nisse.jgit.dynamicVersion", "2.0.0"); // overwrites
        keys.putAll(second);

        assertEquals("2.0.0", keys.get("nisse.jgit.dynamicVersion"));
        assertEquals("linux", keys.get("nisse.os.name"));
        assertEquals(2, keys.getAll().size());
    }
}
