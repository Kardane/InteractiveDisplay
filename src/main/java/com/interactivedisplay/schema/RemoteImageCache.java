package com.interactivedisplay.schema;

import com.interactivedisplay.InteractiveDisplay;
import com.interactivedisplay.debug.DebugEventType;
import com.interactivedisplay.debug.DebugLevel;
import com.interactivedisplay.debug.DebugReason;
import com.interactivedisplay.debug.DebugRecorder;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import javax.imageio.ImageIO;

public final class RemoteImageCache {
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(3);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration CACHE_TTL = Duration.ofMinutes(30);
    private static final int MAX_BYTES = 4 * 1024 * 1024;
    private static final int MAX_DIMENSION = 4096;
    private static final long MAX_PIXELS = 16_777_216L;

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
        if (Files.isRegularFile(target)) {
            if (!isValidCachedImage(target)) {
                Files.deleteIfExists(target);
            } else if (isFresh(target)) {
                return target;
            }
        }

        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(REQUEST_TIMEOUT)
                .GET()
                .build();

        try {
            HttpResponse<InputStream> response = this.httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                closeQuietly(response.body());
                throw new IOException("원격 이미지 응답 실패 status=" + response.statusCode());
            }
            long contentLength = response.headers().firstValueAsLong("Content-Length").orElse(-1L);
            if (contentLength > MAX_BYTES) {
                closeQuietly(response.body());
                throw new IOException("원격 이미지 크기 초과");
            }

            byte[] body;
            try (InputStream input = response.body()) {
                body = readBounded(input);
            }

            BufferedImage image = ImageIO.read(new ByteArrayInputStream(body));
            if (image == null) {
                throw new IOException("이미지 디코딩 실패");
            }
            validateDimensions(image);

            Path tempFile = Files.createTempFile(this.cacheDir, "map-cache-", ".png");
            try {
                ImageIO.write(image, "png", tempFile.toFile());
                moveAtomicallyWhenPossible(tempFile, target);
            } finally {
                Files.deleteIfExists(tempFile);
            }
            return target;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return fallbackOrThrow(url, target, exception);
        } catch (IOException exception) {
            return fallbackOrThrow(url, target, exception);
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

    private boolean isFresh(Path target) {
        if (!Files.isRegularFile(target)) {
            return false;
        }
        try {
            Instant modified = Files.getLastModifiedTime(target).toInstant();
            Duration age = Duration.between(modified, Instant.now());
            return !age.isNegative() && age.compareTo(CACHE_TTL) < 0;
        } catch (IOException exception) {
            return false;
        }
    }

    private static boolean isValidCachedImage(Path target) {
        try {
            BufferedImage image = ImageIO.read(target.toFile());
            if (image == null) {
                return false;
            }
            validateDimensions(image);
            return true;
        } catch (IOException exception) {
            return false;
        }
    }

    private byte[] readBounded(InputStream input) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int total = 0;
        int read;
        while ((read = input.read(buffer)) != -1) {
            total += read;
            if (total > MAX_BYTES) {
                throw new IOException("원격 이미지 크기 초과");
            }
            output.write(buffer, 0, read);
        }
        return output.toByteArray();
    }

    private static void validateDimensions(BufferedImage image) throws IOException {
        int width = image.getWidth();
        int height = image.getHeight();
        if (width <= 0 || height <= 0 || width > MAX_DIMENSION || height > MAX_DIMENSION) {
            throw new IOException("원격 이미지 해상도 초과");
        }
        if ((long) width * height > MAX_PIXELS) {
            throw new IOException("원격 이미지 픽셀 수 초과");
        }
    }

    private Path fallbackOrThrow(String url, Path target, Exception exception) throws IOException, InterruptedException {
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
        if (exception instanceof InterruptedException interruptedException) {
            throw interruptedException;
        }
        throw (IOException) exception;
    }

    private static void moveAtomicallyWhenPossible(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void closeQuietly(InputStream input) {
        if (input == null) {
            return;
        }
        try {
            input.close();
        } catch (IOException ignored) {
            // Best effort: the request already failed validation.
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
