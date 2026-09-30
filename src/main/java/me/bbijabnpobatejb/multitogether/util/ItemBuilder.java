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

    /**
     * Ширина строки описания в символах латиницы: шире подсказка растягивается на пол-экрана.
     */
    public static final int WRAP_WIDTH = 34;

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

    /**
     * Подсказка вида «ЛКМ — выбрать»: до тире — клавиша цветом плагина, после — серым.
     * Длинная переносится, продолжение серое.
     */
    public ItemBuilder hint(String plain) {
        List<String> lines = Msg.wrap(plain, WRAP_WIDTH);
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            int dash = line.indexOf(" — ");
            if (i == 0 && dash > 0) {
                lore.add(Msg.item("<" + Msg.MAIN + ">" + Msg.name(line.substring(0, dash)) + "</" + Msg.MAIN + "> <gray>"
                        + Msg.name(line.substring(dash + 1))));
            } else {
                lore.add(Msg.item("<gray>" + Msg.name(line)));
            }
        }
        return this;
    }

    /**
     * Описание обычным текстом: переносится по словам и красится серым.
     */
    public ItemBuilder description(String plain) {
        for (String line : Msg.wrap(plain, WRAP_WIDTH)) {
            lore.add(Msg.item("<gray>" + Msg.name(line)));
        }
        return this;
    }

    public ItemBuilder amount(int amount) {
        // У ножниц и прочего с прочностью стак — один предмет, число на них не нарисовать
        item.setAmount(Compat.clamp(amount, 1, item.getMaxStackSize()));
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
        if (glint) Compat.glint(meta);
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
