/*
 * Copyright (c) 2023-2026 Maveniverse Org.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 */
package eu.maveniverse.maven.nisse.source.docker;

import eu.maveniverse.maven.nisse.core.NisseConfiguration;
import eu.maveniverse.maven.nisse.core.PropertySource;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import javax.inject.Named;
import javax.inject.Singleton;

/**
 * docker source: detects presence of a Docker compatible container engine (Docker or Podman).
 * <p>
 * If {@code DOCKER_HOST} environment variable is set, only that host is used: {@code unix://} sockets and
 * {@code npipe://} named pipes must exist, other hosts ({@code tcp://}, {@code ssh://}) are trusted as present.
 * Otherwise, well known socket locations of Docker, Docker Desktop, rootless Docker, Podman (rootful and rootless),
 * Colima and Rancher Desktop are probed, and on Windows the Docker Desktop and Podman machine named pipes; the first
 * one whose engine answers ping is used, or if none answers, the first existing one.
 * <p>
 * Present engine is pinged ({@code GET /_ping}) over {@code unix://} (Java 16+), {@code npipe://} and {@code tcp://}
 * (only if TLS is not enabled by {@code DOCKER_TLS} or {@code DOCKER_TLS_VERIFY}), so enabling this source may open
 * a connection to the engine.
 * <p>
 * Published properties:
 * <ul>
 *     <li>{@code present} - {@code "true"} or {@code "false"}, always published</li>
 *     <li>{@code host} - the Docker host, ie. {@code unix:///var/run/docker.sock} (only if present)</li>
 *     <li>{@code socket} - the Unix socket or named pipe path (only if present and is socket or pipe)</li>
 *     <li>{@code type} - {@code "docker"} or {@code "podman"} (only if present and is socket or pipe, or is
 *     running)</li>
 *     <li>{@code running} - {@code "true"} or {@code "false"}, whether engine answered {@code GET /_ping} within
 *     {@code nisse.source.docker.pingTimeout} milliseconds, default 1000 (only if present and can be pinged)</li>
 *     <li>{@code apiVersion} - Docker Engine API version reported by engine (only if running)</li>
 *     <li>{@code cli} - path of {@code docker} or {@code podman} CLI executable, the one matching {@code type}
 *     preferred (only if found on {@code PATH} or in well known install locations)</li>
 * </ul>
 */
@Singleton
@Named(DockerPropertySource.NAME)
public class DockerPropertySource implements PropertySource {
    public static final String NAME = "docker";

    /**
     * Engine ping timeout in milliseconds (default {@code 1000}), may be raised ie. on loaded systems.
     */
    public static final String PING_TIMEOUT = NisseConfiguration.SOURCE_PREFIX + NAME + ".pingTimeout";

    private static final String PRESENT = "present";
    private static final String HOST = "host";
    private static final String SOCKET = "socket";
    private static final String TYPE = "type";
    private static final String CLI = "cli";
    private static final String RUNNING = "running";

    private static final String DOCKER = "docker";
    private static final String PODMAN = "podman";

    private static final String DOCKER_HOST = "DOCKER_HOST";
    private static final String UNIX = "unix://";
    private static final String NPIPE = "npipe://";

    private static final boolean WINDOWS = File.separatorChar == '\\';

    @Override
    public String getName() {
        return NAME;
    }

    /**
     * Disabled by default, as it probes filesystem and may connect to engine; enable it with
     * {@code "nisse.source.docker.active=true"}.
     */
    @Override
    public boolean isActiveByDefault() {
        return false;
    }

    @Override
    public Map<String, String> getProperties(NisseConfiguration configuration) {
        Map<String, String> config = configuration.getConfiguration();
        // Maven exposes env as "env.XXX" properties; Gradle does not, hence the fallback
        return detect(
                k -> {
                    String v = config.get("env." + k);
                    return v != null ? v : System.getenv(k);
                },
                config.get("user.home"),
                pingTimeout(config.get(PING_TIMEOUT)));
    }

    static long pingTimeout(String value) {
        if (value == null) {
            return DockerPing.DEFAULT_TIMEOUT_MS;
        }
        try {
            long timeout = Long.parseLong(value.trim());
            if (timeout > 0) {
                return timeout;
            }
        } catch (NumberFormatException e) {
            // fall through
        }
        throw new IllegalArgumentException(
                PING_TIMEOUT + " must be a positive number of milliseconds, but was: " + value);
    }

    static Map<String, String> detect(Function<String, String> env, String userHome, long pingTimeoutMs) {
        HashMap<String, String> result = new HashMap<>();
        result.put(PRESENT, Boolean.FALSE.toString());

        List<String> hosts = new ArrayList<>();
        String dockerHost = env.apply(DOCKER_HOST);
        if (dockerHost != null && !dockerHost.trim().isEmpty()) {
            // explicit setting wins: no fallback to well known locations
            hosts.add(dockerHost.trim());
        } else if (WINDOWS) {
            hosts.add(NPIPE + "//./pipe/docker_engine");
            hosts.add(NPIPE + "//./pipe/podman-machine-default");
        } else {
            hosts.add(UNIX + "/var/run/docker.sock");
            hosts.add(UNIX + "/run/podman/podman.sock");
            String xdgRuntimeDir = env.apply("XDG_RUNTIME_DIR");
            if (xdgRuntimeDir != null && !xdgRuntimeDir.trim().isEmpty()) {
                hosts.add(UNIX + Paths.get(xdgRuntimeDir, "docker.sock"));
                hosts.add(UNIX + Paths.get(xdgRuntimeDir, "podman", "podman.sock"));
            }
            if (userHome != null) {
                hosts.add(UNIX + Paths.get(userHome, ".docker", "run", "docker.sock"));
                hosts.add(UNIX + Paths.get(userHome, ".docker", "desktop", "docker.sock"));
                hosts.add(UNIX + Paths.get(userHome, ".colima", "default", "docker.sock"));
                hosts.add(UNIX + Paths.get(userHome, ".rd", "docker.sock"));
            }
        }

        boolean tls = isSet(env.apply("DOCKER_TLS")) || isSet(env.apply("DOCKER_TLS_VERIFY"));
        result.putAll(select(hosts, tls, pingTimeoutMs));

        Path cli = findCli(env, userHome, PODMAN.equals(result.get(TYPE)));
        if (cli != null) {
            result.put(CLI, cli.toString());
        }
        return Collections.unmodifiableMap(result);
    }

