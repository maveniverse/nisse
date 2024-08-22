package eu.maveniverse.maven.nisse.plugin4;

import eu.maveniverse.maven.nisse.core.NisseConfiguration;
import eu.maveniverse.maven.nisse.core.NisseManager;
import eu.maveniverse.maven.nisse.core.internal.SimpleNisseConfiguration;
import org.apache.maven.api.Project;
import org.apache.maven.api.Session;
import org.apache.maven.api.di.Inject;
import org.apache.maven.api.plugin.MojoException;
import org.apache.maven.api.plugin.annotations.Mojo;
import org.apache.maven.api.services.ProjectManager;

/**
 * Nisse inject-properties Mojo that injects created properties into project.
 */
@Mojo(name = "inject-properties")
public class InjectPropertiesMojo implements org.apache.maven.api.plugin.Mojo {
    @Inject
    private Project mavenProject;

    @Inject
    private Session mavenSession;

    @Inject
    private NisseManager nisseManager;

    @Override
    public void execute() throws MojoException {
        NisseConfiguration configuration = SimpleNisseConfiguration.builder()
                .withSystemProperties(mavenSession.getSystemProperties())
                .withUserProperties(mavenSession.getUserProperties())
                .withCurrentWorkingDirectory(mavenSession.getTopDirectory())
                .build();
        ProjectManager projectManager = mavenSession.getService(ProjectManager.class);
        nisseManager.createProperties(configuration).forEach((k, v) -> projectManager.setProperty(mavenProject, k, v));
    }
}
