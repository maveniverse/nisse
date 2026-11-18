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
 * If {@code DOCKER_HOST} environment variable is set, it is used as is (and is not probed if it is not a
 * {@code unix://} socket or {@code npipe://} named pipe). Otherwise, well known socket locations of Docker,
 * Docker Desktop, rootless Docker, Podman (rootful and rootless), Colima and Rancher Desktop are probed, and on
 * Windows the Docker Desktop and Podman machine named pipes.
 * <p>
 * Published properties:
 * <ul>
 *     <li>{@code present} - {@code "true"} or {@code "false"}, always published</li>
 *     <li>{@code host} - the Docker host, ie. {@code unix:///var/run/docker.sock} (only if present)</li>
 *     <li>{@code socket} - the Unix socket or named pipe path (only if present and is socket or pipe)</li>
 *     <li>{@code type} - {@code "docker"} or {@code "podman"} (only if present and is socket or pipe, or is
 *     running)</li>
 *     <li>{@code running} - {@code "true"} or {@code "false"}, whether engine answered {@code GET /_ping}
 *     within {@code nisse.source.docker.pingTimeout} milliseconds (default 1000) (only if present, and is {@code tcp://} without TLS, {@code npipe://}, or {@code unix://} and
 *     Java 16+)</li>
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

        for (String host : hosts) {
            Path socket = socketPath(host);
            // tcp://, ssh://: no file to check, trust the user (and ping below)
            // socket: follows symlinks, dangling (ie. engine not installed or stopped) does not count
            if (socket == null || Files.exists(socket)) {
                result.put(PRESENT, Boolean.TRUE.toString());
                result.put(HOST, host);
                if (socket != null) {
                    result.put(SOCKET, socket.toString());
                    result.put(TYPE, isPodman(socket) ? PODMAN : DOCKER);
                }
                String tlsVerify = env.apply("DOCKER_TLS_VERIFY");
                // overrides type with the one reported by engine, if it answered
                result.putAll(DockerPing.ping(
                        host, socket, tlsVerify != null && !tlsVerify.trim().isEmpty(), pingTimeoutMs));
                break;
            }
        }

        Path cli = findCli(env, userHome, PODMAN.equals(result.get(TYPE)));
        if (cli != null) {
            result.put(CLI, cli.toString());
        }
        return Collections.unmodifiableMap(result);
    }

    /**
     * Returns the filesystem path of {@code unix://} or {@code npipe://} host, or {@code null} for other schemes.
     */
    static Path socketPath(String host) {
        try {
            if (host.startsWith(UNIX)) {
                return Paths.get(host.substring(UNIX.length()));
            } else if (host.startsWith(NPIPE)) {
                // npipe:////./pipe/docker_engine -> \\.\pipe\docker_engine
                return Paths.get(host.substring(NPIPE.length()).replace('/', '\\'));
            }
        } catch (InvalidPathException e) {
            // fall through
        }
        return null;
    }

    // /var/run/docker.sock may be a symlink to Podman socket (podman-docker package)
    private static boolean isPodman(Path socket) {
        try {
            return socket.toRealPath().toString().contains(PODMAN);
        } catch (IOException e) {
            return socket.toString().contains(PODMAN);
        }
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
