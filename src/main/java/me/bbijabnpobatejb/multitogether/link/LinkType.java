package me.bbijabnpobatejb.multitogether.link;

import lombok.Getter;
import me.bbijabnpobatejb.multitogether.i18n.Lang;
import me.bbijabnpobatejb.multitogether.util.Compat;
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

    CHAIN("CHAIN", "chain", "⛓", new String[]{"IRON_CHAIN", "CHAIN"},
            List.of("chain", "chains", "цепь", "цепи", "цепью", "цеп")),
    HEALTH("HP", "health", "❤", new String[]{"RED_DYE"},
            List.of("hp", "health", "heal", "life", "хп", "здоровье", "жизнь", "жизни", "хелс")),
    FOOD("FOOD", "food", "☕", new String[]{"COOKED_BEEF"},
            List.of("food", "hunger", "еда", "голод", "сытость", "фуд")),
    INVENTORY("INVENTORY", "inventory", "⚒", new String[]{"CHEST"},
            List.of("inventory", "inv", "items", "инвентарь", "инв", "вещи"));

    /**
     * Как вид пишется в команде и в подсказках.
     */
    private final String key;
    /**
     * Раздел в файле перевода: {@code types.<langKey>.name}.
     */
    private final String langKey;
    /**
     * Значок в чате и меню. Только из BMP: эмодзи вроде 🍖 шрифт Minecraft не рисует.
     */
    private final String icon;
    /**
     * Предмет в меню — по имени: цепь до 1.21.9 называлась CHAIN.
     */
    @Getter(lombok.AccessLevel.NONE)
    private final String[] materials;
    private final List<String> aliases;

    LinkType(String key, String langKey, String icon, String[] materials, List<String> aliases) {
        this.key = key;
        this.langKey = langKey;
        this.icon = icon;
        this.materials = materials;
        this.aliases = aliases;
    }

    public Material getMaterial() {
        Material material = Compat.material(materials);
        return material == null ? Material.PAPER : material;
    }

    /**
     * Название внутри фразы: «цепь», «здоровье».
     */
    public String displayName() {
        return Lang.plain("types." + langKey + ".name");
    }

    /**
     * Заголовок: «Цепь», «Здоровье».
     */
    public String title() {
        return Lang.plain("types." + langKey + ".title");
    }

    public String description() {
        return Lang.plain("types." + langKey + ".description");
    }

    public static LinkType byAlias(String input) {
        String normalized = input.toLowerCase(Locale.ROOT).trim();
        for (LinkType type : values()) {
            if (type.key.equalsIgnoreCase(normalized) || type.name().equalsIgnoreCase(normalized)) return type;
            if (type.aliases.contains(normalized)) return type;
            // Название на языке сервера тоже годится: «kette», «chaîne»
            if (type.displayName().toLowerCase(Locale.ROOT).equals(normalized)) return type;
        }
        return null;
    }
}
