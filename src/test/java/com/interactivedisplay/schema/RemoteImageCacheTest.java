package com.interactivedisplay.schema;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.interactivedisplay.debug.DebugRecorder;
import com.sun.net.httpserver.HttpServer;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RemoteImageCacheTest {
    @Test
    void remoteImageShouldBeCachedAsPng(@TempDir Path tempDir) throws Exception {
        byte[] png = createPng(8, 8, Color.WHITE.getRGB());
        AtomicInteger requestCount = new AtomicInteger();
        HttpServer server = startServer(exchange -> {
            requestCount.incrementAndGet();
            exchange.sendResponseHeaders(200, png.length);
            exchange.getResponseBody().write(png);
            exchange.close();
        });
        try {
            String url = url(server, "/image.png");
            RemoteImageCache cache = new RemoteImageCache(tempDir, new DebugRecorder(10));
            Path cached = cache.resolve(url);
            Path cachedAgain = cache.resolve(url);

            assertTrue(Files.exists(cached));
            assertEquals(cached, cachedAgain);
            assertEquals(1, cache.cacheEntryCount());
            assertEquals(1, requestCount.get());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void staleCacheShouldRefreshAndReplaceImage(@TempDir Path tempDir) throws Exception {
        byte[] first = createPng(8, 8, Color.RED.getRGB());
        byte[] second = createPng(8, 8, Color.BLUE.getRGB());
        AtomicReference<byte[]> response = new AtomicReference<>(first);
        AtomicInteger requestCount = new AtomicInteger();
        HttpServer server = startServer(exchange -> {
            requestCount.incrementAndGet();
            byte[] body = response.get();
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        try {
            String url = url(server, "/refresh.png");
            RemoteImageCache cache = new RemoteImageCache(tempDir, new DebugRecorder(20));
            Path cached = cache.resolve(url);
            int initialRgb = ImageIO.read(cached.toFile()).getRGB(0, 0);

            Files.setLastModifiedTime(cached, staleTime());
            response.set(second);
            Path refreshed = cache.resolve(url);
            int refreshedRgb = ImageIO.read(refreshed.toFile()).getRGB(0, 0);

            assertEquals(cached, refreshed);
            assertNotEquals(initialRgb, refreshedRgb);
            assertEquals(Color.BLUE.getRGB(), refreshedRgb);
            assertEquals(2, requestCount.get());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void staleCacheShouldFallbackWhenRefreshFails(@TempDir Path tempDir) throws Exception {
        byte[] png = createPng(8, 8, Color.MAGENTA.getRGB());
        AtomicInteger status = new AtomicInteger(200);
        AtomicInteger requestCount = new AtomicInteger();
        HttpServer server = startServer(exchange -> {
            requestCount.incrementAndGet();
            if (status.get() != 200) {
                exchange.sendResponseHeaders(status.get(), -1);
                exchange.close();
                return;
            }
            exchange.sendResponseHeaders(200, png.length);
            exchange.getResponseBody().write(png);
            exchange.close();
        });
        try {
            String url = url(server, "/stale.png");
            RemoteImageCache cache = new RemoteImageCache(tempDir, new DebugRecorder(20));
            Path cached = cache.resolve(url);
            byte[] before = Files.readAllBytes(cached);

            Files.setLastModifiedTime(cached, staleTime());
            status.set(503);
            Path fallback = cache.resolve(url);

            assertEquals(cached, fallback);
            assertArrayEquals(before, Files.readAllBytes(fallback));
            assertEquals(2, requestCount.get());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void connectionFailureWithoutCacheShouldPropagateAsIOException(@TempDir Path tempDir) throws Exception {
        int unusedPort;
        try (ServerSocket socket = new ServerSocket(0)) {
            unusedPort = socket.getLocalPort();
        }
        RemoteImageCache cache = new RemoteImageCache(tempDir, new DebugRecorder(10));
        assertThrows(IOException.class, () -> cache.resolve("http://127.0.0.1:" + unusedPort + "/missing.png"));
        assertEquals(0, cache.cacheEntryCount());
    }

    @Test
    void corruptFreshCacheShouldBeRefetchedInsteadOfReturned(@TempDir Path tempDir) throws Exception {
        byte[] png = createPng(8, 8, Color.GREEN.getRGB());
        AtomicInteger requestCount = new AtomicInteger();
        HttpServer server = startServer(exchange -> {
            requestCount.incrementAndGet();
            exchange.sendResponseHeaders(200, png.length);
            exchange.getResponseBody().write(png);
            exchange.close();
        });
        try {
            String url = url(server, "/corrupt.png");
            RemoteImageCache cache = new RemoteImageCache(tempDir, new DebugRecorder(20));
            Path cached = cache.resolve(url);
            Files.writeString(cached, "corrupted-cache");

            Path recovered = cache.resolve(url);

            assertEquals(cached, recovered);
            assertEquals(2, requestCount.get());
            assertEquals(Color.GREEN.getRGB(), ImageIO.read(recovered.toFile()).getRGB(0, 0));
        } finally {
            server.stop(0);
        }
    }

    private static FileTime staleTime() {
        return FileTime.from(Instant.now().minus(Duration.ofMinutes(31)));
    }

    private static HttpServer startServer(com.sun.net.httpserver.HttpHandler handler) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", handler);
        server.start();
        return server;
    }

    private static String url(HttpServer server, String path) {
        return "http://127.0.0.1:" + server.getAddress().getPort() + path;
    }

    private static byte[] createPng(int width, int height, int argb) throws Exception {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                image.setRGB(x, y, argb);
            }
        }
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, "png", output);
        return output.toByteArray();
    }
}
