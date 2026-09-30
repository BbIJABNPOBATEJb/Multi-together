package me.bbijabnpobatejb.multitogether.link;

import java.util.UUID;

/**
 * Неупорядоченная пара игроков: {@code Edge(a, b)} и {@code Edge(b, a)} — одно и то же ребро.
 */
public record Edge(UUID first, UUID second) {

    public Edge {
        if (first.compareTo(second) > 0) {
            UUID swap = first;
            first = second;
            second = swap;
        }
    }

    public boolean contains(UUID uuid) {
        return first.equals(uuid) || second.equals(uuid);
    }

    public UUID other(UUID uuid) {
        return first.equals(uuid) ? second : first;
    }
}
