/*
 * Copyright (c) 2023-2024 Maveniverse Org.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 */
import java.util.regex.Matcher
import java.util.regex.Pattern

File buildLog = new File( basedir, 'build.log' )
assert buildLog.exists()
String buildLogString = buildLog.text
assert buildLogString.contains ('Nisse injecting 3 properties into User Properties')

String[] paths = new String[]{
    "eu/maveniverse/maven/nisse/it/configured-inline/configured-inline/1.0/configured-inline-1.0.pom",
        "eu/maveniverse/maven/nisse/it/configured-inline/mod1/1.0/mod1-1.0.pom",
        "eu/maveniverse/maven/nisse/it/configured-inline/mod2/1.0/mod2-1.0.pom",
}

for (String path : paths) {
    File pomPath = new File(localRepositoryPath, path)
    assert pomPath.exists()
    String pomXml = pomPath.text
    assert !pomXml.contains('${nisse.file.')
}