package eu.maveniverse.maven.nisse.source.propertyfile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class PropertyFilePropertyKeySourceTest {
    @Test
    void smoke(@TempDir Path tempDir) throws Exception {
        Path propertyFile = tempDir.resolve("property.properties");
        Files.write(propertyFile, Arrays.asList("one=en", "two=to", "three=tre"));
        try {
            HashMap<String, String> conf = new HashMap<>();
            conf.put(
                    PropertyFilePropertyKeySource.FILE_NAME,
                    propertyFile.toAbsolutePath().toString());
            new PropertyFilePropertyKeySource()
                    .providedKeys(conf)
                    .forEach(k -> System.out.println(k + " = " + k.getValue().orElse("")));
        } finally {
            System.clearProperty(PropertyFilePropertyKeySource.FILE_NAME);
        }
    }
}
