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

File buildLog = new File( basedir, 'build.log' )
assert buildLog.exists()
String buildLogText = buildLog.text

// The Nisse file source must have injected its computed value
assert buildLogText.contains( 'nisse.file.one=fresh-computed' ) : \
    'Nisse-computed value "fresh-computed" not found in build log'

// The stale placeholder from maven-user.properties must NOT appear as the resolved value
assert !buildLogText.contains( 'nisse.file.one=stale-placeholder' ) : \
    'Stale placeholder from maven-user.properties leaked into Nisse properties'
