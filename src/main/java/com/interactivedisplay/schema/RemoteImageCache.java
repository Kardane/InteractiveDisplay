package com.interactivedisplay.schema;

import com.interactivedisplay.InteractiveDisplay;
import com.interactivedisplay.debug.DebugEventType;
import com.interactivedisplay.debug.DebugLevel;
import com.interactivedisplay.debug.DebugReason;
import com.interactivedisplay.debug.DebugRecorder;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import javax.imageio.ImageIO;

public final class RemoteImageCache {
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(3);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);
    private static final int MAX_BYTES = 4 * 1024 * 1024;

    private final Path cacheDir;
    private final HttpClient httpClient;
    private final DebugRecorder debugRecorder;

    public RemoteImageCache(Path configDir, DebugRecorder debugRecorder) {
        this.cacheDir = configDir.resolve("interactivedisplay").resolve("cache").resolve("maps");
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(CONNECT_TIMEOUT)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        this.debugRecorder = debugRecorder;
    }

    public Path resolve(String url) throws IOException, InterruptedException {
        Files.createDirectories(this.cacheDir);

        Path target = this.cacheDir.resolve(sha256(url) + ".png");
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(REQUEST_TIMEOUT)
                .GET()
                .build();

        try {
            HttpResponse<byte[]> response = this.httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IOException("원격 이미지 응답 실패 status=" + response.statusCode());
            }
            if (response.body().length > MAX_BYTES) {
                throw new IOException("원격 이미지 크기 초과");
            }

            BufferedImage image = ImageIO.read(new java.io.ByteArrayInputStream(response.body()));
            if (image == null) {
                throw new IOException("이미지 디코딩 실패");
            }

            Path tempFile = Files.createTempFile(this.cacheDir, "map-cache-", ".png");
            ImageIO.write(image, "png", tempFile.toFile());
            Files.move(tempFile, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            return target;
        } catch (IOException | InterruptedException exception) {
            this.debugRecorder.record(
                    DebugEventType.SCHEMA_LOAD,
                    DebugLevel.WARN,
                    null,
                    null,
                    null,
                    null,
                    null,
                    DebugReason.SCHEMA_VALIDATION_FAILED,
                    "원격 MAP 캐시 갱신 실패 url=" + url,
                    exception
            );
            InteractiveDisplay.LOGGER.warn(
                    "[{}] remote map cache warn url={} reasonCode={} message={}",
                    InteractiveDisplay.MOD_ID,
                    url,
                    DebugReason.SCHEMA_VALIDATION_FAILED,
                    exception.getMessage()
            );
            if (Files.exists(target)) {
                return target;
            }
            throw exception;
        }
    }

    public int cacheEntryCount() {
        if (!Files.isDirectory(this.cacheDir)) {
            return 0;
        }
        try (var stream = Files.list(this.cacheDir)) {
            return (int) stream.filter(path -> path.getFileName().toString().endsWith(".png")).count();
        } catch (IOException exception) {
            return 0;
        }
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(bytes.length * 2);
            for (byte valueByte : bytes) {
                builder.append(String.format("%02x", valueByte));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }
}
