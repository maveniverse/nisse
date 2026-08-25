/*
 * Copyright (c) 2023-2024 Maveniverse Org.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 */
try {
    // smoke test if we have a needed tools
    def gitVersion = "git --version".execute()
    gitVersion.consumeProcessOutput(System.out, System.out)
    gitVersion.waitFor()
    return gitVersion.exitValue() == 0
} catch (Exception e) {
    // some error occurs - we skip a test
    return false
}
