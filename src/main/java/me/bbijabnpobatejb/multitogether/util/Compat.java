package me.bbijabnpobatejb.multitogether.util;

import lombok.experimental.UtilityClass;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Display;
import org.bukkit.inventory.meta.ItemMeta;

import java.lang.reflect.Method;

/**
 * Всё, что между Paper 1.20 и 26.3 появилось, пропало или переименовано.
 *
 * <p>Плагин компилируется против API 1.20.1, поэтому новое вызывается рефлексией и только
 * если оно есть. Ссылок на константы, которых нет в какой-то из версий, в коде нет: такая
 * ссылка падает {@link NoSuchFieldError} при первом же обращении к классу.</p>
 */
@UtilityClass
public class Compat {

    /**
     * {@code Display#setTeleportDuration} — с 1.20.2. Без него звенья двигаются рывками по тикам.
     */
    private final Method TELEPORT_DURATION = method(Display.class, "setTeleportDuration", int.class);

    /**
     * {@code ItemMeta#setEnchantmentGlintOverride} — с 1.20.5. Раньше блеск — через зачарование.
     */
    private final Method GLINT_OVERRIDE = method(ItemMeta.class, "setEnchantmentGlintOverride", Boolean.class);

    private Method method(Class<?> owner, String name, Class<?>... parameters) {
        try {
            return owner.getMethod(name, parameters);
        } catch (NoSuchMethodException e) {
            return null;
        }
    }

    /**
     * Первый материал из списка, который есть на этом сервере: цепь до 1.21.9 называлась CHAIN.
     */
    public Material material(String... names) {
        for (String name : names) {
            Material material = Material.matchMaterial(name);
            if (material != null) return material;
        }
        return null;
    }

    public boolean hasTeleportDuration() {
        return TELEPORT_DURATION != null;
    }

    public void setTeleportDuration(Display display, int ticks) {
        if (TELEPORT_DURATION == null) return;
        try {
            TELEPORT_DURATION.invoke(display, ticks);
        } catch (ReflectiveOperationException ignored) {
            // без сглаживания — не страшно
        }
    }

    public void glint(ItemMeta meta) {
        try {
            if (GLINT_OVERRIDE != null) {
                GLINT_OVERRIDE.invoke(meta, Boolean.TRUE);
                return;
            }
            // До 1.20.5: невидимое зачарование. getByKey — рефлексией: позже Enchantment стал
            // интерфейсом, и прямой вызов статического метода класса там бы уже не слинковался
            Object enchantment = Enchantment.class.getMethod("getByKey", NamespacedKey.class)
                    .invoke(null, NamespacedKey.minecraft("unbreaking"));
            if (enchantment instanceof Enchantment unbreaking) meta.addEnchant(unbreaking, 1, true);
        } catch (ReflectiveOperationException ignored) {
            // без блеска — не страшно
        }
    }

    /**
     * Максимум здоровья. Атрибут переименован в 1.21.3 (GENERIC_MAX_HEALTH → MAX_HEALTH) и стал
     * интерфейсом, а этот метод есть во всех версиях.
     */
    @SuppressWarnings("deprecation")
    public double maxHealth(org.bukkit.entity.LivingEntity entity) {
        return entity.getMaxHealth();
    }

    public int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    public double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    public float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
