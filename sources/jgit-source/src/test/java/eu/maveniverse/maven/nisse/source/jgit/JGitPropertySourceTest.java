package eu.maveniverse.maven.nisse.source.jgit;

import eu.maveniverse.maven.nisse.core.internal.SimpleNisseConfiguration;
import java.io.IOException;
import org.junit.jupiter.api.Test;

public class JGitPropertySourceTest {
    @Test
    void smoke() throws IOException {
        new JGitPropertySource()
                .getProperties(SimpleNisseConfiguration.builder().build())
                .forEach((k, v) -> System.out.println(k + " = " + v));
    }
}
