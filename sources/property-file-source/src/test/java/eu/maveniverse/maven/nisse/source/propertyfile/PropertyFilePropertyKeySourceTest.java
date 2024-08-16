package eu.maveniverse.maven.nisse.source.propertyfile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class PropertyFilePropertyKeySourceTest {
    @Test
    void smoke(@TempDir Path tempDir) throws Exception {
        Path propertyFile = tempDir.resolve("property.properties");
        Files.write(propertyFile, Arrays.asList("one=einz", "two=zwei"));
        new PropertyFilePropertyKeySource(propertyFile.toAbsolutePath().toString())
                .providedKeys()
                .forEach(k -> System.out.println(k + " = " + k.getValue().orElse("")));
    }
}
