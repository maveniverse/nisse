/*
 * Copyright (c) 2023-2024 Maveniverse Org.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 */
package eu.maveniverse.maven.nisse.source.jgit;

import eu.maveniverse.maven.nisse.core.PropertyKey;
import eu.maveniverse.maven.nisse.core.PropertyKeySource;
import eu.maveniverse.maven.nisse.core.SimplePropertyKey;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
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
@Named(JGitPropertyKeySource.NAME)
public class JGitPropertyKeySource implements PropertyKeySource {
    public static final String NAME = "jgit";

    private static final String PREFIX = NAME + ".";
    private static final String JGIT_COMMIT = PREFIX + "commit";
    private static final String JGIT_DATE = PREFIX + "date";
    private static final String JGIT_AUTHOR = PREFIX + "author";
    private static final String JGIT_COMMITTER = PREFIX + "committer";

    @Override
    public Collection<PropertyKey> providedKeys(Map<String, String> config) {
        ArrayList<PropertyKey> result = new ArrayList<>();
        try (Repository repository = new FileRepositoryBuilder()
                .readEnvironment() // scan environment GIT_* variables
                .findGitDir() // scan up the file system tree
                .build()) {

            if (repository.getDirectory() != null) {
                RevCommit lastCommit = new Git(repository)
                        .log()
                        .setMaxCount(1)
                        .call()
                        .iterator()
                        .next();

                result.add(new SimplePropertyKey(this, JGIT_COMMIT, lastCommit.getName()));
                result.add(new SimplePropertyKey(
                        this,
                        JGIT_DATE,
                        ZonedDateTime.ofInstant(
                                        Instant.ofEpochSecond(lastCommit.getCommitTime()),
                                        lastCommit
                                                .getAuthorIdent()
                                                .getTimeZone()
                                                .toZoneId())
                                .format(DateTimeFormatter.ofPattern("EEE MMM dd HH:mm:ss yyyy Z"))));
                result.add(new SimplePropertyKey(
                        this,
                        JGIT_COMMITTER,
                        lastCommit.getCommitterIdent().toExternalString().split(">")[0] + ">"));
                result.add(new SimplePropertyKey(
                        this,
                        JGIT_AUTHOR,
                        lastCommit.getAuthorIdent().toExternalString().split(">")[0] + ">"));
            }
        } catch (Exception e) {
            // ignore
        }
        return Collections.unmodifiableList(result);
    }
}
