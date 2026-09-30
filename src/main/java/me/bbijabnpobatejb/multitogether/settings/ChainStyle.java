package me.bbijabnpobatejb.multitogether.settings;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import me.bbijabnpobatejb.multitogether.i18n.Lang;
import org.bukkit.Material;

import java.util.Locale;

/**
 * Из какого блока собрана цепь. Медные цепи появились в 1.21.9.
 */
@Getter
@RequiredArgsConstructor
public enum ChainStyle {

    IRON(Material.IRON_CHAIN),
    COPPER(Material.COPPER_CHAIN),
    EXPOSED(Material.EXPOSED_COPPER_CHAIN),
    WEATHERED(Material.WEATHERED_COPPER_CHAIN),
    OXIDIZED(Material.OXIDIZED_COPPER_CHAIN);

    private final Material material;

    public String displayName() {
        return Lang.plain("styles." + name().toLowerCase(Locale.ROOT));
    }

    public ChainStyle next() {
        ChainStyle[] values = values();
        return values[(ordinal() + 1) % values.length];
    }
}
