/*
 * Copyright (c) 2023-2024 Maveniverse Org.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 */

// Verify that pre-expanded values from .mvn/nisse.properties are loaded
// and used as project version (simulates source-archive build without .git).

def mavenLogFile = new File(basedir, 'build.log')
assert mavenLogFile.exists() : "Maven log file does not exist"

def logContent = mavenLogFile.text

// The fallback value should be present in the nisse dump output
assert logContent.contains('nisse.jgit.dynamicVersion=1.2.3-fallback') :
    "Fallback property nisse.jgit.dynamicVersion=1.2.3-fallback not found in build log"

// The build must succeed
assert logContent.contains('BUILD SUCCESS') : "Build did not succeed"
