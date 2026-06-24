/*
 * Copyright (c) 2023-2026 Maveniverse Org.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 */
module eu.maveniverse.maven.nisse.source.jgit {
    requires java.base;
    requires transitive eu.maveniverse.maven.nisse.core;

    requires org.slf4j;
    requires org.apache.maven.resolver;
    requires org.apache.maven.resolver.util;
    requires org.eclipse.jgit;

    exports eu.maveniverse.maven.nisse.source.jgit;
}