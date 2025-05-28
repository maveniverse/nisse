package eu.maveniverse.maven.nisse.source.jgit;

import static org.junit.jupiter.api.Assertions.*;

import eu.maveniverse.maven.nisse.core.internal.SimpleNisseConfiguration;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

public class JGitPropertySourceTest {
    @Test
    void smoke() throws IOException {
        new JGitPropertySource()
                .getProperties(SimpleNisseConfiguration.builder().build())
                .forEach((k, v) -> System.out.println(k + " = " + v));
    }

    @Test
    void testDefaultDateFormat() throws IOException {
        JGitPropertySource source = new JGitPropertySource();
        Map<String, String> properties =
                source.getProperties(SimpleNisseConfiguration.builder().build());

        if (properties.containsKey("date")) {
            String dateValue = properties.get("date");
            System.out.println("Default date format: " + dateValue);
            // Default format should match: EEE MMM dd HH:mm:ss yyyy Z
            // Example: Mon May 27 18:20:45 2024 +0200
            assertTrue(dateValue.matches("\\w{3} \\w{3} \\d{2} \\d{2}:\\d{2}:\\d{2} \\d{4} [+-]\\d{4}"));
        }
    }

    @Test
    void testIso8601DateFormat() throws IOException {
        Map<String, String> systemProps = new HashMap<>();
        systemProps.put("nisse.source.jgit.dateFormat", "iso8601");

        JGitPropertySource source = new JGitPropertySource();
        Map<String, String> properties = source.getProperties(SimpleNisseConfiguration.builder()
                .withSystemProperties(systemProps)
                .build());

        if (properties.containsKey("date")) {
            String dateValue = properties.get("date");
            System.out.println("ISO-8601 date format: " + dateValue);
            // ISO-8601 format should match: yyyy-MM-ddTHH:mm:ssZ
            // Example: 2024-05-27T16:20:45Z
            assertTrue(dateValue.matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}Z"));
        }
    }

    @Test
    void testIso8601OffsetDateFormat() throws IOException {
        Map<String, String> systemProps = new HashMap<>();
        systemProps.put("nisse.source.jgit.dateFormat", "iso8601-offset");

        JGitPropertySource source = new JGitPropertySource();
        Map<String, String> properties = source.getProperties(SimpleNisseConfiguration.builder()
                .withSystemProperties(systemProps)
                .build());

        if (properties.containsKey("date")) {
            String dateValue = properties.get("date");
            System.out.println("ISO-8601 with offset date format: " + dateValue);
            // ISO-8601 with offset format should match: yyyy-MM-ddTHH:mm:ss+XX:XX
            // Example: 2024-05-27T18:20:45+02:00
            assertTrue(dateValue.matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}[+-]\\d{2}:\\d{2}"));
        }
    }

    @Test
    void testCustomDateFormat() throws IOException {
        Map<String, String> systemProps = new HashMap<>();
        systemProps.put("nisse.source.jgit.dateFormat", "custom");
        systemProps.put("nisse.source.jgit.dateFormat.pattern", "yyyy/MM/dd HH:mm");

        JGitPropertySource source = new JGitPropertySource();
        Map<String, String> properties = source.getProperties(SimpleNisseConfiguration.builder()
                .withSystemProperties(systemProps)
                .build());

        if (properties.containsKey("date")) {
            String dateValue = properties.get("date");
            System.out.println("Custom date format: " + dateValue);
            // Custom format should match: yyyy/MM/dd HH:mm
            // Example: 2024/05/27 18:20
            assertTrue(dateValue.matches("\\d{4}/\\d{2}/\\d{2} \\d{2}:\\d{2}"));
        }
    }

    @Test
    void testInvalidDateFormatFallsBackToDefault() throws IOException {
        Map<String, String> systemProps = new HashMap<>();
        systemProps.put("nisse.source.jgit.dateFormat", "invalid-format");

        JGitPropertySource source = new JGitPropertySource();
        Map<String, String> properties = source.getProperties(SimpleNisseConfiguration.builder()
                .withSystemProperties(systemProps)
                .build());

        if (properties.containsKey("date")) {
            String dateValue = properties.get("date");
            System.out.println("Fallback date format: " + dateValue);
            // Should fall back to default git format
            assertTrue(dateValue.matches("\\w{3} \\w{3} \\d{2} \\d{2}:\\d{2}:\\d{2} \\d{4} [+-]\\d{4}"));
        }
    }
}
