package eu.maveniverse.maven.nisse.plugin4;

import eu.maveniverse.maven.nisse.core.NisseConfiguration;
import eu.maveniverse.maven.nisse.core.NisseManager;
import eu.maveniverse.maven.nisse.core.internal.SimpleNisseConfiguration;
import java.nio.file.Paths;
import javax.inject.Inject;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.project.MavenProject;

@Mojo(name = "inject-properties", threadSafe = true)
public class InjectPropertiesMojo extends AbstractMojo {
    @Inject
    private MavenProject mavenProject;

    @Inject
    private MavenSession mavenSession;

    @Inject
    private NisseManager nisseManager;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        NisseConfiguration configuration = SimpleNisseConfiguration.builder()
                .withSystemProperties(mavenSession.getSystemProperties())
                .withUserProperties(mavenSession.getUserProperties())
                .withCurrentWorkingDirectory(Paths.get(mavenSession.getRequest().getBaseDirectory()))
                .build();
        nisseManager
                .createProperties(configuration)
                .forEach((k, v) -> mavenProject.getProperties().setProperty(k, v));
    }
}
