/*
 * Copyright (c) 2023-2024 Maveniverse Org.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 */
void exec(String command) {
    def proc = command.execute(null, basedir)
    proc.consumeProcessOutput(System.out, System.out)
    proc.waitFor()
    assert proc.exitValue() == 0 : "Command '${command}' returned status: ${proc.exitValue()}"
}

def testFile = new File(basedir, 'test.txt')
testFile << 'content'

exec('git init')
exec('git config user.email "you@example.com"')
exec('git config user.name "Your Name"')

exec('git add test.txt')
exec('git commit -m initial-commit')

// Create an old version hint tag (like the issue describes: 0.9.2-SNAPSHOT from a year ago)
exec('git tag 0.9.2-SNAPSHOT')

// Add more commits to simulate time passing
testFile << '\nmore content'
exec('git add test.txt')
exec('git commit -m second-commit')

testFile << '\neven more content'
exec('git add test.txt')
exec('git commit -m third-commit')

// Create a much newer release tag (like the issue describes: 0.13.0 is the latest)
exec('git tag 0.13.0')

// Add one more commit to move HEAD away from the release tag
testFile << '\nfinal content'
exec('git add test.txt')
exec('git commit -m fourth-commit')

// List all tags for debugging
exec('git tag')
