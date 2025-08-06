package eu.maveniverse.maven.nisse.source.jgit;

import static org.junit.jupiter.api.Assertions.*;

import eu.maveniverse.maven.nisse.core.internal.SimpleNisseConfiguration;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
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
            assertTrue(
                    dateValue.matches("\\w{3} \\w{3} \\d{2} \\d{2}:\\d{2}:\\d{2} \\d{4} [+-]\\d{4}"),
                    "Expected date format 'EEE MMM dd HH:mm:ss yyyy Z' but got: " + dateValue);
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
            // But in case of UTC: 2024-05-27T18:20:45Z
            assertTrue(dateValue.matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(Z|[+-]\\d{2}:\\d{2})"), dateValue);
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
            assertTrue(
                    dateValue.matches("\\w{3} \\w{3} \\d{2} \\d{2}:\\d{2}:\\d{2} \\d{4} [+-]\\d{4}"),
                    "Expected fallback date format 'EEE MMM dd HH:mm:ss yyyy Z' but got: " + dateValue);
        }
    }

    @Test
    void testVersionHintPatternMatching() {
        JGitPropertySource source = new JGitPropertySource();

        // Test default pattern: ${version}-SNAPSHOT
        String hintPattern = "${version}-SNAPSHOT";
        // Use the same logic as the actual implementation
        String regexPattern = hintPattern
                .replace(".", "\\.") // Escape literal dots
                .replace("-", "\\-"); // Escape literal dashes
        regexPattern = regexPattern.replace("${version}", "(\\d+\\.\\d+\\.\\d+)");
        Pattern hintTagPattern = Pattern.compile("refs/tags/v?" + regexPattern);

        // Debug: print the actual regex pattern
        System.out.println("Regex pattern: " + hintTagPattern.pattern());

        // Test matching tags
        String testTag1 = "refs/tags/4.1.0-SNAPSHOT";
        System.out.println("Testing: " + testTag1 + " -> "
                + hintTagPattern.matcher(testTag1).matches());
        assertTrue(hintTagPattern.matcher(testTag1).matches());

        String testTag2 = "refs/tags/v4.2.0-SNAPSHOT";
        System.out.println("Testing: " + testTag2 + " -> "
                + hintTagPattern.matcher(testTag2).matches());
        assertTrue(hintTagPattern.matcher(testTag2).matches());

        assertTrue(hintTagPattern.matcher("refs/tags/4.0.0-SNAPSHOT").matches());

        // Test non-matching tags
        assertFalse(hintTagPattern.matcher("refs/tags/3.0.0").matches());
        assertFalse(hintTagPattern.matcher("refs/tags/invalid-tag").matches());
        assertFalse(hintTagPattern.matcher("refs/tags/4.1.0-RELEASE").matches());

        // Test version extraction
        java.util.regex.Matcher matcher = hintTagPattern.matcher("refs/tags/4.1.0-SNAPSHOT");
        if (matcher.matches()) {
            assertEquals("4.1.0", matcher.group(1));
        }

        matcher = hintTagPattern.matcher("refs/tags/v4.2.0-SNAPSHOT");
        if (matcher.matches()) {
            assertEquals("4.2.0", matcher.group(1));
        }
    }

    @Test
    void testCustomVersionHintPatternMatching() {
        JGitPropertySource source = new JGitPropertySource();

        // Test custom pattern: hint-${version}
        String hintPattern = "hint-${version}";
        // Use the same logic as the actual implementation
        String regexPattern = hintPattern
                .replace(".", "\\.") // Escape literal dots
                .replace("-", "\\-"); // Escape literal dashes
        regexPattern = regexPattern.replace("${version}", "(\\d+\\.\\d+\\.\\d+)");
        Pattern hintTagPattern = Pattern.compile("refs/tags/v?" + regexPattern);

        // Test matching tags
        assertTrue(hintTagPattern.matcher("refs/tags/hint-4.1.0").matches());
        assertTrue(hintTagPattern.matcher("refs/tags/hint-3.0.0").matches());

        // Test non-matching tags
        assertFalse(hintTagPattern.matcher("refs/tags/v4.2.0-SNAPSHOT").matches());
        assertFalse(hintTagPattern.matcher("refs/tags/4.0.0").matches());
        assertFalse(hintTagPattern.matcher("refs/tags/invalid-tag").matches());

        // Test version extraction
        java.util.regex.Matcher matcher = hintTagPattern.matcher("refs/tags/hint-4.1.0");
        assertTrue(matcher.matches());
        assertEquals("4.1.0", matcher.group(1));

        matcher = hintTagPattern.matcher("refs/tags/hint-3.0.0");
        assertTrue(matcher.matches());
        assertEquals("3.0.0", matcher.group(1));
    }

    @Test
    void testFindHighestVersionFromHints() {
        JGitPropertySource source = new JGitPropertySource();

        List<String> hintVersions = Arrays.asList("4.1.0", "4.2.0", "3.0.0", "4.0.0");

        Optional<String> highest = source.findHighestVersionFromHints(hintVersions);

        assertTrue(highest.isPresent());
        assertEquals("4.2.0", highest.get());
    }

    @Test
    void testFindHighestVersionFromHintsEmpty() {
        JGitPropertySource source = new JGitPropertySource();

        List<String> hintVersions = Arrays.asList();

        Optional<String> highest = source.findHighestVersionFromHints(hintVersions);

        assertFalse(highest.isPresent());
    }
}
