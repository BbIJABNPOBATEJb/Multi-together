package me.bbijabnpobatejb.multitogether.util;

import com.destroystokyo.paper.profile.PlayerProfile;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.List;

public final class ItemBuilder {

    private final ItemStack item;
    private final List<Component> lore = new ArrayList<>();
    private Component name;
    private boolean glint;
    private Player headOwner;

    private ItemBuilder(Material material) {
        this.item = new ItemStack(material);
    }

    public static ItemBuilder of(Material material) {
        return new ItemBuilder(material);
    }

    public static ItemBuilder head(Player owner) {
        ItemBuilder builder = new ItemBuilder(Material.PLAYER_HEAD);
        builder.headOwner = owner;
        return builder;
    }

    public ItemBuilder name(String miniMessage) {
        this.name = Msg.item(miniMessage);
        return this;
    }

    public ItemBuilder lore(String... lines) {
        for (String line : lines) {
            lore.add(Msg.item(line));
        }
        return this;
    }

    public ItemBuilder lore(List<String> lines) {
        lore.addAll(Msg.lore(lines));
        return this;
    }

    public ItemBuilder amount(int amount) {
        // У ножниц и прочего с прочностью стак — один предмет, число на них не нарисовать
        item.setAmount(Math.clamp(amount, 1, item.getMaxStackSize()));
        return this;
    }

    public ItemBuilder glint(boolean glint) {
        this.glint = glint;
        return this;
    }

    public ItemStack build() {
        ItemMeta meta = item.getItemMeta();
        if (name != null) meta.displayName(name);
        if (!lore.isEmpty()) meta.lore(lore);
        if (glint) meta.setEnchantmentGlintOverride(true);
        meta.addItemFlags(ItemFlag.values());

        if (headOwner != null && meta instanceof SkullMeta skull) {
            PlayerProfile profile = headOwner.getPlayerProfile();
            // С текстурами голова рисуется сразу. Без них (offline-mode без скинов) сервер полез бы
            // за скином к Mojang на каждую перерисовку меню и упёрся бы в 429 — пусть будет Стив
            if (profile.hasTextures()) skull.setPlayerProfile(profile);
        }

        item.setItemMeta(meta);
        return item;
    }
}
