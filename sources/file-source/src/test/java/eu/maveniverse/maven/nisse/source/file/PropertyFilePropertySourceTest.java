package eu.maveniverse.maven.nisse.source.file;

import eu.maveniverse.maven.nisse.core.internal.SimpleNisseConfiguration;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class PropertyFilePropertySourceTest {
    @Test
    void smoke(@TempDir Path tempDir) throws Exception {
        Path propertyFile = tempDir.resolve("property.properties");
        Files.write(propertyFile, Arrays.asList("one=en", "two=to", "three=tre"));
        try {
            HashMap<String, String> conf = new HashMap<>();
            conf.put(
                    PropertyFilePropertySource.FILE_NAME,
                    propertyFile.toAbsolutePath().toString());
            new PropertyFilePropertySource()
                    .getProperties(SimpleNisseConfiguration.builder()
                            .withUserProperties(conf)
                            .build())
                    .forEach((k, v) -> System.out.println(k + " = " + v));
        } finally {
            System.clearProperty(PropertyFilePropertySource.FILE_NAME);
        }
    }
}
