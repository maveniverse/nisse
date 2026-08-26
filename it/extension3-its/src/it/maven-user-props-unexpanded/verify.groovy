/*
 * Copyright (c) 2023-2024 Maveniverse Org.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 */

// Verify that unexpanded export-subst placeholders in .mvn/maven-user.properties
// are silently skipped — the build must succeed without injecting the raw
// $Format:...$ value into user properties.

def mavenLogFile = new File(basedir, 'build.log')
assert mavenLogFile.exists() : "Maven log file does not exist"

def logContent = mavenLogFile.text

// The build must succeed
assert logContent.contains('BUILD SUCCESS') : "Build did not succeed"

// The unexpanded placeholder must NOT appear in nisse dump output
assert !logContent.contains('$Format:') :
    "Unexpanded \$Format:...\$ placeholder was injected — it should have been skipped"
