package me.bbijabnpobatejb.multitogether.link;

import lombok.Getter;
import org.bukkit.Material;

import java.util.List;
import java.util.Locale;

/**
 * Что именно связывает игроков. Связь по каждому виду — отдельный граф:
 * цепь соединяет пары (A—B—C — это две цепи), а здоровье, голод и инвентарь
 * общие на всю связную группу (A—B и B—C дают один общий инвентарь на троих).
 */
@Getter
public enum LinkType {

    CHAIN("CHAIN", "цепь", "⛓", Material.IRON_CHAIN,
            List.of("chain", "chains", "цепь", "цепи", "цепью", "цеп")),
    HEALTH("HP", "здоровье", "❤", Material.RED_DYE,
            List.of("hp", "health", "heal", "life", "хп", "здоровье", "жизнь", "жизни", "хелс")),
    FOOD("FOOD", "голод", "☕", Material.COOKED_BEEF,
            List.of("food", "hunger", "еда", "голод", "сытость", "фуд")),
    INVENTORY("INVENTORY", "инвентарь", "⚒", Material.CHEST,
            List.of("inventory", "inv", "items", "инвентарь", "инв", "вещи"));

    /**
     * Как вид пишется в команде и в подсказках.
     */
    private final String key;
    private final String displayName;
    /**
     * Значок в чате и меню. Только из BMP: эмодзи вроде 🍖 шрифт Minecraft не рисует.
     */
    private final String icon;
    private final Material material;
    private final List<String> aliases;

    LinkType(String key, String displayName, String icon, Material material, List<String> aliases) {
        this.key = key;
        this.displayName = displayName;
        this.icon = icon;
        this.material = material;
        this.aliases = aliases;
    }

    public static LinkType byAlias(String input) {
        String normalized = input.toLowerCase(Locale.ROOT).trim();
        for (LinkType type : values()) {
            if (type.key.equalsIgnoreCase(normalized) || type.name().equalsIgnoreCase(normalized)) return type;
            if (type.aliases.contains(normalized)) return type;
        }
        return null;
    }
}
