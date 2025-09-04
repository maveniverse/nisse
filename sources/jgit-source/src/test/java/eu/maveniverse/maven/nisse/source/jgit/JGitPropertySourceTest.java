package eu.maveniverse.maven.nisse.source.jgit;

import static org.junit.jupiter.api.Assertions.*;

import eu.maveniverse.maven.nisse.core.NisseConfiguration;
import eu.maveniverse.maven.nisse.core.simple.SimpleNisseConfiguration;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import org.eclipse.aether.version.Version;
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

    @Test
    void testVersionHintVsGitHistoryComparison() {
        JGitPropertySource source = new JGitPropertySource();

        // Test case 1: Version hint is lower than git history - should use git history
        VersionInformation gitHistory1 = new VersionInformation("0.13.0");
        VersionInformation hint1 = new VersionInformation("0.9.2");

        Version gitHistoryParsed1 = source.version(gitHistory1.toString());
        Version hintParsed1 = source.version(hint1.toString());

        assertTrue(
                hintParsed1.compareTo(gitHistoryParsed1) < 0,
                "Version hint 0.9.2 should be lower than git history 0.13.0");

        // Test case 2: Version hint is higher than git history - should use hint
        VersionInformation gitHistory2 = new VersionInformation("0.13.0");
        VersionInformation hint2 = new VersionInformation("0.14.0");

        Version gitHistoryParsed2 = source.version(gitHistory2.toString());
        Version hintParsed2 = source.version(hint2.toString());

        assertTrue(
                hintParsed2.compareTo(gitHistoryParsed2) > 0,
                "Version hint 0.14.0 should be higher than git history 0.13.0");

        // Test case 3: Version hint equals git history - should use git history
        VersionInformation gitHistory3 = new VersionInformation("0.13.0");
        VersionInformation hint3 = new VersionInformation("0.13.0");

        Version gitHistoryParsed3 = source.version(gitHistory3.toString());
        Version hintParsed3 = source.version(hint3.toString());

        assertEquals(
                0, hintParsed3.compareTo(gitHistoryParsed3), "Version hint 0.13.0 should equal git history 0.13.0");
    }

    @Test
    void testIsVersionHintTag() throws Exception {
        Map<String, String> configMap = new HashMap<>();
        NisseConfiguration configuration =
                SimpleNisseConfiguration.builder().withUserProperties(configMap).build();

        JGitPropertySource source = new JGitPropertySource();

        // Test default pattern: ${version}-SNAPSHOT
        assertTrue(source.isVersionHintTag(configuration, "refs/tags/4.2.0-SNAPSHOT"));
        assertTrue(source.isVersionHintTag(configuration, "refs/tags/v4.2.0-SNAPSHOT"));
        assertTrue(source.isVersionHintTag(configuration, "refs/tags/1.0.0-SNAPSHOT"));

        // These should NOT match the version hint pattern
        assertFalse(source.isVersionHintTag(configuration, "refs/tags/4.2.0"));
        assertFalse(source.isVersionHintTag(configuration, "refs/tags/v4.2.0"));
        assertFalse(source.isVersionHintTag(configuration, "refs/tags/release-4.2.0"));

        // Test custom pattern
        configMap.put("nisse.source.jgit.versionHintPattern", "hint-${version}");
        NisseConfiguration customConfig =
                SimpleNisseConfiguration.builder().withUserProperties(configMap).build();
        assertTrue(source.isVersionHintTag(customConfig, "refs/tags/hint-3.1.0"));
        assertTrue(source.isVersionHintTag(customConfig, "refs/tags/vhint-3.1.0"));
        assertFalse(source.isVersionHintTag(customConfig, "refs/tags/3.1.0-SNAPSHOT"));
    }
}
