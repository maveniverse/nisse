package eu.maveniverse.maven.nisse.plugin3;

import eu.maveniverse.maven.nisse.core.NisseConfiguration;
import eu.maveniverse.maven.nisse.core.NisseManager;
import eu.maveniverse.maven.nisse.core.internal.SimpleNisseConfiguration;
import java.nio.file.Paths;
import java.util.Map;
import javax.inject.Inject;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.project.MavenProject;

/**
 * Nisse inject-properties Mojo that injects created properties into project.
 */
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
                .withSessionRootDirectory(mavenSession
                        .getRequest()
                        .getMultiModuleProjectDirectory()
                        .toPath())
                .build();
        Map<String, String> properties = nisseManager.createProperties(configuration);
        getLog().info("Injecting " + properties.size() + " properties");
        properties.forEach((k, v) -> mavenProject.getProperties().setProperty(k, v));
    }
}
