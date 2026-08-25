/*
 * Copyright (c) 2023-2024 Maveniverse Org.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 */

// Verify that unexpanded git export-subst placeholders ($Format:...$) in
// .mvn/nisse.properties are rejected and not injected as user properties.
// This simulates a git checkout (not an archive) where the placeholders
// have not been expanded.

def mavenLogFile = new File(basedir, 'build.log')
assert mavenLogFile.exists() : "Maven log file does not exist"

def logContent = mavenLogFile.text

// Unexpanded $Format:...$ placeholders must NOT appear in the dump output
// as injected property values.
assert !logContent.contains('nisse.jgit.dynamicVersion=$Format:') :
    "Unexpanded placeholder for nisse.jgit.dynamicVersion should have been skipped"
assert !logContent.contains('nisse.jgit.date=$Format:') :
    "Unexpanded placeholder for nisse.jgit.date should have been skipped"

// The build must still succeed (POM uses a hardcoded version, not the missing property)
assert logContent.contains('BUILD SUCCESS') : "Build did not succeed"
