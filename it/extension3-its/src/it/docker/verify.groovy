/*
 * Copyright (c) 2023-2026 Maveniverse Org.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 */
File buildLog = new File( basedir, 'build.log' )
assert buildLog.exists()
String log = buildLog.text

// source ran: "present" is always published when source is active
def matcher = log =~ /nisse\.docker\.present=(true|false)/
assert matcher.find()
boolean present = Boolean.parseBoolean(matcher.group(1))
if (present) {
    assert log.contains('nisse.docker.host=')
}

// CI matrix OSes differ in having Docker; only assert what this script can see as well
if (System.getenv('DOCKER_HOST') == null && new File('/var/run/docker.sock').exists()) {
    assert present
}
