package com.interactivedisplay.core.animation;

import com.interactivedisplay.core.window.WindowComponentRuntime;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class AnimationRegistry {
    private static final String DEFAULT_NAMESPACE = "interactivedisplay";
    private static final Map<String, AnimationFactory> FACTORIES = new ConcurrentHashMap<>();

    static {
        register("typewriter", TypewriterAnimationRuntime::new);
        register("fade", FadeAnimationRuntime::new);
    }

    private AnimationRegistry() {
    }

    public static boolean register(String id, AnimationFactory factory) {
        if (factory == null) {
            throw new IllegalArgumentException("animation factory must not be null");
        }
        return FACTORIES.putIfAbsent(normalize(id), factory) == null;
    }

    public static boolean isRegistered(String id) {
        return FACTORIES.containsKey(normalize(id));
    }

    public static Optional<AnimationRuntime> create(WindowComponentRuntime target, AnimationDefinition definition) {
        AnimationFactory factory = FACTORIES.get(normalize(definition.type()));
        return factory == null ? Optional.empty() : Optional.of(factory.create(target, definition));
    }

    public static String normalize(String id) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("animation id must not be blank");
        }
        String normalized = id.trim().toLowerCase(Locale.ROOT);
        return normalized.indexOf(':') >= 0 ? normalized : DEFAULT_NAMESPACE + ":" + normalized;
    }
}
