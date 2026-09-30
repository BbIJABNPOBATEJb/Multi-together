package me.bbijabnpobatejb.multitogether.sync;

import com.destroystokyo.paper.event.player.PlayerPostRespawnEvent;
import me.bbijabnpobatejb.multitogether.link.LinkListener;
import me.bbijabnpobatejb.multitogether.link.LinkService;
import me.bbijabnpobatejb.multitogether.link.LinkType;
import net.minecraft.core.NonNullList;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityEquipment;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.bukkit.Bukkit;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.craftbukkit.inventory.CraftItemStack;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
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
 *
 * <p>С 1.21.5 броня и вторая рука живут не в инвентаре, а в {@link EntityEquipment}, и
 * этот объект у игрока с инвентарём общий. Поэтому делится два поля: {@code Inventory.items}
 * (36 ячеек) и {@code EntityEquipment.items} (броня и вторая рука). Основная рука — это
 * выбранная ячейка хотбара, она у каждого своя.</p>
 *
 * <p>Сервер при возрождении оставляет того же {@link ServerPlayer}, поэтому смерть связь не рвёт.
 * При заходе игрок получает общий список заново: сохранённый при выходе снимок устарел.</p>
 */
public final class InventoryLinker implements LinkListener, Listener {

    private static final Field INVENTORY_ITEMS = findField(Inventory.class, "items", NonNullList.class);
    private static final Field EQUIPMENT_ITEMS = findField(EntityEquipment.class, "items", EnumMap.class);

    private final Plugin plugin;
    private final LinkService links;

    /**
     * Чей инвентарь сейчас общий. Держится и за вышедших: при заходе им выдаётся тот же объект.
     */
    private final Map<UUID, SharedInventory> assigned = new HashMap<>();

