package me.bbijabnpobatejb.multitogether.gui.menu;

import me.bbijabnpobatejb.multitogether.MultiTogether;
import me.bbijabnpobatejb.multitogether.gui.Gui;
import me.bbijabnpobatejb.multitogether.i18n.Lang;
import me.bbijabnpobatejb.multitogether.link.LinkType;
import me.bbijabnpobatejb.multitogether.util.ItemBuilder;
import me.bbijabnpobatejb.multitogether.util.Msg;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Выбор языка: 18 встроенных и те, что положили в папку lang. Язык сохраняется в config.yml.
 *
 * <pre>
 *  10..16, 19..25, 28..34, 37..43 — языки
 *  49 назад
 * </pre>
 */
public class LanguageMenu extends Gui {

    private static final int[] SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43
    };

    private final MultiTogether plugin;
    private final List<UUID> selected;
    private final Set<LinkType> modes;
    private final int page;

    LanguageMenu(MultiTogether plugin, Player viewer, List<UUID> selected, Set<LinkType> modes, int page) {
        super(viewer, 6, Msg.mm("<" + Msg.MAIN + ">⛓</" + Msg.MAIN + "> <dark_gray>" + Lang.mm("language-menu.title")));
        this.plugin = plugin;
        this.selected = selected;
        this.modes = modes;
        this.page = page;
    }

    @Override
    public void draw() {
        clearInventory();

        List<String> languages = Lang.available();
        for (int i = 0; i < languages.size() && i < SLOTS.length; i++) {
            String code = languages.get(i);
            boolean current = code.equals(Lang.code());

            ItemBuilder item = ItemBuilder.of(current ? Material.ENCHANTED_BOOK : Material.BOOK)
                    .name((current ? "<" + Msg.GREEN + ">" : "<white>") + Msg.name(Lang.name(code)) + " <gray>(" + code + ")")
                    .lore("");
            if (current) {
                item.lore("<" + Msg.GREEN + ">" + Lang.mm("language-menu.current"));
            } else {
                item.hint(Lang.plain("language-menu.select"));
            }

            setItem(SLOTS[i], item.build(), (p, c) -> {
                if (code.equals(Lang.code())) return;

                plugin.setLanguage(code);
                p.sendMessage(Msg.success(Lang.mm("language.changed", "language", Msg.white(Lang.name(code)))));
                // Заголовок окна задаётся при открытии: новое окно — уже на новом языке
                switchTo(new LanguageMenu(plugin, p, selected, modes, page));
            });
        }

        setItem(49, ItemBuilder.of(Material.ARROW).name("<white>" + Lang.mm("settings.back")).build(),
                (p, c) -> switchTo(new SettingsMenu(plugin, p, selected, modes, page)));

        fill(ItemBuilder.of(Material.GRAY_STAINED_GLASS_PANE).name(" ").build(), 0, 53);
    }
}
