/*
 * Copyright (c) 2023-2024 Maveniverse Org.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 */

// Verify that empty values in .mvn/nisse.properties are skipped with a warning
// after the empty-value guard added in #194.

def mavenLogFile = new File(basedir, 'build.log')
assert mavenLogFile.exists() : "Maven log file does not exist"

def logContent = mavenLogFile.text

// The empty-value guard (Fix #194) skips blank fallback properties and logs
// a warning.  The property must NOT appear in the nisse dump output.
assert !logContent.contains('nisse.jgit.dynamicVersion=') :
    "Empty fallback property nisse.jgit.dynamicVersion should have been skipped"

// A warning should be logged about the skipped empty property
assert logContent.contains('Skipping empty fallback property') :
    "Expected a warning about skipping the empty fallback property"

// The build must succeed (POM uses a hardcoded version, not the empty property)
assert logContent.contains('BUILD SUCCESS') : "Build did not succeed"
