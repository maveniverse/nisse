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

// Create version hint tags using custom pattern "hint-${version}"
exec('git tag hint-3.1.0')

// Add another commit
testFile << '\nmore content'
exec('git add test.txt')
exec('git commit -m second-commit')

// Create another version hint tag (should be the highest)
exec('git tag hint-3.2.0')

// Create a regular SNAPSHOT tag that should be ignored with custom pattern
exec('git tag 5.0.0-SNAPSHOT')

// Add one more commit
testFile << '\neven more content'
exec('git add test.txt')
exec('git commit -m third-commit')
