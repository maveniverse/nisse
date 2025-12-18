/*
 * Copyright (c) 2023-2024 Maveniverse Org.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 */
// check that property was set

def mavenLogFile = new File(basedir, 'build.log')
assert mavenLogFile.exists() : "Maven log file does not exist"

// Read the log file
def logContent = mavenLogFile.text

// Define a pattern to match the version output
def versionPattern = /\$\{nisse.jgit.dynamicVersion\}=(.+)/
def matcher = (logContent =~ versionPattern)
assert matcher.find() : "Version information not found in log file"

// Extract the version from the matched group
def actualVersion = matcher[0][1]

def expectedVersion = '0.1.0-1-SNAPSHOT'

assert actualVersion == expectedVersion : "Expected version '${expectedVersion}', but found '${actualVersion}'"
