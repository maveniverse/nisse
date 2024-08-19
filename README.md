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

## Usage

Add this to your `.mvn/extensions.xml` file:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<extensions>
    <extension>
        <groupId>eu.maveniverse.maven.nisse</groupId>
        <artifactId>extension3</artifactId>
        <version>0.1.0</version>
    </extension>
</extensions>
```

And then you can inspect what is being added to user properties by running Toolbox `dump`:

```
$ mvn eu.maveniverse.maven.plugins:toolbox:dump -Dverbose -N
```

Look for "USER PROPERTIES" section. Nisse injected some properties for you as they were user properties.
Moreover, you are free to use them as version (ie. `<version>${nisse.jgit.commit}</version>`) and look
what happens. Oh, and just install/deploy as usual, no need for any mumbo-jumbo.
