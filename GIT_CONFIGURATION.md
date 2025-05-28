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
4. Falls back to `0.0.1-{commitCount}` if no version tags found

#### `nisse.source.jgit.appendSnapshot`

**Default:** `true`

Controls whether to append `-SNAPSHOT` qualifier to dynamic versions when the current commit is not tagged.

**Examples:**
- `true`: `1.2.4-5-SNAPSHOT` (5 commits since v1.2.3 tag)
- `false`: `1.2.4-5` (no SNAPSHOT qualifier)

#### `nisse.source.jgit.useVersion`

Override dynamic version resolution with a specific version string.

**Example:** `nisse.source.jgit.useVersion=2.0.0-BETA`

## Version Tag Pattern

The JGit source recognizes semantic version tags matching the pattern:
- `refs/tags/v?((\\d+\\.\\d+\\.\\d+)(.*))`
- Supports optional `v` prefix: `v1.2.3` or `1.2.3`
- Supports additional qualifiers: `1.2.3-alpha`, `v2.0.0-RC1`

## Usage Examples

### Maven Command Line

```bash
# Enable dynamic versioning with ISO-8601 timestamps
mvn compile -Dnisse.source.jgit.dynamicVersion=true -Dnisse.source.jgit.dateFormat=iso8601

# Use custom date format
mvn compile -Dnisse.source.jgit.dateFormat=custom -Dnisse.source.jgit.dateFormat.pattern="yyyy/MM/dd HH:mm"

# Override version and disable SNAPSHOT
mvn compile -Dnisse.source.jgit.useVersion=1.0.0 -Dnisse.source.jgit.appendSnapshot=false
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

## Error Handling

- **Invalid date format**: Falls back to default `git` format with warning
- **Invalid custom pattern**: Falls back to default `git` format with warning
- **No Git repository**: Source is ignored (logged at debug level)
- **Git errors**: Logged and may cause build failure depending on configuration

## Complete Example

```xml
<properties>
    <!-- Enable all JGit features -->
    <nisse.source.jgit.dynamicVersion>true</nisse.source.jgit.dynamicVersion>
    <nisse.source.jgit.appendSnapshot>true</nisse.source.jgit.appendSnapshot>
    <nisse.source.jgit.dateFormat>iso8601</nisse.source.jgit.dateFormat>
</properties>
```

This configuration provides:
- `nisse.jgit.commit`: Latest commit hash
- `nisse.jgit.date`: ISO-8601 formatted timestamp in UTC
- `nisse.jgit.author`: Commit author
- `nisse.jgit.committer`: Commit committer  
- `nisse.jgit.dynamicVersion`: Semantic version with SNAPSHOT qualifier

## Integration with Maven

The JGit properties can be used in Maven builds:

```xml
<version>${nisse.jgit.dynamicVersion}</version>

<properties>
    <build.timestamp>${nisse.jgit.date}</build.timestamp>
    <build.commit>${nisse.jgit.commit}</build.commit>
</properties>
```
