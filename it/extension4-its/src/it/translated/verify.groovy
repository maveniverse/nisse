/*
 * Copyright (c) 2023-2024 Maveniverse Org.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 */
File buildLog = new File( basedir, 'build.log' )
assert buildLog.exists()
assert buildLog.text.contains ('os.detected.name')
assert buildLog.text.contains ('os.detected.arch')
assert buildLog.text.contains ('os.detected.bitness')
assert buildLog.text.contains ('os.detected.classifier')

// alt keys
assert buildLog.text.contains ('something.else.name')
assert buildLog.text.contains ('another.arch')

// falback applied as well to some
assert buildLog.text.contains ('nisse.os.bitness')

// removed
assert !buildLog.text.contains ('nisse.os.version.minor')
