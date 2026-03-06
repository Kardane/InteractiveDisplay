package com.interactivedisplay.core.window;

import com.interactivedisplay.core.positioning.PositionMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

final class WindowStateStore {
    private final Map<String, WindowDefinition> definitions = new ConcurrentHashMap<>();
    private final Map<String, WindowGroupDefinition> groupDefinitions = new ConcurrentHashMap<>();
    private final Map<UUID, Map<String, WindowInstance>> activeWindows = new ConcurrentHashMap<>();
    private final Map<UUID, Map<String, WindowGroupInstance>> activeGroups = new ConcurrentHashMap<>();
    private final Set<String> brokenWindowIds = ConcurrentHashMap.newKeySet();
    private final Set<String> brokenGroupIds = ConcurrentHashMap.newKeySet();

    WindowDefinition definition(String windowId) {
        return this.definitions.get(windowId);
    }

    WindowGroupDefinition groupDefinition(String groupId) {
        return this.groupDefinitions.get(groupId);
    }

    boolean hasDefinition(String windowId) {
        return this.definitions.containsKey(windowId);
    }

    void replaceDefinitions(Map<String, WindowDefinition> definitions) {
        this.definitions.clear();
        this.definitions.putAll(definitions);
    }

    void mergeDefinitions(Map<String, WindowDefinition> definitions) {
        this.definitions.putAll(definitions);
    }

    void putDefinition(String windowId, WindowDefinition definition) {
        this.definitions.put(windowId, definition);
    }

    void replaceGroupDefinitions(Map<String, WindowGroupDefinition> groupDefinitions) {
        this.groupDefinitions.clear();
        this.groupDefinitions.putAll(groupDefinitions);
    }

    void mergeGroupDefinitions(Map<String, WindowGroupDefinition> groupDefinitions) {
        this.groupDefinitions.putAll(groupDefinitions);
    }

    void replaceBrokenWindowIds(Set<String> brokenWindowIds) {
        this.brokenWindowIds.clear();
        this.brokenWindowIds.addAll(brokenWindowIds);
    }

    void replaceBrokenGroupIds(Set<String> brokenGroupIds) {
        this.brokenGroupIds.clear();
        this.brokenGroupIds.addAll(brokenGroupIds);
    }

    Set<String> loadedWindowIds() {
        return Set.copyOf(new TreeSet<>(this.definitions.keySet()));
    }

    Set<String> availableWindowIds(Set<String> discoveredIds) {
        Set<String> windowIds = new TreeSet<>(discoveredIds);
        windowIds.addAll(this.definitions.keySet());
        return Set.copyOf(windowIds);
    }

    Set<String> brokenWindowIds() {
        return Set.copyOf(new TreeSet<>(this.brokenWindowIds));
    }

    int loadedWindowCount() {
        return this.definitions.size();
    }

    Set<String> loadedGroupIds() {
        return Set.copyOf(new TreeSet<>(this.groupDefinitions.keySet()));
    }

    Set<String> availableGroupIds(Set<String> discoveredIds) {
        Set<String> groupIds = new TreeSet<>(discoveredIds);
        groupIds.addAll(this.groupDefinitions.keySet());
        return Set.copyOf(groupIds);
    }

    Set<String> brokenGroupIds() {
        return Set.copyOf(new TreeSet<>(this.brokenGroupIds));
    }

    void putActiveWindow(UUID owner, String windowId, WindowInstance instance) {
        this.activeWindows.computeIfAbsent(owner, ignored -> new ConcurrentHashMap<>()).put(windowId, instance);
    }

    WindowInstance removeActiveWindow(UUID owner, String windowId) {
        Map<String, WindowInstance> playerWindows = this.activeWindows.get(owner);
        if (playerWindows == null) {
            return null;
        }
        WindowInstance instance = playerWindows.remove(windowId);
        if (playerWindows.isEmpty()) {
            this.activeWindows.remove(owner);
        }
        return instance;
    }

    List<String> activeWindowIds(UUID owner) {
        Map<String, WindowInstance> playerWindows = this.activeWindows.get(owner);
        if (playerWindows == null) {
            return List.of();
        }
        return new ArrayList<>(playerWindows.keySet());
    }

    void putActiveGroup(UUID owner, String groupId, WindowGroupInstance instance) {
        this.activeGroups.computeIfAbsent(owner, ignored -> new ConcurrentHashMap<>()).put(groupId, instance);
    }

    WindowGroupInstance removeActiveGroup(UUID owner, String groupId) {
        Map<String, WindowGroupInstance> playerGroups = this.activeGroups.get(owner);
        if (playerGroups == null) {
            return null;
        }
        WindowGroupInstance instance = playerGroups.remove(groupId);
        if (playerGroups.isEmpty()) {
            this.activeGroups.remove(owner);
        }
        return instance;
    }

    List<String> activeGroupIds(UUID owner) {
        Map<String, WindowGroupInstance> playerGroups = this.activeGroups.get(owner);
        if (playerGroups == null) {
            return List.of();
        }
        return new ArrayList<>(playerGroups.keySet());
    }

    int activeWindowCount() {
        int total = 0;
        for (Map<String, WindowInstance> playerWindows : this.activeWindows.values()) {
            total += playerWindows.size();
        }
        for (Map<String, WindowGroupInstance> playerGroups : this.activeGroups.values()) {
            total += playerGroups.size();
        }
        return total;
    }

