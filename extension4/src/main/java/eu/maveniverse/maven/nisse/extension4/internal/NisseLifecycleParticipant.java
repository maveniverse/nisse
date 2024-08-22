package eu.maveniverse.maven.nisse.extension4.internal;

import static java.util.Objects.requireNonNull;

import eu.maveniverse.maven.nisse.core.NisseConfiguration;
import eu.maveniverse.maven.nisse.core.internal.SimpleNisseConfiguration;
import java.io.IOException;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import org.apache.maven.AbstractMavenLifecycleParticipant;
import org.apache.maven.MavenExecutionException;
import org.apache.maven.execution.MavenSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
@Named
class NisseLifecycleParticipant extends AbstractMavenLifecycleParticipant {
    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final NissePropertyInliner inliner;

    @Inject
    public NisseLifecycleParticipant(NissePropertyInliner inliner) {
        this.inliner = requireNonNull(inliner, "inliner");
    }

    @Override
    public void afterProjectsRead(MavenSession session) throws MavenExecutionException {
        NisseConfiguration configuration = SimpleNisseConfiguration.builder()
                .withSystemProperties(session.getSystemProperties())
                .withUserProperties(session.getUserProperties())
                .withCurrentWorkingDirectory(session.getRequest().getTopDirectory())
                .build();
        for (String inlinedKey : configuration.getInlinedPropertyKeys()) {
            if (inliner.inlinedKeys(session).add(inlinedKey)) {
                logger.info("Nisse property {} configured for inlining", inlinedKey);
            }
        }
        try {
            inliner.mayInlinePom(session, session.getProjects());
        } catch (IOException e) {
            throw new MavenExecutionException("Nisse failed to inline", e);
        }
    }
}
