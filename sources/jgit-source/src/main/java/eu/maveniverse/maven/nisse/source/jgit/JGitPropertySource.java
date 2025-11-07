/*
 * Copyright (c) 2023-2024 Maveniverse Org.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 */
package eu.maveniverse.maven.nisse.source.jgit;

import eu.maveniverse.maven.nisse.core.NisseConfiguration;
import eu.maveniverse.maven.nisse.core.PropertySource;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import javax.inject.Named;
import javax.inject.Singleton;
import org.eclipse.aether.util.version.GenericVersionScheme;
import org.eclipse.aether.version.InvalidVersionSpecificationException;
import org.eclipse.aether.version.Version;
import org.eclipse.aether.version.VersionScheme;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.NoHeadException;
import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A source using JGit to get some Git info.
 */
@Singleton
@Named(JGitPropertySource.NAME)
public class JGitPropertySource implements PropertySource {

    public static final String NAME = "jgit";

    private static final String JGIT_COMMIT = "commit";

    private static final String JGIT_DATE = "date";

    private static final String JGIT_AUTHOR = "author";

    private static final String JGIT_COMMITTER = "committer";

    private static final String JGIT_DYNAMIC_VERSION = "dynamicVersion";

    /**
     * Set to {@code true} to enable "dynamic version" feature, it adds the
     * {@link #JGIT_DYNAMIC_VERSION} property to resulting properties.
     *
     */
    private static final String JGIT_CONF_SYSTEM_PROPERTY_DYNAMIC_VERSION = "nisse.source.jgit.dynamicVersion";

    private static final String DEFAULT_DYNAMIC_VERSION = Boolean.FALSE.toString();

    /**
     * Whether the buildNumber shall be appended or not.
     *
     */
    private static final String JGIT_CONF_SYSTEM_PROPERTY_APPEND_BUILD_NUMBER = "nisse.source.jgit.appendBuildNumber";

    private static final String DEFAULT_APPEND_BUILD_NUMBER = Boolean.TRUE.toString();

    /**
     * Whether the SNAPSHOT qualifier shall be appended or not.
     *
     */
    private static final String JGIT_CONF_SYSTEM_PROPERTY_APPEND_SNAPSHOT = "nisse.source.jgit.appendSnapshot";

    private static final String DEFAULT_APPEND_SNAPSHOT = Boolean.TRUE.toString();

    /**
     * Use this version instead of resolving from SCM tag information.
     *
     */
    private static final String JGIT_CONF_SYSTEM_PROPERTY_USE_VERSION = "nisse.source.jgit.useVersion";

    /**
     * Pattern for version hint tags. Use ${version} as placeholder for the version part.
     * Default is "${version}-SNAPSHOT" which matches tags like "4.1.0-SNAPSHOT".
     * Can be customized to patterns like "hint-${version}" or "next-${version}".
     *
     */
    private static final String JGIT_CONF_SYSTEM_PROPERTY_VERSION_HINT_PATTERN = "nisse.source.jgit.versionHintPattern";

    private static final String DEFAULT_VERSION_HINT_PATTERN = "${version}-SNAPSHOT";

    /**
     * Configure the timestamp format for the date property. Supports named patterns:
     * - "git" (default): EEE MMM dd HH:mm:ss yyyy Z
     * - "iso8601": yyyy-MM-dd'T'HH:mm:ss'Z' (UTC)
     * - "iso8601-offset": yyyy-MM-dd'T'HH:mm:ssXXX (with timezone offset)
     * - "custom": use the pattern specified in nisse.source.jgit.dateFormat.pattern
     */
    private static final String JGIT_CONF_SYSTEM_PROPERTY_DATE_FORMAT = "nisse.source.jgit.dateFormat";

    /**
     * Custom date format pattern when dateFormat is set to "custom".
     */
    private static final String JGIT_CONF_SYSTEM_PROPERTY_DATE_FORMAT_PATTERN = "nisse.source.jgit.dateFormat.pattern";

    private static final String DEFAULT_DATE_FORMAT = "git";

    /**
     * Pattern for standard semantic versions, with an optional {@code "v"} prefix.
     */
    protected static final Pattern TAG_VERSION_PATTERN = Pattern.compile("refs/tags/v?((\\d+\\.\\d+\\.\\d+)(.*))");