    int activeBindingCount() {
        int total = 0;
        for (Map<String, WindowInstance> playerWindows : this.activeWindows.values()) {
            for (WindowInstance instance : playerWindows.values()) {
                total += instance.bindingCount();
            }
        }
        for (Map<String, WindowGroupInstance> playerGroups : this.activeGroups.values()) {
            for (WindowGroupInstance group : playerGroups.values()) {
                total += group.currentWindow().bindingCount();
            }
        }
        return total;
    }

    WindowInstance findActiveWindow(UUID owner, String windowId) {
        Map<String, WindowInstance> windows = this.activeWindows.get(owner);
        return windows == null ? null : windows.get(windowId);
    }

    WindowGroupInstance findActiveGroup(UUID owner, String groupId) {
        Map<String, WindowGroupInstance> groups = this.activeGroups.get(owner);
        return groups == null ? null : groups.get(groupId);
    }

    WindowInstance findWindow(UUID owner, String windowId) {
        WindowInstance standalone = findActiveWindow(owner, windowId);
        if (standalone != null) {
            return standalone;
        }
        Map<String, WindowGroupInstance> groups = this.activeGroups.get(owner);
        if (groups == null) {
            return null;
        }
        for (WindowGroupInstance group : groups.values()) {
            if (group.currentWindowId().equals(windowId)) {
                return group.currentWindow();
            }
        }
        return null;
    }

    Set<UUID> owners() {
        Set<UUID> owners = new TreeSet<>(UUID::compareTo);
        owners.addAll(this.activeWindows.keySet());
        owners.addAll(this.activeGroups.keySet());
        return Set.copyOf(owners);
    }

    List<WindowInstance> ownerWindows(UUID owner) {
        List<WindowInstance> windows = new ArrayList<>();
        Map<String, WindowInstance> standalone = this.activeWindows.get(owner);
        if (standalone != null) {
            windows.addAll(standalone.values());
        }
        Map<String, WindowGroupInstance> groups = this.activeGroups.get(owner);
        if (groups != null) {
            for (WindowGroupInstance group : groups.values()) {
                windows.add(group.currentWindow());
            }
        }
        return windows;
    }

    List<WindowContext> ownerWindowContexts(UUID owner) {
        List<WindowContext> windows = new ArrayList<>();
        Map<String, WindowInstance> standalone = this.activeWindows.get(owner);
        if (standalone != null) {
            for (WindowInstance instance : standalone.values()) {
                windows.add(new WindowContext(instance, new WindowNavigationContext(
                        instance.windowId(),
                        null,
                        instance.positionMode(),
                        instance.positionMode() == PositionMode.FIXED ? instance.currentAnchor() : instance.fixedAnchor(),
                        instance.positionMode() == PositionMode.FIXED ? instance.currentYaw() : instance.fixedYaw(),
                        instance.positionMode() == PositionMode.FIXED ? instance.currentPitch() : instance.fixedPitch()
                )));
            }
        }
        Map<String, WindowGroupInstance> groups = this.activeGroups.get(owner);
        if (groups != null) {
            for (WindowGroupInstance group : groups.values()) {
                windows.add(new WindowContext(group.currentWindow(), new WindowNavigationContext(
                        group.currentWindowId(),
                        group.groupId(),
                        group.currentMode(),
                        group.baseAnchor(),
                        group.baseYaw(),
                        group.basePitch()
                )));
            }
        }
        return windows;
    }

    List<WindowManager.BindingSnapshot> bindingSnapshots(UUID owner) {
        List<WindowContext> windows = ownerWindowContexts(owner);
        if (windows.isEmpty()) {
            return List.of();
        }

        List<WindowManager.BindingSnapshot> snapshots = new ArrayList<>();
        for (WindowContext windowContext : windows) {
            WindowInstance instance = windowContext.instance();
            for (WindowComponentRuntime runtime : instance.runtimes()) {
                if (!runtime.interactive() || runtime.action() == null) {
                    continue;
                }
                snapshots.add(new WindowManager.BindingSnapshot(
                        instance.windowId(),
                        runtime.definition().id(),
                        runtime.localPosition(),
                        runtime.hitHalfWidth(),
                        runtime.hitHalfHeight(),
                        runtime.action().type().name(),
                        runtime.action().target()
                ));
            }
        }
        return snapshots;
    }

    List<UUID> ownersForActiveWindow(String windowId) {
        List<UUID> owners = new ArrayList<>();
        for (Map.Entry<UUID, Map<String, WindowInstance>> entry : this.activeWindows.entrySet()) {
            if (entry.getValue().containsKey(windowId)) {
                owners.add(entry.getKey());
            }
        }
        return owners;
    }

    List<UUID> ownersForActiveGroup(String groupId) {
        List<UUID> owners = new ArrayList<>();
        for (Map.Entry<UUID, Map<String, WindowGroupInstance>> entry : this.activeGroups.entrySet()) {
            if (entry.getValue().containsKey(groupId)) {
                owners.add(entry.getKey());
            }
        }
        return owners;
    }

    List<ActiveGroupRef> activeGroupsContainingWindow(String windowId) {
        List<ActiveGroupRef> affected = new ArrayList<>();
        for (Map.Entry<UUID, Map<String, WindowGroupInstance>> ownerEntry : this.activeGroups.entrySet()) {
            for (WindowGroupInstance groupInstance : ownerEntry.getValue().values()) {
                if (groupInstance.currentWindowId().equals(windowId)) {
                    affected.add(new ActiveGroupRef(ownerEntry.getKey(), groupInstance.groupId()));
                }
            }
        }
        return affected;
    }
}
