package com.interactivedisplay.schema;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.interactivedisplay.debug.DebugRecorder;
import com.sun.net.httpserver.HttpServer;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RemoteImageCacheTest {
    @Test
    void remoteImageShouldBeCachedAsPng(@TempDir Path tempDir) throws Exception {
        byte[] png = createPng();
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/image.png", exchange -> {
            exchange.sendResponseHeaders(200, png.length);
            exchange.getResponseBody().write(png);
            exchange.close();
        });
        server.start();
        try {
            String url = "http://127.0.0.1:" + server.getAddress().getPort() + "/image.png";
            RemoteImageCache cache = new RemoteImageCache(tempDir, new DebugRecorder(10));
            Path cached = cache.resolve(url);

            assertTrue(Files.exists(cached));
            assertEquals(1, cache.cacheEntryCount());
        } finally {
            server.stop(0);
        }
    }

    private static byte[] createPng() throws Exception {
        BufferedImage image = new BufferedImage(8, 8, BufferedImage.TYPE_INT_ARGB);
        for (int x = 0; x < 8; x++) {
            for (int y = 0; y < 8; y++) {
                image.setRGB(x, y, (x + y) % 2 == 0 ? Color.WHITE.getRGB() : Color.BLACK.getRGB());
            }
        }
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, "png", output);
        return output.toByteArray();
    }
}
