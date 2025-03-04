package eu.maveniverse.maven.nisse.core.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import eu.maveniverse.maven.nisse.core.NisseConfiguration;
import eu.maveniverse.maven.nisse.core.PropertySource;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

public class SimpleNisseManagerTest {
    @Test
    void smoke() throws IOException {
        Map<String, String> m1 = new HashMap<>();
        m1.put("one", "en");
        m1.put("two", "to");
        Map<String, String> m2 = new HashMap<>();
        m2.put("one", "egy");
        m2.put("two", "kettő");
        PropertySource s1 = new PropertySource() {
            @Override
            public String getName() {
                return "dk";
            }

            @Override
            public Map<String, String> getProperties(NisseConfiguration configuration) {
                return m1;
            }
        };
        PropertySource s2 = new PropertySource() {
            @Override
            public String getName() {
                return "hu";
            }

            @Override
            public Map<String, String> getProperties(NisseConfiguration configuration) {
                return m2;
            }
        };

        SimpleNisseManager snm = new SimpleNisseManager(Arrays.asList(s1, s2));

        SimpleNisseConfiguration conf = SimpleNisseConfiguration.builder().build();
        Map<String, String> allProperties = snm.createProperties(conf);
        assertEquals(4, allProperties.size());
        assertTrue(allProperties.containsKey("nisse.dk.one"));
        assertSame(allProperties.get("nisse.dk.one"), "en");
        assertTrue(allProperties.containsKey("nisse.dk.two"));
        assertSame(allProperties.get("nisse.dk.two"), "to");
        assertTrue(allProperties.containsKey("nisse.hu.one"));
        assertSame(allProperties.get("nisse.hu.one"), "egy");
        assertTrue(allProperties.containsKey("nisse.hu.two"));
        assertSame(allProperties.get("nisse.hu.two"), "kettő");
    }
}