    /**
     * The default version if no version can be determined from git.
     */
    protected final String defaultVersion = "0.1.0";

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final VersionScheme versionScheme = new GenericVersionScheme();

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public Map<String, String> getProperties(NisseConfiguration configuration) {
        HashMap<String, String> result = new HashMap<>();
        try (Repository repository = new FileRepositoryBuilder()
                .readEnvironment()
                .findGitDir(configuration.getCurrentWorkingDirectory().toFile())
                .setMustExist(true)
                .build()) {

            if (repository.getDirectory() != null) {
                RevCommit lastCommit = getLastCommit(repository);

                result.put(JGIT_COMMIT, lastCommit.getName());
                result.put(JGIT_DATE, formatCommitDate(configuration, lastCommit));
                result.put(
                        JGIT_COMMITTER,
                        lastCommit.getCommitterIdent().toExternalString().split(">")[0] + ">");
                result.put(
                        JGIT_AUTHOR,
                        lastCommit.getAuthorIdent().toExternalString().split(">")[0] + ">");
                if (Boolean.parseBoolean(configuration
                        .getConfiguration()
                        .getOrDefault(JGIT_CONF_SYSTEM_PROPERTY_DYNAMIC_VERSION, DEFAULT_DYNAMIC_VERSION))) {
                    result.put(JGIT_DYNAMIC_VERSION, resolveDynamicVersion(configuration, repository));
                }
            }
        } catch (RepositoryNotFoundException | IllegalArgumentException e) {
            logger.debug("Seems this is not a git checkout; ignoring property source {}", NAME, e);
        } catch (Exception e) {
            logger.error("Exception in JGitPropertySource: {}", e.toString());
            throw new RuntimeException(e);
        }
        return Collections.unmodifiableMap(result);
    }

    private RevCommit getLastCommit(Repository repository) throws NoHeadException, GitAPIException {
        return Git.wrap(repository).log().setMaxCount(1).call().iterator().next();
    }

    /**
     * Formats the commit date according to the configured format.
     *
     * @param configuration the Nisse configuration
     * @param commit the commit to format the date for
     * @return the formatted date string
     */
    private String formatCommitDate(NisseConfiguration configuration, RevCommit commit) {
        DateTimeFormatter formatter = resolveDateTimeFormatter(configuration);
        String dateFormat = configuration
                .getConfiguration()
                .getOrDefault(JGIT_CONF_SYSTEM_PROPERTY_DATE_FORMAT, DEFAULT_DATE_FORMAT);

        ZonedDateTime commitDateTime = ZonedDateTime.ofInstant(
                Instant.ofEpochSecond(commit.getCommitTime()),
                commit.getAuthorIdent().getTimeZone().toZoneId());

        // For ISO-8601 format, convert to UTC
        if ("iso8601".equalsIgnoreCase(dateFormat)) {
            commitDateTime = commitDateTime.withZoneSameInstant(java.time.ZoneOffset.UTC);
        }

        return commitDateTime.format(formatter);
    }

    /**
     * Resolves the DateTimeFormatter based on the configuration.
     *
     * @param configuration the Nisse configuration
     * @return the configured DateTimeFormatter
     */
    private DateTimeFormatter resolveDateTimeFormatter(NisseConfiguration configuration) {
        String dateFormat = configuration
                .getConfiguration()
                .getOrDefault(JGIT_CONF_SYSTEM_PROPERTY_DATE_FORMAT, DEFAULT_DATE_FORMAT);

        switch (dateFormat.toLowerCase()) {
            case "git":
                return DateTimeFormatter.ofPattern("EEE MMM dd HH:mm:ss yyyy Z", Locale.ENGLISH);
            case "iso8601":
                return DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'");
            case "iso8601-offset":
                return DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX");
            case "custom":
                String customPattern =
                        configuration.getConfiguration().get(JGIT_CONF_SYSTEM_PROPERTY_DATE_FORMAT_PATTERN);
                if (customPattern != null && !customPattern.trim().isEmpty()) {
                    try {
                        return DateTimeFormatter.ofPattern(customPattern);
                    } catch (IllegalArgumentException e) {
                        logger.warn(
                                "Invalid custom date format pattern '{}', falling back to default 'git' format",
                                customPattern,
                                e);
                        return DateTimeFormatter.ofPattern("EEE MMM dd HH:mm:ss yyyy Z", Locale.ENGLISH);
                    }
                } else {
                    logger.warn(
                            "Custom date format specified but no pattern provided via '{}', falling back to default 'git' format",
                            JGIT_CONF_SYSTEM_PROPERTY_DATE_FORMAT_PATTERN);
                    return DateTimeFormatter.ofPattern("EEE MMM dd HH:mm:ss yyyy Z", Locale.ENGLISH);
                }
            default:
                logger.warn(
                        "Unknown date format '{}', falling back to default 'git' format. Supported formats: git, iso8601, iso8601-offset, custom",
                        dateFormat);
                return DateTimeFormatter.ofPattern("EEE MMM dd HH:mm:ss yyyy Z", Locale.ENGLISH);
        }
    }

