package me.bbijabnpobatejb.multitogether.settings;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.bukkit.Material;

/**
 * Из какого блока собрана цепь. Медные цепи появились в 1.21.9.
 */
@Getter
@RequiredArgsConstructor
public enum ChainStyle {

    IRON(Material.IRON_CHAIN, "Железная"),
    COPPER(Material.COPPER_CHAIN, "Медная"),
    EXPOSED(Material.EXPOSED_COPPER_CHAIN, "Потемневшая медь"),
    WEATHERED(Material.WEATHERED_COPPER_CHAIN, "Выветренная медь"),
    OXIDIZED(Material.OXIDIZED_COPPER_CHAIN, "Окисленная медь");

    private final Material material;
    private final String displayName;

    public ChainStyle next() {
        ChainStyle[] values = values();
        return values[(ordinal() + 1) % values.length];
    }
}
