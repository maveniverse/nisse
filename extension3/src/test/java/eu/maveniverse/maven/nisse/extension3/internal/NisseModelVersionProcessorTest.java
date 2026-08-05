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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collections;
import java.util.Properties;
import javax.inject.Provider;
import org.apache.maven.execution.DefaultMavenExecutionRequest;
import org.apache.maven.execution.DefaultMavenExecutionResult;
import org.apache.maven.execution.MavenExecutionRequest;
import org.apache.maven.execution.MavenSession;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.junit.jupiter.api.Test;

class NisseModelVersionProcessorTest {

    /**
     * Simulates the scenario from issue #179: no .git directory, but the property
     * {@code nisse.jgit.dynamicVersion} is available from a file source (export-subst,
     * maven-user.properties). The ModelVersionProcessor must accept it.
     */
    @Test
    void isValidProperty_acceptsPropertyFromNissePropertyKeys() {
        NissePropertyKeys keys = new NissePropertyKeys();
        keys.putAll(Collections.singletonMap("nisse.jgit.dynamicVersion", "4.3.1"));

        // Session provider that always throws (simulating session not yet available)
        Provider<MavenSession> noSession = () -> {
            throw new IllegalStateException("No session available");
        };

        NissePropertyInliner inliner = new NissePropertyInliner();
        NisseModelVersionProcessor processor = new NisseModelVersionProcessor(noSession, inliner, keys);

        assertTrue(processor.isValidProperty("nisse.jgit.dynamicVersion"));
    }

    /**
     * Properties not starting with {@code nisse.} are always rejected.
     */
    @Test
    void isValidProperty_rejectsNonNisseProperty() {
        NissePropertyKeys keys = new NissePropertyKeys();
        Provider<MavenSession> noSession = () -> {
            throw new IllegalStateException("No session available");
        };
        NissePropertyInliner inliner = new NissePropertyInliner();
        NisseModelVersionProcessor processor = new NisseModelVersionProcessor(noSession, inliner, keys);

        assertFalse(processor.isValidProperty("project.version"));
        assertFalse(processor.isValidProperty("revision"));
    }

    /**
     * Properties not registered in NissePropertyKeys but present in session user properties
     * (e.g. via {@code -D}) should still be accepted.
     */
    @Test
    void isValidProperty_acceptsPropertyFromSessionUserProperties() {
        NissePropertyKeys keys = new NissePropertyKeys();
        // keys is empty — property not from any nisse source

        MavenExecutionRequest executionRequest = new DefaultMavenExecutionRequest();
        executionRequest.getUserProperties().setProperty("nisse.custom.prop", "value");

        DefaultRepositorySystemSession repoSession = new DefaultRepositorySystemSession();
        MavenSession session = new MavenSession(null, repoSession, executionRequest, new DefaultMavenExecutionResult());
        Provider<MavenSession> sessionProvider = () -> session;

        NissePropertyInliner inliner = new NissePropertyInliner();
        NisseModelVersionProcessor processor = new NisseModelVersionProcessor(sessionProvider, inliner, keys);

        assertTrue(processor.isValidProperty("nisse.custom.prop"));
    }

    /**
     * A nisse-prefixed property that exists neither in NissePropertyKeys nor in session
     * user properties should be rejected.
     */
    @Test
    void isValidProperty_rejectsUnknownNisseProperty() {
        NissePropertyKeys keys = new NissePropertyKeys();

        MavenExecutionRequest executionRequest = new DefaultMavenExecutionRequest();
        DefaultRepositorySystemSession repoSession = new DefaultRepositorySystemSession();
        MavenSession session = new MavenSession(null, repoSession, executionRequest, new DefaultMavenExecutionResult());
        Provider<MavenSession> sessionProvider = () -> session;

        NissePropertyInliner inliner = new NissePropertyInliner();
        NisseModelVersionProcessor processor = new NisseModelVersionProcessor(sessionProvider, inliner, keys);

        assertFalse(processor.isValidProperty("nisse.jgit.nonexistent"));
    }

