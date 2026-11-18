package eu.maveniverse.maven.nisse.source.docker;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class DockerPropertySourceTest {
    private static final String EXE = File.separatorChar == '\\' ? ".exe" : "";

    @Test
    void dockerHostUnixPresent(@TempDir Path tmp) throws IOException {
        Path socket = Files.createDirectories(tmp.resolve("podman")).resolve("podman.sock");
        Files.createFile(socket);
        Map<String, String> result =
                DockerPropertySource.detect(env("unix://" + socket), null, DockerPing.DEFAULT_TIMEOUT_MS);
        assertEquals("true", result.get("present"));
        assertEquals(socket.toString(), result.get("socket"));
        assertEquals("unix://" + socket, result.get("host"));
        assertEquals("podman", result.get("type"));
    }

    @Test
    void dockerHostUnixMissing(@TempDir Path tmp) {
        Map<String, String> result = DockerPropertySource.detect(
                env("unix://" + tmp.resolve("docker.sock")), tmp.toString(), DockerPing.DEFAULT_TIMEOUT_MS);
        assertEquals("false", result.get("present"));
        assertFalse(result.containsKey("socket"));
    }

    @Test
    void typeFromSocketFileNameOnly(@TempDir Path tmp) throws IOException {
        // "podman" in a directory name (ie. user home) must not make it Podman
        Path socket = Files.createDirectories(tmp.resolve("podman-dev")).resolve("docker.sock");
        Files.createFile(socket);
        assertFalse(DockerPropertySource.isPodman(socket));
        assertTrue(DockerPropertySource.isPodman(Files.createFile(tmp.resolve("podman-machine-default-api.sock"))));
    }

    @Test
    void dockerHostTcpRunning() throws Exception {
        try (ServerSocket server = engine("HTTP/1.1 200 OK\r\nApi-Version: 1.44\r\nLibpod-Api-Version: 6.2.0\r\n"
                + "Content-Length: 2\r\n\r\nOK")) {
            String host = "tcp://127.0.0.1:" + server.getLocalPort();
            Map<String, String> result = DockerPropertySource.detect(env(host), null, DockerPing.DEFAULT_TIMEOUT_MS);
            assertEquals("true", result.get("present"));
            assertEquals(host, result.get("host"));
            assertFalse(result.containsKey("socket"));
            assertEquals("true", result.get("running"));
            assertEquals("1.44", result.get("apiVersion"));
            assertEquals("podman", result.get("type"));
        }
    }

    @Test
    void truncatedResponseIsNotRunning() throws Exception {
        // status line only, no end of headers
        try (ServerSocket server = engine("HTTP/1.1 200 OK\r\n")) {
            Map<String, String> result = DockerPropertySource.detect(
                    env("tcp://127.0.0.1:" + server.getLocalPort()), null, DockerPing.DEFAULT_TIMEOUT_MS);
            assertEquals("false", result.get("running"));
        }
    }

    @Test
    void dockerHostTcpRefused() throws IOException {
        Map<String, String> result =
                DockerPropertySource.detect(env(refusedHost()), null, DockerPing.DEFAULT_TIMEOUT_MS);
        assertEquals("true", result.get("present"));
        assertEquals("false", result.get("running"));
        assertFalse(result.containsKey("apiVersion"));
    }

    @Test
    void dockerHostTcpHung() throws IOException {
        // accepts connection (backlog) but never answers
        try (ServerSocket server = new ServerSocket(0, 1, loopback())) {
            long start = System.nanoTime();
            Map<String, String> result =
                    DockerPropertySource.detect(env("tcp://127.0.0.1:" + server.getLocalPort()), null, 1500);
            long elapsedMs = (System.nanoTime() - start) / 1_000_000;
            assertEquals("false", result.get("running"));
            // lower bound only (robust on loaded CI): proves configured timeout is used instead of default 1000ms
            assertTrue(elapsedMs >= 1500, "elapsed " + elapsedMs + "ms");
        }
    }

    @Test
    void selectPrefersAnsweringEngine() throws Exception {
        String refused = refusedHost();
        try (ServerSocket server = engine("HTTP/1.1 200 OK\r\nApi-Version: 1.44\r\n\r\nOK")) {
            String running = "tcp://127.0.0.1:" + server.getLocalPort();
            Map<String, String> result =
                    DockerPropertySource.select(Arrays.asList(refused, running), false, DockerPing.DEFAULT_TIMEOUT_MS);
            assertEquals(running, result.get("host"));
            assertEquals("true", result.get("running"));
        }
    }

    @Test
    void selectFallsBackToFirstPresent() throws IOException {
        String first = refusedHost();
        String second = refusedHost();
        Map<String, String> result =
                DockerPropertySource.select(Arrays.asList(first, second), false, DockerPing.DEFAULT_TIMEOUT_MS);
        assertEquals(first, result.get("host"));
        assertEquals("false", result.get("running"));
    }

    @Test
    void pingTimeoutConfig() {
        assertEquals(DockerPing.DEFAULT_TIMEOUT_MS, DockerPropertySource.pingTimeout(null));
        assertEquals(5000, DockerPropertySource.pingTimeout(" 5000 "));
        assertThrows(IllegalArgumentException.class, () -> DockerPropertySource.pingTimeout("0"));
        assertThrows(IllegalArgumentException.class, () -> DockerPropertySource.pingTimeout("-1"));
        assertThrows(IllegalArgumentException.class, () -> DockerPropertySource.pingTimeout("1s"));
    }

    @Test
    void dockerHostTls() {
        for (String tlsVariable : new String[] {"DOCKER_TLS_VERIFY", "DOCKER_TLS"}) {
            Map<String, String> env = new HashMap<>();
            env.put("DOCKER_HOST", "tcp://127.0.0.1:2376");
            env.put(tlsVariable, "1");
            Map<String, String> result = DockerPropertySource.detect(env::get, null, DockerPing.DEFAULT_TIMEOUT_MS);
            assertEquals("true", result.get("present"), tlsVariable);
            assertFalse(result.containsKey("running"), tlsVariable);
        }
    }

    @Test
    void npipeSocketPath() {
        assertEquals(
                "\\\\.\\pipe\\docker_engine",
                DockerPropertySource.socketPath("npipe:////./pipe/docker_engine")
                        .toString());
        assertNull(DockerPropertySource.socketPath("npipe:////./pipe/"));
        assertNull(DockerPropertySource.socketPath("npipe://foo.txt"));
        assertNull(DockerPropertySource.socketPath("tcp://localhost:2375"));
    }

    @Test
    void npipeToRegularFileIsNeitherPresentNorWritten(@TempDir Path tmp) throws IOException {
        Path file = tmp.resolve("important.txt");
        byte[] content = "do not touch".getBytes(StandardCharsets.UTF_8);
        Files.write(file, content);
        Map<String, String> result =
                DockerPropertySource.detect(env("npipe://" + file), null, DockerPing.DEFAULT_TIMEOUT_MS);
        assertEquals("false", result.get("present"));
        assertArrayEquals(content, Files.readAllBytes(file));
    }

    @Test
    void cliMatchingTypePreferred(@TempDir Path tmp) throws IOException {
        Path socket = Files.createDirectories(tmp.resolve("podman")).resolve("podman.sock");
        Files.createFile(socket);
        Path bin = Files.createDirectories(tmp.resolve("bin"));
        for (String name : new String[] {"docker", "podman"}) {
            assertTrue(Files.createFile(bin.resolve(name + EXE)).toFile().setExecutable(true));
        }
        Function<String, String> env = env("unix://" + socket);
        Map<String, String> result = DockerPropertySource.detect(
                k -> "PATH".equals(k) ? bin.toString() : env.apply(k), null, DockerPing.DEFAULT_TIMEOUT_MS);
        assertEquals(bin.resolve("podman" + EXE).toString(), result.get("cli"));
    }

    private static Function<String, String> env(String dockerHost) {
        Map<String, String> env = new HashMap<>();
        env.put("DOCKER_HOST", dockerHost);
        return env::get;
    }

    // clients connect to tcp://127.0.0.1, so bind there (getLoopbackAddress() may be ::1)
    private static InetAddress loopback() throws IOException {
        return InetAddress.getByName("127.0.0.1");
    }

    private static String refusedHost() throws IOException {
        try (ServerSocket server = new ServerSocket(0, 1, loopback())) {
            return "tcp://127.0.0.1:" + server.getLocalPort();
        }
    }

    /**
     * Fake engine answering one request with given raw response, then closing the connection.
     */
    private static ServerSocket engine(String response) throws IOException {
        ServerSocket server = new ServerSocket(0, 1, loopback());
        Thread engine = new Thread(() -> {
            try (Socket s = server.accept()) {
                BufferedReader in =
                        new BufferedReader(new InputStreamReader(s.getInputStream(), StandardCharsets.US_ASCII));
                String line;
                while ((line = in.readLine()) != null && !line.isEmpty()) {
                    // consume request
                }
                s.getOutputStream().write(response.getBytes(StandardCharsets.US_ASCII));
            } catch (IOException e) {
                // test fails on assert
            }
        });
        engine.setDaemon(true);
        engine.start();
        return server;
    }
}
