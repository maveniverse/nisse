/*
 * Copyright (c) 2023-2024 Maveniverse Org.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 */

// Verifies that Nisse-computed properties are injected even when
// .mvn/maven-user.properties exists with a stale value. Maven 3 does NOT
// auto-load maven-user.properties into user properties, so the containsKey
// guard in extension3 will not find the key and Nisse injects its computed
// value normally.
//
// We verify via help:evaluate output (the actual merged effective property),
// not via -Dnisse.dump (which only shows what Nisse computed, not what won).

File buildLog = new File( basedir, 'build.log' )
assert buildLog.exists()
String buildLogText = buildLog.text

// help:evaluate must resolve nisse.file.one to the Nisse-computed value
assert buildLogText.contains( 'fresh-computed' ) : \
    'help:evaluate did not resolve nisse.file.one to "fresh-computed"'
