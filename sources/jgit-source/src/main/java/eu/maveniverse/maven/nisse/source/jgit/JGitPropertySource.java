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
import java.util.HashMap;
import java.util.Map;
import javax.inject.Named;
import javax.inject.Singleton;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;

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

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public Map<String, String> getProperties(NisseConfiguration configuration) {
        HashMap<String, String> result = new HashMap<>();
        try (Repository repository = new FileRepositoryBuilder()
                .readEnvironment() // scan environment GIT_* variables
                .findGitDir(configuration.getCurrentWorkingDirectory().toFile()) // scan up the file system tree
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
            }
        } catch (Exception e) {
            // ignore
        }
        return Collections.unmodifiableMap(result);
    }
}
