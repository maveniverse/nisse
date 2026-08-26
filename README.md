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

There are 3 extension artifacts:

| Artifact | Maven version | Notes |
|----------|---------------|-------|
| **`extension`** (recommended) | Maven 3 and Maven 4 | Universal artifact that works in both Maven versions. Use this unless you have a specific reason not to. |
| `extension3` | Maven 3 only | Does not work in Maven 4. |
| `extension4` | Maven 4 only | Does not work in Maven 3. |

Add the recommended `extension` artifact to `.mvn/extensions.xml`:

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
Note that this only works when Nisse is declared as a ["core extension"](https://maven.apache.org/guides/mini/guide-using-extensions.html) 
(Maven 3 and 4) through **.mvn/extensions.xml** or as a "user-wide extension" (Maven 4 only) through **~/.m2/extensions.xml**.
Otherwise, one may use `dump-properties` Mojo or the `nisse.dump` property of `inject-properties` Mojo. 

### Quick Start: Git-based dynamic versioning

Nisse can derive your project version from Git tags automatically, eliminating hardcoded version
strings from your POM. This works with both Maven 3 and Maven 4.

**1. `.mvn/extensions.xml`** — register Nisse as a core extension:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<extensions>
    <extension>
        <groupId>eu.maveniverse.maven.nisse</groupId>
        <artifactId>extension</artifactId>
        <version>0.9.8</version><!-- replace with latest Nisse version -->
    </extension>
</extensions>
```

**2. `.mvn/maven.config`** — enable dynamic versioning:

```text
-Dnisse.source.jgit.dynamicVersion=true
```

**3. `pom.xml`** — use the dynamic version property:

```xml
<version>${nisse.jgit.dynamicVersion}</version>
```

That's it. Nisse will compute the version from your Git history (tags, commits, branch) and inject
it at build time. Run `mvn validate -N -Dnisse.dump` to see the resolved value.

#### Using translation to map to `${revision}` (CI Friendly Versions)

If you prefer to use Maven's standard `${revision}` property (for example, to keep compatibility
with tools that expect CI Friendly Versions), add a translation file:

**`.mvn/nisse-translation.properties`:**

```properties
jgit.dynamicVersion=revision
```

This translates the Nisse key so the version is published as `${revision}` instead of
`${nisse.jgit.dynamicVersion}`. Your POM then uses:

```xml
<version>${revision}</version>
```

> **Note:** When using translation, make sure the POM references the *translated* property name
> (`${revision}`), not the original (`${nisse.jgit.dynamicVersion}`). Nisse will warn you if a
> translated property is still referenced by its original name.

## Usage with Gradle

Nisse is also available as a Gradle plugin. See the [Gradle Plugin documentation](gradle/README.md) for full details.

Minimal setup:

```groovy
plugins {
    id("eu.maveniverse.gradle.plugins.nisse-gradle-plugin") version "${nisseVersion}"
}

// Access discovered properties in tasks
tasks.register("showVersion") {
    doLast {
        println "OS: ${project.nisse['nisse.os.name']}"
        println "Git commit: ${project.nisse['nisse.jgit.commit']}"
    }
}
```

The plugin supports a `nisseConfig` DSL for configuring sources (dynamic version, counting
version, deactivating sources, etc.) — see the [Gradle README](gradle/README.md).

## Implemented Sources

There are 4 sources provided out of the box:
* `file-source`: it reads up a Java Properties File from disk and publishes that
* `jgit-source`: it uses Eclipse JGit to get some git related data
* `mvn-source`: it provides major/minor/patch versions of currently used Maven (note: Maven 4 already provides this from core)
* `os-source`: heavily inspired by [OS Detector](https://github.com/trustin/os-maven-plugin) and made reusable

Look into ITs for usage examples.
