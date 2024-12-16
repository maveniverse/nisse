// check that property was set

def mavenLogFile = new File(basedir, 'build.log')
assert mavenLogFile.exists() : "Maven log file does not exist"

// Read the log file
def logContent = mavenLogFile.text

// Define a pattern to match the version output
def versionPattern = /org.eclipse.jgit.api.errors.NoHeadException: No HEAD exists and no explicit starting revision was specified/
def matcher = (logContent =~ versionPattern)
assert matcher.find() : "SCM repo should have no head/commits"
