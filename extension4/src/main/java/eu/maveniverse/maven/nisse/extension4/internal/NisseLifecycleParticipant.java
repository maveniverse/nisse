package eu.maveniverse.maven.nisse.extension4.internal;

import javax.inject.Named;
import javax.inject.Singleton;

// TODO: AbstractMavenLifecycleParticipant???
@Singleton
@Named
class NisseLifecycleParticipant { // extends AbstractMavenLifecycleParticipant {
    //    private final Logger logger = LoggerFactory.getLogger(getClass());
    //    private final NissePropertyInliner inliner;
    //
    //    @Inject
    //    public NisseLifecycleParticipant(NissePropertyInliner inliner) {
    //        this.inliner = requireNonNull(inliner, "inliner");
    //    }
    //
    //    @Override
    //    public void afterProjectsRead(MavenSession session) throws MavenExecutionException {
    //        NisseConfiguration configuration = SimpleNisseConfiguration.builder()
    //                .withSystemProperties(session.getSystemProperties())
    //                .withUserProperties(session.getUserProperties())
    //                .withCurrentWorkingDirectory(Paths.get(session.getRequest().getBaseDirectory()))
    //                .build();
    //        for (String inlinedKey : configuration.getInlinedPropertyKeys()) {
    //            if (inliner.inlinedKeys(session).add(inlinedKey)) {
    //                logger.info("Nisse property {} configured for inlining", inlinedKey);
    //            }
    //        }
    //        try {
    //            inliner.mayInlinePom(session, session.getProjects());
    //        } catch (IOException e) {
    //            throw new MavenExecutionException("Nisse failed to inline", e);
    //        }
    //    }
}