    public String resolveDynamicVersion(NisseConfiguration configuration, Repository repository) throws Exception {
        VersionInformation vi;

        Optional<String> useVersion =
                Optional.ofNullable(configuration.getConfiguration().get(JGIT_CONF_SYSTEM_PROPERTY_USE_VERSION));

        if (useVersion.isPresent()) {
            vi = new VersionInformation(useVersion.get());
            logger.debug("Using explicit version from useVersion property: {}", useVersion.get());
        } else {
            // First, get version from git history (regular release tags)
            VersionInformation gitHistoryVersion = getVersionFromGit(configuration, repository);
            logger.debug("Version from git history: {}", gitHistoryVersion.toString());

            // Check if using custom version hint pattern
            String versionHintPattern = configuration
                    .getConfiguration()
                    .getOrDefault(JGIT_CONF_SYSTEM_PROPERTY_VERSION_HINT_PATTERN, DEFAULT_VERSION_HINT_PATTERN);
            boolean isCustomPattern = !DEFAULT_VERSION_HINT_PATTERN.equals(versionHintPattern);

            // Then, check for version hint tags
            Optional<String> versionHint = findVersionHint(configuration, repository);
            if (versionHint.isPresent()) {
                VersionInformation hintVersion = new VersionInformation(versionHint.get());
                logger.debug("Version hint found: {}", hintVersion.toString());

                if (isCustomPattern) {
                    // With custom pattern, version hints take priority (git history only contains matching tags)
                    vi = mayAddSnapshotQualifier(configuration, hintVersion);
                    logger.debug("Using version hint (custom pattern): {}", versionHint.get());
                } else {
                    // With default pattern, compare versions
                    // Check if git history version is the default (meaning no regular release tags found)
                    boolean isDefaultGitVersion = gitHistoryVersion.getMajor() == 0
                            && gitHistoryVersion.getMinor() == 1
                            && gitHistoryVersion.getPatch() == 0;

                    if (isDefaultGitVersion) {
                        // No regular release tags found, use version hint directly
                        vi = mayAddSnapshotQualifier(configuration, hintVersion);
                        logger.debug("Using version hint (no regular release tags found): {}", versionHint.get());
                    } else {
                        // Compare versions - use hint only if it's higher than git history version
                        Version gitHistoryVersionParsed = version(gitHistoryVersion.toString());
                        Version hintVersionParsed = version(hintVersion.toString());

                        if (hintVersionParsed.compareTo(gitHistoryVersionParsed) > 0) {
                            // Version hint is higher, use it
                            vi = mayAddSnapshotQualifier(configuration, hintVersion);
                            logger.debug("Using version hint (higher than git history): {}", versionHint.get());
                        } else {
                            // Git history version is higher or equal, use it
                            vi = gitHistoryVersion;
                            logger.debug(
                                    "Using git history version (higher than or equal to version hint): {}",
                                    gitHistoryVersion.toString());
                        }
                    }
                }
            } else {
                // No version hint, use git history version
                vi = gitHistoryVersion;
                logger.debug("Using version resolved from git history (no version hint found)");
            }
        }

        logger.debug("dynamic version resolved to: {}", vi.toString());

        return vi.toString();
    }

