import java.util.regex.Matcher
import java.util.regex.Pattern

File buildLog = new File( basedir, 'build.log' )
assert buildLog.exists()
String buildLogString = buildLog.text

String placeholder = 'nisse.jgit.commit'
// assert buildLogString.contains (placeholder)

String search = placeholder + "="
int commitHashStart = buildLogString.indexOf(search) + search.length()
assert commitHashStart > -1
String commitHash = buildLogString.substring(commitHashStart, commitHashStart + 40)
assert commitHash != null
System.out.println(commitHash)

String[] paths = new String[]{
    "eu/maveniverse/maven/nisse/it/ci-friendly/ci-friendly/${commitHash}/ci-friendly-${commitHash}.pom",
    "eu/maveniverse/maven/nisse/it/ci-friendly/mod1/${commitHash}/mod1-${commitHash}.pom",
    "eu/maveniverse/maven/nisse/it/ci-friendly/mod2/${commitHash}/mod2-${commitHash}.pom"
}

for (String path : paths) {
    File pomPath = new File(localRepositoryPath, path)
    assert pomPath.exists()
    String pomXml = pomPath.text
    assert pomXml.contains(commitHash)
    assert !pomXml.contains(placeholder)
}