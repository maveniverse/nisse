package eu.maveniverse.maven.nisse.source.osdetector;

import eu.maveniverse.maven.nisse.core.simple.SimpleNisseConfiguration;
import java.io.IOException;
import org.junit.jupiter.api.Test;

public class OsDetectorPropertySourceTest {
    @Test
    void smoke() throws IOException {
        new OsDetectorPropertySource()
                .getProperties(SimpleNisseConfiguration.builder().build())
                .forEach((k, v) -> System.out.println(k + " = " + v));
    }
}
