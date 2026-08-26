package eu.maveniverse.maven.nisse.source.jgit;

import static org.junit.jupiter.api.Assertions.*;

import eu.maveniverse.maven.nisse.core.NisseConfiguration;
import eu.maveniverse.maven.nisse.core.simple.SimpleNisseConfiguration;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import org.eclipse.aether.version.Version;
import org.eclipse.jgit.lib.ObjectId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

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
    void testRedactCredentialsHttpsUserAndPassword() {
        assertEquals(
                "https://github.com/example/repo.git",
                JGitPropertySource.redactCredentials("https://user:s3cr3t@github.com/example/repo.git"));
    }

    @Test
    void testRedactCredentialsHttpsUserOnly() {
        assertEquals(
                "https://github.com/example/repo.git",
                JGitPropertySource.redactCredentials("https://token@github.com/example/repo.git"));
    }

    @Test
    void testRedactCredentialsPlainHttpsUnchanged() {
        assertEquals(
                "https://github.com/example/repo.git",
                JGitPropertySource.redactCredentials("https://github.com/example/repo.git"));
    }

    @Test
    void testRedactCredentialsScpStyleSshUnchanged() {
        assertEquals(
                "git@github.com:example/repo.git",
                JGitPropertySource.redactCredentials("git@github.com:example/repo.git"));
    }

    @Test
    void testRedactCredentialsSshUriUnchanged() {
        assertEquals(
                "ssh://git@github.com/example/repo.git",
                JGitPropertySource.redactCredentials("ssh://git@github.com/example/repo.git"));
    }

    @Test
    void testRemoteUrlRedactedFromProperties(@TempDir Path tempDir) throws Exception {
        Path mainRepo = tempDir.resolve("main-repo");
        Files.createDirectories(mainRepo);

        exec(mainRepo, "git", "init", "-b", "master");
        exec(mainRepo, "git", "config", "user.email", "test@test.com");
        exec(mainRepo, "git", "config", "user.name", "Test");
        Files.write(mainRepo.resolve("file.txt"), "hello".getBytes(StandardCharsets.UTF_8));
        exec(mainRepo, "git", "add", "file.txt");
        exec(mainRepo, "git", "commit", "-m", "initial commit");
        exec(mainRepo, "git", "remote", "add", "origin", "https://user:s3cr3t@example.com/example/repo.git");

        Map<String, String> properties = new JGitPropertySource()
                .getProperties(SimpleNisseConfiguration.builder()
                        .withCurrentWorkingDirectory(mainRepo)
                        .build());

        assertEquals("https://example.com/example/repo.git", properties.get("remoteUrl"));
        assertFalse(properties.get("remoteUrl").contains("s3cr3t"));
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

    @Test
    void testWorktreeSupport(@TempDir Path tempDir) throws Exception {
        Path mainRepo = tempDir.resolve("main-repo");
        Files.createDirectories(mainRepo);

        exec(mainRepo, "git", "init");
        exec(mainRepo, "git", "config", "user.email", "test@test.com");
        exec(mainRepo, "git", "config", "user.name", "Test");
        Files.write(mainRepo.resolve("file.txt"), "hello".getBytes(StandardCharsets.UTF_8));
        exec(mainRepo, "git", "add", "file.txt");
        exec(mainRepo, "git", "commit", "-m", "initial commit");
        exec(mainRepo, "git", "tag", "v1.0.0");

        // Create a worktree with an additional commit
        Path worktree = tempDir.resolve("worktree");
        exec(mainRepo, "git", "worktree", "add", worktree.toString());
        Files.write(worktree.resolve("worktree-file.txt"), "worktree".getBytes(StandardCharsets.UTF_8));
        exec(worktree, "git", "add", "worktree-file.txt");
        exec(worktree, "git", "commit", "-m", "worktree commit");

        String mainCommit = execOutput(mainRepo, "git", "rev-parse", "HEAD").trim();
        String worktreeCommit = execOutput(worktree, "git", "rev-parse", "HEAD").trim();
        assertNotEquals(mainCommit, worktreeCommit, "Worktree should have a different HEAD");

        // JGitPropertySource should work from the worktree directory
        JGitPropertySource source = new JGitPropertySource();
        Map<String, String> properties = source.getProperties(SimpleNisseConfiguration.builder()
                .withCurrentWorkingDirectory(worktree)
                .build());

        assertFalse(properties.isEmpty(), "Properties should not be empty when opened from a worktree");
        assertEquals(worktreeCommit, properties.get("commit"), "Should resolve worktree HEAD, not main HEAD");

        // Verify the main repo still works
        Map<String, String> mainProperties = source.getProperties(SimpleNisseConfiguration.builder()
                .withCurrentWorkingDirectory(mainRepo)
                .build());
        assertEquals(mainCommit, mainProperties.get("commit"), "Main repo should still work");
    }

    @Test
    void testBranchNameSimple(@TempDir Path tempDir) throws Exception {
        Path mainRepo = tempDir.resolve("main-repo");
        Files.createDirectories(mainRepo);

        exec(mainRepo, "git", "init", "-b", "master");
        exec(mainRepo, "git", "config", "user.email", "test@test.com");
        exec(mainRepo, "git", "config", "user.name", "Test");
        Files.write(mainRepo.resolve("file.txt"), "hello".getBytes(StandardCharsets.UTF_8));
        exec(mainRepo, "git", "add", "file.txt");
        exec(mainRepo, "git", "commit", "-m", "initial commit");
        exec(mainRepo, "git", "tag", "v1.0.0");

        Map<String, String> properties = new JGitPropertySource()
                .getProperties(SimpleNisseConfiguration.builder()
                        .withCurrentWorkingDirectory(mainRepo)
                        .build());

        assertFalse(properties.isEmpty(), "Properties should not be empty");
        assertEquals("master", properties.get("branchName"));

        exec(mainRepo, "git", "branch", "-m", "main");

        properties = new JGitPropertySource()
                .getProperties(SimpleNisseConfiguration.builder()
                        .withCurrentWorkingDirectory(mainRepo)
                        .build());

        assertFalse(properties.isEmpty(), "Properties should not be empty");
        assertEquals("main", properties.get("branchName"));
    }

    @Test
    void testBranchNameDetached(@TempDir Path tempDir) throws Exception {
        Path mainRepo = tempDir.resolve("main-repo");
        Files.createDirectories(mainRepo);

        exec(mainRepo, "git", "init", "-b", "master");
        exec(mainRepo, "git", "config", "user.email", "test@test.com");
        exec(mainRepo, "git", "config", "user.name", "Test");
        Files.write(mainRepo.resolve("file1.txt"), "hello".getBytes(StandardCharsets.UTF_8));
        exec(mainRepo, "git", "add", "file1.txt");
        exec(mainRepo, "git", "commit", "-m", "file1");
        Files.write(mainRepo.resolve("file2.txt"), "hello again".getBytes(StandardCharsets.UTF_8));
        exec(mainRepo, "git", "add", "file2.txt");
        exec(mainRepo, "git", "commit", "-m", "file2");

        exec(mainRepo, "git", "checkout", "HEAD^");

        Map<String, String> properties = new JGitPropertySource()
                .getProperties(SimpleNisseConfiguration.builder()
                        .withCurrentWorkingDirectory(mainRepo)
                        .build());

        assertFalse(properties.isEmpty(), "Properties should not be empty");
        assertFalse(properties.containsKey("branchName"));
    }

    @Test
    void testBranchNameWorktree(@TempDir Path tempDir) throws Exception {
        Path mainRepo = tempDir.resolve("main-repo");
        Files.createDirectories(mainRepo);

        exec(mainRepo, "git", "init", "-b", "master");
        exec(mainRepo, "git", "config", "user.email", "test@test.com");
        exec(mainRepo, "git", "config", "user.name", "Test");
        Files.write(mainRepo.resolve("file.txt"), "hello".getBytes(StandardCharsets.UTF_8));
        exec(mainRepo, "git", "add", "file.txt");
        exec(mainRepo, "git", "commit", "-m", "initial commit");
        exec(mainRepo, "git", "tag", "v1.0.0");

        // Create a worktree with an additional commit
        Path worktree = tempDir.resolve("worktree");
        exec(mainRepo, "git", "worktree", "add", worktree.toString(), "-b", "master-wt");
        Files.write(worktree.resolve("worktree-file.txt"), "worktree".getBytes(StandardCharsets.UTF_8));
        exec(worktree, "git", "add", "worktree-file.txt");
        exec(worktree, "git", "commit", "-m", "worktree commit");

        String mainCommit = execOutput(mainRepo, "git", "rev-parse", "HEAD").trim();
        String worktreeCommit = execOutput(worktree, "git", "rev-parse", "HEAD").trim();
        assertNotEquals(mainCommit, worktreeCommit, "Worktree should have a different HEAD");

        Map<String, String> properties = new JGitPropertySource()
                .getProperties(SimpleNisseConfiguration.builder()
                        .withCurrentWorkingDirectory(worktree)
                        .build());

        assertFalse(properties.isEmpty(), "Properties should not be empty");
        assertEquals("master-wt", properties.get("branchName"));

        exec(worktree, "git", "branch", "-m", "main-wt");

        properties = new JGitPropertySource()
                .getProperties(SimpleNisseConfiguration.builder()
                        .withCurrentWorkingDirectory(worktree)
                        .build());

        assertFalse(properties.isEmpty(), "Properties should not be empty");
        assertEquals("main-wt", properties.get("branchName"));

        // check main repo
        properties = new JGitPropertySource()
                .getProperties(SimpleNisseConfiguration.builder()
                        .withCurrentWorkingDirectory(mainRepo)
                        .build());

        assertFalse(properties.isEmpty(), "Properties should not be empty");
        assertEquals("master", properties.get("branchName"));
    }

    @Test
    void testBranchNameWorktreeDetached(@TempDir Path tempDir) throws Exception {
        Path mainRepo = tempDir.resolve("main-repo");
        Files.createDirectories(mainRepo);

        exec(mainRepo, "git", "init", "-b", "master");
        exec(mainRepo, "git", "config", "user.email", "test@test.com");
        exec(mainRepo, "git", "config", "user.name", "Test");
        Files.write(mainRepo.resolve("file1.txt"), "hello".getBytes(StandardCharsets.UTF_8));
        exec(mainRepo, "git", "add", "file1.txt");
        exec(mainRepo, "git", "commit", "-m", "file1 commit");
        Files.write(mainRepo.resolve("file2.txt"), "hello again".getBytes(StandardCharsets.UTF_8));
        exec(mainRepo, "git", "add", "file2.txt");
        exec(mainRepo, "git", "commit", "-m", "file2 commit");

        // Create a worktree with an additional commit
        Path worktree = tempDir.resolve("worktree");
        exec(mainRepo, "git", "worktree", "add", worktree.toString(), "-b", "master-wt");
        Files.write(worktree.resolve("worktree-file1.txt"), "worktree".getBytes(StandardCharsets.UTF_8));
        exec(worktree, "git", "add", "worktree-file1.txt");
        exec(worktree, "git", "commit", "-m", "worktree-file1 commit");
        Files.write(worktree.resolve("worktree-file2.txt"), "worktree".getBytes(StandardCharsets.UTF_8));
        exec(worktree, "git", "add", "worktree-file2.txt");
        exec(worktree, "git", "commit", "-m", "worktree-file2 commit");

        String mainCommit = execOutput(mainRepo, "git", "rev-parse", "HEAD").trim();
        String worktreeCommit = execOutput(worktree, "git", "rev-parse", "HEAD").trim();
        assertNotEquals(mainCommit, worktreeCommit, "Worktree should have a different HEAD");

        exec(worktree, "git", "checkout", "HEAD^");

        Map<String, String> properties = new JGitPropertySource()
                .getProperties(SimpleNisseConfiguration.builder()
                        .withCurrentWorkingDirectory(worktree)
                        .build());

        assertFalse(properties.isEmpty(), "Properties should not be empty");
        assertFalse(properties.containsKey("branchName"));
    }

    @Test
    void testBranchNameTwoBranchesSameCommit(@TempDir Path tempDir) throws Exception {
        Path mainRepo = tempDir.resolve("main-repo");
        Files.createDirectories(mainRepo);

        exec(mainRepo, "git", "init", "-b", "master");
        exec(mainRepo, "git", "config", "user.email", "test@test.com");
        exec(mainRepo, "git", "config", "user.name", "Test");
        Files.write(mainRepo.resolve("file1.txt"), "hello".getBytes(StandardCharsets.UTF_8));
        exec(mainRepo, "git", "add", "file1.txt");
        exec(mainRepo, "git", "commit", "-m", "file1 commit");
        Files.write(mainRepo.resolve("file2.txt"), "hello again".getBytes(StandardCharsets.UTF_8));
        exec(mainRepo, "git", "add", "file2.txt");
        exec(mainRepo, "git", "commit", "-m", "file2 commit");

        // create two branch; but both point to same commit
        exec(mainRepo, "git", "checkout", "-b", "dev1");
        exec(mainRepo, "git", "checkout", "-b", "dev2");

        Map<String, String> properties = new JGitPropertySource()
                .getProperties(SimpleNisseConfiguration.builder()
                        .withCurrentWorkingDirectory(mainRepo)
                        .build());

        assertFalse(properties.isEmpty(), "Properties should not be empty");
        assertTrue(properties.containsKey("branchName"));
        assertEquals("dev2", properties.get("branchName"));
    }

    @Test
    void testBranchNameTwoWorktreesSameCommit(@TempDir Path tempDir) throws Exception {
        Path mainRepo = tempDir.resolve("main-repo");
        Files.createDirectories(mainRepo);

        exec(mainRepo, "git", "init", "-b", "master");
        exec(mainRepo, "git", "config", "user.email", "test@test.com");
        exec(mainRepo, "git", "config", "user.name", "Test");
        Files.write(mainRepo.resolve("file.txt"), "hello".getBytes(StandardCharsets.UTF_8));
        exec(mainRepo, "git", "add", "file.txt");
        exec(mainRepo, "git", "commit", "-m", "initial commit");
        exec(mainRepo, "git", "tag", "v1.0.0");

        // Create a worktrees without additional commit
        Path worktree1 = tempDir.resolve("worktree1");
        exec(mainRepo, "git", "worktree", "add", worktree1.toString());
        Path worktree2 = tempDir.resolve("worktree2");
        exec(mainRepo, "git", "worktree", "add", worktree2.toString());

        String mainCommit = execOutput(mainRepo, "git", "rev-parse", "HEAD").trim();
        String worktree1Commit =
                execOutput(worktree1, "git", "rev-parse", "HEAD").trim();
        String worktree2Commit =
                execOutput(worktree2, "git", "rev-parse", "HEAD").trim();
        assertEquals(mainCommit, worktree1Commit, "Worktree should have a different HEAD");
        assertEquals(mainCommit, worktree2Commit, "Worktree should have a different HEAD");

        Map<String, String> properties = new JGitPropertySource()
                .getProperties(SimpleNisseConfiguration.builder()
                        .withCurrentWorkingDirectory(worktree1)
                        .build());

        assertFalse(properties.isEmpty(), "Properties should not be empty");
        assertEquals("worktree1", properties.get("branchName"));

        properties = new JGitPropertySource()
                .getProperties(SimpleNisseConfiguration.builder()
                        .withCurrentWorkingDirectory(worktree2)
                        .build());

        assertFalse(properties.isEmpty(), "Properties should not be empty");
        assertEquals("worktree2", properties.get("branchName"));

        // check main repo
        properties = new JGitPropertySource()
                .getProperties(SimpleNisseConfiguration.builder()
                        .withCurrentWorkingDirectory(mainRepo)
                        .build());

        assertFalse(properties.isEmpty(), "Properties should not be empty");
        assertEquals("master", properties.get("branchName"));
    }

    @Test
    void testVersionHintReachability(@TempDir Path tempDir) throws Exception {
        Path repo = tempDir.resolve("repo");
        Files.createDirectories(repo);

        exec(repo, "git", "init", "-b", "master");
        exec(repo, "git", "config", "user.email", "test@test.com");
        exec(repo, "git", "config", "user.name", "Test");

        // Initial commit and release tag
        Files.write(repo.resolve("file.txt"), "v1".getBytes(StandardCharsets.UTF_8));
        exec(repo, "git", "add", "file.txt");
        exec(repo, "git", "commit", "-m", "initial");
        exec(repo, "git", "tag", "1.0.0");

        // Create maintenance branch from this point
        exec(repo, "git", "branch", "maintenance");

        // Add commit on master with version hint tag (unreachable from maintenance)
        Files.write(repo.resolve("file.txt"), "v2".getBytes(StandardCharsets.UTF_8));
        exec(repo, "git", "add", "file.txt");
        exec(repo, "git", "commit", "-m", "master work");
        exec(repo, "git", "tag", "-a", "2.0.0-SNAPSHOT", "-m", "version hint");

        // Switch to maintenance, add a commit with release tag
        exec(repo, "git", "checkout", "maintenance");
        Files.write(repo.resolve("maint.txt"), "fix".getBytes(StandardCharsets.UTF_8));
        exec(repo, "git", "add", "maint.txt");
        exec(repo, "git", "commit", "-m", "maintenance fix");
        exec(repo, "git", "tag", "1.0.1");

        // Resolve dynamic version from maintenance branch
        Map<String, String> userProps = new HashMap<>();
        userProps.put("nisse.source.jgit.dynamicVersion", "true");

        JGitPropertySource source = new JGitPropertySource();
        Map<String, String> properties = source.getProperties(SimpleNisseConfiguration.builder()
                .withCurrentWorkingDirectory(repo)
                .withUserProperties(userProps)
                .build());

        String dynamicVersion = properties.get("dynamicVersion");
        assertNotNull(dynamicVersion, "dynamicVersion should be set");
        assertEquals(
                "1.0.1",
                dynamicVersion,
                "Should resolve version from maintenance branch tag, not unreachable master hint tag");
    }

    @Test
    void testVersionHintMultipleTags(@TempDir Path tempDir) throws Exception {
        Path repo = tempDir.resolve("repo");
        Files.createDirectories(repo);

        exec(repo, "git", "init", "-b", "master");
        exec(repo, "git", "config", "user.email", "test@test.com");
        exec(repo, "git", "config", "user.name", "Test");

        // Initial commit with two version hint tags (both reachable from HEAD)
        Files.write(repo.resolve("file.txt"), "v1".getBytes(StandardCharsets.UTF_8));
        exec(repo, "git", "add", "file.txt");
        exec(repo, "git", "commit", "-m", "initial");
        exec(repo, "git", "tag", "-a", "2.0.0-SNAPSHOT", "-m", "hint low");
        exec(repo, "git", "tag", "-a", "3.0.0-SNAPSHOT", "-m", "hint high");

        // Another commit so HEAD is ahead of the hint tags
        Files.write(repo.resolve("file.txt"), "v2".getBytes(StandardCharsets.UTF_8));
        exec(repo, "git", "add", "file.txt");
        exec(repo, "git", "commit", "-m", "work");

        // Dynamic version should pick the highest reachable hint: 3.0.0
        Map<String, String> userProps = new HashMap<>();
        userProps.put("nisse.source.jgit.dynamicVersion", "true");

        JGitPropertySource source = new JGitPropertySource();
        Map<String, String> properties = source.getProperties(SimpleNisseConfiguration.builder()
                .withCurrentWorkingDirectory(repo)
                .withUserProperties(userProps)
                .build());

        String dynamicVersion = properties.get("dynamicVersion");
        assertNotNull(dynamicVersion, "dynamicVersion should be set");
        assertTrue(
                dynamicVersion.startsWith("3.0.0"),
                "Should use highest reachable hint tag (3.0.0) but got: " + dynamicVersion);
    }

    @Test
    void testVersionHintMixedReachability(@TempDir Path tempDir) throws Exception {
        Path repo = tempDir.resolve("repo");
        Files.createDirectories(repo);

        exec(repo, "git", "init", "-b", "master");
        exec(repo, "git", "config", "user.email", "test@test.com");
        exec(repo, "git", "config", "user.name", "Test");

        // Initial commit — branch point
        Files.write(repo.resolve("file.txt"), "v1".getBytes(StandardCharsets.UTF_8));
        exec(repo, "git", "add", "file.txt");
        exec(repo, "git", "commit", "-m", "initial");

        // Create feature branch from this point
        exec(repo, "git", "branch", "feature");

        // On master: add commit with a high hint tag (unreachable from feature)
        Files.write(repo.resolve("file.txt"), "v2".getBytes(StandardCharsets.UTF_8));
        exec(repo, "git", "add", "file.txt");
        exec(repo, "git", "commit", "-m", "master work");
        exec(repo, "git", "tag", "-a", "5.0.0-SNAPSHOT", "-m", "hint on master");

        // Switch to feature: add commit with a lower hint tag (reachable from feature)
        exec(repo, "git", "checkout", "feature");
        Files.write(repo.resolve("feat.txt"), "feat".getBytes(StandardCharsets.UTF_8));
        exec(repo, "git", "add", "feat.txt");
        exec(repo, "git", "commit", "-m", "feature work");
        exec(repo, "git", "tag", "-a", "2.0.0-SNAPSHOT", "-m", "hint on feature");

        // Another commit so HEAD is ahead of the hint
        Files.write(repo.resolve("feat.txt"), "feat2".getBytes(StandardCharsets.UTF_8));
        exec(repo, "git", "add", "feat.txt");
        exec(repo, "git", "commit", "-m", "more feature work");

        // From feature branch, only the 2.0.0-SNAPSHOT hint should be reachable
        Map<String, String> userProps = new HashMap<>();
        userProps.put("nisse.source.jgit.dynamicVersion", "true");

        JGitPropertySource source = new JGitPropertySource();
        Map<String, String> properties = source.getProperties(SimpleNisseConfiguration.builder()
                .withCurrentWorkingDirectory(repo)
                .withUserProperties(userProps)
                .build());

        String dynamicVersion = properties.get("dynamicVersion");
        assertNotNull(dynamicVersion, "dynamicVersion should be set");
        assertTrue(
                dynamicVersion.startsWith("2.0.0"),
                "Should use only reachable hint tag (2.0.0), ignoring unreachable 5.0.0, but got: " + dynamicVersion);
    }

    @Test
    void testCountingVersion(@TempDir Path tempDir) throws Exception {
        Map<String, String> userProps = new HashMap<>();
        userProps.put("nisse.source.jgit.countingVersion", "true");
        JGitPropertySource source = new JGitPropertySource();

        Path repo = tempDir.resolve("repo");
        Files.createDirectories(repo);

        exec(repo, "git", "init", "-b", "master");
        exec(repo, "git", "config", "user.email", "test@test.com");
        exec(repo, "git", "config", "user.name", "Test");

        // 1 plain commit → 0.0.0, commit=1
        Files.write(repo.resolve("file.txt"), "v1".getBytes(StandardCharsets.UTF_8));
        exec(repo, "git", "add", "file.txt");
        exec(repo, "git", "commit", "-m", "initial");

        assertCountingVersion("0.0.0-1", source, repo, userProps);

        // 2nd plain commit → 0.0.0, commit=2
        Files.write(repo.resolve("file.txt"), "v2".getBytes(StandardCharsets.UTF_8));
        exec(repo, "git", "add", "file.txt");
        exec(repo, "git", "commit", "-m", "fix");

        assertCountingVersion("0.0.0-2", source, repo, userProps);

        // [patch] → patch=1, commit=0
        exec(repo, "git", "commit", "--allow-empty", "-m", "[patch] release");

        assertCountingVersion("0.0.1", source, repo, userProps);

        // plain commit after [patch] → 0.0.1, commit=1
        Files.write(repo.resolve("file.txt"), "v4".getBytes(StandardCharsets.UTF_8));
        exec(repo, "git", "add", "file.txt");
        exec(repo, "git", "commit", "-m", "fix");

        assertCountingVersion("0.0.1-1", source, repo, userProps);

        // [minor] → minor=1, patch=0, commit=0
        exec(repo, "git", "commit", "--allow-empty", "-m", "[minor] release");

        assertCountingVersion("0.1.0", source, repo, userProps);

        // plain commit → 0.1.0, commit=1
        exec(repo, "git", "commit", "--allow-empty", "-m", "fix");

        assertCountingVersion("0.1.0-1", source, repo, userProps);

        // [major] → major=1, minor=0, patch=0, commit=0
        exec(repo, "git", "commit", "--allow-empty", "-m", "[major] big change");

        assertCountingVersion("1.0.0", source, repo, userProps);

        // plain commit → 1.0.0, commit=1
        exec(repo, "git", "commit", "--allow-empty", "-m", "fix");

        assertCountingVersion("1.0.0-1", source, repo, userProps);
    }

    @Test
    void testCountingVersionStartFrom(@TempDir Path tempDir) throws Exception {
        Map<String, String> userProps = new HashMap<>();
        userProps.put("nisse.source.jgit.countingVersion", "true");
        userProps.put("nisse.source.jgit.countingVersion.startMajor", "0");
        userProps.put("nisse.source.jgit.countingVersion.startMinor", "0");
        userProps.put("nisse.source.jgit.countingVersion.startPatch", "1");
        JGitPropertySource source = new JGitPropertySource();

        Path repo = tempDir.resolve("repo");
        Files.createDirectories(repo);

        exec(repo, "git", "init", "-b", "master");
        exec(repo, "git", "config", "user.email", "test@test.com");
        exec(repo, "git", "config", "user.name", "Test");

        // 1 plain commit with startPatch=1 → 0.0.1, commit=1
        exec(repo, "git", "commit", "--allow-empty", "-m", "initial");

        assertCountingVersion("0.0.1-1", source, repo, userProps);

        // [minor] → minor=1, patch=0, commit=0
        exec(repo, "git", "commit", "--allow-empty", "-m", "[minor] release");

        assertCountingVersion("0.1.0", source, repo, userProps);
    }

    @Test
    void testCountingVersionPattern(@TempDir Path tempDir) throws Exception {
        Map<String, String> userProps = new HashMap<>();
        userProps.put("nisse.source.jgit.countingVersion", "true");
        // Use dot separator like gradle-git-versioner: %M.%m.%p(.%c)
        userProps.put("nisse.source.jgit.countingVersion.pattern", "%M.%m.%p(.%c)");
        JGitPropertySource source = new JGitPropertySource();

        Path repo = tempDir.resolve("repo");
        Files.createDirectories(repo);

        exec(repo, "git", "init", "-b", "master");
        exec(repo, "git", "config", "user.email", "test@test.com");
        exec(repo, "git", "config", "user.name", "Test");

        // [minor] then 2 plain commits
        exec(repo, "git", "commit", "--allow-empty", "-m", "[minor] first");
        exec(repo, "git", "commit", "--allow-empty", "-m", "fix 1");
        exec(repo, "git", "commit", "--allow-empty", "-m", "fix 2");

        // With dot pattern: 0.1.0.2
        assertCountingVersion("0.1.0.2", source, repo, userProps);

        // [patch] resets commit → 0.1.1 (no .0 suffix)
        exec(repo, "git", "commit", "--allow-empty", "-m", "[patch] release");

        assertCountingVersion("0.1.1", source, repo, userProps);
    }

    @Test
    void testCountingVersionFullMessage(@TempDir Path tempDir) throws Exception {
        Map<String, String> userProps = new HashMap<>();
        userProps.put("nisse.source.jgit.countingVersion", "true");
        JGitPropertySource source = new JGitPropertySource();

        Path repo = tempDir.resolve("repo");
        Files.createDirectories(repo);

        exec(repo, "git", "init", "-b", "master");
        exec(repo, "git", "config", "user.email", "test@test.com");
        exec(repo, "git", "config", "user.name", "Test");

        // Directive in commit body (not subject line) should still match
        exec(repo, "git", "commit", "--allow-empty", "-m", "some fix\n\n[minor] bump version");

        assertCountingVersion("0.1.0", source, repo, userProps);
    }

    @Test
    void testCountingVersionTagsIgnored(@TempDir Path tempDir) throws Exception {
        Map<String, String> userProps = new HashMap<>();
        userProps.put("nisse.source.jgit.countingVersion", "true");
        JGitPropertySource source = new JGitPropertySource();

        Path repo = tempDir.resolve("repo");
        Files.createDirectories(repo);

        exec(repo, "git", "init", "-b", "master");
        exec(repo, "git", "config", "user.email", "test@test.com");
        exec(repo, "git", "config", "user.name", "Test");

        // [minor] commit, tagged
        exec(repo, "git", "commit", "--allow-empty", "-m", "[minor] release");
        exec(repo, "git", "tag", "v0.1.0");

        // Version on the tag should be 0.1.0 — no double-bump
        assertCountingVersion("0.1.0", source, repo, userProps);

        // 1 plain commit after tag
        exec(repo, "git", "commit", "--allow-empty", "-m", "fix");

        // Should be 0.1.0-1 — tags don't affect counting, only commit messages
        assertCountingVersion("0.1.0-1", source, repo, userProps);
    }

    @Test
    void testCountingVersionCustomMatch(@TempDir Path tempDir) throws Exception {
        Map<String, String> userProps = new HashMap<>();
        userProps.put("nisse.source.jgit.countingVersion", "true");
        userProps.put("nisse.source.jgit.countingVersion.matchMinor", "#minor");
        userProps.put("nisse.source.jgit.countingVersion.matchPatch", "#patch");
        JGitPropertySource source = new JGitPropertySource();

        Path repo = tempDir.resolve("repo");
        Files.createDirectories(repo);

        exec(repo, "git", "init", "-b", "master");
        exec(repo, "git", "config", "user.email", "test@test.com");
        exec(repo, "git", "config", "user.name", "Test");

        // Default [minor] should NOT match with custom config
        exec(repo, "git", "commit", "--allow-empty", "-m", "[minor] release");

        assertCountingVersion("0.0.0-1", source, repo, userProps);

        // Custom #minor SHOULD match
        exec(repo, "git", "commit", "--allow-empty", "-m", "#minor release");

        assertCountingVersion("0.1.0", source, repo, userProps);
    }

    @Test
    void testFormatCountingVersion() {
        // Default pattern with commit count
        assertEquals("1.2.3-4", JGitPropertySource.formatCountingVersion("%M.%m.%p(-%c)", 1, 2, 3, 4));
        // Default pattern without commit count
        assertEquals("1.2.3", JGitPropertySource.formatCountingVersion("%M.%m.%p(-%c)", 1, 2, 3, 0));
        // Dot pattern with commit count
        assertEquals("1.2.3.4", JGitPropertySource.formatCountingVersion("%M.%m.%p(.%c)", 1, 2, 3, 4));
        // Dot pattern without commit count
        assertEquals("1.2.3", JGitPropertySource.formatCountingVersion("%M.%m.%p(.%c)", 1, 2, 3, 0));
        // No conditional section
        assertEquals("1.2.3", JGitPropertySource.formatCountingVersion("%M.%m.%p", 1, 2, 3, 0));
        assertEquals("1.2.3", JGitPropertySource.formatCountingVersion("%M.%m.%p", 1, 2, 3, 5));
    }

    @Test
    void testBuildTagVersionPatternDefault() {
        // Empty prefix should return the default TAG_VERSION_PATTERN (v? prefix)
        Pattern defaultPattern = JGitPropertySource.buildTagVersionPattern("");
        assertSame(JGitPropertySource.TAG_VERSION_PATTERN, defaultPattern);

        Pattern nullPattern = JGitPropertySource.buildTagVersionPattern(null);
        assertSame(JGitPropertySource.TAG_VERSION_PATTERN, nullPattern);

        // Default pattern matches v-prefixed and bare version tags
        assertTrue(defaultPattern.matcher("refs/tags/v1.0.0").matches());
        assertTrue(defaultPattern.matcher("refs/tags/1.0.0").matches());
        assertFalse(defaultPattern.matcher("refs/tags/jline-3.28.0").matches());
        assertFalse(defaultPattern.matcher("refs/tags/release-1.0.0").matches());
    }

    @Test
    void testBuildTagVersionPatternCustomPrefix() {
        Pattern jlinePattern = JGitPropertySource.buildTagVersionPattern("jline-");
        assertTrue(jlinePattern.matcher("refs/tags/jline-3.28.0").matches());
        assertFalse(jlinePattern.matcher("refs/tags/v3.28.0").matches());
        assertFalse(jlinePattern.matcher("refs/tags/3.28.0").matches());

        // Verify version extraction
        java.util.regex.Matcher m = jlinePattern.matcher("refs/tags/jline-3.28.0");
        assertTrue(m.matches());
        assertEquals("3.28.0", m.group(1));

        Pattern releasePattern = JGitPropertySource.buildTagVersionPattern("release-");
        assertTrue(releasePattern.matcher("refs/tags/release-1.0.0").matches());
        assertFalse(releasePattern.matcher("refs/tags/v1.0.0").matches());

        Pattern camelPattern = JGitPropertySource.buildTagVersionPattern("camel-");
        assertTrue(camelPattern.matcher("refs/tags/camel-4.8.0").matches());
        assertFalse(camelPattern.matcher("refs/tags/v4.8.0").matches());
    }

    @Test
    void testBuildTagVersionPatternWithQualifier() {
        Pattern jlinePattern = JGitPropertySource.buildTagVersionPattern("jline-");
        java.util.regex.Matcher m = jlinePattern.matcher("refs/tags/jline-3.28.0-rc1");
        assertTrue(m.matches());
        assertEquals("3.28.0-rc1", m.group(1));
        assertEquals("3.28.0", m.group(2));
        assertEquals("-rc1", m.group(3));
    }

    @Test
    void testTagPrefixDynamicVersion(@TempDir Path tempDir) throws Exception {
        Path repo = tempDir.resolve("repo");
        Files.createDirectories(repo);

        exec(repo, "git", "init", "-b", "master");
        exec(repo, "git", "config", "user.email", "test@test.com");
        exec(repo, "git", "config", "user.name", "Test");

        // Create initial commit with a prefixed tag
        Files.write(repo.resolve("file.txt"), "v1".getBytes(StandardCharsets.UTF_8));
        exec(repo, "git", "add", "file.txt");
        exec(repo, "git", "commit", "-m", "initial");
        exec(repo, "git", "tag", "jline-1.0.0");

        // Create a second commit so version is computed from tag history
        Files.write(repo.resolve("file.txt"), "v2".getBytes(StandardCharsets.UTF_8));
        exec(repo, "git", "add", "file.txt");
        exec(repo, "git", "commit", "-m", "second commit");

        // Without tagPrefix, the jline- tag is not recognized → falls back to default
        Map<String, String> propsNoPrefix = new HashMap<>();
        propsNoPrefix.put("nisse.source.jgit.dynamicVersion", "true");

        JGitPropertySource source = new JGitPropertySource();
        Map<String, String> resultNoPrefix = source.getProperties(SimpleNisseConfiguration.builder()
                .withCurrentWorkingDirectory(repo)
                .withUserProperties(propsNoPrefix)
                .build());

        String versionNoPrefix = resultNoPrefix.get("dynamicVersion");
        assertNotNull(versionNoPrefix, "dynamicVersion should be set");
        assertTrue(
                versionNoPrefix.startsWith("0.1.0"),
                "Without tagPrefix, should fall back to default 0.1.0 but got: " + versionNoPrefix);

        // With tagPrefix=jline-, the tag is recognized
        Map<String, String> propsWithPrefix = new HashMap<>();
        propsWithPrefix.put("nisse.source.jgit.dynamicVersion", "true");
        propsWithPrefix.put("nisse.source.jgit.tagPrefix", "jline-");

        Map<String, String> resultWithPrefix = source.getProperties(SimpleNisseConfiguration.builder()
                .withCurrentWorkingDirectory(repo)
                .withUserProperties(propsWithPrefix)
                .build());

        String versionWithPrefix = resultWithPrefix.get("dynamicVersion");
        assertNotNull(versionWithPrefix, "dynamicVersion should be set");
        assertTrue(
                versionWithPrefix.startsWith("1.0."),
                "With tagPrefix=jline-, should resolve from jline-1.0.0 tag but got: " + versionWithPrefix);
    }

    @Test
    void testTagPrefixExactMatch(@TempDir Path tempDir) throws Exception {
        Path repo = tempDir.resolve("repo");
        Files.createDirectories(repo);

        exec(repo, "git", "init", "-b", "master");
        exec(repo, "git", "config", "user.email", "test@test.com");
        exec(repo, "git", "config", "user.name", "Test");

        // Create a commit tagged with the prefixed tag — HEAD is on the tag
        Files.write(repo.resolve("file.txt"), "v1".getBytes(StandardCharsets.UTF_8));
        exec(repo, "git", "add", "file.txt");
        exec(repo, "git", "commit", "-m", "release");
        exec(repo, "git", "tag", "mylib-2.5.0");

        Map<String, String> userProps = new HashMap<>();
        userProps.put("nisse.source.jgit.dynamicVersion", "true");
        userProps.put("nisse.source.jgit.tagPrefix", "mylib-");

        JGitPropertySource source = new JGitPropertySource();
        Map<String, String> result = source.getProperties(SimpleNisseConfiguration.builder()
                .withCurrentWorkingDirectory(repo)
                .withUserProperties(userProps)
                .build());

        assertEquals("2.5.0", result.get("dynamicVersion"), "Should resolve exact version from tag on HEAD");
    }

    @Test
    void testUnmatchedTagsFallbackToDefault(@TempDir Path tempDir) throws Exception {
        // This test exercises the INFO logging path: tags exist but none match the pattern.
        // With slf4j-simple we cannot assert on the log message directly, but we verify
        // the behavioral outcome (fallback to default version) which triggers the log.
        Path repo = tempDir.resolve("repo");
        Files.createDirectories(repo);

        exec(repo, "git", "init", "-b", "master");
        exec(repo, "git", "config", "user.email", "test@test.com");
        exec(repo, "git", "config", "user.name", "Test");

        // Create commits with tags that use non-standard prefixes
        Files.write(repo.resolve("file.txt"), "v1".getBytes(StandardCharsets.UTF_8));
        exec(repo, "git", "add", "file.txt");
        exec(repo, "git", "commit", "-m", "initial");
        exec(repo, "git", "tag", "release-1.0.0");

        Files.write(repo.resolve("file.txt"), "v2".getBytes(StandardCharsets.UTF_8));
        exec(repo, "git", "add", "file.txt");
        exec(repo, "git", "commit", "-m", "second");
        exec(repo, "git", "tag", "camel-2.0.0");

        Files.write(repo.resolve("file.txt"), "v3".getBytes(StandardCharsets.UTF_8));
        exec(repo, "git", "add", "file.txt");
        exec(repo, "git", "commit", "-m", "third");

        // Default config: neither release- nor camel- tags match → fallback to 0.1.0
        Map<String, String> userProps = new HashMap<>();
        userProps.put("nisse.source.jgit.dynamicVersion", "true");

        JGitPropertySource source = new JGitPropertySource();
        Map<String, String> result = source.getProperties(SimpleNisseConfiguration.builder()
                .withCurrentWorkingDirectory(repo)
                .withUserProperties(userProps)
                .build());

        String version = result.get("dynamicVersion");
        assertNotNull(version, "dynamicVersion should be set");
        assertTrue(
                version.startsWith("0.1.0"),
                "Tags exist but none match default pattern, should fall back to 0.1.0 but got: " + version);

        // With tagPrefix=camel-, the camel-2.0.0 tag is recognized
        userProps.put("nisse.source.jgit.tagPrefix", "camel-");

        result = source.getProperties(SimpleNisseConfiguration.builder()
                .withCurrentWorkingDirectory(repo)
                .withUserProperties(userProps)
                .build());

        version = result.get("dynamicVersion");
        assertNotNull(version, "dynamicVersion should be set");
        assertTrue(
                version.startsWith("2.0."),
                "With tagPrefix=camel-, should resolve from camel-2.0.0 tag but got: " + version);
    }

    @Test
    void testPresetSnapshot(@TempDir Path tempDir) throws Exception {
        // Snapshot preset: increment patch, append build number, append SNAPSHOT, no branch, no dirty
        Map<String, String> userProps = new HashMap<>();
        userProps.put("nisse.source.jgit.dynamicVersion", "true");
        userProps.put("nisse.source.jgit.dynamicVersion.preset", "snapshot");
        JGitPropertySource source = new JGitPropertySource();

        Path repo = newRepo(tempDir);
        exec(repo, "git", "commit", "--allow-empty", "-m", "chore: base");
        exec(repo, "git", "tag", "1.0.0");
        exec(repo, "git", "commit", "--allow-empty", "-m", "fix: a bug");

        assertDynamicVersion("1.0.1-1-SNAPSHOT", source, repo, userProps);
    }

    @Test
    void testPresetCi(@TempDir Path tempDir) throws Exception {
        // CI preset: increment patch, append build number, append branch name, no SNAPSHOT
        Map<String, String> userProps = new HashMap<>();
        userProps.put("nisse.source.jgit.dynamicVersion", "true");
        userProps.put("nisse.source.jgit.dynamicVersion.preset", "ci");
        JGitPropertySource source = new JGitPropertySource();

        Path repo = newRepo(tempDir);
        exec(repo, "git", "commit", "--allow-empty", "-m", "chore: base");
        exec(repo, "git", "tag", "1.0.0");
        exec(repo, "git", "commit", "--allow-empty", "-m", "fix: a bug");

        assertDynamicVersion("1.0.1-1-master", source, repo, userProps);
    }

    @Test
    void testPresetRelease(@TempDir Path tempDir) throws Exception {
        // Release preset: exact tag version, no increment, no qualifiers
        Map<String, String> userProps = new HashMap<>();
        userProps.put("nisse.source.jgit.dynamicVersion", "true");
        userProps.put("nisse.source.jgit.dynamicVersion.preset", "release");
        JGitPropertySource source = new JGitPropertySource();

        Path repo = newRepo(tempDir);
        exec(repo, "git", "commit", "--allow-empty", "-m", "chore: base");
        exec(repo, "git", "tag", "1.0.0");

        // On the tag itself: exact version
        assertDynamicVersion("1.0.0", source, repo, userProps);

        // Even after additional commits: no increment (versionIncrement=none), no qualifiers
        exec(repo, "git", "commit", "--allow-empty", "-m", "fix: a bug");
        assertDynamicVersion("1.0.0", source, repo, userProps);
    }

    @Test
    void testPresetDirtyClean(@TempDir Path tempDir) throws Exception {
        // Dirty preset on a clean repo: like snapshot, no DIRTY qualifier
        Map<String, String> userProps = new HashMap<>();
        userProps.put("nisse.source.jgit.dynamicVersion", "true");
        userProps.put("nisse.source.jgit.dynamicVersion.preset", "dirty");
        JGitPropertySource source = new JGitPropertySource();

        Path repo = newRepo(tempDir);
        exec(repo, "git", "commit", "--allow-empty", "-m", "chore: base");
        exec(repo, "git", "tag", "1.0.0");
        exec(repo, "git", "commit", "--allow-empty", "-m", "fix: a bug");

        // Clean repo: same as snapshot (no DIRTY qualifier)
        assertDynamicVersion("1.0.1-1-SNAPSHOT", source, repo, userProps);
    }

    @Test
    void testPresetDirtyUncommitted(@TempDir Path tempDir) throws Exception {
        // Dirty preset with uncommitted changes: like snapshot + DIRTY qualifier
        Map<String, String> userProps = new HashMap<>();
        userProps.put("nisse.source.jgit.dynamicVersion", "true");
        userProps.put("nisse.source.jgit.dynamicVersion.preset", "dirty");
        JGitPropertySource source = new JGitPropertySource();

        Path repo = newRepo(tempDir);
        Files.write(repo.resolve("file.txt"), "v1".getBytes(StandardCharsets.UTF_8));
        exec(repo, "git", "add", "file.txt");
        exec(repo, "git", "commit", "-m", "chore: base");
        exec(repo, "git", "tag", "1.0.0");
        exec(repo, "git", "commit", "--allow-empty", "-m", "fix: a bug");

        // Create uncommitted changes
        Files.write(repo.resolve("file.txt"), "dirty".getBytes(StandardCharsets.UTF_8));

        assertDynamicVersion("1.0.1-1-DIRTY-SNAPSHOT", source, repo, userProps);
    }

    @Test
    void testPresetOverriddenByExplicitProperty(@TempDir Path tempDir) throws Exception {
        // Explicit properties override preset defaults
        Map<String, String> userProps = new HashMap<>();
        userProps.put("nisse.source.jgit.dynamicVersion", "true");
        userProps.put("nisse.source.jgit.dynamicVersion.preset", "ci");
        // Override: disable branch name even though ci preset enables it
        userProps.put("nisse.source.jgit.appendBranchName", "false");
        JGitPropertySource source = new JGitPropertySource();

        Path repo = newRepo(tempDir);
        exec(repo, "git", "commit", "--allow-empty", "-m", "chore: base");
        exec(repo, "git", "tag", "1.0.0");
        exec(repo, "git", "commit", "--allow-empty", "-m", "fix: a bug");

        // CI preset would add branch name, but the explicit override disables it
        assertDynamicVersion("1.0.1-1", source, repo, userProps);
    }

    @Test
    void testPresetCaseInsensitive(@TempDir Path tempDir) throws Exception {
        // Preset name should be case-insensitive
        Map<String, String> userProps = new HashMap<>();
        userProps.put("nisse.source.jgit.dynamicVersion", "true");
        userProps.put("nisse.source.jgit.dynamicVersion.preset", "RELEASE");
        JGitPropertySource source = new JGitPropertySource();

        Path repo = newRepo(tempDir);
        exec(repo, "git", "commit", "--allow-empty", "-m", "chore: base");
        exec(repo, "git", "tag", "2.0.0");

        assertDynamicVersion("2.0.0", source, repo, userProps);
    }

    @Test
    void testPresetUnknownIgnored(@TempDir Path tempDir) throws Exception {
        // Unknown preset name should be ignored (fall back to default behavior)
        Map<String, String> userProps = new HashMap<>();
        userProps.put("nisse.source.jgit.dynamicVersion", "true");
        userProps.put("nisse.source.jgit.dynamicVersion.preset", "bogus");
        JGitPropertySource source = new JGitPropertySource();

        Path repo = newRepo(tempDir);
        exec(repo, "git", "commit", "--allow-empty", "-m", "chore: base");
        exec(repo, "git", "tag", "1.0.0");
        exec(repo, "git", "commit", "--allow-empty", "-m", "fix: a bug");

        // Should behave as if no preset was set (default behavior = snapshot-like)
        assertDynamicVersion("1.0.1-1-SNAPSHOT", source, repo, userProps);
    }

    @Test
    void testPresetFromString() {
        assertEquals(DynamicVersionPreset.SNAPSHOT, DynamicVersionPreset.fromString("snapshot"));
        assertEquals(DynamicVersionPreset.CI, DynamicVersionPreset.fromString("ci"));
        assertEquals(DynamicVersionPreset.RELEASE, DynamicVersionPreset.fromString("release"));
        assertEquals(DynamicVersionPreset.DIRTY, DynamicVersionPreset.fromString("dirty"));
        assertEquals(DynamicVersionPreset.SNAPSHOT, DynamicVersionPreset.fromString("SNAPSHOT"));
        assertEquals(DynamicVersionPreset.CI, DynamicVersionPreset.fromString("  CI  "));
        assertNull(DynamicVersionPreset.fromString("bogus"));
        assertNull(DynamicVersionPreset.fromString(""));
        assertNull(DynamicVersionPreset.fromString(null));
    }

    @Test
    void testPresetDefaults() {
        // Verify each preset returns the expected default values
        Map<String, String> snapshot = DynamicVersionPreset.SNAPSHOT.defaults();
        assertEquals("true", snapshot.get("nisse.source.jgit.increasePatchVersion"));
        assertEquals("true", snapshot.get("nisse.source.jgit.appendBuildNumber"));
        assertEquals("true", snapshot.get("nisse.source.jgit.appendSnapshot"));
        assertEquals("false", snapshot.get("nisse.source.jgit.appendBranchName"));
        assertEquals("false", snapshot.get("nisse.source.jgit.appendDirty"));

        Map<String, String> ci = DynamicVersionPreset.CI.defaults();
        assertEquals("true", ci.get("nisse.source.jgit.increasePatchVersion"));
        assertEquals("true", ci.get("nisse.source.jgit.appendBuildNumber"));
        assertEquals("false", ci.get("nisse.source.jgit.appendSnapshot"));
        assertEquals("true", ci.get("nisse.source.jgit.appendBranchName"));
        assertEquals("false", ci.get("nisse.source.jgit.appendDirty"));

        Map<String, String> release = DynamicVersionPreset.RELEASE.defaults();
        assertEquals("none", release.get("nisse.source.jgit.versionIncrement"));
        assertEquals("false", release.get("nisse.source.jgit.appendBuildNumber"));
        assertEquals("false", release.get("nisse.source.jgit.appendSnapshot"));
        assertEquals("false", release.get("nisse.source.jgit.appendBranchName"));
        assertEquals("false", release.get("nisse.source.jgit.appendDirty"));

        Map<String, String> dirty = DynamicVersionPreset.DIRTY.defaults();
        assertEquals("true", dirty.get("nisse.source.jgit.increasePatchVersion"));
        assertEquals("true", dirty.get("nisse.source.jgit.appendBuildNumber"));
        assertEquals("true", dirty.get("nisse.source.jgit.appendSnapshot"));
        assertEquals("false", dirty.get("nisse.source.jgit.appendBranchName"));
        assertEquals("true", dirty.get("nisse.source.jgit.appendDirty"));
    }

    @Test
    void sanitizeBranchName() {
        assertEquals("master", JGitPropertySource.sanitizeBranchName("master"));
        assertEquals("feat-cool-feature-01", JGitPropertySource.sanitizeBranchName("feat/cool-feature-01"));
        assertEquals(
                "is-this-valid-branch-name-at-all",
                JGitPropertySource.sanitizeBranchName("is this valid branch name at all?"));
    }

    @Test
    void testConventionalCommitsBumpFromMessages() {
        assertEquals(JGitPropertySource.Bump.PATCH, JGitPropertySource.bumpFrom("fix: a bug"));
        assertEquals(JGitPropertySource.Bump.PATCH, JGitPropertySource.bumpFrom("chore(deps): bump something"));
        assertEquals(JGitPropertySource.Bump.MINOR, JGitPropertySource.bumpFrom("feat: a feature"));
        assertEquals(JGitPropertySource.Bump.MINOR, JGitPropertySource.bumpFrom("feat(core): a scoped feature"));
        assertEquals(JGitPropertySource.Bump.MAJOR, JGitPropertySource.bumpFrom("feat!: breaking"));
        assertEquals(JGitPropertySource.Bump.MAJOR, JGitPropertySource.bumpFrom("fix(api)!: breaking"));
        assertEquals(
                JGitPropertySource.Bump.MAJOR,
                JGitPropertySource.bumpFrom("feat: something\n\nBREAKING CHANGE: it moved"));
        assertEquals(
                JGitPropertySource.Bump.MAJOR,
                JGitPropertySource.bumpFrom("feat: something\n\nBREAKING-CHANGE: it moved"));

        // not Conventional Commits at all: still a patch, never less than increasePatchVersion gave
        assertEquals(JGitPropertySource.Bump.PATCH, JGitPropertySource.bumpFrom("just a message"));
        assertEquals(JGitPropertySource.Bump.PATCH, JGitPropertySource.bumpFrom(""));

        // a type merely starting with "feat" is not "feat"
        assertEquals(JGitPropertySource.Bump.PATCH, JGitPropertySource.bumpFrom("feature: not the feat type"));

        // "BREAKING CHANGE" counts only as a footer in the trailer block, never as prose
        assertEquals(
                JGitPropertySource.Bump.PATCH, JGitPropertySource.bumpFrom("fix: mentions BREAKING CHANGE: inline"));
        assertEquals(
                JGitPropertySource.Bump.PATCH,
                JGitPropertySource.bumpFrom("fix: a bug\n\nBREAKING CHANGE: none, quoting a changelog\n\nreally"));
        // an ordinary commit has no footers at all — this is the body git revert copies verbatim
        assertEquals(
                JGitPropertySource.Bump.PATCH,
                JGitPropertySource.bumpFrom(
                        "Revert \"feat: x\"\n\nThis reverts commit abc.\n\nBREAKING CHANGE: moved"));
        // the specification requires a non-empty scope and a space after the colon
        assertEquals(JGitPropertySource.Bump.PATCH, JGitPropertySource.bumpFrom("feat(): empty scope"));

        // the highest wins
        assertEquals(
                JGitPropertySource.Bump.MAJOR,
                JGitPropertySource.highestBumpFrom(Arrays.asList("fix: a", "feat: b", "feat!: c")));
        assertEquals(
                JGitPropertySource.Bump.MINOR, JGitPropertySource.highestBumpFrom(Arrays.asList("fix: a", "feat: b")));
        assertEquals(JGitPropertySource.Bump.PATCH, JGitPropertySource.highestBumpFrom(Collections.emptyList()));
    }

    @Test
    void testConventionalCommitsVersion(@TempDir Path tempDir) throws Exception {
        Map<String, String> userProps = new HashMap<>();
        userProps.put("nisse.source.jgit.dynamicVersion", "true");
        userProps.put("nisse.source.jgit.versionIncrement", "conventionalCommits");
        userProps.put("nisse.source.jgit.appendBuildNumber", "false");
        userProps.put("nisse.source.jgit.appendSnapshot", "false");
        JGitPropertySource source = new JGitPropertySource();

        Path repo = tempDir.resolve("repo");
        Files.createDirectories(repo);

        exec(repo, "git", "init", "-b", "master");
        exec(repo, "git", "config", "user.email", "test@test.com");
        exec(repo, "git", "config", "user.name", "Test");
        exec(repo, "git", "config", "commit.gpgsign", "false");

        Files.write(repo.resolve("file.txt"), "v1".getBytes(StandardCharsets.UTF_8));
        exec(repo, "git", "add", "file.txt");
        // The tagged commit's own message is load-bearing: were it counted, this would major-bump.
        exec(repo, "git", "commit", "-m", "feat!: the release commit itself");
        exec(repo, "git", "tag", "1.2.3");

        // on the tag itself: the tag is the version, untouched
        assertDynamicVersion("1.2.3", source, repo, userProps);

        // fix: -> patch
        exec(repo, "git", "commit", "--allow-empty", "-m", "fix: a bug");
        assertDynamicVersion("1.2.4", source, repo, userProps);

        // feat: outranks the fix -> minor, patch reset
        exec(repo, "git", "commit", "--allow-empty", "-m", "feat: a feature");
        assertDynamicVersion("1.3.0", source, repo, userProps);

        // a breaking change outranks both -> major, minor and patch reset
        exec(repo, "git", "commit", "--allow-empty", "-m", "feat!: a breaking feature");
        assertDynamicVersion("2.0.0", source, repo, userProps);

        // a later ordinary commit does not lower the increment
        exec(repo, "git", "commit", "--allow-empty", "-m", "docs: tidy up");
        assertDynamicVersion("2.0.0", source, repo, userProps);
    }

    @Test
    void testConventionalCommitsDisabledKeepsPatchBehaviour(@TempDir Path tempDir) throws Exception {
        Map<String, String> userProps = new HashMap<>();
        userProps.put("nisse.source.jgit.dynamicVersion", "true");
        userProps.put("nisse.source.jgit.appendBuildNumber", "false");
        userProps.put("nisse.source.jgit.appendSnapshot", "false");
        JGitPropertySource source = new JGitPropertySource();

        Path repo = tempDir.resolve("repo");
        Files.createDirectories(repo);

        exec(repo, "git", "init", "-b", "master");
        exec(repo, "git", "config", "user.email", "test@test.com");
        exec(repo, "git", "config", "user.name", "Test");
        exec(repo, "git", "config", "commit.gpgsign", "false");

        Files.write(repo.resolve("file.txt"), "v1".getBytes(StandardCharsets.UTF_8));
        exec(repo, "git", "add", "file.txt");
        // The tagged commit's own message is load-bearing: were it counted, this would major-bump.
        exec(repo, "git", "commit", "-m", "feat!: the release commit itself");
        exec(repo, "git", "tag", "1.2.3");

        // the default is unchanged: a breaking feature still only increases the patch version
        exec(repo, "git", "commit", "--allow-empty", "-m", "feat!: a breaking feature");
        assertDynamicVersion("1.2.4", source, repo, userProps);
    }

    @Test
    void testConventionalCommitsAcrossAMergeOfABranchCutBeforeTheTag(@TempDir Path tempDir) throws Exception {
        Map<String, String> userProps = new HashMap<>();
        userProps.put("nisse.source.jgit.dynamicVersion", "true");
        userProps.put("nisse.source.jgit.versionIncrement", "conventionalCommits");
        userProps.put("nisse.source.jgit.appendBuildNumber", "false");
        userProps.put("nisse.source.jgit.appendSnapshot", "false");
        JGitPropertySource source = new JGitPropertySource();

        Path repo = newRepo(tempDir);
        exec(repo, "git", "commit", "--allow-empty", "-m", "chore: base");

        // a branch cut BEFORE the release, carrying the feature
        exec(repo, "git", "checkout", "-b", "side");
        exec(repo, "git", "commit", "--allow-empty", "-m", "feat: side feature");

        // the release happens on master afterwards, so the side commit is older than the tag
        exec(repo, "git", "checkout", "master");
        exec(repo, "git", "commit", "--allow-empty", "-m", "chore: release");
        exec(repo, "git", "tag", "1.2.3");

        // ...and only then is the branch merged
        exec(repo, "git", "merge", "--no-ff", "-m", "chore: merge side", "side");

        // The feature is in tag..HEAD, so it counts — even though its commit date precedes the tag.
        assertDynamicVersion("1.3.0", source, repo, userProps);
    }

    @Test
    void testConventionalCommitsSupersedesIncreasePatchVersion(@TempDir Path tempDir) throws Exception {
        // Uses the deprecated boolean fallback to verify backward compatibility:
        // conventionalCommits=true takes precedence over increasePatchVersion=false
        Map<String, String> userProps = new HashMap<>();
        userProps.put("nisse.source.jgit.dynamicVersion", "true");
        userProps.put("nisse.source.jgit.conventionalCommits", "true");
        userProps.put("nisse.source.jgit.increasePatchVersion", "false");
        userProps.put("nisse.source.jgit.appendBuildNumber", "false");
        userProps.put("nisse.source.jgit.appendSnapshot", "false");
        JGitPropertySource source = new JGitPropertySource();

        Path repo = newRepo(tempDir);
        exec(repo, "git", "commit", "--allow-empty", "-m", "chore: base");
        exec(repo, "git", "tag", "1.2.3");
        exec(repo, "git", "commit", "--allow-empty", "-m", "feat: a feature");

        // increasePatchVersion=false would have pinned this to 1.2.3; conventionalCommits supersedes it
        assertDynamicVersion("1.3.0", source, repo, userProps);
    }

    @Test
    void testConventionalCommitsWithBuildNumberAndNoReleaseTag(@TempDir Path tempDir) throws Exception {
        Map<String, String> userProps = new HashMap<>();
        userProps.put("nisse.source.jgit.dynamicVersion", "true");
        userProps.put("nisse.source.jgit.versionIncrement", "conventionalCommits");
        userProps.put("nisse.source.jgit.appendSnapshot", "false");
        JGitPropertySource source = new JGitPropertySource();

        Path repo = newRepo(tempDir);
        exec(repo, "git", "commit", "--allow-empty", "-m", "chore: base");
        exec(repo, "git", "tag", "1.2.3");
        exec(repo, "git", "commit", "--allow-empty", "-m", "feat: a feature");
        exec(repo, "git", "commit", "--allow-empty", "-m", "docs: more");

        // the build number rides along with a minor increase exactly as it does with a patch one
        assertDynamicVersion("1.3.0-2", source, repo, userProps);

        // with no release tag at all the fallback stands, whatever the commits say
        Path untagged = newRepo(tempDir.resolve("untagged"));
        exec(untagged, "git", "commit", "--allow-empty", "-m", "feat!: breaking, but nothing to increase from");
        assertDynamicVersion("0.1.0-1", source, untagged, userProps);
    }

    @Test
    void testConventionalCommitsDropsAQualifierItHasOutgrown(@TempDir Path tempDir) throws Exception {
        Map<String, String> userProps = new HashMap<>();
        userProps.put("nisse.source.jgit.dynamicVersion", "true");
        userProps.put("nisse.source.jgit.versionIncrement", "conventionalCommits");
        userProps.put("nisse.source.jgit.appendBuildNumber", "false");
        userProps.put("nisse.source.jgit.appendSnapshot", "false");
        JGitPropertySource source = new JGitPropertySource();

        Path repo = newRepo(tempDir);
        exec(repo, "git", "commit", "--allow-empty", "-m", "chore: base");
        exec(repo, "git", "tag", "1.2.3-rc1");
        exec(repo, "git", "commit", "--allow-empty", "-m", "feat!: breaking");

        // 2.0.0-rc1 would claim to be a candidate for a release that never had one
        assertDynamicVersion("2.0.0", source, repo, userProps);
    }

    @Test
    void testConventionalCommitsMinorOnZeroZeroXIsNotMistakenForNoReleaseTag(@TempDir Path tempDir) throws Exception {
        Map<String, String> userProps = new HashMap<>();
        userProps.put("nisse.source.jgit.dynamicVersion", "true");
        userProps.put("nisse.source.jgit.versionIncrement", "conventionalCommits");
        userProps.put("nisse.source.jgit.appendBuildNumber", "false");
        userProps.put("nisse.source.jgit.appendSnapshot", "false");
        JGitPropertySource source = new JGitPropertySource();

        Path repo = newRepo(tempDir);
        exec(repo, "git", "commit", "--allow-empty", "-m", "chore: base");
        exec(repo, "git", "tag", "0.0.5");
        exec(repo, "git", "commit", "--allow-empty", "-m", "feat: a feature");

        // A feat on a 0.0.x tag lands on exactly 0.1.0 — which is also the no-tag fallback value.
        assertDynamicVersion("0.1.0", source, repo, userProps);

        // A lower version hint must NOT win. It would if "no release tag found" were inferred from the value,
        // and the build would go backwards from 0.1.0 to 0.0.6.
        exec(repo, "git", "tag", "0.0.6-SNAPSHOT");
        assertDynamicVersion("0.1.0", source, repo, userProps);
    }

    @Test
    void testVersionIncrementNone(@TempDir Path tempDir) throws Exception {
        Map<String, String> userProps = new HashMap<>();
        userProps.put("nisse.source.jgit.dynamicVersion", "true");
        userProps.put("nisse.source.jgit.versionIncrement", "none");
        userProps.put("nisse.source.jgit.appendBuildNumber", "false");
        userProps.put("nisse.source.jgit.appendSnapshot", "false");
        JGitPropertySource source = new JGitPropertySource();

        Path repo = newRepo(tempDir);
        exec(repo, "git", "commit", "--allow-empty", "-m", "chore: base");
        exec(repo, "git", "tag", "1.2.3");
        exec(repo, "git", "commit", "--allow-empty", "-m", "feat!: breaking");

        // versionIncrement=none suppresses every kind of increment
        assertDynamicVersion("1.2.3", source, repo, userProps);
    }

    @Test
    void testVersionIncrementEnumOverridesDeprecatedBooleans(@TempDir Path tempDir) throws Exception {
        Map<String, String> userProps = new HashMap<>();
        userProps.put("nisse.source.jgit.dynamicVersion", "true");
        // the new enum property takes precedence over the deprecated booleans
        userProps.put("nisse.source.jgit.versionIncrement", "conventionalCommits");
        userProps.put("nisse.source.jgit.increasePatchVersion", "false");
        userProps.put("nisse.source.jgit.appendBuildNumber", "false");
        userProps.put("nisse.source.jgit.appendSnapshot", "false");
        JGitPropertySource source = new JGitPropertySource();

        Path repo = newRepo(tempDir);
        exec(repo, "git", "commit", "--allow-empty", "-m", "chore: base");
        exec(repo, "git", "tag", "1.2.3");
        exec(repo, "git", "commit", "--allow-empty", "-m", "feat: a feature");

        // versionIncrement=conventionalCommits wins over increasePatchVersion=false
        assertDynamicVersion("1.3.0", source, repo, userProps);
    }

    @Test
    void testDeprecatedIncreasePatchVersionFalseResolvedAsNone(@TempDir Path tempDir) throws Exception {
        Map<String, String> userProps = new HashMap<>();
        userProps.put("nisse.source.jgit.dynamicVersion", "true");
        // no versionIncrement set — deprecated boolean is the fallback
        userProps.put("nisse.source.jgit.increasePatchVersion", "false");
        userProps.put("nisse.source.jgit.appendBuildNumber", "false");
        userProps.put("nisse.source.jgit.appendSnapshot", "false");
        JGitPropertySource source = new JGitPropertySource();

        Path repo = newRepo(tempDir);
        exec(repo, "git", "commit", "--allow-empty", "-m", "chore: base");
        exec(repo, "git", "tag", "1.2.3");
        exec(repo, "git", "commit", "--allow-empty", "-m", "feat!: breaking");

        // increasePatchVersion=false without the new property resolves to "none"
        assertDynamicVersion("1.2.3", source, repo, userProps);
    }

    @Test
    void testZeroMajorDemotionDemotesMajorToMinorOnZeroX(@TempDir Path tempDir) throws Exception {
        Map<String, String> userProps = new HashMap<>();
        userProps.put("nisse.source.jgit.dynamicVersion", "true");
        userProps.put("nisse.source.jgit.versionIncrement", "conventionalCommits");
        userProps.put("nisse.source.jgit.versionIncrement.zeroMajorDemotion", "true");
        userProps.put("nisse.source.jgit.appendBuildNumber", "false");
        userProps.put("nisse.source.jgit.appendSnapshot", "false");
        JGitPropertySource source = new JGitPropertySource();

        Path repo = newRepo(tempDir);
        exec(repo, "git", "commit", "--allow-empty", "-m", "chore: base");
        exec(repo, "git", "tag", "0.5.2");
        exec(repo, "git", "commit", "--allow-empty", "-m", "feat!: breaking");

        // with demotion enabled on 0.x: MAJOR -> MINOR, so 0.5.2 -> 0.6.0 (not 1.0.0)
        assertDynamicVersion("0.6.0", source, repo, userProps);
    }

    @Test
    void testZeroMajorDemotionDoesNotAffectNonZeroMajor(@TempDir Path tempDir) throws Exception {
        Map<String, String> userProps = new HashMap<>();
        userProps.put("nisse.source.jgit.dynamicVersion", "true");
        userProps.put("nisse.source.jgit.versionIncrement", "conventionalCommits");
        userProps.put("nisse.source.jgit.versionIncrement.zeroMajorDemotion", "true");
        userProps.put("nisse.source.jgit.appendBuildNumber", "false");
        userProps.put("nisse.source.jgit.appendSnapshot", "false");
        JGitPropertySource source = new JGitPropertySource();

        Path repo = newRepo(tempDir);
        exec(repo, "git", "commit", "--allow-empty", "-m", "chore: base");
        exec(repo, "git", "tag", "1.5.2");
        exec(repo, "git", "commit", "--allow-empty", "-m", "feat!: breaking");

        // demotion only applies to 0.x; on 1.x a breaking change still bumps major
        assertDynamicVersion("2.0.0", source, repo, userProps);
    }

    @Test
    void testZeroMajorDemotionDisabledKeepsMajorBump(@TempDir Path tempDir) throws Exception {
        Map<String, String> userProps = new HashMap<>();
        userProps.put("nisse.source.jgit.dynamicVersion", "true");
        userProps.put("nisse.source.jgit.versionIncrement", "conventionalCommits");
        // explicitly disabled (the default)
        userProps.put("nisse.source.jgit.versionIncrement.zeroMajorDemotion", "false");
        userProps.put("nisse.source.jgit.appendBuildNumber", "false");
        userProps.put("nisse.source.jgit.appendSnapshot", "false");
        JGitPropertySource source = new JGitPropertySource();

        Path repo = newRepo(tempDir);
        exec(repo, "git", "commit", "--allow-empty", "-m", "chore: base");
        exec(repo, "git", "tag", "0.5.2");
        exec(repo, "git", "commit", "--allow-empty", "-m", "feat!: breaking");

        // without demotion: plain semver, 0.5.2 -> 1.0.0
        assertDynamicVersion("1.0.0", source, repo, userProps);
    }

    @Test
    void testIncreaseVersionWithZeroMajorDemotion() {
        // unit-test the increaseVersion method directly for demotion
        VersionInformation vi;

        // MAJOR on 0.x with demotion -> MINOR
        vi = new VersionInformation("0.5.2");
        JGitPropertySource.increaseVersion(vi, JGitPropertySource.Bump.MAJOR, true);
        assertEquals("0.6.0", vi.toString());

        // MAJOR on 1.x with demotion -> still MAJOR (demotion only applies to 0.x)
        vi = new VersionInformation("1.5.2");
        JGitPropertySource.increaseVersion(vi, JGitPropertySource.Bump.MAJOR, true);
        assertEquals("2.0.0", vi.toString());

        // MINOR on 0.x with demotion -> MINOR (no change, demotion only affects MAJOR)
        vi = new VersionInformation("0.5.2");
        JGitPropertySource.increaseVersion(vi, JGitPropertySource.Bump.MINOR, true);
        assertEquals("0.6.0", vi.toString());

        // MAJOR on 0.x without demotion -> MAJOR
        vi = new VersionInformation("0.5.2");
        JGitPropertySource.increaseVersion(vi, JGitPropertySource.Bump.MAJOR, false);
        assertEquals("1.0.0", vi.toString());

        // MAJOR on 0.x with demotion drops qualifier too
        vi = new VersionInformation("0.5.2-rc1");
        JGitPropertySource.increaseVersion(vi, JGitPropertySource.Bump.MAJOR, true);
        assertEquals("0.6.0", vi.toString());
    }

    @Test
    void testBuildNumberUsesReachabilityCountAcrossMerge(@TempDir Path tempDir) throws Exception {
        // The build number must use the reachability-based commit count (tag..HEAD), not the
        // date-ordered walk count, so that commits on a merged branch are counted correctly.
        Map<String, String> userProps = new HashMap<>();
        userProps.put("nisse.source.jgit.dynamicVersion", "true");
        userProps.put("nisse.source.jgit.versionIncrement", "conventionalCommits");
        userProps.put("nisse.source.jgit.appendSnapshot", "false");
        JGitPropertySource source = new JGitPropertySource();

        Path repo = newRepo(tempDir);
        exec(repo, "git", "commit", "--allow-empty", "-m", "chore: base");

        // a branch cut BEFORE the release
        exec(repo, "git", "checkout", "-b", "side");
        exec(repo, "git", "commit", "--allow-empty", "-m", "feat: side feature");

        // the release happens on master afterwards
        exec(repo, "git", "checkout", "master");
        exec(repo, "git", "commit", "--allow-empty", "-m", "chore: release");
        exec(repo, "git", "tag", "1.2.3");

        // ...and only then is the branch merged
        exec(repo, "git", "merge", "--no-ff", "-m", "chore: merge side", "side");

        // tag..HEAD contains 2 reachable commits: the merge commit and the side feature commit.
        // With conventionalCommits, the feat: bumps minor -> 1.3.0 and the build number is 2.
        assertDynamicVersion("1.3.0-2", source, repo, userProps);
    }

    @Test
    void testTabAfterColonIsNotAConventionalCommit() {
        // The specification requires a literal space after the colon; a tab must not match.
        assertEquals(JGitPropertySource.Bump.PATCH, JGitPropertySource.bumpFrom("feat:\ta description"));
        assertEquals(JGitPropertySource.Bump.PATCH, JGitPropertySource.bumpFrom("fix:\tanother"));
    }

    @Test
    void testInvalidVersionIncrementFallsToPatch(@TempDir Path tempDir) throws Exception {
        // An unknown versionIncrement value should fall back to patch rather than silently doing nothing
        Map<String, String> userProps = new HashMap<>();
        userProps.put("nisse.source.jgit.dynamicVersion", "true");
        userProps.put("nisse.source.jgit.versionIncrement", "bogus");
        userProps.put("nisse.source.jgit.appendBuildNumber", "false");
        userProps.put("nisse.source.jgit.appendSnapshot", "false");
        JGitPropertySource source = new JGitPropertySource();

        Path repo = newRepo(tempDir);
        exec(repo, "git", "commit", "--allow-empty", "-m", "chore: base");
        exec(repo, "git", "tag", "1.2.3");
        exec(repo, "git", "commit", "--allow-empty", "-m", "feat!: breaking");

        // "bogus" falls back to "patch", so the version increments the patch only
        assertDynamicVersion("1.2.4", source, repo, userProps);
    }

    @Test
    void testWhitespaceVersionIncrementIsTrimmed(@TempDir Path tempDir) throws Exception {
        // Whitespace around the value should be trimmed and matched case-insensitively
        Map<String, String> userProps = new HashMap<>();
        userProps.put("nisse.source.jgit.dynamicVersion", "true");
        userProps.put("nisse.source.jgit.versionIncrement", "  None  ");
        userProps.put("nisse.source.jgit.appendBuildNumber", "false");
        userProps.put("nisse.source.jgit.appendSnapshot", "false");
        JGitPropertySource source = new JGitPropertySource();

        Path repo = newRepo(tempDir);
        exec(repo, "git", "commit", "--allow-empty", "-m", "chore: base");
        exec(repo, "git", "tag", "1.2.3");
        exec(repo, "git", "commit", "--allow-empty", "-m", "feat!: breaking");

        // "  None  " should be trimmed and lowered to "none"
        assertDynamicVersion("1.2.3", source, repo, userProps);
    }

    @Test
    void testSubclassOverrideOfGetVersionFromGitIsRespected(@TempDir Path tempDir) throws Exception {
        Map<String, String> userProps = new HashMap<>();
        userProps.put("nisse.source.jgit.dynamicVersion", "true");
        userProps.put("nisse.source.jgit.appendBuildNumber", "false");
        userProps.put("nisse.source.jgit.appendSnapshot", "false");

        // A subclass that overrides the version resolution hook
        JGitPropertySource source = new JGitPropertySource() {
            @Override
            protected GitVersion getVersionFromGit(
                    Map<String, String> properties,
                    NisseConfiguration configuration,
                    org.eclipse.jgit.api.Git git,
                    ObjectId head) {
                return new GitVersion(new VersionInformation("9.9.9"), true);
            }
        };

        Path repo = newRepo(tempDir);
        exec(repo, "git", "commit", "--allow-empty", "-m", "chore: base");
        exec(repo, "git", "tag", "1.2.3");
        exec(repo, "git", "commit", "--allow-empty", "-m", "fix: a bug");

        // The subclass override should be used, not the default git resolution
        assertDynamicVersion("9.9.9", source, repo, userProps);
    }

    private static Path newRepo(Path dir) throws Exception {
        Files.createDirectories(dir);
        exec(dir, "git", "init", "-b", "master");
        exec(dir, "git", "config", "user.email", "test@test.com");
        exec(dir, "git", "config", "user.name", "Test");
        // never inherit the developer's signing configuration
        exec(dir, "git", "config", "commit.gpgsign", "false");
        return dir;
    }

    private static void assertDynamicVersion(
            String expected, JGitPropertySource source, Path repo, Map<String, String> userProps) throws Exception {
        Map<String, String> properties = source.getProperties(SimpleNisseConfiguration.builder()
                .withCurrentWorkingDirectory(repo)
                .withUserProperties(userProps)
                .build());
        String value = properties.get("dynamicVersion");
        assertNotNull(value, "dynamicVersion should be set");
        assertEquals(expected, value);
    }

    private static void assertCountingVersion(
            String expected, JGitPropertySource source, Path repo, Map<String, String> userProps) throws Exception {
        Map<String, String> properties = source.getProperties(SimpleNisseConfiguration.builder()
                .withCurrentWorkingDirectory(repo)
                .withUserProperties(userProps)
                .build());
        String value = properties.get("countingVersion");
        assertNotNull(value, "countingVersion should be set");
        assertEquals(expected, value);
    }

    private static void exec(Path workDir, String... command) throws Exception {
        Process process = new ProcessBuilder(command)
                .directory(workDir.toFile())
                .redirectErrorStream(true)
                .start();
        byte[] buf = new byte[4096];
        while (process.getInputStream().read(buf) != -1) {
            // drain
        }
        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new RuntimeException("Command failed with exit code " + exitCode + ": " + String.join(" ", command));
        }
    }

    private static String execOutput(Path workDir, String... command) throws Exception {
        Process process = new ProcessBuilder(command)
                .directory(workDir.toFile())
                .redirectErrorStream(true)
                .start();
        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int n;
        while ((n = process.getInputStream().read(buf)) != -1) {
            baos.write(buf, 0, n);
        }
        int exitCode = process.waitFor();
        String output = new String(baos.toByteArray(), StandardCharsets.UTF_8);
        if (exitCode != 0) {
            throw new RuntimeException("Command failed: " + String.join(" ", command) + "\n" + output);
        }
        return output;
    }
}
