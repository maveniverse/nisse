package eu.maveniverse.maven.nisse.source.osdetector;

import java.util.Collections;
import org.junit.jupiter.api.Test;

public class OsDetectorPropertyKeySourceTest {
    @Test
    void smoke() {
        new OsDetectorPropertyKeySource()
                .providedKeys(Collections.emptyMap())
                .forEach(k -> System.out.println(k + " = " + k.getValue().orElse("")));
    }
}
