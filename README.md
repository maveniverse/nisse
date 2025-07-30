# Maveniverse Nisse

Requirements:
* Java: 8+
* Maven: 3.8.x+

Nisse is a suite of extensions and plugins for Maven 3 and Maven 4 that provides following:
* in Maven 3 "fixes" the CI Friendly version support, as out of the box implementation is flaky (allows you to deploy broken POMs).
* provides "property sources", aggregates properties got from them, it may rename/translate property keys, and publishes properties to Maven.
* provides drop-in-replacement for discontinued [OS Detector plugin](https://github.com/trustin/os-maven-plugin).
* is extensible, one can add new property sources as needed.

## Usage with Maven

There are 3 extensions: `extension3` meant to be used with Maven 3 exclusively (does not work in Maven 4), then
`extension4` meant to be used with Maven 4 exclusively (does not work in Maven 3), and finally `extension`, that
works in both, Maven 3 and Maven 4. Last is the recommended extension to be used. Use it like this:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<extensions>
    <extension>
        <groupId>eu.maveniverse.maven.nisse</groupId>
        <artifactId>extension</artifactId>
        <version>${version.nisse}</version>
    </extension>
</extensions>
```

Note: Nisse can be used as "plugin only" as well, but functionality in this case is limited ONLY to providing 
properties for interpolation (within a project).

Nisse can serve as OS Detector **drop in replacement**, just add Nisse as extension to your project and
specify `-Dnisse.compat.osDetector` on CLI or better, in `.mvn/maven.config` file. If this option present, 
Nisse will emit **same properties** as OS Detector did.

To check what Nisse injects, simplest command to use is Nisse dump:

```
$ mvn validate -N -Dnisse.dump
```

The `-N` is needed only if you are in root of some complex multi-module project.

## Implemented Sources

There are 4 sources provided out of the box:
* `file-source`: it reads up a Java Properties File from disk and publishes that
* `jgit-source`: it uses Eclipse JGit to get some git related data
* `mvn-source`: it provides major/minor/patch versions of currently used Maven (note: Maven 4 already provides this from core)
* `os-source`: heavily inspired by [OS Detector](https://github.com/trustin/os-maven-plugin) and made reusable

Look into ITs for usage examples.
