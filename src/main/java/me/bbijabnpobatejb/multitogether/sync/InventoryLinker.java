package me.bbijabnpobatejb.multitogether.sync;

import com.destroystokyo.paper.event.player.PlayerPostRespawnEvent;
import me.bbijabnpobatejb.multitogether.link.LinkListener;
import me.bbijabnpobatejb.multitogether.link.LinkService;
import me.bbijabnpobatejb.multitogether.link.LinkType;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Общий инвентарь. Как в Linked-Inventory: игрокам подсовывается один и тот же список
 * предметов, поэтому любое изменение сразу видно всем, без копирования и без дюпа.
 * Как именно устроен инвентарь в этой версии сервера, знает {@link InventoryAccess}.
 *
 * <p>Сервер при возрождении оставляет того же игрока, поэтому смерть связь не рвёт.
 * При заходе игрок получает общий список заново: сохранённый при выходе снимок устарел.</p>
 */
public final class InventoryLinker implements LinkListener, Listener {

    private final Plugin plugin;
    private final LinkService links;

    private InventoryAccess access;
    private boolean unsupported;

    /**
     * Чей инвентарь сейчас общий. Держится и за вышедших: при заходе им выдаётся тот же объект.
     */
    private final Map<UUID, SharedInventory> assigned = new HashMap<>();

    public InventoryLinker(Plugin plugin, LinkService links) {
        this.plugin = plugin;
        this.links = links;
    }

    /**
     * Устройство инвентаря узнаётся по первому игроку и дальше не меняется.
     */
    private InventoryAccess access(Player sample) {
        if (access != null || unsupported || sample == null) return access;
        try {
            access = InventoryAccess.inspect(sample);
            plugin.getLogger().info("Shared inventory: " + (access.isLegacy() ? "three-list" : "equipment") + " layout");
        } catch (Exception e) {
            unsupported = true;
            plugin.getLogger().log(Level.SEVERE, "Shared inventory is not supported on this server version", e);
        }
        return access;
    }

    public boolean isSupported() {
        return !unsupported;
    }

