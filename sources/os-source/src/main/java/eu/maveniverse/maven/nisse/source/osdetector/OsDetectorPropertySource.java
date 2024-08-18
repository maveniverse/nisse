/*
 * Copyright (c) 2023-2024 Maveniverse Org.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 */
package eu.maveniverse.maven.nisse.source.osdetector;

import eu.maveniverse.maven.nisse.core.NisseConfiguration;
import eu.maveniverse.maven.nisse.core.PropertySource;
import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Named;
import javax.inject.Singleton;

/**
 * OS source heavily inspired by <a href="https://github.com/trustin/os-maven-plugin">trustin/os-maven-plugin</a>.
 * This source creates properties in constructor and keeps the forever.
 */
@Singleton
@Named(OsDetectorPropertySource.NAME)
public class OsDetectorPropertySource implements PropertySource {
    public static final String NAME = "os";

    private static final String DETECTED_NAME = "name";
    private static final String DETECTED_ARCH = "arch";
    private static final String DETECTED_BITNESS = "bitness";
    private static final String DETECTED_VERSION = "version";
    private static final String DETECTED_VERSION_MAJOR = DETECTED_VERSION + ".major";
    private static final String DETECTED_VERSION_MINOR = DETECTED_VERSION + ".minor";
    private static final String DETECTED_CLASSIFIER = "classifier";
    private static final String DETECTED_RELEASE = "release";
    private static final String DETECTED_RELEASE_VERSION = DETECTED_RELEASE + ".version";
    private static final String DETECTED_RELEASE_LIKE_PREFIX = DETECTED_RELEASE + ".like.";

    private static final String UNKNOWN = "unknown";
    private static final String LINUX_ID_PREFIX = "ID=";
    private static final String LINUX_ID_LIKE_PREFIX = "ID_LIKE=";
    private static final String LINUX_VERSION_ID_PREFIX = "VERSION_ID=";
    private static final String[] LINUX_OS_RELEASE_FILES = {"/etc/os-release", "/usr/lib/os-release"};
    private static final String REDHAT_RELEASE_FILE = "/etc/redhat-release";
    private static final String[] DEFAULT_REDHAT_VARIANTS = {"rhel", "fedora"};

    private static final Pattern VERSION_REGEX = Pattern.compile("((\\d+)\\.(\\d+)).*");
    private static final Pattern REDHAT_MAJOR_VERSION_REGEX = Pattern.compile("(\\d+)");

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public Map<String, String> getProperties(NisseConfiguration configuration) {
        return detectOs(configuration.getSystemProperties());
    }

    private static Map<String, String> detectOs(Map<String, String> systemProperties) {
        HashMap<String, String> result = new HashMap<>();

        final String osName = systemProperties.get("os.name");
        final String osArch = systemProperties.get("os.arch");
        final String osVersion = systemProperties.get("os.version");

        final String detectedName = normalizeOs(osName);
        final String detectedArch = normalizeArch(osArch);
        final int detectedBitness = determineBitness(systemProperties, detectedArch);

        result.put(DETECTED_NAME, detectedName);
        result.put(DETECTED_ARCH, detectedArch);
        result.put(DETECTED_BITNESS, "" + detectedBitness);

        final Matcher versionMatcher = VERSION_REGEX.matcher(osVersion);
        if (versionMatcher.matches()) {
            result.put(DETECTED_VERSION, versionMatcher.group(1));
            result.put(DETECTED_VERSION_MAJOR, versionMatcher.group(2));
            result.put(DETECTED_VERSION_MINOR, versionMatcher.group(3));
        }

        final String failOnUnknownOS = systemProperties.get("failOnUnknownOS");
        if (!"false".equalsIgnoreCase(failOnUnknownOS)) {
            if (UNKNOWN.equals(detectedName)) {
                throw new IllegalStateException("unknown os.name: " + osName);
            }
            if (UNKNOWN.equals(detectedArch)) {
                throw new IllegalStateException("unknown os.arch: " + osArch);
            }
        }

        // Assume the default classifier, without any os "like" extension.
        String detectedClassifierBuilder = detectedName + '-' + detectedArch;

        // For Linux systems, add additional properties regarding details of the OS.
        final LinuxRelease linuxRelease = "linux".equals(detectedName) ? getLinuxRelease() : null;
        if (linuxRelease != null) {
            result.put(DETECTED_RELEASE, linuxRelease.id);
            if (linuxRelease.version != null) {
                result.put(DETECTED_RELEASE_VERSION, linuxRelease.version);
            }

            // Add properties for all systems that this OS is "like".
            for (String like : linuxRelease.like) {
                final String propKey = DETECTED_RELEASE_LIKE_PREFIX + like;
                result.put(propKey, "true");
            }
        }
        result.put(DETECTED_CLASSIFIER, detectedClassifierBuilder);

        return Collections.unmodifiableMap(result);
    }

