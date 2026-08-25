/*
 * Copyright (c) 2023-2024 Maveniverse Org.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 */

// Verify that JGit-resolved values take priority over .mvn/nisse.properties
// fallback values when both are present.
// - nisse.properties has: nisse.jgit.dynamicVersion=0.0.1-fallback
// - JGit resolves from git tag v2.0.0: nisse.jgit.dynamicVersion=2.0.0
// JGit must win.

def mavenLogFile = new File(basedir, 'build.log')
assert mavenLogFile.exists() : "Maven log file does not exist"

def logContent = mavenLogFile.text

// JGit value (from tag v2.0.0) must override the fallback
assert logContent.contains('nisse.jgit.dynamicVersion=2.0.0') :
    "JGit-resolved version 2.0.0 should override fallback value"

// The fallback value must NOT be present
assert !logContent.contains('nisse.jgit.dynamicVersion=0.0.1-fallback') :
    "Fallback value 0.0.1-fallback should have been overridden by JGit"

// The build must succeed
assert logContent.contains('BUILD SUCCESS') : "Build did not succeed"
