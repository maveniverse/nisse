/*
 * Copyright (c) 2023-2024 Maveniverse Org.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 */

// Verify that ${session.rootDirectory} in .mvn/maven-user.properties is
// interpolated by NisseConfigurationProcessor (matching Maven 4 behaviour).
// The resolved value must contain the real path, not the literal placeholder.

File buildLog = new File(basedir, 'build.log')
assert buildLog.exists()
String buildLogText = buildLog.text

// The build must succeed
assert buildLogText.contains('BUILD SUCCESS') : 'Build did not succeed'

// help:evaluate must show the interpolated path, not the raw placeholder.
// The resolved value should end with /some-path and NOT contain ${session.rootDirectory}.
assert buildLogText.contains('/some-path') :
    'Interpolated path suffix /some-path not found in help:evaluate output'
assert !buildLogText.contains('${session.rootDirectory}') :
    '${session.rootDirectory} was not interpolated — it should have been resolved to an actual path'
