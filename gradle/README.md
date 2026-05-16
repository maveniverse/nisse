# Nisse Gradle Plugin

The Nisse Gradle plugin provides the same property-source functionality as the
[Maven extension](../README.md) but for Gradle builds. It discovers properties from
multiple sources (Git via JGit, OS detection) and makes them available to your build.

## Requirements

* Java 8+
* Gradle 8.x+

## Quick Start — Minimal Setup

Apply the plugin. All sources (jgit, os) are active by default:

```groovy
plugins {
    id("eu.maveniverse.gradle.plugins.nisse-gradle-plugin") version "${nisseVersion}"
}

tasks.register("showVersion") {
    doLast {
        println "OS:   ${project.nisse['nisse.os.name']}"
        println "Arch: ${project.nisse['nisse.os.arch']}"
        println "Git commit: ${project.nisse['nisse.jgit.commit']}"
    }
}
```

That's it — no further configuration required.

> **Note:** The `nisse` extension (a `Map<String, String>`) is populated during project
> evaluation, so access it inside `doLast { }` blocks, `afterEvaluate { }`, or other
> deferred contexts rather than at the top level of your build script.

## The `nisse` Extension

The plugin registers a `nisse` extension on the project. It is a `Map<String, String>`
containing all properties discovered by the active sources.

### Available Properties

#### OS Source (`nisse.os.*`)

| Property | Description |
|---|---|
| `nisse.os.name` | Detected OS name (`osx`, `linux`, `windows`, …) |
| `nisse.os.arch` | Detected architecture (`x86_64`, `aarch_64`, …) |
| `nisse.os.bitness` | Bit width (`32` or `64`) |
| `nisse.os.version` | OS kernel version (e.g. `14.5`) |
| `nisse.os.version.major` | Major part of the OS version |
| `nisse.os.version.minor` | Minor part of the OS version |
| `nisse.os.classifier` | Composite classifier (e.g. `osx-aarch_64`) |
| `nisse.os.release` | Linux distribution ID (Linux only) |
| `nisse.os.release.version` | Linux distribution version (Linux only) |
| `nisse.os.release.like.*` | Linux distribution compatibility (Linux only) |

#### JGit Source (`nisse.jgit.*`)

Always present when the project is inside a Git repository:

| Property | Description |
|---|---|
| `nisse.jgit.commit` | Full commit hash of HEAD |
| `nisse.jgit.shortCommitId` | Abbreviated commit hash (default 7 chars) |
| `nisse.jgit.date` | Commit timestamp |
| `nisse.jgit.author` | Author of HEAD commit |
| `nisse.jgit.committer` | Committer of HEAD commit |
| `nisse.jgit.clean` | `true` if the working tree is clean |
| `nisse.jgit.branchName` | Current branch name (if on a branch) |

Conditional properties (require explicit opt-in via `nisseConfig`):

| Property | Description |
|---|---|
| `nisse.jgit.dynamicVersion` | Version derived from Git tags (requires `dynamicVersion = true`) |
| `nisse.jgit.countingVersion` | Version derived from commit message directives (requires `countingVersion = true`) |

## Configuration via `nisseConfig` DSL

Use the `nisseConfig` block to configure property sources:

```groovy
plugins {
    id("eu.maveniverse.gradle.plugins.nisse-gradle-plugin") version "${nisseVersion}"
}

nisseConfig {
    jgit {
        dynamicVersion = true
        appendSnapshot = false
    }
}
```

### Deactivating a Source

```groovy
nisseConfig {
    jgit {
        active = false   // disable Git-based properties entirely
    }
    os {
        active = false   // disable OS detection properties
    }
}
```

### JGit Source Options

All options correspond to `nisse.source.jgit.*` system properties. When set via the DSL,
they take precedence over system properties.

