# Git Configuration (JGit Source)

The JGit property source provides Git repository information and supports dynamic version generation based on Git tags and commits. This document covers all configuration options and features.

## Overview

The JGit source (`jgit`) extracts information from the Git repository and provides the following properties:

- `commit`: The SHA-1 hash of the latest commit
- `date`: The commit date (configurable format)
- `author`: The commit author information
- `committer`: The commit committer information
- `dynamicVersion`: Dynamically generated version based on Git tags (optional)

## Basic Properties

### Always Available Properties

| Property | Description | Example |
|----------|-------------|---------|
| `commit` | Latest commit SHA-1 hash | `a1b2c3d4e5f6789012345678901234567890abcd` |
| `date` | Commit timestamp | `Mon May 27 18:20:45 2024 +0200` (default format) |
| `author` | Commit author | `John Doe <john.doe@example.com>` |
| `committer` | Commit committer | `John Doe <john.doe@example.com>` |

## Configuration Properties

### Date Format Configuration

#### `nisse.source.jgit.dateFormat`

Controls the timestamp format for the `date` property.

**Supported values:**

- **`git`** (default): Traditional Git format
  - Pattern: `EEE MMM dd HH:mm:ss yyyy Z`
  - Example: `Mon May 27 18:20:45 2024 +0200`

- **`iso8601`**: ISO-8601 format in UTC
  - Pattern: `yyyy-MM-dd'T'HH:mm:ss'Z'`
  - Example: `2024-05-27T16:20:45Z`
  - Note: Automatically converts to UTC timezone

- **`iso8601-offset`**: ISO-8601 format with timezone offset
  - Pattern: `yyyy-MM-dd'T'HH:mm:ssXXX`
  - Example: `2024-05-27T18:20:45+02:00`

- **`custom`**: Use custom pattern specified by `nisse.source.jgit.dateFormat.pattern`

#### `nisse.source.jgit.dateFormat.pattern`

Custom date format pattern when `dateFormat` is set to `custom`. Uses Java `DateTimeFormatter` syntax.

**Examples:**
- `yyyy/MM/dd HH:mm` → `2024/05/27 18:20`
- `dd-MM-yyyy` → `27-05-2024`
- `HH:mm:ss` → `18:20:45`

### Dynamic Version Configuration

#### `nisse.source.jgit.dynamicVersion`

**Default:** `false`

Set to `true` to enable dynamic version generation. When enabled, adds the `dynamicVersion` property.

**Dynamic version logic:**
1. Searches Git history for semantic version tags (e.g., `v1.2.3`, `1.2.3`)
2. If current commit has a version tag, uses that version
3. If current commit doesn't have a tag, increments patch version and adds build number
4. Falls back to `0.1.0-{commitCount}` if no version tags found

#### `nisse.source.jgit.appendSnapshot`

**Default:** `true`

Controls whether to append `-SNAPSHOT` qualifier to dynamic versions when the current commit is not tagged.

**Examples:**
- `true`: `1.2.4-5-SNAPSHOT` (5 commits since v1.2.3 tag)
- `false`: `1.2.4-5` (no SNAPSHOT qualifier)

#### `nisse.source.jgit.useVersion`

Override dynamic version resolution with a specific version string.

**Example:** `nisse.source.jgit.useVersion=2.0.0-BETA`

#### `nisse.source.jgit.versionHintPattern`

**Default:** `${version}-SNAPSHOT`

Pattern for version hint tags that provide reproducible version planning without requiring CI environment changes. Version hint tags are processed before falling back to git history resolution.

**Pattern syntax:**
- Use `${version}` as placeholder for semantic version (e.g., `4.1.0`)
- Pattern is converted to regex for tag matching
- Supports optional `v` prefix on tags

**Common patterns:**
- `${version}-SNAPSHOT` (default): Matches tags like `4.1.0-SNAPSHOT`, `v4.2.0-SNAPSHOT`
- `hint-${version}`: Matches tags like `hint-4.1.0`, `hint-4.2.0`
- `next-${version}`: Matches tags like `next-3.1.0`, `next-3.2.0`
- `planned-${version}`: Matches tags like `planned-2.0.0`, `planned-2.1.0`

**Version resolution priority:**
1. **`useVersion` property** (highest priority) - explicit override
2. **Version hint tags** (new feature) - highest version from matching tags
3. **Git history resolution** (lowest priority) - existing behavior

**Benefits:**
- **Reproducible builds**: No CI environment changes needed
- **Version planning**: Easy maintenance branch and version bump planning
- **Clean workflow**: Hint tags become obsolete once real release tags are created
- **Flexible patterns**: Customize to match your team's workflow

## Version Tag Patterns

### Release Tags

The JGit source recognizes semantic version tags matching the pattern:
- `refs/tags/v?((\\d+\\.\\d+\\.\\d+)(.*))`
- Supports optional `v` prefix: `v1.2.3` or `1.2.3`
- Supports additional qualifiers: `1.2.3-alpha`, `v2.0.0-RC1`

### Version Hint Tags

Version hint tags provide a way to specify intended versions without modifying CI environments. They follow the configurable pattern set by `nisse.source.jgit.versionHintPattern`.

**Default pattern:** `${version}-SNAPSHOT`
- Matches: `4.1.0-SNAPSHOT`, `v4.2.0-SNAPSHOT`, `5.0.0-SNAPSHOT`
- Use case: Planning next version for maintenance branches