    /**
     * Simulates the full scenario from issue #179:
     * <ol>
     *   <li>isValidProperty is called with no session (during early model validation)</li>
     *   <li>The property is found in NissePropertyKeys and accepted</li>
     *   <li>Later, overwriteModelProperties is called with a session available</li>
     *   <li>The deferred key is flushed and the value is taken from NissePropertyKeys</li>
     * </ol>
     */
    @Test
    void overwriteModelProperties_handlesDeferredInlinedKeys() {
        NissePropertyKeys keys = new NissePropertyKeys();
        keys.putAll(Collections.singletonMap("nisse.jgit.dynamicVersion", "4.3.1"));

        NissePropertyInliner inliner = new NissePropertyInliner();

        // Phase 1: isValidProperty called when session is NOT available
        Provider<MavenSession> noSession = () -> {
            throw new IllegalStateException("No session available");
        };
        NisseModelVersionProcessor processor = new NisseModelVersionProcessor(noSession, inliner, keys);
        assertTrue(processor.isValidProperty("nisse.jgit.dynamicVersion"));

        // Phase 2: overwriteModelProperties called when session IS available
        MavenExecutionRequest executionRequest = new DefaultMavenExecutionRequest();
        DefaultRepositorySystemSession repoSession = new DefaultRepositorySystemSession();
        MavenSession session = new MavenSession(null, repoSession, executionRequest, new DefaultMavenExecutionResult());

        // Replace the session provider with a working one via a new processor that shares state.
        // Since NisseModelVersionProcessor is a singleton, in production the same instance is
        // called in both phases. We simulate this by using the same processor instance
        // and providing a session that works for overwriteModelProperties.
        // We need to use reflection or a workaround since the provider is final.
        // Instead, let's create a mutable provider:
        MutableSessionProvider mutableProvider = new MutableSessionProvider();
        NisseModelVersionProcessor processor2 = new NisseModelVersionProcessor(mutableProvider, inliner, keys);

        // Phase 1: no session — isValidProperty defers the key
        assertTrue(processor2.isValidProperty("nisse.jgit.dynamicVersion"));

        // Phase 2: session becomes available
        mutableProvider.setSession(session);

        Properties modelProperties = new Properties();
        processor2.overwriteModelProperties(modelProperties, null);

        assertTrue(modelProperties.containsKey("nisse.jgit.dynamicVersion"));
        assertEquals("4.3.1", modelProperties.getProperty("nisse.jgit.dynamicVersion"));
    }

    /**
     * When both session and NissePropertyKeys have the value, session takes precedence.
     */
    @Test
    void overwriteModelProperties_sessionTakesPrecedence() {
        NissePropertyKeys keys = new NissePropertyKeys();
        keys.putAll(Collections.singletonMap("nisse.jgit.dynamicVersion", "from-file"));

        MavenExecutionRequest executionRequest = new DefaultMavenExecutionRequest();
        executionRequest.getUserProperties().setProperty("nisse.jgit.dynamicVersion", "from-session");

        DefaultRepositorySystemSession repoSession = new DefaultRepositorySystemSession();
        MavenSession session = new MavenSession(null, repoSession, executionRequest, new DefaultMavenExecutionResult());
        Provider<MavenSession> sessionProvider = () -> session;

        NissePropertyInliner inliner = new NissePropertyInliner();
        NisseModelVersionProcessor processor = new NisseModelVersionProcessor(sessionProvider, inliner, keys);

        assertTrue(processor.isValidProperty("nisse.jgit.dynamicVersion"));

        Properties modelProperties = new Properties();
        processor.overwriteModelProperties(modelProperties, null);

        assertEquals("from-session", modelProperties.getProperty("nisse.jgit.dynamicVersion"));
    }

    /**
     * A mutable Provider that can switch between "no session" and "session available"
     * states, simulating the Maven lifecycle where the session becomes available after
     * model validation.
     */
    private static class MutableSessionProvider implements Provider<MavenSession> {
        private volatile MavenSession session;

        void setSession(MavenSession session) {
            this.session = session;
        }

        @Override
        public MavenSession get() {
            MavenSession s = session;
            if (s == null) {
                throw new IllegalStateException("No session available");
            }
            return s;
        }
    }
}
