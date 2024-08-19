# Maveniverse Nisse

This is a near-trivial Maven 3 and 4 extension, that provides following services:
* sources properties from different sources and make them Maven User Properties
* redoes the [CI Friendly Versions](https://maven.apache.org/maven-ci-friendly.html) feature
* is able to "inline" properties, so there is no need to flatten or any other mumbo-jumbo, it "just works"

## Implemented Sources

Currently there are 4 sources just to showcase things:
* file-source: it reads up a Java Properties File
* jgit-source: it uses Eclipse JGit to get some git related data
* mvn-source: it provides major/minor/patch versions of currently use Maven
* os-source: heavily inspired by https://github.com/trustin/os-maven-plugin (and annoyed that user does not maintain it, so code is not reusable nor works with Maven4) 

Look into ITs for examples.