**Custom patterns:**
- `hint-${version}`: Matches `hint-4.1.0`, `hint-4.2.0`
- `next-${version}`: Matches `next-3.1.0`, `next-3.2.0`
- `planned-${version}`: Matches `planned-2.0.0`

## Usage Examples

### Maven Command Line

```bash
# Enable dynamic versioning with ISO-8601 timestamps
mvn compile -Dnisse.source.jgit.dynamicVersion=true -Dnisse.source.jgit.dateFormat=iso8601

# Use custom date format
mvn compile -Dnisse.source.jgit.dateFormat=custom -Dnisse.source.jgit.dateFormat.pattern="yyyy/MM/dd HH:mm"

# Override version and disable SNAPSHOT
mvn compile -Dnisse.source.jgit.useVersion=1.0.0 -Dnisse.source.jgit.appendSnapshot=false

# Use version hint tags with default pattern
mvn compile -Dnisse.source.jgit.dynamicVersion=true
# Looks for tags like: 4.1.0-SNAPSHOT, 4.2.0-SNAPSHOT

# Use version hint tags with custom pattern
mvn compile -Dnisse.source.jgit.dynamicVersion=true -Dnisse.source.jgit.versionHintPattern="hint-\${version}"
# Looks for tags like: hint-4.1.0, hint-4.2.0
```

### Maven Properties

```xml
<properties>
    <nisse.source.jgit.dynamicVersion>true</nisse.source.jgit.dynamicVersion>
    <nisse.source.jgit.dateFormat>iso8601</nisse.source.jgit.dateFormat>
    <nisse.source.jgit.appendSnapshot>false</nisse.source.jgit.appendSnapshot>
</properties>
```

### System Properties

```java
System.setProperty("nisse.source.jgit.dynamicVersion", "true");
System.setProperty("nisse.source.jgit.dateFormat", "iso8601");
```

## Version Hint Workflow Examples

### Planning a Maintenance Release

```bash
# Current state: working on maintenance branch from v4.0.0
git checkout -b maintenance/4.0.x

# Create version hint for next patch release
git tag 4.0.1-SNAPSHOT

# Build uses version 4.0.1 (from hint tag)
mvn clean install -Dnisse.source.jgit.dynamicVersion=true

# When ready to release, create actual release tag
git tag v4.0.1
# The hint tag is now ignored in favor of the release tag
```

### Planning a Minor Version Bump

```bash
# Working on feature branch, planning next minor version
git checkout -b feature/new-api

# Create version hint for next minor release
git tag 4.1.0-SNAPSHOT

# All builds on this branch use version 4.1.0
mvn clean install -Dnisse.source.jgit.dynamicVersion=true

# Multiple hint tags - highest version wins
git tag 4.2.0-SNAPSHOT  # This version will be used now

# When feature is complete and merged, create release tag
git tag v4.2.0
```

### Custom Pattern Workflow

```bash
# Configure custom pattern for your team
mvn clean install \
  -Dnisse.source.jgit.dynamicVersion=true \
  -Dnisse.source.jgit.versionHintPattern="next-\${version}"

# Create hint tags using your pattern
git tag next-3.1.0
git tag next-3.2.0  # Highest version used

# Regular SNAPSHOT tags are ignored with custom pattern
git tag 5.0.0-SNAPSHOT  # This won't be used

# Build uses version 3.2.0 (from next-3.2.0 tag)
```

## Error Handling

- **Invalid date format**: Falls back to default `git` format with warning
- **Invalid custom pattern**: Falls back to default `git` format with warning
- **No Git repository**: Source is ignored (logged at debug level)
- **Git errors**: Logged and may cause build failure depending on configuration

## Complete Configuration Examples

### Standard Configuration with Version Hints

```xml
<properties>
    <!-- Enable all JGit features -->
    <nisse.source.jgit.dynamicVersion>true</nisse.source.jgit.dynamicVersion>
    <nisse.source.jgit.appendSnapshot>true</nisse.source.jgit.appendSnapshot>
    <nisse.source.jgit.dateFormat>iso8601</nisse.source.jgit.dateFormat>
    <!-- Use default version hint pattern: ${version}-SNAPSHOT -->
</properties>
```

### Custom Version Hint Pattern

```xml
<properties>
    <nisse.source.jgit.dynamicVersion>true</nisse.source.jgit.dynamicVersion>
    <nisse.source.jgit.versionHintPattern>hint-${version}</nisse.source.jgit.versionHintPattern>
    <nisse.source.jgit.appendSnapshot>false</nisse.source.jgit.appendSnapshot>
</properties>
```

### Properties Provided

Both configurations provide:
- `nisse.jgit.commit`: Latest commit hash
- `nisse.jgit.date`: ISO-8601 formatted timestamp in UTC
- `nisse.jgit.author`: Commit author
- `nisse.jgit.committer`: Commit committer
- `nisse.jgit.dynamicVersion`: Semantic version (with version hint support)

## Integration with Maven

The JGit properties can be used in Maven builds:

```xml
<version>${nisse.jgit.dynamicVersion}</version>

<properties>
    <build.timestamp>${nisse.jgit.date}</build.timestamp>
    <build.commit>${nisse.jgit.commit}</build.commit>
</properties>
```
