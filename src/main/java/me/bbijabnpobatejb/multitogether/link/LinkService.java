package me.bbijabnpobatejb.multitogether.link;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Все связи между игроками. Только память: после остановки сервера связей нет.
 * Вызывать с основного потока.
 */
public final class LinkService {

    private final Map<LinkType, LinkGraph> graphs = new EnumMap<>(LinkType.class);
    private final List<LinkListener> listeners = new CopyOnWriteArrayList<>();
    private final Map<UUID, String> names = new HashMap<>();

    public LinkService() {
        for (LinkType type : LinkType.values()) {
            graphs.put(type, new LinkGraph());
        }
    }

    public void addListener(LinkListener listener) {
        listeners.add(listener);
    }

    /**
     * Связывает двух игроков выбранными видами. Возвращает виды, которых раньше между ними не было.
     */
    public Set<LinkType> link(Player a, Player b, Set<LinkType> types) {
        remember(a);
        remember(b);

        Set<LinkType> added = EnumSet.noneOf(LinkType.class);
        for (LinkType type : types) {
            LinkGraph graph = graphs.get(type);
            Set<UUID> affected = new HashSet<>(graph.component(a.getUniqueId()));
            affected.addAll(graph.component(b.getUniqueId()));

            if (graph.link(a.getUniqueId(), b.getUniqueId())) {
                added.add(type);
                fire(type, affected);
            }
        }
        return added;
    }

    /**
     * Разрывает связь пары. Возвращает виды, которые действительно были.
     */
    public Set<LinkType> unlink(UUID a, UUID b, Set<LinkType> types) {
        Set<LinkType> removed = EnumSet.noneOf(LinkType.class);
        for (LinkType type : types) {
            LinkGraph graph = graphs.get(type);
            Set<UUID> affected = new HashSet<>(graph.component(a));

            if (graph.unlink(a, b)) {
                removed.add(type);
                fire(type, affected);
            }
        }
        return removed;
    }

    /**
     * Отвязывает игрока ото всех. Возвращает, сколько связей порвано по каждому виду.
     */
    public Map<LinkType, Integer> unlinkPlayer(UUID player, Set<LinkType> types) {
        Map<LinkType, Integer> removed = new EnumMap<>(LinkType.class);
        for (LinkType type : types) {
            LinkGraph graph = graphs.get(type);
            Set<UUID> affected = new HashSet<>(graph.component(player));

            Set<UUID> former = graph.unlinkAll(player);
            if (!former.isEmpty()) {
                removed.put(type, former.size());
                fire(type, affected);
            }
        }
        return removed;
    }

    /**
     * Снимает все связи выбранных видов. Возвращает число порванных пар.
     */
    public int unlinkEverything(Set<LinkType> types) {
        int count = 0;
        for (LinkType type : types) {
            LinkGraph graph = graphs.get(type);
            if (graph.isEmpty()) continue;

            count += graph.edges().size();
            Set<UUID> affected = graph.nodes();
            graph.clear();
            fire(type, affected);
        }
        return count;
    }

    private void fire(LinkType type, Set<UUID> affected) {
        for (LinkListener listener : listeners) {
            listener.onLinksChanged(type, Set.copyOf(affected));
        }
    }

    public boolean isLinked(LinkType type, UUID a, UUID b) {
        return graphs.get(type).isLinked(a, b);
    }

    public boolean hasLinks(LinkType type, UUID player) {
        return graphs.get(type).hasLinks(player);
    }

    public boolean hasAnyLinks(UUID player) {
        for (LinkGraph graph : graphs.values()) {
            if (graph.hasLinks(player)) return true;
        }
        return false;
    }

    public Set<UUID> neighbors(LinkType type, UUID player) {
        return graphs.get(type).neighbors(player);
    }

    public Set<UUID> component(LinkType type, UUID player) {
        return graphs.get(type).component(player);
    }

    public List<Set<UUID>> components(LinkType type) {
        return graphs.get(type).components();
    }

    public List<Edge> edges(LinkType type) {
        return graphs.get(type).edges();
    }

    public boolean isEmpty() {
        for (LinkGraph graph : graphs.values()) {
            if (!graph.isEmpty()) return false;
        }
        return true;
    }

    public void remember(Player player) {
        names.put(player.getUniqueId(), player.getName());
    }

    public String name(UUID uuid) {
        Player online = Bukkit.getPlayer(uuid);
        if (online != null) return online.getName();

        String cached = names.get(uuid);
        if (cached != null) return cached;

        OfflinePlayer offline = Bukkit.getOfflinePlayer(uuid);
        return offline.getName() == null ? uuid.toString().substring(0, 8) : offline.getName();
    }

    public List<String> names(Set<UUID> uuids) {
        List<String> result = new ArrayList<>(uuids.size());
        for (UUID uuid : uuids) {
            result.add(name(uuid));
        }
        return result;
    }
}