    private static String normalizeOs(String value) {
        value = normalize(value);
        if (value.startsWith("aix")) {
            return "aix";
        }
        if (value.startsWith("hpux")) {
            return "hpux";
        }
        if (value.startsWith("os400")) {
            // Avoid the names such as os4000
            if (value.length() <= 5 || !Character.isDigit(value.charAt(5))) {
                return "os400";
            }
        }
        if (value.startsWith("linux")) {
            return "linux";
        }
        if (value.startsWith("mac") || value.startsWith("osx")) {
            return "osx";
        }
        if (value.startsWith("freebsd")) {
            return "freebsd";
        }
        if (value.startsWith("openbsd")) {
            return "openbsd";
        }
        if (value.startsWith("netbsd")) {
            return "netbsd";
        }
        if (value.startsWith("solaris") || value.startsWith("sunos")) {
            return "sunos";
        }
        if (value.startsWith("windows")) {
            return "windows";
        }
        if (value.startsWith("zos")) {
            return "zos";
        }
        return UNKNOWN;
    }

    private static String normalizeArch(String value) {
        value = normalize(value);
        if (value.matches("^(x8664|amd64|ia32e|em64t|x64)$")) {
            return "x86_64";
        }
        if (value.matches("^(x8632|x86|i[3-6]86|ia32|x32)$")) {
            return "x86_32";
        }
        if (value.matches("^(ia64w?|itanium64)$")) {
            return "itanium_64";
        }
        if ("ia64n".equals(value)) {
            return "itanium_32";
        }
        if (value.matches("^(sparc|sparc32)$")) {
            return "sparc_32";
        }
        if (value.matches("^(sparcv9|sparc64)$")) {
            return "sparc_64";
        }
        if (value.matches("^(arm|arm32)$")) {
            return "arm_32";
        }
        if ("aarch64".equals(value)) {
            return "aarch_64";
        }
        if (value.matches("^(mips|mips32)$")) {
            return "mips_32";
        }
        if (value.matches("^(mipsel|mips32el)$")) {
            return "mipsel_32";
        }
        if ("mips64".equals(value)) {
            return "mips_64";
        }
        if ("mips64el".equals(value)) {
            return "mipsel_64";
        }
        if (value.matches("^(ppc|ppc32)$")) {
            return "ppc_32";
        }
        if (value.matches("^(ppcle|ppc32le)$")) {
            return "ppcle_32";
        }
        if ("ppc64".equals(value)) {
            return "ppc_64";
        }
        if ("ppc64le".equals(value)) {
            return "ppcle_64";
        }
        if ("s390".equals(value)) {
            return "s390_32";
        }
        if ("s390x".equals(value)) {
            return "s390_64";
        }
        if (value.matches("^(riscv|riscv32)$")) {
            return "riscv";
        }
        if ("riscv64".equals(value)) {
            return "riscv64";
        }
        if ("e2k".equals(value)) {
            return "e2k";
        }
        if ("loongarch64".equals(value)) {
            return "loongarch_64";
        }
        return UNKNOWN;
    }

    private static String normalize(String value) {
        if (value == null) {
            return "";
        }
        return value.toLowerCase(Locale.US).replaceAll("[^a-z0-9]+", "");
    }

    private static LinuxRelease getLinuxRelease() {
        // First, look for the os-release file.
        for (String osReleaseFileName : LINUX_OS_RELEASE_FILES) {
            LinuxRelease res = parseLinuxOsReleaseFile(osReleaseFileName);
            if (res != null) {
                return res;
            }
        }
        // Older versions of redhat don't have /etc/os-release. In this case, try
        // parsing this file.
        return parseLinuxRedhatReleaseFile(REDHAT_RELEASE_FILE);
    }

