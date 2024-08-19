/*
 * Copyright (c) 2023-2024 Maveniverse Org.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 */
package eu.maveniverse.maven.nisse.extension3.internal;

import static java.util.Objects.requireNonNull;

import eu.maveniverse.maven.nisse.core.NisseConfiguration;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.InputSource;
import org.apache.maven.model.Model;
import org.apache.maven.model.building.FileModelSource;
import org.apache.maven.model.building.ModelProcessor;
import org.apache.maven.project.MavenProject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
@Named
final class NissePropertyInliner {
    private static final String NEEDS_INLINING_LIST = NisseConfiguration.PROPERTY_PREFIX + "needs-inlining";
    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final ModelProcessor modelProcessor;

    @Inject
    public NissePropertyInliner(ModelProcessor modelProcessor) {
        this.modelProcessor = requireNonNull(modelProcessor, "modelProcessor");
    }

    @SuppressWarnings("unchecked")
    Collection<String> inlinedKeys(MavenSession session) {
        return (CopyOnWriteArrayList<String>) session.getRepositorySession()
                .getData()
                .computeIfAbsent(NissePropertyInliner.NEEDS_INLINING_LIST, CopyOnWriteArrayList::new);
    }

    void mayInlinePom(MavenSession session, Collection<MavenProject> mavenProjects) throws IOException {
        Collection<String> inlinedKeys = inlinedKeys(session);
        if (!inlinedKeys.isEmpty()) {
            logger.info("Nisse inlining properties: {}", inlinedKeys);
            for (MavenProject mavenProject : mavenProjects) {
                Path pomPath = mavenProject.getFile().toPath();
                Model model = readProjectModel(pomPath);
                for (String key : inlinedKeys) {
                    String keyPlaceholder = "${" + key + "}";
                    if (keyPlaceholder.equals(model.getVersion())) {
                        // needs rewrite
                        logger.info(" * {} needs inlining of {}", mavenProject.getId(), key);
                        Path inlinedPomPath = Paths.get(mavenProject.getBuild().getDirectory());
                        Files.createDirectories(inlinedPomPath);
                        Files.copy(pomPath, inlinedPomPath, StandardCopyOption.REPLACE_EXISTING);
                    }
                }
            }
        } else {
            logger.info("No need for inlining");
        }
    }

    private Model readProjectModel(Path pom) throws IOException {
        FileModelSource modelSource = new FileModelSource(pom.toFile());
        Map<String, Object> options = new HashMap<>();
        options.put(ModelProcessor.IS_STRICT, true);
        options.put(ModelProcessor.INPUT_SOURCE, new InputSource());
        options.put(ModelProcessor.SOURCE, modelSource);
        return modelProcessor.read(modelSource.getInputStream(), options);
    }
}
