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
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.inject.Named;
import javax.inject.Singleton;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;

@Singleton
@Named(JGitPropertyKeySource.NAME)
public class JGitPropertyKeySource implements PropertyKeySource {
    public static final String NAME = "jgit";

    private static final String JGIT_COMMIT = "jgit.commit";
    private static final String JGIT_DATE = "jgit.date";
    private static final String JGIT_AUTHOR = "jgit.author";
    private static final String JGIT_COMMITTER = "jgit.committer";

    private final List<PropertyKey> propertyKeys = Collections.unmodifiableList(Arrays.asList(
            new JGitPropertyKey(this, JGIT_COMMIT),
            new JGitPropertyKey(this, JGIT_DATE),
            new JGitPropertyKey(this, JGIT_AUTHOR),
            new JGitPropertyKey(this, JGIT_COMMITTER)));

    private final ConcurrentHashMap<String, Map<String, String>> cache = new ConcurrentHashMap<>();

    String getValue(String key) {
        return cache.computeIfAbsent("data", k -> {
                    HashMap<String, String> data = new HashMap<>();
                    try (Repository repository = new FileRepositoryBuilder()
                            .readEnvironment() // scan environment GIT_* variables
                            .findGitDir() // scan up the file system tree
                            .build(); ) {

                        if (repository.getDirectory() != null) {
                            RevCommit lastCommit = new Git(repository)
                                    .log()
                                    .setMaxCount(1)
                                    .call()
                                    .iterator()
                                    .next();

                            data.put(JGIT_COMMIT, lastCommit.getName());
                            data.put(
                                    JGIT_DATE,
                                    ZonedDateTime.ofInstant(
                                                    Instant.ofEpochSecond(lastCommit.getCommitTime()),
                                                    lastCommit
                                                            .getAuthorIdent()
                                                            .getTimeZone()
                                                            .toZoneId())
                                            .format(DateTimeFormatter.ofPattern("EEE MMM dd HH:mm:ss yyyy Z")));
                            data.put(
                                    JGIT_COMMITTER,
                                    lastCommit
                                                    .getCommitterIdent()
                                                    .toExternalString()
                                                    .split(">")[0] + ">");
                            data.put(
                                    JGIT_AUTHOR,
                                    lastCommit
                                                    .getAuthorIdent()
                                                    .toExternalString()
                                                    .split(">")[0] + ">");
                        }
                    } catch (Exception e) {
                        // ignore
                    }
                    return data;
                })
                .get(key);
    }

    @Override
    public Collection<PropertyKey> providedKeys() {
        return propertyKeys;
    }
}
