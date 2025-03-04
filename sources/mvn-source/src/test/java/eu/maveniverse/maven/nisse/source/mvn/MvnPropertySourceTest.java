package eu.maveniverse.maven.nisse.source.mvn;

import eu.maveniverse.maven.nisse.core.internal.SimpleNisseConfiguration;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

public class MvnPropertySourceTest {
    @Test
    void smoke() throws IOException {
        Map<String, String> up = new HashMap<>();

        up.clear();
        up.put("maven.version", "3.9.9");
        up.put(
                "maven.home",
                Paths.get("src/test/resources/mvn399").toAbsolutePath().toString());
        System.out.println(up);
        new MvnPropertySource()
                .getProperties(SimpleNisseConfiguration.builder()
                        .withUserProperties(up)
                        .build())
                .forEach((k, v) -> System.out.println(k + " = " + v));

        up.clear();
        up.put("maven.version", "4.0.0-beta-3");
        up.put(
                "maven.home",
                Paths.get("src/test/resources/mvn400-beta-3").toAbsolutePath().toString());
        System.out.println(up);
        new MvnPropertySource()
                .getProperties(SimpleNisseConfiguration.builder()
                        .withUserProperties(up)
                        .build())
                .forEach((k, v) -> System.out.println(k + " = " + v));
    }
}
