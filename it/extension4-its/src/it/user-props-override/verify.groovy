/*
 * Copyright (c) 2023-2024 Maveniverse Org.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 */

// Verifies that Nisse-computed properties override values pre-loaded from
// .mvn/maven-user.properties. Maven 4 auto-loads that file before extensions
// run, so without the put (vs putIfAbsent) fix, the stale placeholder would
// persist and Nisse's computed value would be ignored.

File buildLog = new File( basedir, 'build.log' )
assert buildLog.exists()
String buildLogText = buildLog.text

// The Nisse file source must have injected its computed value
assert buildLogText.contains( 'nisse.file.one=fresh-computed' ) : \
    'Nisse-computed value "fresh-computed" not found in build log — put override failed'

// The stale placeholder from maven-user.properties must NOT appear as the resolved value
assert !buildLogText.contains( 'nisse.file.one=stale-placeholder' ) : \
    'Stale placeholder from maven-user.properties was not overridden by Nisse'
