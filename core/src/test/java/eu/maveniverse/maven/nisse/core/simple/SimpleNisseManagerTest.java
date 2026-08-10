package eu.maveniverse.maven.nisse.core.simple;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import eu.maveniverse.maven.nisse.core.NisseConfiguration;
import eu.maveniverse.maven.nisse.core.PropertySource;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

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

    @Test
    void fallbackPropertiesAreLoaded(@TempDir Path tempDir) throws IOException {
        // Set up .mvn/nisse.properties
        Path mvnDir = tempDir.resolve(".mvn");
        Files.createDirectories(mvnDir);
        Properties fallbackProps = new Properties();
        fallbackProps.setProperty("nisse.jgit.dynamicVersion", "1.2.3");
        fallbackProps.setProperty("nisse.jgit.date", "2024-01-01T00:00:00Z");
        writeProperties(mvnDir.resolve("nisse.properties"), fallbackProps);

        SimpleNisseManager snm = new SimpleNisseManager(Arrays.asList());

        SimpleNisseConfiguration conf = SimpleNisseConfiguration.builder()
                .withSessionRootDirectory(tempDir)
                .build();

        Map<String, String> allProperties = snm.createProperties(conf);
        assertEquals(2, allProperties.size());
        assertEquals("1.2.3", allProperties.get("nisse.jgit.dynamicVersion"));
        assertEquals("2024-01-01T00:00:00Z", allProperties.get("nisse.jgit.date"));
    }

    @Test
    void propertySourcesOverrideFallback(@TempDir Path tempDir) throws IOException {
        // Set up .mvn/nisse.properties with fallback values
        Path mvnDir = tempDir.resolve(".mvn");
        Files.createDirectories(mvnDir);
        Properties fallbackProps = new Properties();
        fallbackProps.setProperty("nisse.test.key1", "fallback-value");
        fallbackProps.setProperty("nisse.test.key2", "only-in-fallback");
        writeProperties(mvnDir.resolve("nisse.properties"), fallbackProps);

        // PropertySource that produces nisse.test.key1 (should override fallback)
        PropertySource source = new PropertySource() {
            @Override
            public String getName() {
                return "test";
            }

            @Override
            public Map<String, String> getProperties(NisseConfiguration configuration) {
                Map<String, String> props = new HashMap<>();
                props.put("key1", "source-value");
                return props;
            }
        };

        SimpleNisseManager snm = new SimpleNisseManager(Arrays.asList(source));

        SimpleNisseConfiguration conf = SimpleNisseConfiguration.builder()
                .withSessionRootDirectory(tempDir)
                .build();

        Map<String, String> allProperties = snm.createProperties(conf);
        // source value should override fallback
        assertEquals("source-value", allProperties.get("nisse.test.key1"));
        // fallback-only value should be preserved
        assertEquals("only-in-fallback", allProperties.get("nisse.test.key2"));
    }

    @Test
    void unexpandedPlaceholdersAreSkipped(@TempDir Path tempDir) throws IOException {
        // Set up .mvn/nisse.properties with unexpanded export-subst placeholders
        Path mvnDir = tempDir.resolve(".mvn");
        Files.createDirectories(mvnDir);
        Properties fallbackProps = new Properties();
        fallbackProps.setProperty("nisse.jgit.dynamicVersion", "$Format:%(describe:tags=true)$");
        fallbackProps.setProperty("nisse.jgit.date", "$Format:%cI$");
        fallbackProps.setProperty("nisse.jgit.commit", "abc123"); // valid expanded value
        writeProperties(mvnDir.resolve("nisse.properties"), fallbackProps);

        SimpleNisseManager snm = new SimpleNisseManager(Arrays.asList());

        SimpleNisseConfiguration conf = SimpleNisseConfiguration.builder()
                .withSessionRootDirectory(tempDir)
                .build();

        Map<String, String> allProperties = snm.createProperties(conf);
        // unexpanded placeholders should be skipped
        assertFalse(allProperties.containsKey("nisse.jgit.dynamicVersion"));
        assertFalse(allProperties.containsKey("nisse.jgit.date"));
        // valid value should be loaded
        assertEquals("abc123", allProperties.get("nisse.jgit.commit"));
    }

    @Test
    void missingNissePropertiesFileIsIgnored(@TempDir Path tempDir) throws IOException {
        // No .mvn/nisse.properties file exists
        SimpleNisseManager snm = new SimpleNisseManager(Arrays.asList());

        SimpleNisseConfiguration conf = SimpleNisseConfiguration.builder()
                .withSessionRootDirectory(tempDir)
                .build();

        Map<String, String> allProperties = snm.createProperties(conf);
        assertTrue(allProperties.isEmpty());
    }

    @Test
    void isUnexpandedPlaceholder() {
        assertTrue(SimpleNisseManager.isUnexpandedPlaceholder("$Format:%(describe:tags=true)$"));
        assertTrue(SimpleNisseManager.isUnexpandedPlaceholder("$Format:%cI$"));
        assertTrue(SimpleNisseManager.isUnexpandedPlaceholder("$Format:%H$"));

        assertFalse(SimpleNisseManager.isUnexpandedPlaceholder("1.2.3"));
        assertFalse(SimpleNisseManager.isUnexpandedPlaceholder(""));
        assertFalse(SimpleNisseManager.isUnexpandedPlaceholder(null));
        assertFalse(SimpleNisseManager.isUnexpandedPlaceholder("some-value-with-$dollar"));
        assertFalse(SimpleNisseManager.isUnexpandedPlaceholder("$NotFormat:something$"));
    }

    @Test
    void expandedPlaceholderValuesAreLoaded(@TempDir Path tempDir) throws IOException {
        // Simulate what git archive produces: expanded export-subst values
        Path mvnDir = tempDir.resolve(".mvn");
        Files.createDirectories(mvnDir);
        Properties fallbackProps = new Properties();
        fallbackProps.setProperty("nisse.jgit.dynamicVersion", "v1.0.0-3-g1234567");
        fallbackProps.setProperty("nisse.jgit.date", "2024-06-15T10:30:00+02:00");
        writeProperties(mvnDir.resolve("nisse.properties"), fallbackProps);

        SimpleNisseManager snm = new SimpleNisseManager(Arrays.asList());

        SimpleNisseConfiguration conf = SimpleNisseConfiguration.builder()
                .withSessionRootDirectory(tempDir)
                .build();

        Map<String, String> allProperties = snm.createProperties(conf);
        assertEquals("v1.0.0-3-g1234567", allProperties.get("nisse.jgit.dynamicVersion"));
        assertEquals("2024-06-15T10:30:00+02:00", allProperties.get("nisse.jgit.date"));
    }

    private static void writeProperties(Path path, Properties props) throws IOException {
        try (OutputStream out = Files.newOutputStream(path)) {
            props.store(out, null);
        }
    }
}
