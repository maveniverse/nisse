/*
 * Copyright (c) 2023-2026 Maveniverse Org.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 */
File buildLog = new File( basedir, 'build.log' )
assert buildLog.exists()
// script got configuration on stdin
assert buildLog.text =~ /(?m)^hello-maven-\d/
// script got session root as args[1]
assert buildLog.text.contains ('nisse.script.root=' + basedir.canonicalPath) || buildLog.text.contains ('nisse.script.root=' + basedir.absolutePath)
