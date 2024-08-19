/*
 * Copyright (c) 2023-2024 Maveniverse Org.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 */
package eu.maveniverse.maven.nisse.extension4.internal;

import eu.maveniverse.maven.nisse.core.NisseConfiguration;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.inject.Named;
import javax.inject.Singleton;
import org.apache.maven.api.Project;
import org.apache.maven.api.Session;
import org.apache.maven.api.SessionData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
@Named
public final class NissePropertyInliner {
    private static final SessionData.Key<Set> NEEDS_INLINING_LIST =
            SessionData.key(Set.class, NisseConfiguration.PROPERTY_PREFIX + "needs-inlining");
    private final Logger logger = LoggerFactory.getLogger(getClass());

    public Collection<String> inlinedKeys(Session session) {
        return (Set<String>) session.getData()
                .computeIfAbsent(NissePropertyInliner.NEEDS_INLINING_LIST, ConcurrentHashMap::newKeySet);
    }

    void mayInlinePom(Session session, Collection<Project> mavenProjects) throws IOException {
        Collection<String> inlinedKeys = inlinedKeys(session);
        if (!inlinedKeys.isEmpty()) {
            Map<String, String> inlinedProperties = new HashMap<>();
            for (String inlinedKey : inlinedKeys) {
                inlinedProperties.put(
                        "${" + inlinedKey + "}", session.getUserProperties().get(inlinedKey));
            }
            logger.info("Nisse inlining following properties:");
            for (Map.Entry<String, String> entry : inlinedProperties.entrySet()) {
                logger.info(" * {}={}", entry.getKey(), entry.getValue());
            }
            logger.info("Checking POMs for inlining:");
            for (Project mavenProject : mavenProjects) {
                Path pomPath = mavenProject.getPomPath();
                if (isAnyKeyPresent(inlinedProperties, pomPath)) {
                    // needs rewrite
                    logger.info(" * {}:{} needs inlining", mavenProject.getGroupId(), mavenProject.getArtifactId());
                    Path inlinedPomPath = Paths.get(mavenProject.getBuild().getDirectory())
                            .resolve("inlined-" + pomPath.getFileName());
                    Files.createDirectories(inlinedPomPath.getParent());
                    inline(pomPath, inlinedPomPath, inlinedProperties);
                    // TODO: ???
                    // mavenProject.setFile(inlinedPomPath.toFile());
                }
            }
        } else {
            logger.info("No need for inlining");
        }
    }

    private boolean isAnyKeyPresent(Map<String, String> inlinedProperties, Path file) throws IOException {
        try (Stream<String> lines = Files.lines(file, StandardCharsets.UTF_8)) {
            return lines.anyMatch(l -> {
                for (String placeholder : inlinedProperties.keySet()) {
                    if (l.contains(placeholder)) {
                        return true;
                    }
                }
                return false;
            });
        }
    }

    private void inline(Path sourcePom, Path targetPom, Map<String, String> inlinedProperties) throws IOException {
        Function<String, String> replacer = line -> {
            if (line.contains("${") && line.contains("}")) {
                String processed = line;
                for (Map.Entry<String, String> entry : inlinedProperties.entrySet()) {
                    processed = processed.replace(entry.getKey(), entry.getValue());
                }
                return processed;
            }
            return line;
        };

        try (Stream<String> lines =
                Files.lines(sourcePom, StandardCharsets.UTF_8).map(replacer)) {
            Files.write(targetPom, lines.collect(Collectors.toList()), StandardCharsets.UTF_8);
        }
    }
}