    /**
     * Parses a file in the format of {@code /etc/os-release} and return a {@link LinuxRelease}
     * based on the {@code ID}, {@code ID_LIKE}, and {@code VERSION_ID} entries.
     */
    private static LinuxRelease parseLinuxOsReleaseFile(String fileName) {
        try (BufferedReader reader = Files.newBufferedReader(Paths.get(fileName))) {
            String id = null;
            String version = null;
            final Set<String> likeSet = new LinkedHashSet<>();
            String line;
            while ((line = reader.readLine()) != null) {
                // Parse the ID line.
                if (line.startsWith(LINUX_ID_PREFIX)) {
                    // Set the ID for this version.
                    id = normalizeOsReleaseValue(line.substring(LINUX_ID_PREFIX.length()));

                    // Also add the ID to the "like" set.
                    likeSet.add(id);
                    continue;
                }

                // Parse the VERSION_ID line.
                if (line.startsWith(LINUX_VERSION_ID_PREFIX)) {
                    // Set the ID for this version.
                    version = normalizeOsReleaseValue(line.substring(LINUX_VERSION_ID_PREFIX.length()));
                    continue;
                }

                // Parse the ID_LIKE line.
                if (line.startsWith(LINUX_ID_LIKE_PREFIX)) {
                    line = normalizeOsReleaseValue(line.substring(LINUX_ID_LIKE_PREFIX.length()));

                    // Split the line on any whitespace.
                    final String[] parts = line.split("\\s+");
                    Collections.addAll(likeSet, parts);
                }
            }

            if (id != null) {
                return new LinuxRelease(id, version, likeSet);
            }
        } catch (IOException ignored) {
            // Just absorb. Don't treat failure to read /etc/os-release as an error.
        }
        return null;
    }

    /**
     * Parses the {@code /etc/redhat-release} and returns a {@link LinuxRelease} containing the
     * ID and like ["rhel", "fedora", ID]. Currently only supported for CentOS, Fedora, and RHEL.
     * Other variants will return {@code null}.
     */
    private static LinuxRelease parseLinuxRedhatReleaseFile(String fileName) {
        try (BufferedReader reader = Files.newBufferedReader(Paths.get(fileName))) {

            // There is only a single line in this file.
            String line = reader.readLine();
            if (line != null) {
                line = line.toLowerCase(Locale.US);

                final String id;
                String version = null;
                if (line.contains("centos")) {
                    id = "centos";
                } else if (line.contains("fedora")) {
                    id = "fedora";
                } else if (line.contains("red hat enterprise linux")) {
                    id = "rhel";
                } else {
                    // Other variants are not currently supported.
                    return null;
                }

                final Matcher versionMatcher = REDHAT_MAJOR_VERSION_REGEX.matcher(line);
                if (versionMatcher.find()) {
                    version = versionMatcher.group(1);
                }

                final Set<String> likeSet = new LinkedHashSet<>(Arrays.asList(DEFAULT_REDHAT_VARIANTS));
                likeSet.add(id);

                return new LinuxRelease(id, version, likeSet);
            }
        } catch (IOException ignored) {
            // Just absorb. Don't treat failure to read /etc/os-release as an error.
        }
        return null;
    }

    private static String normalizeOsReleaseValue(String value) {
        // Remove any quotes from the string.
        return value.trim().replace("\"", "");
    }

    private static int determineBitness(Map<String, String> systemProperties, String architecture) {
        // try the widely adopted sun specification first.
        String bitness = systemProperties.getOrDefault("sun.arch.data.model", "");

        if (!bitness.isEmpty() && bitness.matches("[0-9]+")) {
            return Integer.parseInt(bitness, 10);
        }

        // bitness from sun.arch.data.model cannot be used. Try the IBM specification.
        bitness = systemProperties.getOrDefault("com.ibm.vm.bitmode", "");

        if (!bitness.isEmpty() && bitness.matches("[0-9]+")) {
            return Integer.parseInt(bitness, 10);
        }

        // as a last resort, try to determine the bitness from the architecture.
        return guessBitnessFromArchitecture(architecture);
    }

    public static int guessBitnessFromArchitecture(final String arch) {
        if (arch.contains("64")) {
            return 64;
        }

        return 32;
    }

    private static class LinuxRelease {
        final String id;
        final String version;
        final Collection<String> like;

        LinuxRelease(String id, String version, Set<String> like) {
            this.id = id;
            this.version = version;
            this.like = Collections.unmodifiableCollection(like);
        }
    }
}
