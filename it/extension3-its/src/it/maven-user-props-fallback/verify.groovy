/*
 * Copyright (c) 2023-2024 Maveniverse Org.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 */

// Verify that nisse.jgit.dynamicVersion from .mvn/maven-user.properties is
// loaded by NisseConfigurationProcessor (Maven 3 compatibility) and accepted
// by NisseModelVersionProcessor as a valid <version> expression.
// This simulates a source-archive build where git export-subst expanded the
// placeholder but there is no .git directory for the jgit source.

def mavenLogFile = new File(basedir, 'build.log')
assert mavenLogFile.exists() : "Maven log file does not exist"

def logContent = mavenLogFile.text

// The build must succeed — without the fix, ModelVersionProcessor rejects the
// property with "'version' must be a constant version".
assert logContent.contains('BUILD SUCCESS') : "Build did not succeed"
