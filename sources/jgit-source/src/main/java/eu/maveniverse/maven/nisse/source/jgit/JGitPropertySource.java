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
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
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
import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.ConfigConstants;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
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

    private static final String JGIT_SHORT_COMMIT_ID = "shortCommitId";

    private static final String JGIT_DATE = "date";

    private static final String JGIT_AUTHOR = "author";

    private static final String JGIT_COMMITTER = "committer";

    private static final String JGIT_DYNAMIC_VERSION = "dynamicVersion";

    private static final String JGIT_COUNTING_VERSION = "countingVersion";

    private static final String JGIT_CLEAN = "clean";

    private static final String JGIT_BRANCH_NAME = "branchName";

    private static final String JGIT_REMOTE_NAME = "remoteName";

    private static final String JGIT_REMOTE_URL = "remoteUrl";

    private static final String JGIT_COMMON_DIR = "commonDir";

    /**
     * Specify the length for the short commit id.
     */
    private static final String JGIT_CONF_SYSTEM_PROPERTY_SHORT_COMMIT_ID_LENGTH =
            "nisse.source.jgit.shortCommitIdLength";

    private static final String DEFAULT_SHORT_COMMIT_ID_LENGTH = "7";

    /**
     * Set to {@code true} to enable "dynamic version" feature, it adds the
     * {@link #JGIT_DYNAMIC_VERSION} property to resulting properties.
     */
    private static final String JGIT_CONF_SYSTEM_PROPERTY_DYNAMIC_VERSION = "nisse.source.jgit.dynamicVersion";

    private static final String DEFAULT_DYNAMIC_VERSION = Boolean.FALSE.toString();

    /**
     * Set to {@code true} to enable "counting version" feature, it adds the
     * {@link #JGIT_COUNTING_VERSION} property to resulting properties.
     * <p>
     * Counting version walks the entire commit history from oldest to newest,
     * accumulating version bumps from commit message directives ({@code [major]},
     * {@code [minor]}, {@code [patch]}). Commits without a directive increment
     * the commit count. This is compatible with the
     * <a href="https://github.com/nickolay-kondratyev/gradle-git-versioner">gradle-git-versioner</a>
     * plugin.
     */
    private static final String JGIT_CONF_SYSTEM_PROPERTY_COUNTING_VERSION = "nisse.source.jgit.countingVersion";

    private static final String DEFAULT_COUNTING_VERSION = Boolean.FALSE.toString();

    /**
     * The major version to start counting from. Default is {@code 0}.
     */
    private static final String JGIT_CONF_COUNTING_START_MAJOR = "nisse.source.jgit.countingVersion.startMajor";

    private static final String DEFAULT_COUNTING_START_MAJOR = "0";

    /**
     * The minor version to start counting from. Default is {@code 0}.
     */
    private static final String JGIT_CONF_COUNTING_START_MINOR = "nisse.source.jgit.countingVersion.startMinor";

    private static final String DEFAULT_COUNTING_START_MINOR = "0";

    /**
     * The patch version to start counting from. Default is {@code 0}.
     */
    private static final String JGIT_CONF_COUNTING_START_PATCH = "nisse.source.jgit.countingVersion.startPatch";

    private static final String DEFAULT_COUNTING_START_PATCH = "0";

    /**
     * The string to match in commit messages for major version bumps. Default is {@code [major]}.
     */
    private static final String JGIT_CONF_COUNTING_MATCH_MAJOR = "nisse.source.jgit.countingVersion.matchMajor";

    private static final String DEFAULT_COUNTING_MATCH_MAJOR = "[major]";

    /**
     * The string to match in commit messages for minor version bumps. Default is {@code [minor]}.
     */
    private static final String JGIT_CONF_COUNTING_MATCH_MINOR = "nisse.source.jgit.countingVersion.matchMinor";

    private static final String DEFAULT_COUNTING_MATCH_MINOR = "[minor]";

    /**
     * The string to match in commit messages for patch version bumps. Default is {@code [patch]}.
     */
    private static final String JGIT_CONF_COUNTING_MATCH_PATCH = "nisse.source.jgit.countingVersion.matchPatch";

    private static final String DEFAULT_COUNTING_MATCH_PATCH = "[patch]";

    /**
     * The pattern to format the counting version. Supports placeholders:
     * {@code %M} (major), {@code %m} (minor), {@code %p} (patch), {@code %c} (commit count).
     * Parenthesised sections like {@code (-%c)} are included only when commit count &gt; 0.
     * <p>
     * Default is {@code %M.%m.%p(-%c)} which produces e.g. {@code 1.2.3} or {@code 1.2.3-4}.
     * Use {@code %M.%m.%p(.%c)} for dot-separated commit count like gradle-git-versioner.
     */
    private static final String JGIT_CONF_COUNTING_PATTERN = "nisse.source.jgit.countingVersion.pattern";

    private static final String DEFAULT_COUNTING_PATTERN = "%M.%m.%p(-%c)";

    /**
     * Whether the patch version shall be increased or not, when calculating dynamic version and there is no tag
     * on current commit. <strong>To be used with consideration!</strong>
     * <p>
     * Warning: disabling both, this and {@link #DEFAULT_APPEND_BUILD_NUMBER} feature will produce same
     * version over and over again (the last found tag). Moreover, disabling this feature, but keeping
     * {@link #JGIT_CONF_SYSTEM_PROPERTY_APPEND_SNAPSHOT} enabled, will produce "backward" Maven versions!
     */
    private static final String JGIT_CONF_SYSTEM_PROPERTY_INCREASE_PATCH_VERSION =
            "nisse.source.jgit.increasePatchVersion";

    private static final String DEFAULT_INCREASE_PATCH_VERSION = Boolean.TRUE.toString();

    /**
     * Set to {@code true} to derive the version increase from Conventional Commits made since the last version
     * tag, instead of always increasing the patch version. Supersedes
     * {@link #JGIT_CONF_SYSTEM_PROPERTY_INCREASE_PATCH_VERSION}. See {@code GIT_CONFIGURATION.md}.
     */
    private static final String JGIT_CONF_SYSTEM_PROPERTY_CONVENTIONAL_COMMITS =
            "nisse.source.jgit.conventionalCommits";

    private static final String DEFAULT_CONVENTIONAL_COMMITS = Boolean.FALSE.toString();

    /**
     * Whether the buildNumber shall be appended or not.
     */
    private static final String JGIT_CONF_SYSTEM_PROPERTY_APPEND_BUILD_NUMBER = "nisse.source.jgit.appendBuildNumber";

    private static final String DEFAULT_APPEND_BUILD_NUMBER = Boolean.TRUE.toString();

    /**
     * Whether the SNAPSHOT qualifier shall be appended or not.
     */
    private static final String JGIT_CONF_SYSTEM_PROPERTY_APPEND_SNAPSHOT = "nisse.source.jgit.appendSnapshot";

    private static final String DEFAULT_APPEND_SNAPSHOT = Boolean.TRUE.toString();

    /**
     * Whether the branch name shall be appended or not.
     */
    private static final String JGIT_CONF_SYSTEM_PROPERTY_APPEND_BRANCH_NAME = "nisse.source.jgit.appendBranchName";

    private static final String DEFAULT_APPEND_BRANCH_NAME = Boolean.FALSE.toString();

    /**
     * Whether the DIRTY qualifier shall be appended or not.
     */
    private static final String JGIT_CONF_SYSTEM_PROPERTY_APPEND_DIRTY = "nisse.source.jgit.appendDirty";

    private static final String DEFAULT_APPEND_DIRTY = Boolean.FALSE.toString();

    /**
     * The DIRTY qualifier.
     */
    private static final String JGIT_CONF_SYSTEM_PROPERTY_DIRTY_QUALIFIER = "nisse.source.jgit.dirtyQualifier";

    private static final String DEFAULT_DIRTY_QUALIFIER = "DIRTY";

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
     * A Conventional Commits subject line: {@code type(optional scope)!: description}, where {@code !} marks a
     * breaking change.
     */
    private static final Pattern CONVENTIONAL_COMMIT_SUBJECT =
            Pattern.compile("(?<type>[a-zA-Z]+)(\\([^)]+\\))?(?<breaking>!)?:\\s");

    /**
     * The Conventional Commits breaking change footer. The specification allows both spellings.
     */
    private static final Pattern BREAKING_CHANGE_FOOTER =
            Pattern.compile("^BREAKING[ -]CHANGE: \\S", Pattern.MULTILINE);

    /**
     * Matches the credential-bearing userinfo (user, or user:password) in an HTTP(S) remote URL,
     * e.g. {@code https://user:token@host/repo.git}. SSH-style URLs (scp-like {@code git@host:path}
     * or {@code ssh://user@host/path}) never match, since they carry no scheme or a non-http(s) one.
     */
    private static final Pattern HTTP_CREDENTIAL_PATTERN = Pattern.compile("^(https?://)[^/@]+@(.*)$");

    /**
     * The default version if no version can be determined from git.
     */
    protected final String defaultVersion = "0.1.0";

    /**
     * Configure the list of remote names you are interested in (in order).
     */
    private static final String JGIT_CONF_SYSTEM_PROPERTY_REMOTE_NAMES = "nisse.source.jgit.remoteNames";

    /**
     * The default remote names.
     */
    private static final String DEFAULT_REMOTE_NAMES = "upstream,origin";

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final VersionScheme versionScheme = new GenericVersionScheme();

    /**
     * Splits incoming string at comma, semicolon or pipe character, and after trimming and filtering
     * for empty strings, returns the resulted list of strings.
     */
    private static List<String> csv(String csv) {
        if (csv == null || csv.trim().isEmpty()) {
            return Collections.emptyList();
        }
        return Arrays.stream(csv.split("[,;|]"))
                .map(String::trim)
                .filter(name -> !name.isEmpty())
                .collect(Collectors.toList());
    }

    /**
     * Strips {@code user:password@}/{@code user@} userinfo from HTTP(S) remote URLs so credentials
     * never leak into build properties. SSH-style URLs (scp-like or {@code ssh://}) are left untouched,
     * since their userinfo is at most a login name, never a secret.
     */
    static String redactCredentials(String url) {
        Matcher m = HTTP_CREDENTIAL_PATTERN.matcher(url);
        return m.matches() ? m.group(1) + m.group(2) : url;
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public Map<String, String> getProperties(NisseConfiguration configuration) {
        HashMap<String, String> result = new HashMap<>();
        try {
            File cwd = configuration.getCurrentWorkingDirectory().toFile();
            FileRepositoryBuilder builder =
                    new FileRepositoryBuilder().readEnvironment().findGitDir(cwd);
            Path worktreeGitDir = null;

            File gitDir = builder.getGitDir();
            if (gitDir != null) {
                Path commonDirFile = gitDir.toPath().resolve("commondir");
                if (Files.exists(commonDirFile)) {
                    worktreeGitDir = gitDir.toPath();
                    String commonDirRef = new String(Files.readAllBytes(commonDirFile), StandardCharsets.UTF_8).trim();
                    File commonDir =
                            gitDir.toPath().resolve(commonDirRef).normalize().toFile();
                    logger.debug("Detected git worktree: gitDir={}, commonDir={}", gitDir, commonDir);
                    result.put(JGIT_COMMON_DIR, commonDir.getAbsolutePath());
                    builder.setGitDir(commonDir);
                    builder.setWorkTree(cwd);
                    builder.setIndexFile(worktreeGitDir.resolve("index").toFile());
                }
            }

            try (Repository repository = builder.setMustExist(true).build();
                    Git git = Git.wrap(repository)) {
                if (repository.getDirectory() != null) {
                    ObjectId head = resolveHead(repository, worktreeGitDir);
                    RevCommit lastCommit = getLastCommit(git, head);

                    result.put(JGIT_COMMIT, lastCommit.getName());
                    String length = configuration
                            .getConfiguration()
                            .getOrDefault(
                                    JGIT_CONF_SYSTEM_PROPERTY_SHORT_COMMIT_ID_LENGTH, DEFAULT_SHORT_COMMIT_ID_LENGTH);
                    result.put(
                            JGIT_SHORT_COMMIT_ID,
                            lastCommit.abbreviate(Integer.parseInt(length)).name());
                    result.put(JGIT_DATE, formatCommitDate(configuration, lastCommit));
                    result.put(
                            JGIT_COMMITTER,
                            lastCommit.getCommitterIdent().toExternalString().split(">")[0] + ">");
                    result.put(
                            JGIT_AUTHOR,
                            lastCommit.getAuthorIdent().toExternalString().split(">")[0] + ">");
                    result.put(JGIT_CLEAN, Boolean.toString(isClean(git)));

                    Config config = repository.getConfig();
                    List<String> wantedRemotes = csv(configuration
                            .getConfiguration()
                            .getOrDefault(JGIT_CONF_SYSTEM_PROPERTY_REMOTE_NAMES, DEFAULT_REMOTE_NAMES));
                    for (String remote : wantedRemotes) {
                        String url = Arrays.stream(config.getStringList(
                                        ConfigConstants.CONFIG_REMOTE_SECTION, remote, ConfigConstants.CONFIG_KEY_URL))
                                .filter(value -> value != null && !value.trim().isEmpty())
                                .findFirst()
                                .orElse(null);
                        if (url != null && !url.trim().isEmpty()) {
                            result.put(JGIT_REMOTE_NAME, remote);
                            result.put(JGIT_REMOTE_URL, redactCredentials(url));
                            break;
                        }
                    }

                    Optional<Ref> localBranch = localBranch(repository, worktreeGitDir, head);
                    localBranch
                            .map(r -> Repository.shortenRefName(r.getName()))
                            .ifPresent(branchName -> result.put(JGIT_BRANCH_NAME, branchName));

                    if (Boolean.parseBoolean(configuration
                            .getConfiguration()
                            .getOrDefault(JGIT_CONF_SYSTEM_PROPERTY_DYNAMIC_VERSION, DEFAULT_DYNAMIC_VERSION))) {
                        result.put(JGIT_DYNAMIC_VERSION, resolveDynamicVersion(result, configuration, git, head));
                    }
                    if (Boolean.parseBoolean(configuration
                            .getConfiguration()
                            .getOrDefault(JGIT_CONF_SYSTEM_PROPERTY_COUNTING_VERSION, DEFAULT_COUNTING_VERSION))) {
                        result.put(JGIT_COUNTING_VERSION, resolveCountingVersion(configuration, git, head));
                    }
                }
            }
        } catch (RepositoryNotFoundException | IllegalArgumentException e) {
            logger.debug("Seems this is not a git checkout; ignoring property source {}", NAME, e);
        } catch (Exception e) {
            logger.error("Exception in JGitPropertySource: {}", e.toString());
            throw new IllegalStateException(e);
        }
        return Collections.unmodifiableMap(result);
    }

    /**
     * Resolves the HEAD commit id. In a worktree, HEAD is stored in the worktree-specific git
     * directory rather than the common directory, so this method reads it from the correct location.
     *
     * @param repository the repository (opened against the common dir)
     * @param worktreeGitDir the worktree-specific git directory, or {@code null} for normal repos
     */
    private ObjectId resolveHead(Repository repository, Path worktreeGitDir) throws IOException {
        if (worktreeGitDir != null) {
            Path headFile = worktreeGitDir.resolve("HEAD");
            String headContent = new String(Files.readAllBytes(headFile), StandardCharsets.UTF_8).trim();
            if (headContent.startsWith("ref: ")) {
                String refName = headContent.substring(5);
                Ref ref = repository.exactRef(refName);
                if (ref != null) {
                    return ref.getObjectId();
                }
                // Worktree HEAD points to a branch that doesn't exist yet
                return null;
            } else {
                return ObjectId.fromString(headContent);
            }
        }

        return repository.resolve("HEAD");
    }

    /**
     * Resolves the HEAD ref. In a worktree, HEAD is stored in the worktree-specific git
     * directory rather than the common directory, so this method reads it from the correct location.
     *
     * @param repository the repository (opened against the common dir)
     * @param worktreeGitDir the worktree-specific git directory, or {@code null} for normal repos
     */
    private Ref resolveHeadRef(Repository repository, Path worktreeGitDir) throws IOException {
        if (worktreeGitDir != null) {
            Path headFile = worktreeGitDir.resolve("HEAD");
            String headContent = new String(Files.readAllBytes(headFile), StandardCharsets.UTF_8).trim();
            if (headContent.startsWith("ref: ")) {
                String refName = headContent.substring(5);
                return repository.exactRef(refName);
                // Worktree HEAD points to a branch that doesn't exist yet
            } else {
                return null;
            }
        }

        return repository.exactRef("HEAD");
    }

    private RevCommit getLastCommit(Git git, ObjectId head) throws GitAPIException, IOException {
        if (head != null) {
            return git.log().add(head).setMaxCount(1).call().iterator().next();
        }
        // Fall back to default HEAD resolution — throws NoHeadException if no HEAD exists
        return git.log().setMaxCount(1).call().iterator().next();
    }

    private boolean isClean(Git git) throws GitAPIException {
        return git.status().call().isClean();
    }

    private Optional<Ref> localBranch(Repository repository, Path worktreeGitDir, ObjectId head) throws IOException {
        if (worktreeGitDir != null) {
            Ref wtHead = resolveHeadRef(repository, worktreeGitDir);
            if (wtHead != null) {
                if (wtHead.isSymbolic()) {
                    return Optional.of(wtHead.getTarget());
                }
                if (!"HEAD".equals(wtHead.getName())) {
                    return Optional.of(wtHead);
                }
            }
        }
        if (head != null) {
            Set<Ref> refs = repository.getRefDatabase().getTipsWithSha1(head);
            for (Ref r : refs) {
                if (r.isSymbolic()) {
                    return Optional.of(r.getTarget());
                }
            }
            if (refs.size() == 1) {
                Ref ref = refs.iterator().next();
                // if "detached" return empty
                if (!"HEAD".equals(ref.getName())) {
                    return Optional.of(ref);
                }
            }
        }
        return Optional.empty();
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

    String resolveDynamicVersion(
            Map<String, String> properties, NisseConfiguration configuration, Git git, ObjectId head)
            throws GitAPIException, IOException {
        VersionInformation vi;

        Optional<String> useVersion =
                Optional.ofNullable(configuration.getConfiguration().get(JGIT_CONF_SYSTEM_PROPERTY_USE_VERSION));

        if (useVersion.isPresent()) {
            vi = new VersionInformation(useVersion.get());
            logger.debug("Using explicit version from useVersion property: {}", useVersion.get());
        } else {
            // First, get version from git history (regular release tags)
            GitVersion gitVersion = resolveVersionFromGit(properties, configuration, git, head);
            VersionInformation gitHistoryVersion = gitVersion.getVersion();
            logger.debug("Version from git history: {}", gitHistoryVersion.toString());

            // Check if using custom version hint pattern
            String versionHintPattern = configuration
                    .getConfiguration()
                    .getOrDefault(JGIT_CONF_SYSTEM_PROPERTY_VERSION_HINT_PATTERN, DEFAULT_VERSION_HINT_PATTERN);
            boolean isCustomPattern = !DEFAULT_VERSION_HINT_PATTERN.equals(versionHintPattern);

            // Then, check for version hint tags
            Optional<String> versionHint = findVersionHint(configuration, git, head);
            if (versionHint.isPresent()) {
                VersionInformation hintVersion = new VersionInformation(versionHint.get());
                logger.debug("Version hint found: {}", hintVersion);

                if (isCustomPattern) {
                    // With custom pattern, version hints take priority (git history only contains matching tags)
                    vi = mayAddQualifier(properties, configuration, hintVersion);
                    logger.debug("Using version hint (custom pattern): {}", versionHint.get());
                } else {
                    // With default pattern, compare versions
                    // Whether a regular release tag was found at all. Reported by the walk rather than
                    // inferred from the value: 0.1.0 is also what increasing a 0.0.x tag's minor produces.
                    boolean isDefaultGitVersion = !gitVersion.isFromReleaseTag();

                    if (isDefaultGitVersion) {
                        // No regular release tags found, use version hint directly
                        vi = mayAddQualifier(properties, configuration, hintVersion);
                        logger.debug("Using version hint (no regular release tags found): {}", versionHint.get());
                    } else {
                        // Compare versions - use hint only if it's higher than git history version
                        Version gitHistoryVersionParsed = version(gitHistoryVersion.toString());
                        Version hintVersionParsed = version(hintVersion.toString());

                        if (hintVersionParsed.compareTo(gitHistoryVersionParsed) > 0) {
                            // Version hint is higher, use it
                            vi = mayAddQualifier(properties, configuration, hintVersion);
                            logger.debug("Using version hint (higher than git history): {}", versionHint.get());
                        } else {
                            // Git history version is higher or equal, use it
                            vi = gitHistoryVersion;
                            logger.debug(
                                    "Using git history version (higher than or equal to version hint): {}",
                                    gitHistoryVersion);
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

    /**
     * Resolves the counting version by walking the entire commit history from oldest to newest,
     * accumulating version bumps from commit message directives. This is compatible with the
     * gradle-git-versioner algorithm: each commit either bumps a version component (and resets
     * lower components and the commit count) or increments the commit count.
     */
    String resolveCountingVersion(NisseConfiguration configuration, Git git, ObjectId head)
            throws GitAPIException, IOException {
        Map<String, String> config = configuration.getConfiguration();

        int major = Integer.parseInt(config.getOrDefault(JGIT_CONF_COUNTING_START_MAJOR, DEFAULT_COUNTING_START_MAJOR));
        int minor = Integer.parseInt(config.getOrDefault(JGIT_CONF_COUNTING_START_MINOR, DEFAULT_COUNTING_START_MINOR));
        int patch = Integer.parseInt(config.getOrDefault(JGIT_CONF_COUNTING_START_PATCH, DEFAULT_COUNTING_START_PATCH));
        String matchMajor = config.getOrDefault(JGIT_CONF_COUNTING_MATCH_MAJOR, DEFAULT_COUNTING_MATCH_MAJOR);
        String matchMinor = config.getOrDefault(JGIT_CONF_COUNTING_MATCH_MINOR, DEFAULT_COUNTING_MATCH_MINOR);
        String matchPatch = config.getOrDefault(JGIT_CONF_COUNTING_MATCH_PATCH, DEFAULT_COUNTING_MATCH_PATCH);
        String pattern = config.getOrDefault(JGIT_CONF_COUNTING_PATTERN, DEFAULT_COUNTING_PATTERN);

        int commitCount = 0;

        Iterable<RevCommit> commits =
                head != null ? git.log().add(head).call() : git.log().call();
        List<RevCommit> all = new ArrayList<>();
        for (RevCommit c : commits) {
            all.add(c);
        }
        Collections.reverse(all);

        for (RevCommit c : all) {
            String message = c.getFullMessage();
            if (message.contains(matchMajor)) {
                major++;
                minor = 0;
                patch = 0;
                commitCount = 0;
            } else if (message.contains(matchMinor)) {
                minor++;
                patch = 0;
                commitCount = 0;
            } else if (message.contains(matchPatch)) {
                patch++;
                commitCount = 0;
            } else {
                commitCount++;
            }
        }

        String version = formatCountingVersion(pattern, major, minor, patch, commitCount);
        logger.debug("counting version resolved to: {}", version);
        return version;
    }

    /**
     * Formats a counting version using the given pattern.
     * <p>
     * Placeholders: {@code %M} (major), {@code %m} (minor), {@code %p} (patch), {@code %c} (commit count).
     * Parenthesised sections like {@code (-%c)} are removed entirely when commit count is 0,
     * and included (without parentheses) when commit count &gt; 0.
     */
    static String formatCountingVersion(String pattern, int major, int minor, int patch, int commitCount) {
        String result = pattern.replace("%M", Integer.toString(major))
                .replace("%m", Integer.toString(minor))
                .replace("%p", Integer.toString(patch))
                .replace("%c", Integer.toString(commitCount));
        if (commitCount != 0) {
            result = result.replace("(", "").replace(")", "");
        } else {
            result = result.replaceAll("\\([^)]*\\)", "");
        }
        return result;
    }

    protected VersionInformation getVersionFromGit(
            Map<String, String> properties, NisseConfiguration configuration, Git git, ObjectId head)
            throws GitAPIException, IOException {
        return resolveVersionFromGit(properties, configuration, git, head).getVersion();
    }

    /**
     * As {@link #getVersionFromGit}, but also reporting whether a release tag was found at all.
     */
    protected GitVersion resolveVersionFromGit(
            Map<String, String> properties, NisseConfiguration configuration, Git git, ObjectId head)
            throws GitAPIException, IOException {
        RevCommit lastCommit = getLastCommit(git, head);
        logger.debug("last commit: {}", lastCommit.toString());

        Iterable<RevCommit> commits =
                head != null ? git.log().add(head).call() : git.log().call();
        int count = 0;
        for (RevCommit commit : commits) {
            Optional<VersionInformation> ovi = getHighestVersionTagForCommit(configuration, git, commit);

            if (ovi.isPresent()) {
                VersionInformation vi = ovi.get();

                if (commit.equals(lastCommit)) {
                    return new GitVersion(vi, true);
                } else {
                    boolean conventionalCommits = Boolean.parseBoolean(configuration
                            .getConfiguration()
                            .getOrDefault(
                                    JGIT_CONF_SYSTEM_PROPERTY_CONVENTIONAL_COMMITS, DEFAULT_CONVENTIONAL_COMMITS));
                    if (conventionalCommits) {
                        increaseVersion(vi, highestBumpFrom(messagesSince(git, head, commit)));
                    } else {
                        boolean increasePatchVersion = Boolean.parseBoolean(configuration
                                .getConfiguration()
                                .getOrDefault(
                                        JGIT_CONF_SYSTEM_PROPERTY_INCREASE_PATCH_VERSION,
                                        DEFAULT_INCREASE_PATCH_VERSION));
                        if (increasePatchVersion) {
                            vi.setPatch(vi.getPatch() + 1);
                        }
                    }
                    boolean appendBuildNumber = Boolean.parseBoolean(configuration
                            .getConfiguration()
                            .getOrDefault(JGIT_CONF_SYSTEM_PROPERTY_APPEND_BUILD_NUMBER, DEFAULT_APPEND_BUILD_NUMBER));
                    if (appendBuildNumber) {
                        vi.setBuildNumber(count);
                    }
                    return new GitVersion(mayAddQualifier(properties, configuration, vi), true);
                }
            }
            count++;
        }
        return new GitVersion(
                mayAddQualifier(properties, configuration, new VersionInformation(defaultVersion + "-" + count)),
                false);
    }

    /**
     * The full messages of the commits reachable from {@code head} but not from {@code tagged}: the range
     * {@code tagged..head}.
     *
     * <p>Asking git for the range explicitly matters. The walk that finds the tag is ordered by commit date, so a
     * branch cut before the tag and merged after it is reached only <em>after</em> the tagged commit, and would
     * otherwise be missed. Reachability, not date, is what "since the last release" means. The tagged commit
     * itself is excluded by construction.
     */
    private List<String> messagesSince(Git git, ObjectId head, RevCommit tagged) throws GitAPIException, IOException {
        ObjectId until = head != null ? head : git.getRepository().resolve(Constants.HEAD);
        List<String> messages = new ArrayList<>();
        for (RevCommit commit : git.log().add(until).not(tagged.getId()).call()) {
            messages.add(commit.getFullMessage());
        }
        return messages;
    }

    /**
     * A version resolved from git history, and whether it came from a release tag at all. That cannot be
     * recognised from the value: {@link #defaultVersion} is also what increasing a {@code 0.0.x} tag's minor
     * produces.
     */
    protected static final class GitVersion {
        private final VersionInformation version;

        private final boolean fromReleaseTag;

        GitVersion(VersionInformation version, boolean fromReleaseTag) {
            this.version = version;
            this.fromReleaseTag = fromReleaseTag;
        }

        public VersionInformation getVersion() {
            return version;
        }

        public boolean isFromReleaseTag() {
            return fromReleaseTag;
        }
    }

    /**
     * The version component a set of commits calls for, ordered so that the highest wins.
     */
    enum Bump {
        PATCH,
        MINOR,
        MAJOR
    }

    /**
     * The highest increase called for by the given full commit messages, per Conventional Commits.
     * <p>
     * A message that is not a Conventional Commit still contributes {@link Bump#PATCH}: this mode replaces the
     * unconditional patch increment, so it must never increment less than that did.
     */
    static Bump highestBumpFrom(Collection<String> fullMessages) {
        Bump highest = Bump.PATCH;
        for (String message : fullMessages) {
            Bump bump = bumpFrom(message);
            if (bump.compareTo(highest) > 0) {
                highest = bump;
            }
            if (highest == Bump.MAJOR) {
                return highest;
            }
        }
        return highest;
    }

    /**
     * The increment called for by a single full commit message (subject and body).
     */
    static Bump bumpFrom(String fullMessage) {
        if (fullMessage == null || fullMessage.isEmpty()) {
            return Bump.PATCH;
        }
        int newline = fullMessage.indexOf('\n');
        String subject = newline < 0 ? fullMessage : fullMessage.substring(0, newline);
        Matcher matcher = CONVENTIONAL_COMMIT_SUBJECT.matcher(subject);
        if (!matcher.lookingAt()) {
            // Not a Conventional Commit, so it has no footers either. A BREAKING CHANGE line in the body of an
            // ordinary commit is prose: a quoted changelog, or the body git revert copies verbatim.
            return Bump.PATCH;
        }
        if (matcher.group("breaking") != null || hasBreakingFooter(fullMessage)) {
            return Bump.MAJOR;
        }
        return "feat".equalsIgnoreCase(matcher.group("type")) ? Bump.MINOR : Bump.PATCH;
    }

    /**
     * Whether the message's trailer block declares a breaking change. Only the last paragraph is searched, which
     * is where the specification puts footers; the same words earlier in the body are prose.
     */
    private static boolean hasBreakingFooter(String fullMessage) {
        int lastBlankLine = fullMessage.lastIndexOf("\n\n");
        return lastBlankLine >= 0
                && BREAKING_CHANGE_FOOTER
                        .matcher(fullMessage.substring(lastBlankLine + 2))
                        .find();
    }

    /**
     * Applies an increment, resetting the components below it as semantic versioning requires.
     */
    static void increaseVersion(VersionInformation vi, Bump bump) {
        if (bump == Bump.MAJOR) {
            vi.setMajor(vi.getMajor() + 1);
            vi.setMinor(0);
            vi.setPatch(0);
            // 1.2.3-rc1 becoming 2.0.0-rc1 would claim to be a candidate for a release that never had one.
            vi.setQualifier(null);
        } else if (bump == Bump.MINOR) {
            vi.setMinor(vi.getMinor() + 1);
            vi.setPatch(0);
            vi.setQualifier(null);
        } else {
            vi.setPatch(vi.getPatch() + 1);
        }
    }

    private Optional<VersionInformation> getHighestVersionTagForCommit(
            NisseConfiguration configuration, Git git, RevCommit commit) throws GitAPIException {
        // get tags use semantic version (X.Y.Z or vX.Y.Z) for commit
        List<String> versionTagsForCommit = getVersionedTagsForCommit(configuration, git, commit);
        logger.debug("commit {} {}: {}", commit.getId(), commit.getShortMessage(), versionTagsForCommit.toString());
        return findHighestVersion(versionTagsForCommit);
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
            throw new IllegalStateException(e);
        }
    }

    protected VersionInformation mayAddQualifier(
            Map<String, String> properties, NisseConfiguration configuration, VersionInformation vi) {
        String qualifier = null;
        boolean appendDirty = Boolean.parseBoolean(configuration
                .getConfiguration()
                .getOrDefault(JGIT_CONF_SYSTEM_PROPERTY_APPEND_DIRTY, DEFAULT_APPEND_DIRTY));
        if (appendDirty) {
            if (!Boolean.parseBoolean(properties.get(JGIT_CLEAN))) {
                qualifier = appendQualifier(
                        qualifier,
                        configuration
                                .getConfiguration()
                                .getOrDefault(JGIT_CONF_SYSTEM_PROPERTY_DIRTY_QUALIFIER, DEFAULT_DIRTY_QUALIFIER));
            }
        }
        boolean appendBranchName = Boolean.parseBoolean(configuration
                .getConfiguration()
                .getOrDefault(JGIT_CONF_SYSTEM_PROPERTY_APPEND_BRANCH_NAME, DEFAULT_APPEND_BRANCH_NAME));
        if (appendBranchName) {
            String localBranch = properties.get(JGIT_BRANCH_NAME);
            if (localBranch == null) {
                throw new IllegalStateException("Branch name configured to be qualifier, but is absent");
            }
            String sanitizedBranchName = sanitizeBranchName(localBranch);
            if (sanitizedBranchName == null || sanitizedBranchName.trim().isEmpty()) {
                throw new IllegalStateException("Branch name configured to be qualifier, but is empty");
            }
            qualifier = appendQualifier(qualifier, sanitizeBranchName(localBranch));
        }
        boolean appendSnapshot = Boolean.parseBoolean(configuration
                .getConfiguration()
                .getOrDefault(JGIT_CONF_SYSTEM_PROPERTY_APPEND_SNAPSHOT, DEFAULT_APPEND_SNAPSHOT));
        if (appendSnapshot) {
            qualifier = appendQualifier(qualifier, "SNAPSHOT");
        }

        if (qualifier != null) {
            vi.setQualifier(qualifier);
        }
        return vi;
    }

    protected String appendQualifier(String prevQualifier, String appendedQualifier) {
        if (appendedQualifier == null) {
            return prevQualifier;
        }
        if (prevQualifier == null) {
            return appendedQualifier;
        } else {
            return prevQualifier + "-" + appendedQualifier;
        }
    }

    /**
     * Find version hint from tags matching the configured pattern.
     * Version hint tags provide a way to specify the next version without requiring
     * environment variable changes, making builds more reproducible.
     *
     * @param configuration The Nisse configuration
     * @param git The git repository
     * @return Optional version string extracted from hint tags
     * @throws GitAPIException if git operations fail
     */
    protected Optional<String> findVersionHint(NisseConfiguration configuration, Git git, ObjectId head)
            throws GitAPIException {
        String hintPattern = configuration
                .getConfiguration()
                .getOrDefault(JGIT_CONF_SYSTEM_PROPERTY_VERSION_HINT_PATTERN, DEFAULT_VERSION_HINT_PATTERN);

        List<String> hintVersions = findVersionHintTags(git, hintPattern, head);
        logger.debug("Found version hint tags: {}", hintVersions);

        return findHighestVersionFromHints(hintVersions);
    }

    /**
     * Find all tags that match the version hint pattern and extract versions from them.
     *
     * @param git The git instance
     * @param hintPattern The pattern to match (e.g., "${version}-SNAPSHOT")
     * @return List of version strings extracted from matching tags
     * @throws GitAPIException if git operations fail
     */
    protected List<String> findVersionHintTags(Git git, String hintPattern, ObjectId head) throws GitAPIException {
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

        Repository repository = git.getRepository();
        return git.tagList().call().stream()
                .filter(tag -> hintTagPattern.matcher(tag.getName()).matches())
                .filter(tag -> isReachableFrom(repository, tag, head))
                .map(Ref::getName)
                .map(hintTagPattern::matcher)
                .filter(m -> m.matches() && m.groupCount() > 0)
                .map(m -> m.group(1)) // Extract the version part
                .collect(Collectors.toList());
    }

    private boolean isReachableFrom(Repository repository, Ref tag, ObjectId head) {
        if (head == null) {
            return true;
        }
        try {
            Ref peeledRef = repository.getRefDatabase().peel(tag);
            ObjectId tagObjectId =
                    (peeledRef.getPeeledObjectId() != null ? peeledRef.getPeeledObjectId() : tag.getObjectId());
            try (RevWalk revWalk = new RevWalk(repository)) {
                RevCommit tagCommit = revWalk.parseCommit(tagObjectId);
                RevCommit headCommit = revWalk.parseCommit(head);
                return revWalk.isMergedInto(tagCommit, headCommit);
            }
        } catch (IOException e) {
            logger.debug("Could not check reachability for tag {}: {}", tag.getName(), e.getMessage());
            return false;
        }
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

    private static final Pattern UNSAFE_CHARS = Pattern.compile("[^A-Za-z0-9._-]");

    /**
     * Turns a branch name into a path fragment that is safe on all platforms.
     */
    static String sanitizeBranchName(String branchName) {
        List<String> segments = new ArrayList<>();
        for (String segment : branchName.split("/")) {
            String cleaned = UNSAFE_CHARS.matcher(segment).replaceAll("-");
            segments.add(cleaned);
        }
        String result = String.join("-", segments);
        while (result.endsWith("-")) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }
}
