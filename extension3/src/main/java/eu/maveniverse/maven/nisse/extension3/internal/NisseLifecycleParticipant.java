package eu.maveniverse.maven.nisse.extension3.internal;

import static java.util.Objects.requireNonNull;

import java.io.IOException;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import org.apache.maven.AbstractMavenLifecycleParticipant;
import org.apache.maven.MavenExecutionException;
import org.apache.maven.execution.MavenSession;

@Singleton
@Named
class NisseLifecycleParticipant extends AbstractMavenLifecycleParticipant {
    private final NissePropertyInliner inliner;

    @Inject
    public NisseLifecycleParticipant(NissePropertyInliner inliner) {
        this.inliner = requireNonNull(inliner, "inliner");
    }

    @Override
    public void afterProjectsRead(MavenSession session) throws MavenExecutionException {
        try {
            inliner.mayInlinePom(session, session.getProjects());
        } catch (IOException e) {
            throw new MavenExecutionException("Nisse failed to inline", e);
        }
    }
}