    public InventoryLinker(Plugin plugin, LinkService links) {
        this.plugin = plugin;
        this.links = links;
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

        Set<NonNullList<ItemStack>> claimed = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Set<UUID> group : groups) {
            try {
                if (group.size() < 2) {
                    group.forEach(this::detach);
                } else {
                    reconcile(group, claimed);
                }
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "Не удалось связать инвентари " + links.names(group), e);
            }
        }
    }

    /**
     * Сводит группу к одному списку. Если у участников были разные — вещи переезжают в общий,
     * что не влезло, падает под ноги хозяину.
     */
    private void reconcile(Set<UUID> group, Set<NonNullList<ItemStack>> claimed) throws IllegalAccessException {
        Map<SharedInventory, List<UUID>> users = new HashMap<>();
        List<SharedInventory> order = new ArrayList<>();

        for (UUID member : group) {
            SharedInventory inventory = currentOf(member, order);
            if (inventory == null) continue;

            if (!users.containsKey(inventory)) order.add(inventory);
            users.computeIfAbsent(inventory, k -> new ArrayList<>()).add(member);
        }
        if (order.isEmpty()) return;

        SharedInventory primary = order.getFirst();
        for (SharedInventory candidate : order) {
            if (users.get(candidate).size() > users.get(primary).size()) primary = candidate;
        }

        // Список уже достался другой группе этого же разделения — эта получает копию
        SharedInventory target = claimed.contains(primary.items) ? primary.copy() : primary;
        for (SharedInventory other : order) {
            if (other == primary || claimed.contains(other.items)) continue;

            moveInto(target, other, dropperOf(users.get(other), group));
        }
        claimed.add(target.items);

        for (UUID member : group) {
            assigned.put(member, target);
            Player player = Bukkit.getPlayer(member);
            if (player != null) attach(player, target);
        }
    }

    /**
     * Какой инвентарь у игрока сейчас: живой список онлайн-игрока или запомненный у вышедшего.
     */
    private SharedInventory currentOf(UUID member, List<SharedInventory> known) throws IllegalAccessException {
        Player player = Bukkit.getPlayer(member);
        if (player == null) return assigned.get(member);

        Inventory inventory = handle(player).getInventory();
        NonNullList<ItemStack> items = items(inventory);
        for (SharedInventory candidate : known) {
            if (candidate.items == items) return candidate;
        }

        SharedInventory remembered = assigned.get(member);
        if (remembered != null && remembered.items == items) return remembered;

        return new SharedInventory(items, equipment(inventory));
    }

    private Player dropperOf(List<UUID> owners, Set<UUID> group) {
        for (UUID uuid : owners) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) return player;
        }
        for (UUID uuid : group) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) return player;
        }
        return null;
    }

    /**
     * Перекладывает вещи из {@code from} в {@code to}. Сами стаки переезжают, а не копируются,
     * поэтому {@code from} после этого пустой.
     */
    private void moveInto(SharedInventory to, SharedInventory from, Player dropper) {
        List<ItemStack> leftovers = new ArrayList<>();

        for (Map.Entry<EquipmentSlot, ItemStack> entry : from.equipment.entrySet()) {
            ItemStack stack = entry.getValue();
            if (stack == null || stack.isEmpty()) continue;

            EquipmentSlot slot = entry.getKey();
            ItemStack current = to.equipment.getOrDefault(slot, ItemStack.EMPTY);
            if (slot != EquipmentSlot.MAINHAND && current.isEmpty()) {
                to.equipment.put(slot, stack);
            } else {
                ItemStack rest = addToList(to.items, stack);
                if (!rest.isEmpty()) leftovers.add(rest);
            }
        }
        from.equipment.replaceAll((slot, stack) -> ItemStack.EMPTY);

        for (int i = 0; i < from.items.size(); i++) {
            ItemStack stack = from.items.get(i);
            if (stack.isEmpty()) continue;

            ItemStack rest = addToList(to.items, stack);
            if (!rest.isEmpty()) leftovers.add(rest);
            from.items.set(i, ItemStack.EMPTY);
        }

        if (dropper == null) {
            if (!leftovers.isEmpty()) {
                plugin.getLogger().warning("Некуда выбросить " + leftovers.size() + " стаков при слиянии инвентарей");
            }
            return;
        }
        for (ItemStack stack : leftovers) {
            dropper.getWorld().dropItemNaturally(dropper.getLocation(), CraftItemStack.asBukkitCopy(stack));
        }
    }

    /**
     * Докладывает стак в список: сначала в такие же неполные стаки, потом в пустые ячейки.
     * Возвращает то, что не влезло.
     */
    private static ItemStack addToList(NonNullList<ItemStack> list, ItemStack stack) {
        if (stack.isStackable()) {
            for (ItemStack existing : list) {
                if (stack.isEmpty()) return ItemStack.EMPTY;
                if (existing.isEmpty() || !ItemStack.isSameItemSameComponents(existing, stack)) continue;

                int space = existing.getMaxStackSize() - existing.getCount();
                if (space <= 0) continue;

                int moved = Math.min(space, stack.getCount());
                existing.grow(moved);
                stack.shrink(moved);
            }
        }
        if (stack.isEmpty()) return ItemStack.EMPTY;

        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).isEmpty()) {
                list.set(i, stack);
                return ItemStack.EMPTY;
            }
        }
        return stack;
    }

    /**
     * Игрок выходит из группы и уносит копию того, что было в общем инвентаре.
     */
    private void detach(UUID member) {
        SharedInventory inventory = assigned.remove(member);
        if (inventory == null) return;

        Player player = Bukkit.getPlayer(member);
        if (player == null) return;

        try {
            if (isAttached(player, inventory)) attach(player, inventory.copy());
        } catch (IllegalAccessException e) {
            plugin.getLogger().log(Level.SEVERE, "Не удалось отвязать инвентарь " + player.getName(), e);
        }
    }

    private void attach(Player player, SharedInventory inventory) throws IllegalAccessException {
        if (isAttached(player, inventory)) return;

        ServerPlayer handle = handle(player);
        Inventory nms = handle.getInventory();
        INVENTORY_ITEMS.set(nms, inventory.items);
        EQUIPMENT_ITEMS.set(nms.equipment, inventory.equipment);
        nms.setChanged();

        // Клиент получает инвентарь целиком сразу, не дожидаясь сверки по ячейкам
        handle.inventoryMenu.sendAllDataToRemote();
        if (handle.containerMenu != handle.inventoryMenu) {
            handle.containerMenu.sendAllDataToRemote();
        }
    }

    private boolean isAttached(Player player, SharedInventory inventory) throws IllegalAccessException {
        Inventory nms = handle(player).getInventory();
        return items(nms) == inventory.items && equipment(nms) == inventory.equipment;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    private void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        SharedInventory inventory = assigned.get(player.getUniqueId());
        if (inventory == null) return;

        if (links.component(LinkType.INVENTORY, player.getUniqueId()).size() < 2) {
            assigned.remove(player.getUniqueId());
            return;
        }

        try {
            // Загруженное с диска — снимок на момент выхода, общий список новее
            attach(player, inventory);
        } catch (IllegalAccessException e) {
            plugin.getLogger().log(Level.SEVERE, "Не удалось вернуть общий инвентарь " + player.getName(), e);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    private void onPostRespawn(PlayerPostRespawnEvent event) {
        Player player = event.getPlayer();
        SharedInventory inventory = assigned.get(player.getUniqueId());
        if (inventory == null) return;

        try {
            // Сервер переиспользует игрока, но если кто-то подменил инвентарь — вернуть общий
            if (!isAttached(player, inventory)) reconcile(links.component(LinkType.INVENTORY, player.getUniqueId()),
                    Collections.newSetFromMap(new IdentityHashMap<>()));
        } catch (IllegalAccessException e) {
            plugin.getLogger().log(Level.SEVERE, "Не удалось вернуть общий инвентарь " + player.getName(), e);
        }
    }

    public boolean isShared(Player player) {
        return assigned.containsKey(player.getUniqueId());
    }

    /**
     * При выключении плагина каждый онлайн-игрок получает свою копию: иначе списки остались бы
     * общими до перезахода, а связи уже нет.
     */
    public void shutdown() {
        for (Map.Entry<UUID, SharedInventory> entry : assigned.entrySet()) {
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player == null) continue;

            try {
                if (isAttached(player, entry.getValue())) attach(player, entry.getValue().copy());
            } catch (IllegalAccessException e) {
                plugin.getLogger().log(Level.SEVERE, "Не удалось отвязать инвентарь " + player.getName(), e);
            }
        }
        assigned.clear();
    }

    private static ServerPlayer handle(Player player) {
        return ((CraftPlayer) player).getHandle();
    }

    @SuppressWarnings("unchecked")
    private static NonNullList<ItemStack> items(Inventory inventory) throws IllegalAccessException {
        return (NonNullList<ItemStack>) INVENTORY_ITEMS.get(inventory);
    }

    @SuppressWarnings("unchecked")
    private static EnumMap<EquipmentSlot, ItemStack> equipment(Inventory inventory) throws IllegalAccessException {
        return (EnumMap<EquipmentSlot, ItemStack>) EQUIPMENT_ITEMS.get(inventory.equipment);
    }

    /**
     * Поле по имени из мэппингов Mojang, а если его переименуют — по типу: в обоих классах
     * поле нужного типа одно.
     */
    private static Field findField(Class<?> owner, String name, Class<?> type) {
        try {
            Field field = owner.getDeclaredField(name);
            if (type.isAssignableFrom(field.getType())) {
                field.setAccessible(true);
                return field;
            }
        } catch (NoSuchFieldException ignored) {
            // ниже поиск по типу
        }

        for (Field field : owner.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers())) continue;
            if (!type.isAssignableFrom(field.getType())) continue;

            field.setAccessible(true);
            return field;
        }
        throw new IllegalStateException("Нет поля " + type.getSimpleName() + " в " + owner.getName());
    }

    /**
     * Списки, которые делят игроки группы. Сравнение по ссылке, а не по содержимому:
     * два разных инвентаря с одинаковыми вещами — всё равно разные инвентари.
     */
    private static final class SharedInventory {

        final NonNullList<ItemStack> items;
        final EnumMap<EquipmentSlot, ItemStack> equipment;

        SharedInventory(NonNullList<ItemStack> items, EnumMap<EquipmentSlot, ItemStack> equipment) {
            this.items = items;
            this.equipment = equipment;
        }

        SharedInventory copy() {
            NonNullList<ItemStack> itemsCopy = NonNullList.withSize(items.size(), ItemStack.EMPTY);
            for (int i = 0; i < items.size(); i++) {
                itemsCopy.set(i, items.get(i).copy());
            }

            EnumMap<EquipmentSlot, ItemStack> equipmentCopy = new EnumMap<>(EquipmentSlot.class);
            equipment.forEach((slot, stack) -> equipmentCopy.put(slot, stack.copy()));
            return new SharedInventory(itemsCopy, equipmentCopy);
        }
    }
}
