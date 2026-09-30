package me.bbijabnpobatejb.multitogether.sync;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Доступ к спискам предметов игрока внутри сервера — одинаковый для Paper 1.20–26.3.
 *
 * <p>Имён полей не знаем: в 1.20–1.20.4 сервер в рантайме на мэппингах Spigot, дальше — на
 * Mojang. Поэтому поля ищутся по типу и содержимому. Раскладок две:</p>
 * <ul>
 *     <li>до 1.21.4: в инвентаре три списка — 36 ячеек, 4 брони, 1 вторая рука — и список
 *     из этих трёх («compartments»), по которому сервер ходит во всех операциях;</li>
 *     <li>с 1.21.5: список из 36 ячеек и объект экипировки, общий с сущностью игрока,
 *     а в нём EnumMap слотов брони и второй руки.</li>
 * </ul>
 *
 * <p>Предметы копируются и читаются через {@code CraftItemStack.asBukkitCopy / asNMSCopy}:
 * у этих методов CraftBukkit имена во всех версиях одни.</p>
 */
final class InventoryAccess {

    /**
     * Хранилище предметов игрока: сами объекты сервера, по ссылке.
     */
    static final class Storage {
        final List<Object> items;
        final List<Object> armor;
        final List<Object> offhand;
        final Map<Object, Object> equipment;

        Storage(List<Object> items, List<Object> armor, List<Object> offhand, Map<Object, Object> equipment) {
            this.items = items;
            this.armor = armor;
            this.offhand = offhand;
            this.equipment = equipment;
        }
    }

    private static final String[] ARMOR_SLOTS = {"FEET", "LEGS", "CHEST", "HEAD"};

    private final Field items;
    private final Field armor;
    private final Field offhand;
    private final Field compartments;
    private final Field equipmentHolder;
    private final Field equipmentMap;

    private final Method withSize;
    private final Object emptyStack;
    private final Method asBukkitCopy;
    private final Method asNmsCopy;

    private InventoryAccess(Field items, Field armor, Field offhand, Field compartments,
                            Field equipmentHolder, Field equipmentMap,
                            Method withSize, Object emptyStack, Method asBukkitCopy, Method asNmsCopy) {
        this.items = items;
        this.armor = armor;
        this.offhand = offhand;
        this.compartments = compartments;
        this.equipmentHolder = equipmentHolder;
        this.equipmentMap = equipmentMap;
        this.withSize = withSize;
        this.emptyStack = emptyStack;
        this.asBukkitCopy = asBukkitCopy;
        this.asNmsCopy = asNmsCopy;
    }