```groovy
nisseConfig {
    jgit {
        // --- source activation ---
        active = true                        // default: true

        // --- general ---
        shortCommitIdLength = 10             // default: 7
        dateFormat = 'iso8601'               // 'git' (default), 'iso8601', 'iso8601-offset', 'custom'
        dateFormatPattern = 'yyyyMMdd'       // only used when dateFormat = 'custom'

        // --- dynamic version (tag-based) ---
        dynamicVersion = true                // default: false
        increasePatchVersion = true          // default: true
        appendBuildNumber = true             // default: true
        appendSnapshot = true                // default: true
        appendDirty = false                  // default: false
        dirtyQualifier = 'DIRTY'             // default: 'DIRTY'
        useVersion = '1.2.3'                 // override: bypass Git detection entirely
        versionHintPattern = '${version}-SNAPSHOT'  // default: '${version}-SNAPSHOT'

        // --- counting version (commit-message-based) ---
        countingVersion = true               // default: false
        countingStartMajor = 0               // default: 0
        countingStartMinor = 0               // default: 0
        countingStartPatch = 0               // default: 0
        countingMatchMajor = '[major]'       // default: '[major]'
        countingMatchMinor = '[minor]'       // default: '[minor]'
        countingMatchPatch = '[patch]'       // default: '[patch]'
        countingPattern = '%M.%m.%p(-%c)'    // default: '%M.%m.%p(-%c)'
    }
}
```

### OS Source Options

```groovy
nisseConfig {
    os {
        active = true   // default: true
    }
}
```

## Dynamic Version

When `dynamicVersion = true`, the jgit source derives a version from the most recent
semver Git tag (e.g. `v1.2.3` or `1.2.3`). If HEAD is not on a tag, the patch version
is incremented, a build number is appended, and optionally a `-SNAPSHOT` qualifier:

```text
v1.2.3  →  on tag: 1.2.3
         not on tag: 1.2.4-5-SNAPSHOT  (5 commits after tag)
```

Control the output with `appendSnapshot`, `appendBuildNumber`, `increasePatchVersion`,
`appendDirty`, and `dirtyQualifier`.

## Counting Version

When `countingVersion = true`, the jgit source walks the **entire** commit history
from oldest to newest and derives a version from commit message directives:

* A commit containing `[major]` → bumps major, resets minor/patch/count
* A commit containing `[minor]` → bumps minor, resets patch/count
* A commit containing `[patch]` → bumps patch, resets count
* Any other commit → increments the commit count

The default output pattern `%M.%m.%p(-%c)` produces versions like:

```text
0.0.0       (no commits)
0.0.0-3     (3 ordinary commits)
1.0.0       ([major] commit, no further commits)
1.1.0-2     ([major], then [minor], then 2 ordinary commits)
```

The pattern is configurable — for example, `%M.%m.%p(.%c)` uses dots instead of
dashes (compatible with [gradle-git-versioner](https://github.com/nickolay-kondratyev/gradle-git-versioner)).

### Example: Using Counting Version as Project Version

```groovy
plugins {
    id("eu.maveniverse.gradle.plugins.nisse-gradle-plugin") version "${nisseVersion}"
}

nisseConfig {
    jgit {
        countingVersion = true
    }
}

// nisse properties are available after evaluation
afterEvaluate {
    version = project.nisse['nisse.jgit.countingVersion'] ?: '0.0.0'
}
```

With a history like:
```text
commit 3: "bugfix"                → commitCount=1
commit 2: "[minor] add feature"   → minor=1, reset count
commit 1: "[major] initial"       → major=1, reset minor/patch/count
```

`project.version` resolves to `1.1.0-1`.

### Example: Counting Version with Custom Start and Pattern

```groovy
nisseConfig {
    jgit {
        countingVersion = true
        countingStartMajor = 2
        countingPattern = '%M.%m.%p(.%c)'   // dot-separated commit count
    }
}
```

## Dumping Properties

Use the built-in `nisseDump` task to inspect all discovered properties:

```text
> gradle nisseDump

> Task :nisseDump
Nisse dump:
nisse.os.name=osx
nisse.os.arch=aarch_64
nisse.os.bitness=64
nisse.os.classifier=osx-aarch_64
nisse.jgit.commit=abc1234...
nisse.jgit.shortCommitId=abc1234
nisse.jgit.clean=true
nisse.jgit.branchName=main
...
```

## System Property Fallback

All configuration options can also be set via system properties (e.g., on the command
line or in `gradle.properties`). The DSL takes precedence when both are set.

```properties
# gradle.properties
systemProp.nisse.source.jgit.dynamicVersion=true
systemProp.nisse.source.jgit.appendSnapshot=false
```

Or on the command line:

```shell
gradle build -Dnisse.source.jgit.dynamicVersion=true
```
