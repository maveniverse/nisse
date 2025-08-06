// check that property was set

def mavenLogFile = new File(basedir, 'build.log')
assert mavenLogFile.exists() : "Maven log file does not exist"

// Read the log file
def logContent = mavenLogFile.text

// Define a pattern to match the version output
def versionPattern = /\$\{nisse.jgit.dynamicVersion\}=(.+)/
def matcher = (logContent =~ versionPattern)
assert matcher.find() : "Version information not found in log file"

// Extract the version from the matched group
def actualVersion = matcher[0][1]

// Should use the highest version hint tag with custom pattern (3.2.0) and add SNAPSHOT since we're ahead
// The 5.0.0-SNAPSHOT tag should be ignored because it doesn't match "hint-${version}" pattern
def expectedVersion = '3.2.0-SNAPSHOT'

assert actualVersion == expectedVersion : "Expected version '${expectedVersion}', but found '${actualVersion}'"