    protected VersionInformation getVersionFromGit(NisseConfiguration configuration, Repository repository)
            throws Exception {
        try (Git git = Git.wrap(repository)) {

            RevCommit lastCommit = getLastCommit(repository);
            logger.debug("last commit: {}", lastCommit.toString());

            Iterable<RevCommit> commits = git.log().call();
            int count = 0;
            for (RevCommit commit : commits) {
                Optional<VersionInformation> ovi = getHighestVersionTagForCommit(configuration, git, commit);

                if (ovi.isPresent()) {
                    VersionInformation vi = ovi.get();

                    if (commit.equals(lastCommit)) {
                        return vi;
                    } else {
                        vi.setPatch(vi.getPatch() + 1);
                        boolean appendBuildNumber = Boolean.parseBoolean(configuration
                                .getConfiguration()
                                .getOrDefault(
                                        JGIT_CONF_SYSTEM_PROPERTY_APPEND_BUILD_NUMBER, DEFAULT_APPEND_BUILD_NUMBER));
                        if (appendBuildNumber) {
                            vi.setBuildNumber(count);
                        }
                        return mayAddSnapshotQualifier(configuration, vi);
                    }
                }
                count++;
            }
            return mayAddSnapshotQualifier(configuration, new VersionInformation(defaultVersion + "-" + count));
        } catch (GitAPIException e) {
            throw new Exception("Error reading Git information.", e);
        }
    }

    private Optional<VersionInformation> getHighestVersionTagForCommit(
            NisseConfiguration configuration, Git git, RevCommit commit) throws GitAPIException {
        // get tags use semantic version (X.Y.Z or vX.Y.Z) for for commit
        List<String> versionTagsForCommit = getVersionedTagsForCommit(configuration, git, commit);
        logger.debug("commit {} {}: {}", commit.getId(), commit.getShortMessage(), versionTagsForCommit.toString());
        Optional<VersionInformation> ovi = findHighestVersion(versionTagsForCommit);

        return ovi;
    }

    protected List<String> getVersionedTagsForCommit(NisseConfiguration configuration, Git git, RevCommit commit)
            throws GitAPIException {
        // Check if using custom version hint pattern
        String versionHintPattern = configuration
                .getConfiguration()
                .getOrDefault(JGIT_CONF_SYSTEM_PROPERTY_VERSION_HINT_PATTERN, DEFAULT_VERSION_HINT_PATTERN);
        boolean isCustomPattern = !DEFAULT_VERSION_HINT_PATTERN.equals(versionHintPattern);

        return git.tagList().call().stream()
                .filter(tag -> {
                    try {
                        Ref peeledRef = git.getRepository().getRefDatabase().peel(tag);
                        ObjectId id = (peeledRef.getPeeledObjectId() != null
                                ? peeledRef.getPeeledObjectId()
                                : tag.getObjectId());

                        return id.equals(commit.getId());
                    } catch (Exception e) {
                        return false;
                    }
                })
                .map(Ref::getName)
                .filter(tagName -> {
                    if (isCustomPattern) {
                        // With custom pattern, only consider tags that match the pattern
                        return isVersionHintTag(configuration, tagName);
                    } else {
                        // With default pattern, exclude version hint tags to avoid double-counting
                        return !isVersionHintTag(configuration, tagName);
                    }
                })
                .map(TAG_VERSION_PATTERN::matcher)
                .filter(m -> m.matches() && m.groupCount() > 0)
                .map(m -> m.group(1))
                .collect(Collectors.toList());
    }

    protected Optional<VersionInformation> findHighestVersion(List<String> versionTags) {
        return versionTags.stream()
                .map(this::version)
                .max(Comparator.comparing(version -> version))
                .map(Version::toString)
                .map(VersionInformation::new);
    }

    protected Version version(String string) {
        try {
            return versionScheme.parseVersion(string);
        } catch (InvalidVersionSpecificationException e) {
            throw new RuntimeException(e);
        }
    }

