package eu.maveniverse.maven.nisse.source.osdetector;

import org.junit.jupiter.api.Test;

public class OsDetectorPropertyKeySourceTest {
    @Test
    void smoke() {
        new OsDetectorPropertyKeySource()
                .providedKeys()
                .forEach(k -> System.out.println(k + " = " + k.getValue().orElse("")));
    }
}
