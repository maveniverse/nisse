/*
 * Copyright (c) 2023-2024 Maveniverse Org.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 */
File buildLog = new File( basedir, 'build.log' )
assert buildLog.exists()
assert buildLog.text.contains ('[INFO]  * eu.maveniverse.maven.nisse.it.mmi:multi-module-inline needs inlining')
assert buildLog.text.contains ('[INFO]  * eu.maveniverse.maven.nisse.it.mmi:mod1 needs inlining')
assert buildLog.text.contains ('[INFO]  * eu.maveniverse.maven.nisse.it.mmi:mod2 needs inlining')
