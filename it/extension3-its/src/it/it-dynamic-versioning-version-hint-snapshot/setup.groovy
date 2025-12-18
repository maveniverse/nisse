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

// Create a version hint tag for 4.1.0
exec('git tag 4.1.0-SNAPSHOT')

// Add another commit to move HEAD away from the tag
testFile << '\nmore content'
exec('git add test.txt')
exec('git commit -m second-commit')

// Create another version hint tag for 4.2.0 (should be the highest)
exec('git tag 4.2.0-SNAPSHOT')

// Add one more commit
testFile << '\neven more content'
exec('git add test.txt')
exec('git commit -m third-commit')