    /**
     * Разбирает устройство инвентаря по живому игроку. Бросает исключение, если раскладка
     * незнакомая: тогда общий инвентарь выключается, остальное работает.
     */
    static InventoryAccess inspect(Player player) throws ReflectiveOperationException {
        Object inventory = nmsInventory(player);

        Field items = null, armor = null, offhand = null, compartments = null;
        Field equipmentHolder = null, equipmentMap = null;

        for (Class<?> type = inventory.getClass(); type != null && type != Object.class; type = type.getSuperclass()) {
            for (Field field : type.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers())) continue;
                field.setAccessible(true);
                Object value = field.get(inventory);
                if (value == null) continue;

                if (value instanceof List<?> list && isServerClass(list.getClass())) {
                    // NonNullList: различаются по размеру
                    if (list.size() == 36 && items == null) items = field;
                    else if (list.size() == 4 && armor == null) armor = field;
                    else if (list.size() == 1 && offhand == null) offhand = field;
                } else if (value instanceof List<?> list && !list.isEmpty()
                        && list.get(0) instanceof List<?> first && isServerClass(first.getClass())) {
                    compartments = field;
                } else if (isServerClass(value.getClass()) && value.getClass().getSimpleName().contains("Equipment")) {
                    Field map = singleEnumMap(value.getClass());
                    if (map != null) {
                        equipmentHolder = field;
                        equipmentMap = map;
                    }
                }
            }
        }

        if (items == null) throw new IllegalStateException("No 36-slot item list in " + inventory.getClass().getName());
        boolean legacy = armor != null && offhand != null && compartments != null;
        if (!legacy && equipmentHolder == null) {
            throw new IllegalStateException("Unknown armor layout in " + inventory.getClass().getName());
        }
        if (legacy) {
            equipmentHolder = null;
            equipmentMap = null;
        }

        Class<?> listClass = items.get(inventory).getClass();
        Method withSize = null;
        for (Method method : listClass.getDeclaredMethods()) {
            Class<?>[] parameters = method.getParameterTypes();
            if (Modifier.isStatic(method.getModifiers()) && parameters.length == 2
                    && parameters[0] == int.class && parameters[1] == Object.class
                    && listClass.isAssignableFrom(method.getReturnType())) {
                withSize = method;
            }
        }
        if (withSize == null) throw new IllegalStateException("No withSize in " + listClass.getName());
        withSize.setAccessible(true);

        // Класс стака — по самим ячейкам: у CraftItemStack с 26.2 несколько перегрузок
        // asBukkitCopy (ItemStack, ItemInstance, ItemStackTemplate), по ним тип не угадать
        Class<?> nmsStack = ((List<?>) items.get(inventory)).get(0).getClass();

        Class<?> craftItemStack = Class.forName(Bukkit.getServer().getClass().getPackage().getName() + ".inventory.CraftItemStack");
        Method asBukkitCopy = null;
        Method asNmsCopy = null;
        for (Method method : craftItemStack.getMethods()) {
            if (!Modifier.isStatic(method.getModifiers()) || method.getParameterCount() != 1) continue;
            Class<?> parameter = method.getParameterTypes()[0];

            if (method.getName().equals("asBukkitCopy") && parameter.isAssignableFrom(nmsStack)) {
                // точное совпадение типа важнее общего интерфейса
                if (asBukkitCopy == null || parameter == nmsStack) asBukkitCopy = method;
            }
            if (method.getName().equals("asNMSCopy") && parameter == ItemStack.class
                    && nmsStack.isAssignableFrom(method.getReturnType())) {
                asNmsCopy = method;
            }
        }
        if (asBukkitCopy == null || asNmsCopy == null) throw new IllegalStateException("No CraftItemStack copy methods");

        Object emptyStack = null;
        for (Class<?> type = nmsStack; type != null && emptyStack == null; type = type.getSuperclass()) {
            for (Field field : type.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers()) && field.getType() == nmsStack) {
                    field.setAccessible(true);
                    emptyStack = field.get(null);
                    break;
                }
            }
        }
        // С 26.2 EMPTY объявлен в интерфейсе ItemInstance
        for (Class<?> type : nmsStack.getInterfaces()) {
            if (emptyStack != null) break;
            for (Field field : type.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers()) && field.getType() == nmsStack) {
                    field.setAccessible(true);
                    emptyStack = field.get(null);
                    break;
                }
            }
        }
        if (emptyStack == null) throw new IllegalStateException("No empty item stack in " + nmsStack.getName());

        return new InventoryAccess(items, armor, offhand, compartments, equipmentHolder, equipmentMap,
                withSize, emptyStack, asBukkitCopy, asNmsCopy);
    }

    /**
     * Единственное EnumMap-поле в классе или его предках: у игрока экипировка — подкласс, а
     * карта слотов объявлена в родителе.
     */
    private static Field singleEnumMap(Class<?> type) {
        Field found = null;
        for (Class<?> current = type; current != null && current != Object.class; current = current.getSuperclass()) {
            for (Field field : current.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers()) || !EnumMap.class.isAssignableFrom(field.getType())) continue;
                if (found != null) return null;
                found = field;
            }
        }
        if (found != null) found.setAccessible(true);
        return found;
    }

    private static boolean isServerClass(Class<?> type) {
        return type.getName().startsWith("net.minecraft.");
    }

    private static Object nmsInventory(Player player) throws ReflectiveOperationException {
        Object craftInventory = player.getInventory();
        return craftInventory.getClass().getMethod("getInventory").invoke(craftInventory);
    }

    boolean isLegacy() {
        return equipmentHolder == null;
    }

    @SuppressWarnings("unchecked")
    Storage read(Player player) throws ReflectiveOperationException {
        Object inventory = nmsInventory(player);
        if (isLegacy()) {
            return new Storage((List<Object>) items.get(inventory), (List<Object>) armor.get(inventory),
                    (List<Object>) offhand.get(inventory), null);
        }
        Object holder = equipmentHolder.get(inventory);
        return new Storage((List<Object>) items.get(inventory), null, null, (Map<Object, Object>) equipmentMap.get(holder));
    }

    /**
     * Подставляет игроку хранилище. Клиент получает инвентарь целиком.
     */
    void write(Player player, Storage storage) throws ReflectiveOperationException {
        Object inventory = nmsInventory(player);
        items.set(inventory, storage.items);
        if (isLegacy()) {
            armor.set(inventory, storage.armor);
            offhand.set(inventory, storage.offhand);
            // Все операции сервера идут по этому списку списков — он должен смотреть на новые
            compartments.set(inventory, List.of(storage.items, storage.armor, storage.offhand));
        } else {
            equipmentMap.set(equipmentHolder.get(inventory), storage.equipment);
        }
        player.updateInventory();
    }

    boolean isAttached(Player player, Storage storage) throws ReflectiveOperationException {
        return read(player).items == storage.items;
    }

    @SuppressWarnings("unchecked")
    private List<Object> newList(int size) throws ReflectiveOperationException {
        return (List<Object>) withSize.invoke(null, size, emptyStack);
    }

    /**
     * Пустое хранилище той же раскладки.
     */
    Storage empty(Storage like) throws ReflectiveOperationException {
        if (isLegacy()) return new Storage(newList(like.items.size()), newList(4), newList(1), null);

        Map<Object, Object> equipment = copyMap(like.equipment);
        equipment.replaceAll((slot, stack) -> emptyStack);
        return new Storage(newList(like.items.size()), null, null, equipment);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Map<Object, Object> copyMap(Map<Object, Object> map) {
        return new EnumMap((EnumMap) map);
    }

    /**
     * Независимая копия: новые списки, новые стаки.
     */
    Storage copy(Storage source) throws ReflectiveOperationException {
        Storage copy = empty(source);
        copyInto(source.items, copy.items);
        if (isLegacy()) {
            copyInto(source.armor, copy.armor);
            copyInto(source.offhand, copy.offhand);
        } else {
            for (Map.Entry<Object, Object> entry : source.equipment.entrySet()) {
                copy.equipment.put(entry.getKey(), copyStack(entry.getValue()));
            }
        }
        return copy;
    }

    private void copyInto(List<Object> from, List<Object> to) throws ReflectiveOperationException {
        for (int i = 0; i < from.size() && i < to.size(); i++) {
            to.set(i, copyStack(from.get(i)));
        }
    }

    private Object copyStack(Object nms) throws ReflectiveOperationException {
        return asNmsCopy.invoke(null, toBukkit(nms));
    }

    ItemStack toBukkit(Object nms) throws ReflectiveOperationException {
        return (ItemStack) asBukkitCopy.invoke(null, nms);
    }

    private Object toNms(ItemStack stack) throws ReflectiveOperationException {
        return asNmsCopy.invoke(null, stack);
    }

    private boolean isEmpty(Object nms) throws ReflectiveOperationException {
        ItemStack stack = toBukkit(nms);
        return stack == null || stack.getType().isAir() || stack.getAmount() <= 0;
    }

    /**
     * Забирает всё из хранилища: экипировку по слотам, остальное списком. Хранилище пустеет.
     */
    Map<String, ItemStack> takeEquipment(Storage storage) throws ReflectiveOperationException {
        Map<String, ItemStack> result = new LinkedHashMap<>();
        if (isLegacy()) {
            for (int i = 0; i < storage.armor.size() && i < ARMOR_SLOTS.length; i++) {
                take(storage.armor, i, ARMOR_SLOTS[i], result);
            }
            take(storage.offhand, 0, "OFFHAND", result);
            return result;
        }
        for (Map.Entry<Object, Object> entry : storage.equipment.entrySet()) {
            if (entry.getValue() == null || isEmpty(entry.getValue())) continue;
            result.put(((Enum<?>) entry.getKey()).name(), toBukkit(entry.getValue()));
            entry.setValue(emptyStack);
        }
        return result;
    }

    private void take(List<Object> list, int index, String slot, Map<String, ItemStack> result) throws ReflectiveOperationException {
        if (index >= list.size() || isEmpty(list.get(index))) return;
        result.put(slot, toBukkit(list.get(index)));
        list.set(index, emptyStack);
    }

    List<ItemStack> takeItems(Storage storage) throws ReflectiveOperationException {
        List<ItemStack> result = new ArrayList<>();
        for (int i = 0; i < storage.items.size(); i++) {
            if (isEmpty(storage.items.get(i))) continue;
            result.add(toBukkit(storage.items.get(i)));
            storage.items.set(i, emptyStack);
        }
        return result;
    }

    /**
     * Кладёт предмет в слот экипировки, если он пуст. Возвращает {@code false}, если занят или
     * такого слота в этой версии нет.
     */
    boolean putEquipment(Storage storage, String slot, ItemStack stack) throws ReflectiveOperationException {
        if (isLegacy()) {
            if (slot.equals("OFFHAND")) return putIfEmpty(storage.offhand, 0, stack);
            for (int i = 0; i < ARMOR_SLOTS.length; i++) {
                if (ARMOR_SLOTS[i].equals(slot)) return putIfEmpty(storage.armor, i, stack);
            }
            return false;
        }
        if (slot.equals("MAINHAND")) return false;
        for (Map.Entry<Object, Object> entry : storage.equipment.entrySet()) {
            if (!((Enum<?>) entry.getKey()).name().equals(slot)) continue;
            if (entry.getValue() != null && !isEmpty(entry.getValue())) return false;
            entry.setValue(toNms(stack));
            return true;
        }
        return false;
    }

    private boolean putIfEmpty(List<Object> list, int index, ItemStack stack) throws ReflectiveOperationException {
        if (index >= list.size() || !isEmpty(list.get(index))) return false;
        list.set(index, toNms(stack));
        return true;
    }

    /**
     * Докладывает стак в основные ячейки: сначала в такие же неполные, потом в пустые.
     * Возвращает то, что не влезло, или {@code null}.
     */
    ItemStack addItem(Storage storage, ItemStack stack) throws ReflectiveOperationException {
        ItemStack rest = stack.clone();
        int max = rest.getMaxStackSize();

        for (int i = 0; i < storage.items.size() && rest.getAmount() > 0 && max > 1; i++) {
            ItemStack existing = toBukkit(storage.items.get(i));
            if (existing == null || existing.getType().isAir() || !existing.isSimilar(rest)) continue;

            int space = max - existing.getAmount();
            if (space <= 0) continue;

            int moved = Math.min(space, rest.getAmount());
            existing.setAmount(existing.getAmount() + moved);
            storage.items.set(i, toNms(existing));
            rest.setAmount(rest.getAmount() - moved);
        }

        for (int i = 0; i < storage.items.size() && rest.getAmount() > 0; i++) {
            if (!isEmpty(storage.items.get(i))) continue;
            storage.items.set(i, toNms(rest));
            return null;
        }
        return rest.getAmount() > 0 ? rest : null;
    }
}
