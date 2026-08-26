/*
 * Copyright (c) 2023-2024 Maveniverse Org.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 */

// Verify that a nisse.* property containing an unresolved ${…} placeholder
// causes the build to fail with a clear error message.

def mavenLogFile = new File(basedir, 'build.log')
assert mavenLogFile.exists() : "Maven log file does not exist"

def logContent = mavenLogFile.text

// The build must fail — the exception fires during ConfigurationProcessor
// (before the lifecycle starts), so Maven prints "Error executing Maven."
// rather than the usual "BUILD FAILURE" banner.
assert logContent.contains('Error executing Maven') : "Build should have failed"

// The error message must mention the problematic property and the unresolved placeholder
assert logContent.contains('unresolved placeholder') :
    "Error message should mention 'unresolved placeholder'"
assert logContent.contains('nisse.jgit.dynamicVersion') :
    "Error message should mention the property name"
