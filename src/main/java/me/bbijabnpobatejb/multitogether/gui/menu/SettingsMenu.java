package me.bbijabnpobatejb.multitogether.gui.menu;

import me.bbijabnpobatejb.multitogether.MultiTogether;
import me.bbijabnpobatejb.multitogether.gui.Gui;
import me.bbijabnpobatejb.multitogether.i18n.Lang;
import me.bbijabnpobatejb.multitogether.link.LinkActions;
import me.bbijabnpobatejb.multitogether.link.LinkType;
import me.bbijabnpobatejb.multitogether.settings.ChainStyle;
import me.bbijabnpobatejb.multitogether.settings.Settings;
import me.bbijabnpobatejb.multitogether.util.ItemBuilder;
import me.bbijabnpobatejb.multitogether.util.Msg;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Настройки цепи. Меняются сразу для всех цепей и живут до перезапуска сервера; язык
 * сохраняется в config.yml. Каждый клик отвечает в чат, что именно поменялось.
 *
 * <pre>
 *  10 −1   11 −0.5   13 длина    15 +0.5   16 +1
 *  19 −10% 20 −5%    22 жёсткость 24 +5%   25 +10%
 *  28 вид  29 видимость  30 упор присевшего  32 возрождение  33 телепорты  34 звуки
 *  40 язык
 *  45 сброс                    49 назад
 * </pre>
 */
public class SettingsMenu extends Gui {

    private final MultiTogether plugin;
    private final Settings settings;

    // Состояние главного меню, чтобы вернуться в него как было
    private final List<UUID> selected;
    private final Set<LinkType> modes;
    private final int page;

    public SettingsMenu(MultiTogether plugin, Player viewer) {
        this(plugin, viewer, new ArrayList<>(), EnumSet.allOf(LinkType.class), 0);
    }

    SettingsMenu(MultiTogether plugin, Player viewer, List<UUID> selected, Set<LinkType> modes, int page) {
        super(viewer, 6, Msg.mm("<" + Msg.MAIN + ">⛓</" + Msg.MAIN + "> <dark_gray>" + Lang.mm("settings.title")));
        this.plugin = plugin;
        this.settings = plugin.getSettings();
        this.selected = selected;
        this.modes = modes;
        this.page = page;
    }

    @Override
    public void draw() {
        clearInventory();

        setItem(4, ItemBuilder.of(Material.COMPARATOR)
                .name("<" + Msg.MAIN + ">" + Lang.mm("settings.title"))
                .lore("")
                .description(Lang.plain("settings.info"))
                .build());

        drawLength();
        drawStiffness();
        drawToggles();

        setItem(40, ItemBuilder.of(Material.WRITABLE_BOOK)
                        .name("<" + Msg.MAIN + ">" + Lang.mm("settings.language",
                                "language", Msg.white(Lang.name(Lang.code()))))
                        .lore("")
                        .description(Lang.plain("settings.language-hint"))
                        .lore("")
                        .hint(Lang.plain("settings.click-to-change"))
                        .build(),
                (p, c) -> switchTo(new LanguageMenu(plugin, p, selected, modes, page)));

        setItem(45, ItemBuilder.of(Material.WATER_BUCKET)
                        .name("<" + Msg.YELLOW + ">" + Lang.mm("settings.reset"))
                        .lore("")
                        .hint(Lang.plain("menu.shift-confirm"))
                        .build(),
                (p, c) -> {
                    if (!c.isShiftClick()) {
                        p.sendMessage(Msg.warn(Lang.mm("settings.reset-shift")));
                        return;
                    }
                    settings.reset();
                    p.sendMessage(Msg.success(Lang.mm("settings.reset-done")));
                    draw();
                });

        setItem(49, ItemBuilder.of(Material.ARROW).name("<white>" + Lang.mm("settings.back")).build(),
                (p, c) -> switchTo(new LinkMenu(plugin, p, selected, modes, page)));

        fill(ItemBuilder.of(Material.GRAY_STAINED_GLASS_PANE).name(" ").build(), 0, 53);
    }

    private void drawLength() {
        double length = settings.getChainLength();
        setItem(13, ItemBuilder.of(settings.getStyle().getMaterial())
                .name("<" + Msg.MAIN + ">" + Lang.mm("settings.length", "length", Msg.white(LinkActions.format(length))))
                .lore("")
                .description(Lang.plain("settings.length-hint"))
                .lore("")
                .description(Lang.plain("settings.length-range",
                        "min", LinkActions.format(Settings.MIN_LENGTH),
                        "max", LinkActions.format(Settings.MAX_LENGTH),
                        "default", LinkActions.format(Settings.DEFAULT_LENGTH)))
                .amount((int) Math.round(length))
                .build());

        lengthStep(10, -1);
        lengthStep(11, -0.5);
        lengthStep(15, 0.5);
        lengthStep(16, 1);
    }

