package eu.maveniverse.maven.nisse.source.docker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import eu.maveniverse.maven.nisse.core.simple.SimpleNisseConfiguration;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class DockerPropertySourceTest {
    @Test
    void smoke() {
        new DockerPropertySource()
                .getProperties(SimpleNisseConfiguration.builder().build())
                .forEach((k, v) -> System.out.println(k + " = " + v));
    }

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
    void dockerHostTcpRunning() throws Exception {
        try (ServerSocket server = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            Thread engine = new Thread(() -> {
                try (Socket s = server.accept()) {
                    BufferedReader in =
                            new BufferedReader(new InputStreamReader(s.getInputStream(), StandardCharsets.US_ASCII));
                    String line;
                    while ((line = in.readLine()) != null && !line.isEmpty()) {
                        // consume request
                    }
                    s.getOutputStream()
                            .write(("HTTP/1.1 200 OK\r\nApi-Version: 1.44\r\nLibpod-Api-Version: 6.2.0\r\n"
                                            + "Content-Length: 2\r\n\r\nOK")
                                    .getBytes(StandardCharsets.US_ASCII));
                } catch (IOException e) {
                    // test fails on assert
                }
            });
            engine.start();
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
    void dockerHostTcpRefused() throws IOException {
        int port;
        try (ServerSocket server = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            port = server.getLocalPort();
        }
        Map<String, String> result =
                DockerPropertySource.detect(env("tcp://127.0.0.1:" + port), null, DockerPing.DEFAULT_TIMEOUT_MS);
        assertEquals("true", result.get("present"));
        assertEquals("false", result.get("running"));
        assertFalse(result.containsKey("apiVersion"));
    }

    @Test
    void dockerHostTcpHung() throws IOException {
        // accepts connection (backlog) but never answers
        try (ServerSocket server = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
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
    void pingTimeoutConfig() {
        assertEquals(DockerPing.DEFAULT_TIMEOUT_MS, DockerPropertySource.pingTimeout(null));
        assertEquals(5000, DockerPropertySource.pingTimeout(" 5000 "));
        assertThrows(IllegalArgumentException.class, () -> DockerPropertySource.pingTimeout("0"));
        assertThrows(IllegalArgumentException.class, () -> DockerPropertySource.pingTimeout("-1"));
        assertThrows(IllegalArgumentException.class, () -> DockerPropertySource.pingTimeout("1s"));
    }

    @Test
    void dockerHostTls() {
        Map<String, String> env = new HashMap<>();
        env.put("DOCKER_HOST", "tcp://127.0.0.1:2376");
        env.put("DOCKER_TLS_VERIFY", "1");
        Map<String, String> result = DockerPropertySource.detect(env::get, null, DockerPing.DEFAULT_TIMEOUT_MS);
        assertEquals("true", result.get("present"));
        assertFalse(result.containsKey("running"));
    }

    @Test
    void npipeSocketPath() {
        assertEquals(
                "\\\\.\\pipe\\docker_engine",
                DockerPropertySource.socketPath("npipe:////./pipe/docker_engine")
                        .toString());
        assertNull(DockerPropertySource.socketPath("tcp://localhost:2375"));
    }

    @Test
    void cliMatchingTypePreferred(@TempDir Path tmp) throws IOException {
        Path socket = Files.createDirectories(tmp.resolve("podman")).resolve("podman.sock");
        Files.createFile(socket);
        Path bin = Files.createDirectories(tmp.resolve("bin"));
        for (String name : new String[] {"docker", "podman"}) {
            assertTrue(Files.createFile(bin.resolve(name)).toFile().setExecutable(true));
        }
        Function<String, String> env = env("unix://" + socket);
        Map<String, String> result = DockerPropertySource.detect(
                k -> "PATH".equals(k) ? bin.toString() : env.apply(k), null, DockerPing.DEFAULT_TIMEOUT_MS);
        assertEquals(bin.resolve("podman").toString(), result.get("cli"));
    }

    private static Function<String, String> env(String dockerHost) {
        Map<String, String> env = new HashMap<>();
        env.put("DOCKER_HOST", dockerHost);
        return env::get;
    }
}
