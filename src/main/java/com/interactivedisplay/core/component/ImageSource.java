package com.interactivedisplay.core.component;

import java.nio.file.Path;

public record ImageSource(
        String rawValue,
        boolean remote,
        Path resolvedPath
) {
}
