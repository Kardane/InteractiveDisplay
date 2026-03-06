package com.interactivedisplay.schema;

import com.interactivedisplay.core.component.ImageSource;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class MapImageResolver {
    private final Path imagesDir;
    private final RemoteImageCache remoteImageCache;

    public MapImageResolver(Path configDir, RemoteImageCache remoteImageCache) {
        this.imagesDir = configDir.resolve("interactivedisplay").resolve("images");
        this.remoteImageCache = remoteImageCache;
    }

    public ImageSource resolve(String rawValue) throws IOException, InterruptedException {
        Path normalizedImagesDir = this.imagesDir.toAbsolutePath().normalize();
        Files.createDirectories(normalizedImagesDir);
        if (rawValue.startsWith("http://") || rawValue.startsWith("https://")) {
            Path cachedPath = this.remoteImageCache.resolve(rawValue);
            return new ImageSource(rawValue, true, cachedPath);
        }

        Path resolved = normalizedImagesDir.resolve(rawValue).normalize();
        if (!resolved.startsWith(normalizedImagesDir)) {
            throw new IOException("MAP 로컬 경로가 images 디렉터리 밖을 가리킴");
        }
        if (!Files.exists(resolved)) {
            throw new IOException("MAP 로컬 파일이 없음: " + rawValue);
        }
        return new ImageSource(rawValue, false, resolved);
    }
}
