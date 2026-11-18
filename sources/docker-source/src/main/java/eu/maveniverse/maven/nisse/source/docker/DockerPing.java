/*
 * Copyright (c) 2023-2026 Maveniverse Org.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 */
package eu.maveniverse.maven.nisse.source.docker;

import java.io.BufferedInputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.net.InetSocketAddress;
import java.net.ProtocolFamily;
import java.net.SocketAddress;
import java.net.StandardProtocolFamily;
import java.net.URI;
import java.nio.channels.ByteChannel;
import java.nio.channels.Channels;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Pings Docker Engine API ({@code GET /_ping}) over {@code tcp://}, {@code npipe://} or (on Java 16+)
 * {@code unix://} host.
 */
final class DockerPing {
    static final long TIMEOUT_MS = 1000;

    private static final String TCP = "tcp://";
    private static final String NPIPE = "npipe://";
    private static final String UNIX = "unix://";
    private static final int MAX_HEADER_SIZE = 16 * 1024;

    // Java 16+; Nisse baseline is Java 8, hence reflection
    private static final boolean UNIX_SUPPORTED =
            Arrays.stream(StandardProtocolFamily.values()).anyMatch(f -> "UNIX".equals(f.name()));

    private DockerPing() {}

    /**
     * Returns {@code running} (and when engine answered, {@code apiVersion} and {@code type}) properties, or empty
     * map if the host cannot be pinged (ie. {@code ssh://}, TLS, or {@code unix://} on Java older than 16).
     */
    static Map<String, String> ping(String host, Path socket, boolean tls) {
        if (!(host.startsWith(TCP) && !tls || host.startsWith(NPIPE) || host.startsWith(UNIX) && UNIX_SUPPORTED)) {
            return Collections.emptyMap();
        }
        AtomicReference<Closeable> channel = new AtomicReference<>();
        AtomicReference<Map<String, String>> headers = new AtomicReference<>();
        // reads from channels/pipes cannot time out, so run it on a thread and close the channel if it hangs
        Thread thread = new Thread(
                () -> {
                    try (ByteChannel ch = open(host, socket)) {
                        channel.set(ch);
                        headers.set(exchange(ch));
                    } catch (Exception e) {
                        // not running
                    }
                },
                "nisse-docker-ping");
        thread.setDaemon(true);
        thread.start();
        try {
            thread.join(TIMEOUT_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        boolean hung = thread.isAlive();
        if (hung) {
            closeQuietly(channel.get());
        }
        Map<String, String> h = hung ? null : headers.get();
        HashMap<String, String> result = new HashMap<>();
        result.put("running", Boolean.toString(h != null));
        if (h != null) {
            if (h.containsKey("api-version")) {
                result.put("apiVersion", h.get("api-version"));
            }
            // Podman compat API always sends this header, Docker never
            result.put("type", h.containsKey("libpod-api-version") ? "podman" : "docker");
        }
        return result;
    }

    private static ByteChannel open(String host, Path socket) throws Exception {
        if (host.startsWith(TCP)) {
            URI uri = URI.create(host);
            return SocketChannel.open(new InetSocketAddress(uri.getHost(), uri.getPort() == -1 ? 2375 : uri.getPort()));
        } else if (host.startsWith(NPIPE)) {
            return new RandomAccessFile(socket.toFile(), "rw").getChannel();
        } else {
            ProtocolFamily unix = StandardProtocolFamily.valueOf("UNIX");
            SocketChannel ch = (SocketChannel)
                    SocketChannel.class.getMethod("open", ProtocolFamily.class).invoke(null, unix);
            try {
                ch.connect((SocketAddress) Class.forName("java.net.UnixDomainSocketAddress")
                        .getMethod("of", Path.class)
                        .invoke(null, socket));
                return ch;
            } catch (Exception e) {
                ch.close();
                throw e;
            }
        }
    }

    /**
     * Returns lower-cased response headers if response status was 200, {@code null} otherwise.
     */
    private static Map<String, String> exchange(ByteChannel ch) throws IOException {
        OutputStream out = Channels.newOutputStream(ch);
        out.write(
                "GET /_ping HTTP/1.1\r\nHost: docker\r\nConnection: close\r\n\r\n".getBytes(StandardCharsets.US_ASCII));
        out.flush();
        // read headers only: some engines keep connection open regardless
        InputStream in = new BufferedInputStream(Channels.newInputStream(ch));
        StringBuilder response = new StringBuilder();
        int b;
        while (response.length() < MAX_HEADER_SIZE && (b = in.read()) != -1) {
            response.append((char) b);
            if (response.length() >= 4 && response.lastIndexOf("\r\n\r\n") == response.length() - 4) {
                break;
            }
        }
        String[] lines = response.toString().split("\r\n");
        if (!lines[0].matches("HTTP/1\\.[01] 200( .*)?")) {
            return null;
        }
        HashMap<String, String> headers = new HashMap<>();
        for (int i = 1; i < lines.length; i++) {
            int idx = lines[i].indexOf(':');
            if (idx > 0) {
                headers.put(
                        lines[i].substring(0, idx).trim().toLowerCase(Locale.ROOT),
                        lines[i].substring(idx + 1).trim());
            }
        }
        return headers;
    }

    private static void closeQuietly(Closeable closeable) {
        if (closeable != null) {
            try {
                closeable.close();
            } catch (IOException e) {
                // ignore
            }
        }
    }
}
