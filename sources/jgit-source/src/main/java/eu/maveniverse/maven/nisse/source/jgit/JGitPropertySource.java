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
import java.io.IOException;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import javax.inject.Named;
import javax.inject.Singleton;
import org.apache.maven.artifact.versioning.ComparableVersion;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.ObjectId;
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

    private static final String JGIT_DATE = "date";

    private static final String JGIT_AUTHOR = "author";

    private static final String JGIT_COMMITTER = "committer";

    private static final String JGIT_DYNAMIC_VERSION = "dynamic.version";

    // standard semantic versioning with an optional 'v' prefix
    protected static final Pattern TAG_VERSION_PATTERN = Pattern.compile("refs/tags/(?:v)?((\\d+\\.\\d+\\.\\d+)(.*))");

    protected String defaultVersion = "0.0.1";

    /**
     * Whether the SNAPSHOT qualifier shall be apppended or not.
     *
     * -Dnisse.jgit.appendSnapshot
     *
     */
    private static final String JGIT_CONF_SYSTEM_PROPERTY_APPEND_SNAPSHOT = "nisse.jgit.appendSnapshot";

    private static final boolean DEFAULT_APPEND_SNAPSHOT = true;

    /**
     * Use this version instead of resolving from SCM tag information.
     *
     * -Dnisse.jgit.useVersion
     *
     */
    private static final String JGIT_CONF_SYSTEM_PROPERTY_USE_VERSION = "nisse.jgit.useVersion";

    private final Logger logger = LoggerFactory.getLogger(getClass());

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public Map<String, String> getProperties(NisseConfiguration configuration) {
        HashMap<String, String> result = new HashMap<>();
        try (Repository repository = new FileRepositoryBuilder()
                .readEnvironment() // scan
                // environment
                // GIT_*
                // variables
                .findGitDir(configuration.getCurrentWorkingDirectory().toFile()) // scan up
                // the
                // file
                // system
                // tree
                .build()) {

            if (repository.getDirectory() != null) {
                RevCommit lastCommit = new Git(repository)
                        .log()
                        .setMaxCount(1)
                        .call()
                        .iterator()
                        .next();

                result.put(JGIT_COMMIT, lastCommit.getName());
                result.put(
                        JGIT_DATE,
                        ZonedDateTime.ofInstant(
                                        Instant.ofEpochSecond(lastCommit.getCommitTime()),
                                        lastCommit
                                                .getAuthorIdent()
                                                .getTimeZone()
                                                .toZoneId())
                                .format(DateTimeFormatter.ofPattern("EEE MMM dd HH:mm:ss yyyy Z")));
                result.put(
                        JGIT_COMMITTER,
                        lastCommit.getCommitterIdent().toExternalString().split(">")[0] + ">");
                result.put(
                        JGIT_AUTHOR,
                        lastCommit.getAuthorIdent().toExternalString().split(">")[0] + ">");
                result.put(JGIT_DYNAMIC_VERSION, resolveDynamicVersion(configuration, repository));
            }
        } catch (Exception e) {
            logger.error("Exception in JGitPropertySource: {}", e.toString());
        }
        return Collections.unmodifiableMap(result);
    }

    public String resolveDynamicVersion(NisseConfiguration configuration, Repository repository) throws Exception {
        VersionInformation vi;

        Optional<String> useVersion = Optional.ofNullable(
                (String) configuration.getConfiguration().get(JGIT_CONF_SYSTEM_PROPERTY_USE_VERSION));

        // Optional<String> mayBeVersion = Optional.ofNullable(useVersion);
        vi = (useVersion.isPresent()
                ? new VersionInformation(useVersion.get())
                : getVersionFromGit(configuration, repository));

        logger.debug("DVX: {}", vi.toString());
        return vi.toString();
    }

    protected VersionInformation getVersionFromGit(NisseConfiguration configuration, Repository repository)
            throws Exception {

        RevCommit latestCommit = getLatestCommit(repository);

        return getVersionFromCommit(configuration, repository, latestCommit);
    }

    protected RevCommit getLatestCommit(Repository repository) throws Exception {
        try (RevWalk revWalk = new RevWalk(repository)) {
            ObjectId head = repository.resolve("HEAD");

            if (head == null) {
                throw new Exception("SCM repo has no head/commits.");
            }

            return revWalk.parseCommit(head);
        } catch (IOException e) {
            throw new Exception("SCM repo most likely has no commits.", e);
        }
    }

    protected VersionInformation getVersionFromCommit(
            NisseConfiguration configuration, Repository repository, RevCommit latestCommit) throws Exception {
        try (Git git = Git.wrap(repository)) {

            // get tags use semantic version (X.Y.Z or vX.Y.Z) for latest commit
            List<String> versionTagsForLatestCommit = getVersionedTagsForCommit(git, latestCommit);

            Optional<VersionInformation> ovi = findHighestVersion(versionTagsForLatestCommit);
            // use latest commit's version tag
            if (ovi.isPresent()) {
                return ovi.get();
            }

            Iterable<RevCommit> commits = git.log().call();
            int count = 0;
            for (RevCommit commit : commits) {
                logger.debug("commit #{} {}", count, commit.getShortMessage());

                versionTagsForLatestCommit = getVersionedTagsForCommit(git, commit);
                ovi = findHighestVersion(versionTagsForLatestCommit);

                if (ovi.isPresent()) {
                    VersionInformation vi = ovi.get();
                    vi.setPatch(vi.getPatch() + 1);
                    vi.setBuildNumber(count);

                    return addSnapshotQualifier(configuration, vi);
                }
                count++;
            }
            return addSnapshotQualifier(configuration, new VersionInformation(defaultVersion + "-" + count));
        } catch (GitAPIException e) {
            throw new Exception("Error reading Git information.", e);
        }
    }

    protected List<String> getVersionedTagsForCommit(Git git, RevCommit commit) throws GitAPIException {
        return git.tagList().call().stream()
                .filter(tag -> tag.getObjectId().equals(commit.getId()))
                .map(ref -> ref.getName())
                .filter(tagName -> {
                    Matcher matcher = TAG_VERSION_PATTERN.matcher(tagName);
                    return matcher.matches() && matcher.groupCount() > 0;
                })
                .map(tagName -> {
                    Matcher matcher = TAG_VERSION_PATTERN.matcher(tagName);
                    matcher.matches();
                    return matcher.group(1);
                })
                .collect(Collectors.toList());
    }

    protected Optional<VersionInformation> findHighestVersion(List<String> versionTags) {
        return versionTags.stream()
                .max((v1, v2) -> new ComparableVersion(v1).compareTo(new ComparableVersion(v2)))
                .map(VersionInformation::new);
    }

    protected VersionInformation addSnapshotQualifier(NisseConfiguration configuration, VersionInformation vi) {
        boolean appendSnapshot = Boolean.parseBoolean((String) configuration
                .getConfiguration()
                .getOrDefault(JGIT_CONF_SYSTEM_PROPERTY_APPEND_SNAPSHOT, Boolean.toString(DEFAULT_APPEND_SNAPSHOT)));

        if (appendSnapshot) {
            vi.setQualifier("SNAPSHOT");
        }
        return vi;
    }
}