    @Override
    public void onLinksChanged(LinkType type, Set<UUID> affected) {
        if (type != LinkType.INVENTORY) return;

        List<Set<UUID>> groups = new ArrayList<>();
        Set<UUID> seen = new HashSet<>();
        for (UUID uuid : affected) {
            if (seen.contains(uuid)) continue;
            Set<UUID> group = links.component(LinkType.INVENTORY, uuid);
            seen.addAll(group);
            groups.add(group);
        }

        // Самая большая группа сохраняет прежний список, остальные получают копии
        groups.sort(Comparator.comparingInt(Set<UUID>::size).reversed());

        Set<List<Object>> claimed = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Set<UUID> group : groups) {
            InventoryAccess inventory = access(firstOnline(group));
            if (inventory == null) continue;

            try {
                if (group.size() < 2) {
                    for (UUID member : group) detach(inventory, member);
                } else {
                    reconcile(inventory, group, claimed);
                }
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to link inventories of " + links.names(group), e);
            }
        }
    }

    /**
     * Сводит группу к одному хранилищу. Если у участников были разные — вещи переезжают в общее,
     * что не влезло, падает под ноги хозяину.
     */
    private void reconcile(InventoryAccess inventory, Set<UUID> group, Set<List<Object>> claimed) throws ReflectiveOperationException {
        Map<SharedInventory, List<UUID>> users = new HashMap<>();
        List<SharedInventory> order = new ArrayList<>();

        for (UUID member : group) {
            SharedInventory shared = currentOf(inventory, member, order);
            if (shared == null) continue;

            if (!users.containsKey(shared)) order.add(shared);
            users.computeIfAbsent(shared, k -> new ArrayList<>()).add(member);
        }
        if (order.isEmpty()) return;

        SharedInventory primary = order.get(0);
        for (SharedInventory candidate : order) {
            if (users.get(candidate).size() > users.get(primary).size()) primary = candidate;
        }

        // Список уже достался другой группе этого же разделения — эта получает копию
        SharedInventory target = claimed.contains(primary.storage.items)
                ? new SharedInventory(inventory.copy(primary.storage))
                : primary;
        for (SharedInventory other : order) {
            if (other == primary || claimed.contains(other.storage.items)) continue;
            moveInto(inventory, target, other, dropperOf(users.get(other), group));
        }
        claimed.add(target.storage.items);

        for (UUID member : group) {
            assigned.put(member, target);
            Player player = Bukkit.getPlayer(member);
            if (player != null) attach(inventory, player, target);
        }
    }

    /**
     * Какой инвентарь у игрока сейчас: живой онлайн-игрока или запомненный у вышедшего.
     */
    private SharedInventory currentOf(InventoryAccess inventory, UUID member, List<SharedInventory> known) throws ReflectiveOperationException {
        Player player = Bukkit.getPlayer(member);
        if (player == null) return assigned.get(member);

        InventoryAccess.Storage storage = inventory.read(player);
        for (SharedInventory candidate : known) {
            if (candidate.storage.items == storage.items) return candidate;
        }

        SharedInventory remembered = assigned.get(member);
        if (remembered != null && remembered.storage.items == storage.items) return remembered;

        return new SharedInventory(storage);
    }

    private static Player firstOnline(Set<UUID> group) {
        for (UUID uuid : group) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) return player;
        }
        return null;
    }

    private static Player dropperOf(List<UUID> owners, Set<UUID> group) {
        Player owner = firstOnline(new HashSet<>(owners));
        return owner != null ? owner : firstOnline(group);
    }

    /**
     * Перекладывает вещи из {@code from} в {@code to}: экипировку в свободные слоты, остальное
     * в ячейки. {@code from} после этого пустой.
     */
    private void moveInto(InventoryAccess inventory, SharedInventory to, SharedInventory from, Player dropper) throws ReflectiveOperationException {
        List<ItemStack> rest = new ArrayList<>();
        for (Map.Entry<String, ItemStack> entry : inventory.takeEquipment(from.storage).entrySet()) {
            if (!inventory.putEquipment(to.storage, entry.getKey(), entry.getValue())) rest.add(entry.getValue());
        }
        rest.addAll(inventory.takeItems(from.storage));

        for (ItemStack stack : rest) {
            ItemStack leftover = inventory.addItem(to.storage, stack);
            if (leftover == null) continue;

            if (dropper == null) {
                plugin.getLogger().warning("Nowhere to drop " + leftover + " while merging inventories");
                continue;
            }
            dropper.getWorld().dropItemNaturally(dropper.getLocation(), leftover);
        }
    }

    /**
     * Игрок выходит из группы и уносит копию того, что было в общем инвентаре.
     */
    private void detach(InventoryAccess inventory, UUID member) throws ReflectiveOperationException {
        SharedInventory shared = assigned.remove(member);
        if (shared == null) return;

        Player player = Bukkit.getPlayer(member);
        if (player == null) return;

        if (inventory.isAttached(player, shared.storage)) {
            inventory.write(player, inventory.copy(shared.storage));
        }
    }

    private void attach(InventoryAccess inventory, Player player, SharedInventory shared) throws ReflectiveOperationException {
        if (!inventory.isAttached(player, shared.storage)) inventory.write(player, shared.storage);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    private void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        SharedInventory shared = assigned.get(player.getUniqueId());
        if (shared == null) return;

        if (links.component(LinkType.INVENTORY, player.getUniqueId()).size() < 2) {
            assigned.remove(player.getUniqueId());
            return;
        }

        InventoryAccess inventory = access(player);
        if (inventory == null) return;
        try {
            // Загруженное с диска — снимок на момент выхода, общий список новее
            attach(inventory, player, shared);
        } catch (ReflectiveOperationException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to restore the shared inventory of " + player.getName(), e);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    private void onPostRespawn(PlayerPostRespawnEvent event) {
        Player player = event.getPlayer();
        SharedInventory shared = assigned.get(player.getUniqueId());
        InventoryAccess inventory = access(player);
        if (shared == null || inventory == null) return;

        try {
            // Сервер переиспользует игрока, но если кто-то подменил инвентарь — вернуть общий
            if (!inventory.isAttached(player, shared.storage)) {
                reconcile(inventory, links.component(LinkType.INVENTORY, player.getUniqueId()),
                        Collections.newSetFromMap(new IdentityHashMap<>()));
            }
        } catch (ReflectiveOperationException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to restore the shared inventory of " + player.getName(), e);
        }
    }

    /**
     * При выключении плагина каждый онлайн-игрок получает свою копию: иначе списки остались бы
     * общими до перезахода, а связи уже нет.
     */
    public void shutdown() {
        if (access != null) {
            for (Map.Entry<UUID, SharedInventory> entry : assigned.entrySet()) {
                Player player = Bukkit.getPlayer(entry.getKey());
                if (player == null) continue;

                try {
                    if (access.isAttached(player, entry.getValue().storage)) {
                        access.write(player, access.copy(entry.getValue().storage));
                    }
                } catch (ReflectiveOperationException e) {
                    plugin.getLogger().log(Level.SEVERE, "Failed to unlink the inventory of " + player.getName(), e);
                }
            }
        }
        assigned.clear();
    }

    /**
     * Хранилище, которое делят игроки группы. Сравнение по ссылке, а не по содержимому:
     * два разных инвентаря с одинаковыми вещами — всё равно разные инвентари.
     */
    private static final class SharedInventory {

        final InventoryAccess.Storage storage;

        SharedInventory(InventoryAccess.Storage storage) {
            this.storage = storage;
        }
    }
}
