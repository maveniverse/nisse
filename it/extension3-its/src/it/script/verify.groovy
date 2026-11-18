/*
 * Copyright (c) 2023-2026 Maveniverse Org.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 */
File buildLog = new File( basedir, 'build.log' )
assert buildLog.exists()
// script got configuration on stdin: user property from maven.config, and Maven provided property
assert buildLog.text =~ /(?m)^hello-from-config-maven-\d/
// script got session root as args[1]: compare complete value
def root = (buildLog.text =~ /nisse\.script\.root=(.*)/)
assert root.find()
String rootValue = root.group(1).trim()
assert rootValue == basedir.canonicalPath || rootValue == basedir.absolutePath
