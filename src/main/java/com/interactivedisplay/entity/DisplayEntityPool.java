package com.interactivedisplay.entity;

import com.interactivedisplay.core.window.WindowComponentRuntime;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.registry.RegistryKey;
import net.minecraft.world.World;

public final class DisplayEntityPool {
    private static final long RETENTION_TICKS = 100L;

    private final Map<PoolKey, Deque<PooledRuntime>> pool = new HashMap<>();

    public WindowComponentRuntime acquire(UUID owner, RegistryKey<World> worldKey, String signature, long currentTick) {
        PoolKey key = new PoolKey(owner, worldKey, signature);
        Deque<PooledRuntime> runtimes = this.pool.get(key);
        if (runtimes == null) {
            return null;
        }
        while (!runtimes.isEmpty()) {
            PooledRuntime pooled = runtimes.removeFirst();
            if (currentTick - pooled.releasedTick <= RETENTION_TICKS) {
                if (runtimes.isEmpty()) {
                    this.pool.remove(key);
                }
                return pooled.runtime;
            }
        }
        this.pool.remove(key);
        return null;
    }

    public void release(UUID owner, RegistryKey<World> worldKey, WindowComponentRuntime runtime, long currentTick) {
        PoolKey key = new PoolKey(owner, worldKey, runtime.signature());
        this.pool.computeIfAbsent(key, ignored -> new ArrayDeque<>()).addLast(new PooledRuntime(runtime, currentTick));
    }

    public void expire(long currentTick, Consumer<WindowComponentRuntime> destroyer) {
        Iterator<Map.Entry<PoolKey, Deque<PooledRuntime>>> iterator = this.pool.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<PoolKey, Deque<PooledRuntime>> entry = iterator.next();
            Deque<PooledRuntime> runtimes = entry.getValue();
            while (!runtimes.isEmpty() && currentTick - runtimes.peekFirst().releasedTick > RETENTION_TICKS) {
                destroyer.accept(runtimes.removeFirst().runtime);
            }
            if (runtimes.isEmpty()) {
                iterator.remove();
            }
        }
    }

    public int size() {
        int count = 0;
        for (Deque<PooledRuntime> deque : this.pool.values()) {
            count += deque.size();
        }
        return count;
    }

    private record PoolKey(UUID owner, RegistryKey<World> worldKey, String signature) {
    }

    private record PooledRuntime(WindowComponentRuntime runtime, long releasedTick) {
    }
}