    /**
     * Returns properties of the first present host whose engine answers ping (or cannot be pinged), or if none
     * answers, of the first present host. Returns empty map if no host is present.
     */
    static Map<String, String> select(List<String> hosts, boolean tls, long pingTimeoutMs) {
        Map<String, String> fallback = Collections.emptyMap();
        for (String host : hosts) {
            Path socket = socketPath(host);
            boolean socketBased = host.startsWith(UNIX) || host.startsWith(NPIPE);
            // tcp://, ssh://: no file to check, trust the user (and ping below)
            // socket: follows symlinks, dangling (ie. engine not installed or stopped) does not count
            // npipe: exists on Windows only, elsewhere a same named file must not be opened (and written)
            if (socketBased && (socket == null || host.startsWith(NPIPE) && !WINDOWS || !Files.exists(socket))) {
                continue;
            }
            HashMap<String, String> candidate = new HashMap<>();
            candidate.put(PRESENT, Boolean.TRUE.toString());
            candidate.put(HOST, host);
            if (socket != null) {
                candidate.put(SOCKET, socket.toString());
                candidate.put(TYPE, isPodman(socket) ? PODMAN : DOCKER);
            }
            // overrides type with the one reported by engine, if it answered
            candidate.putAll(DockerPing.ping(host, socket, tls, pingTimeoutMs));
            if (!Boolean.FALSE.toString().equals(candidate.get(RUNNING))) {
                return candidate;
            }
            if (fallback.isEmpty()) {
                fallback = candidate;
            }
        }
        return fallback;
    }

    private static boolean isSet(String value) {
        return value != null && !value.trim().isEmpty();
    }

    /**
     * Returns the filesystem path of {@code unix://} or {@code npipe://} host, or {@code null} for other schemes and
     * for named pipe paths not in Windows pipe namespace ({@code \\.\pipe\}).
     */
    static Path socketPath(String host) {
        try {
            if (host.startsWith(UNIX)) {
                return Paths.get(host.substring(UNIX.length()));
            } else if (host.startsWith(NPIPE)) {
                // npipe:////./pipe/docker_engine -> \\.\pipe\docker_engine
                String pipe = host.substring(NPIPE.length()).replace('/', '\\');
                if (pipe.startsWith("\\\\.\\pipe\\") && pipe.length() > 9) {
                    return Paths.get(pipe);
                }
            }
        } catch (InvalidPathException e) {
            // fall through
        }
        return null;
    }

    // socket file name only (podman.sock, podman-machine-*): /var/run/docker.sock may be a symlink to Podman socket
    // (podman-docker package), while directory names (ie. user home) are irrelevant
    static boolean isPodman(Path socket) {
        Path real;
        try {
            real = socket.toRealPath();
        } catch (IOException e) {
            real = socket;
        }
        Path fileName = real.getFileName();
        return fileName != null && fileName.toString().contains(PODMAN);
    }

    private static Path findCli(Function<String, String> env, String userHome, boolean podmanFirst) {
        List<String> names = podmanFirst ? Arrays.asList(PODMAN, DOCKER) : Arrays.asList(DOCKER, PODMAN);
        List<Path> dirs = new ArrayList<>();
        String path = env.apply("PATH");
        if (path != null) {
            for (String dir : path.split(File.pathSeparator)) {
                if (!dir.trim().isEmpty()) {
                    try {
                        dirs.add(Paths.get(dir.trim()));
                    } catch (InvalidPathException e) {
                        // ignore bogus PATH entry
                    }
                }
            }
        }
        // well known install locations, that may not be on PATH
        if (WINDOWS) {
            String programFiles = env.apply("ProgramFiles");
            if (programFiles != null) {
                dirs.add(Paths.get(programFiles, "Docker", "Docker", "resources", "bin"));
                dirs.add(Paths.get(programFiles, "RedHat", "Podman"));
            }
        } else {
            dirs.add(Paths.get("/usr/local/bin"));
            dirs.add(Paths.get("/opt/homebrew/bin"));
            dirs.add(Paths.get("/opt/podman/bin"));
            dirs.add(Paths.get("/Applications/Docker.app/Contents/Resources/bin"));
            if (userHome != null) {
                dirs.add(Paths.get(userHome, ".docker", "bin"));
                dirs.add(Paths.get(userHome, ".rd", "bin"));
            }
        }
        for (String name : names) {
            String executable = WINDOWS ? name + ".exe" : name;
            for (Path dir : dirs) {
                Path candidate = dir.resolve(executable);
                if (Files.isRegularFile(candidate) && Files.isExecutable(candidate)) {
                    return candidate;
                }
            }
        }
        return null;
    }
}
