package me.bbijabnpobatejb.multitogether.link;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Неориентированный граф связей одного вида. Живёт только в памяти: до остановки сервера.
 */
final class LinkGraph {

    private final Map<UUID, Set<UUID>> adjacency = new HashMap<>();

    boolean link(UUID a, UUID b) {
        if (a.equals(b)) return false;
        boolean added = adjacency.computeIfAbsent(a, k -> new LinkedHashSet<>()).add(b);
        adjacency.computeIfAbsent(b, k -> new LinkedHashSet<>()).add(a);
        return added;
    }

    boolean unlink(UUID a, UUID b) {
        boolean removed = removeHalf(a, b);
        removeHalf(b, a);
        return removed;
    }

    /**
     * Рвёт все связи игрока и возвращает бывших соседей.
     */
    Set<UUID> unlinkAll(UUID a) {
        Set<UUID> neighbors = adjacency.remove(a);
        if (neighbors == null) return Set.of();

        for (UUID neighbor : neighbors) {
            removeHalf(neighbor, a);
        }
        return neighbors;
    }

    private boolean removeHalf(UUID from, UUID to) {
        Set<UUID> set = adjacency.get(from);
        if (set == null) return false;

        boolean removed = set.remove(to);
        if (set.isEmpty()) adjacency.remove(from);
        return removed;
    }

    boolean isLinked(UUID a, UUID b) {
        Set<UUID> set = adjacency.get(a);
        return set != null && set.contains(b);
    }

    boolean hasLinks(UUID a) {
        return adjacency.containsKey(a);
    }

    Set<UUID> neighbors(UUID a) {
        Set<UUID> set = adjacency.get(a);
        return set == null ? Set.of() : Collections.unmodifiableSet(set);
    }

    /**
     * Связная группа игрока, он сам тоже в ней. Без связей — группа из него одного.
     */
    Set<UUID> component(UUID start) {
        Set<UUID> visited = new LinkedHashSet<>();
        ArrayDeque<UUID> queue = new ArrayDeque<>();
        visited.add(start);
        queue.add(start);

        while (!queue.isEmpty()) {
            UUID current = queue.poll();
            for (UUID next : adjacency.getOrDefault(current, Set.of())) {
                if (visited.add(next)) queue.add(next);
            }
        }
        return visited;
    }

    /**
     * Все группы, где больше одного игрока.
     */
    List<Set<UUID>> components() {
        List<Set<UUID>> result = new ArrayList<>();
        Set<UUID> seen = new HashSet<>();

        for (UUID node : adjacency.keySet()) {
            if (seen.contains(node)) continue;
            Set<UUID> component = component(node);
            seen.addAll(component);
            result.add(component);
        }
        return result;
    }

    List<Edge> edges() {
        Set<Edge> result = new LinkedHashSet<>();
        adjacency.forEach((a, set) -> set.forEach(b -> result.add(new Edge(a, b))));
        return new ArrayList<>(result);
    }

    Set<UUID> nodes() {
        return new HashSet<>(adjacency.keySet());
    }

    void clear() {
        adjacency.clear();
    }

    boolean isEmpty() {
        return adjacency.isEmpty();
    }
}