    protected VersionInformation mayAddSnapshotQualifier(NisseConfiguration configuration, VersionInformation vi) {
        boolean appendSnapshot = Boolean.parseBoolean(configuration
                .getConfiguration()
                .getOrDefault(JGIT_CONF_SYSTEM_PROPERTY_APPEND_SNAPSHOT, DEFAULT_APPEND_SNAPSHOT));
        if (appendSnapshot) {
            vi.setQualifier("SNAPSHOT");
        }
        return vi;
    }

    /**
     * Find version hint from tags matching the configured pattern.
     * Version hint tags provide a way to specify the next version without requiring
     * environment variable changes, making builds more reproducible.
     *
     * @param configuration The Nisse configuration
     * @param repository The git repository
     * @return Optional version string extracted from hint tags
     * @throws Exception if git operations fail
     */
    protected Optional<String> findVersionHint(NisseConfiguration configuration, Repository repository)
            throws Exception {
        try (Git git = Git.wrap(repository)) {
            String hintPattern = configuration
                    .getConfiguration()
                    .getOrDefault(JGIT_CONF_SYSTEM_PROPERTY_VERSION_HINT_PATTERN, DEFAULT_VERSION_HINT_PATTERN);

            List<String> hintVersions = findVersionHintTags(git, hintPattern);
            logger.debug("Found version hint tags: {}", hintVersions);

            return findHighestVersionFromHints(hintVersions);
        } catch (GitAPIException e) {
            throw new Exception("Error reading version hint tags from Git.", e);
        }
    }

    /**
     * Find all tags that match the version hint pattern and extract versions from them.
     *
     * @param git The git instance
     * @param hintPattern The pattern to match (e.g., "${version}-SNAPSHOT")
     * @return List of version strings extracted from matching tags
     * @throws GitAPIException if git operations fail
     */
    protected List<String> findVersionHintTags(Git git, String hintPattern) throws GitAPIException {
        // Convert hint pattern to regex pattern
        // ${version} becomes a capturing group for semantic version
        // We need to be careful about the order of replacements to avoid double-escaping

        // First, escape special regex characters in the pattern (except the placeholder)
        String regexPattern = hintPattern
                .replace(".", "\\.") // Escape literal dots
                .replace("-", "\\-"); // Escape literal dashes

        // Then replace the placeholder with the version regex (dots already escaped in target)
        regexPattern = regexPattern.replace("${version}", "(\\d+\\.\\d+\\.\\d+)");

        Pattern hintTagPattern = Pattern.compile("refs/tags/v?" + regexPattern);
        logger.debug("Using version hint regex pattern: {}", hintTagPattern.pattern());

        return git.tagList().call().stream()
                .map(Ref::getName)
                .map(hintTagPattern::matcher)
                .filter(m -> m.matches() && m.groupCount() > 0)
                .map(m -> m.group(1)) // Extract the version part
                .collect(Collectors.toList());
    }

    /**
     * Find the highest version from the list of version hint strings.
     *
     * @param hintVersions List of version strings from hint tags
     * @return Optional highest version string
     */
    protected Optional<String> findHighestVersionFromHints(List<String> hintVersions) {
        return hintVersions.stream().max(Comparator.comparing(this::version));
    }

    /**
     * Check if a tag name matches the version hint pattern.
     *
     * @param configuration The Nisse configuration
     * @param tagName The full tag name (e.g., "refs/tags/1.0.0-SNAPSHOT")
     * @return true if the tag matches the version hint pattern
     */
    protected boolean isVersionHintTag(NisseConfiguration configuration, String tagName) {
        String versionHintPattern = configuration
                .getConfiguration()
                .getOrDefault(JGIT_CONF_SYSTEM_PROPERTY_VERSION_HINT_PATTERN, DEFAULT_VERSION_HINT_PATTERN);

        // Convert hint pattern to regex pattern (same logic as findVersionHintTags)
        // First, escape special regex characters in the pattern (except the placeholder)
        String regexPattern = versionHintPattern
                .replace(".", "\\.") // Escape literal dots
                .replace("-", "\\-"); // Escape literal dashes

        // Then replace the placeholder with the version regex
        regexPattern = regexPattern.replace("${version}", "(\\d+\\.\\d+\\.\\d+)");

        Pattern hintTagPattern = Pattern.compile("refs/tags/v?" + regexPattern);

        return hintTagPattern.matcher(tagName).matches();
    }
}
