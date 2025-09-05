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

// Should use the higher release tag version (0.13.1-1-SNAPSHOT) instead of the old hint tag (0.9.2-SNAPSHOT)
// The version should be 0.13.1-1-SNAPSHOT because:
// - 0.13.0 is the latest release tag
// - We're 1 commit ahead of it, so patch gets incremented to 0.13.1
// - Build number is 1 (commits ahead)
// - SNAPSHOT qualifier is added because we're ahead
def expectedVersion = '0.13.1-1-SNAPSHOT'

assert actualVersion == expectedVersion : "Expected version '${expectedVersion}', but found '${actualVersion}'. The old version hint tag (0.9.2-SNAPSHOT) should not take precedence over the newer release tag (0.13.0)."
