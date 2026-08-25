/*
 * Copyright (c) 2023-2024 Maveniverse Org.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 */

// Verify nisse's current behavior with empty values in .mvn/nisse.properties:
// an empty string is not an unexpanded placeholder, so it IS loaded as-is.
// (See maveniverse/nisse#194 for future empty-version-guard discussion.)

def mavenLogFile = new File(basedir, 'build.log')
assert mavenLogFile.exists() : "Maven log file does not exist"

def logContent = mavenLogFile.text

// Empty value should be loaded (not rejected) — nisse treats it as a valid
// (albeit empty) property value.  The dump line ends with '=' and no value.
assert logContent.contains('nisse.jgit.dynamicVersion=') :
    "Empty fallback property nisse.jgit.dynamicVersion should have been loaded"

// The build must succeed (POM uses a hardcoded version, not the empty property)
assert logContent.contains('BUILD SUCCESS') : "Build did not succeed"