    private void lengthStep(int slot, double delta) {
        String label = Lang.plain("settings.length-step", "delta", signed(delta));
        step(slot, delta > 0, label, player -> {
            settings.setChainLength(settings.getChainLength() + delta);
            player.sendMessage(Msg.info(Lang.mm("settings.length-changed",
                    "length", Msg.white(LinkActions.format(settings.getChainLength())))));
        });
    }

    private void drawStiffness() {
        setItem(22, ItemBuilder.of(Material.PISTON)
                .name("<" + Msg.MAIN + ">" + Lang.mm("settings.stiffness", "value", Msg.white(String.valueOf(percent()))))
                .lore("")
                .description(Lang.plain("settings.stiffness-hint"))
                .lore("")
                .description(Lang.plain("settings.stiffness-default",
                        "value", String.valueOf(Math.round(Settings.DEFAULT_STIFFNESS * 100))))
                .build());

        stiffnessStep(19, -10);
        stiffnessStep(20, -5);
        stiffnessStep(24, 5);
        stiffnessStep(25, 10);
    }

    private void stiffnessStep(int slot, int deltaPercent) {
        String label = Lang.plain("settings.stiffness-step", "delta", signed(deltaPercent));
        step(slot, deltaPercent > 0, label, player -> {
            settings.setStiffness(settings.getStiffness() + deltaPercent / 100.0);
            player.sendMessage(Msg.info(Lang.mm("settings.stiffness-changed",
                    "value", Msg.white(String.valueOf(percent())))));
        });
    }

    private long percent() {
        return Math.round(settings.getStiffness() * 100);
    }

    private void drawToggles() {
        ChainStyle style = settings.getStyle();
        setItem(28, ItemBuilder.of(style.getMaterial())
                        .name("<" + Msg.MAIN + ">" + Lang.mm("settings.style", "style", Msg.white(style.displayName())))
                        .lore("")
                        .hint(Lang.plain("settings.click-to-change"))
                        .build(),
                (p, c) -> {
                    settings.setStyle(style.next());
                    p.sendMessage(Msg.info(Lang.mm("settings.style-changed",
                            "style", Msg.white(settings.getStyle().displayName()))));
                    draw();
                });

        toggle(29, Material.ENDER_EYE, "settings.visible", settings.isChainVisible(), settings::setChainVisible);
        toggle(30, Material.LEATHER_BOOTS, "settings.anchor", settings.isSneakAnchor(), settings::setSneakAnchor);
        toggle(32, Material.RED_BED, "settings.respawn", settings.isRespawnTogether(), settings::setRespawnTogether);
        toggle(33, Material.ENDER_PEARL, "settings.teleport", settings.isTeleportTogether(), settings::setTeleportTogether);
        toggle(34, Material.NOTE_BLOCK, "settings.sounds", settings.isSounds(), settings::setSounds);
    }

    private void step(int slot, boolean plus, String label, Consumer<Player> action) {
        ItemStack item = ItemBuilder.of(plus ? Material.LIME_STAINED_GLASS_PANE : Material.RED_STAINED_GLASS_PANE)
                .name((plus ? "<" + Msg.GREEN + ">" : "<" + Msg.RED + ">") + Msg.name(label))
                .build();
        setItem(slot, item, (p, c) -> {
            action.accept(p);
            draw();
        });
    }

    /**
     * Переключатель: название и описание по ключу, {@code <key>-hint} — описание.
     */
    private void toggle(int slot, Material material, String key, boolean value, Consumer<Boolean> setter) {
        setItem(slot, ItemBuilder.of(material)
                        .name("<" + Msg.MAIN + ">" + Lang.mm(key))
                        .lore("")
                        .description(Lang.plain(key + "-hint"))
                        .lore("", value
                                ? "<" + Msg.GREEN + ">" + Lang.mm("menu.enabled")
                                : "<" + Msg.RED + ">" + Lang.mm("menu.disabled"))
                        .glint(value)
                        .build(),
                (p, c) -> {
                    setter.accept(!value);
                    p.sendMessage(Msg.info(Lang.mm(value ? "settings.toggled-off" : "settings.toggled-on",
                            "setting", Msg.white(Lang.plain(key)))));
                    draw();
                });
    }

    private static String signed(double value) {
        String number = LinkActions.format(Math.abs(value));
        return (value < 0 ? "−" : "+") + number;
    }
}
