package me.bbijabnpobatejb.multitogether.settings;

import me.bbijabnpobatejb.multitogether.i18n.Lang;
import me.bbijabnpobatejb.multitogether.util.Compat;
import org.bukkit.Material;

import java.util.Locale;

/**
 * Из какого блока собрана цепь. Материалы ищутся по имени: железная цепь до 1.21.9 называлась
 * CHAIN, а медных до 1.21.9 нет вовсе — на таких серверах их стилей просто нет в меню.
 */
public enum ChainStyle {

    IRON("IRON_CHAIN", "CHAIN"),
    COPPER("COPPER_CHAIN"),
    EXPOSED("EXPOSED_COPPER_CHAIN"),
    WEATHERED("WEATHERED_COPPER_CHAIN"),
    OXIDIZED("OXIDIZED_COPPER_CHAIN");

    private final String[] names;
    private Material material;
    private boolean resolved;

    ChainStyle(String... names) {
        this.names = names;
    }

    /**
     * Блок цепи. Для железной есть всегда; для медных — {@code null}, если сервер старше 1.21.9.
     */
    public Material getMaterial() {
        if (!resolved) {
            material = Compat.material(names);
            resolved = true;
        }
        return material;
    }

    public boolean isAvailable() {
        return getMaterial() != null;
    }

    public String displayName() {
        return Lang.plain("styles." + name().toLowerCase(Locale.ROOT));
    }

    /**
     * Следующий стиль, который есть на этом сервере.
     */
    public ChainStyle next() {
        ChainStyle[] values = values();
        for (int step = 1; step <= values.length; step++) {
            ChainStyle candidate = values[(ordinal() + step) % values.length];
            if (candidate.isAvailable()) return candidate;
        }
        return this;
    }
}
