package eu.maveniverse.maven.nisse.core.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;

import eu.maveniverse.maven.nisse.core.NisseConfiguration;
import eu.maveniverse.maven.nisse.core.PropertyKeyNamingStrategies;
import eu.maveniverse.maven.nisse.core.PropertySource;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import org.junit.jupiter.api.Test;

public class PropertyKeyNamingStrategiesTest {
    @Test
    void smoke() throws IOException {
        Map<String, List<String>> translations = new HashMap<>();
        translations.put("one.first", Collections.singletonList("first.theOnly"));
        translations.put("two.first", Arrays.asList("second.one", "second.two"));
        BiFunction<PropertySource, String, List<String>> strategy = PropertyKeyNamingStrategies.combine(Arrays.asList(
                PropertyKeyNamingStrategies.translated(
                        translations,
                        PropertyKeyNamingStrategies.sourcePrefixed(),
                        PropertyKeyNamingStrategies.nisseDefault()),
                PropertyKeyNamingStrategies.osDetector()));

        PropertySource oneSource = new PropertySource() {
            @Override
            public String getName() {
                return "one";
            }

            @Override
            public Map<String, String> getProperties(NisseConfiguration configuration) {
                throw new UnsupportedOperationException("not implemented");
            }
        };
        PropertySource twoSource = new PropertySource() {
            @Override
            public String getName() {
                return "two";
            }

            @Override
            public Map<String, String> getProperties(NisseConfiguration configuration) {
                throw new UnsupportedOperationException("not implemented");
            }
        };
        PropertySource osSource = new PropertySource() {
            @Override
            public String getName() {
                return "os";
            }

            @Override
            public Map<String, String> getProperties(NisseConfiguration configuration) {
                throw new UnsupportedOperationException("not implemented");
            }
        };
        List<String> keys;

        keys = strategy.apply(oneSource, "first");
        assertEquals(Collections.singletonList("first.theOnly"), keys);

        keys = strategy.apply(twoSource, "first");
        assertEquals(Arrays.asList("second.one", "second.two"), keys);

        keys = strategy.apply(osSource, "name");
        assertEquals(Arrays.asList("nisse.os.name", "os.detected.name"), keys);
    }
}
