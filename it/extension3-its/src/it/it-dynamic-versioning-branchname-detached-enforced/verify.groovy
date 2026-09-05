/*
 * Copyright (c) 2023-2026 Maveniverse Org.
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

// build failed, we should have the error in output
assert logContent.contains('Branch name configured to be qualifier, but is absent')
