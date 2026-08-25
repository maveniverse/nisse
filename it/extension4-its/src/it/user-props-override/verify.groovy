/*
 * Copyright (c) 2023-2024 Maveniverse Org.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 */

// Verifies that Nisse-computed properties override unexpanded export-subst
// placeholders pre-loaded from .mvn/maven-user.properties. Maven 4 auto-loads
// that file before extensions run, populating user properties with the raw
// $Format:..$ value. The isUnexpandedPlaceholder() check must detect it and
// the merge loop must override it with the Nisse-computed value.
//
// We verify via help:evaluate output (the actual merged effective property),
// not via -Dnisse.dump (which only shows what Nisse computed, not what won).

File buildLog = new File( basedir, 'build.log' )
assert buildLog.exists()
String buildLogText = buildLog.text

// help:evaluate must resolve nisse.file.one to the Nisse-computed value
assert buildLogText.contains( 'fresh-computed' ) : \
    'help:evaluate did not resolve nisse.file.one to "fresh-computed" — placeholder override failed'

// The unexpanded export-subst placeholder must NOT appear as the resolved value
assert !buildLogText.contains( '%(describe:tags=true)' ) : \
    'Unexpanded $Format:..$ placeholder from maven-user.properties was not overridden'
