package me.bbijabnpobatejb.multitogether.gui.menu;

import me.bbijabnpobatejb.multitogether.MultiTogether;
import me.bbijabnpobatejb.multitogether.gui.Gui;
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
 * Настройки цепи. Меняются сразу для всех цепей и живут до перезапуска сервера.
 *
 * <pre>
 *  10 −1   11 −0.5   13 длина    15 +0.5   16 +1
 *  19 −0.1 20 −0.05  22 жёсткость 24 +0.05 25 +0.1
 *  28 вид  29 видимость  30 упор присевшего  32 возрождение  33 телепорты  34 звуки
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
        super(viewer, 6, Msg.mm("<" + Msg.MAIN + ">⛓</" + Msg.MAIN + "> <dark_gray>Настройки цепи"));
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
                .name("<" + Msg.MAIN + ">Настройки цепи")
                .lore("", "<gray>Действуют на все цепи сразу", "<gray>и сбрасываются перезапуском сервера")
                .build());

        drawLength();
        drawStiffness();
        drawToggles();

        setItem(45, ItemBuilder.of(Material.WATER_BUCKET)
                        .name("<" + Msg.YELLOW + ">Сбросить настройки")
                        .lore("", "<" + Msg.YELLOW + ">Shift+клик</" + Msg.YELLOW + "> <gray>— подтвердить")
                        .build(),
                (p, c) -> {
                    if (!c.isShiftClick()) {
                        p.sendMessage(Msg.warn("Чтобы сбросить настройки, нажми с Shift"));
                        return;
                    }
                    settings.reset();
                    draw();
                });

        setItem(49, ItemBuilder.of(Material.ARROW).name("<white>‹ Назад").build(),
                (p, c) -> switchTo(new LinkMenu(plugin, p, selected, modes, page)));

        fill(ItemBuilder.of(Material.GRAY_STAINED_GLASS_PANE).name(" ").build(), 0, 53);
    }

    private void drawLength() {
        double length = settings.getChainLength();
        setItem(13, ItemBuilder.of(settings.getStyle().getMaterial())
                .name("<" + Msg.MAIN + ">Длина цепи: <white>" + LinkActions.format(length) + " бл.")
                .lore("",
                        "<gray>Расстояние между поясами игроков,",
                        "<gray>на котором цепь натягивается.",
                        "",
                        "<gray>От <white>" + LinkActions.format(Settings.MIN_LENGTH) + "</white> до <white>"
                                + LinkActions.format(Settings.MAX_LENGTH) + "</white>, по умолчанию <white>"
                                + LinkActions.format(Settings.DEFAULT_LENGTH))
                .amount((int) Math.round(length))
                .build());

        step(10, false, "−1 блок", () -> settings.setChainLength(length - 1));
        step(11, false, "−0.5 блока", () -> settings.setChainLength(length - 0.5));
        step(15, true, "+0.5 блока", () -> settings.setChainLength(length + 0.5));
        step(16, true, "+1 блок", () -> settings.setChainLength(length + 1));
    }

    private void drawStiffness() {
        double stiffness = settings.getStiffness();
        int percent = (int) Math.round(stiffness * 100);
        setItem(22, ItemBuilder.of(Material.PISTON)
                .name("<" + Msg.MAIN + ">Жёсткость: <white>" + percent + "%")
                .lore("",
                        "<gray>Сколько перетяжки цепь выбирает за тик.",
                        "<gray>Мягче — пружинит, жёстче — дёргает.",
                        "",
                        "<gray>По умолчанию <white>" + Math.round(Settings.DEFAULT_STIFFNESS * 100) + "%")
                .build());

        step(19, false, "−10%", () -> settings.setStiffness(stiffness - 0.1));
        step(20, false, "−5%", () -> settings.setStiffness(stiffness - 0.05));
        step(24, true, "+5%", () -> settings.setStiffness(stiffness + 0.05));
        step(25, true, "+10%", () -> settings.setStiffness(stiffness + 0.1));
    }

    private void drawToggles() {
        ChainStyle style = settings.getStyle();
        setItem(28, ItemBuilder.of(style.getMaterial())
                        .name("<" + Msg.MAIN + ">Вид цепи: <white>" + style.getDisplayName())
                        .lore("", "<gray>Нажми, чтобы сменить")
                        .build(),
                (p, c) -> {
                    settings.setStyle(style.next());
                    draw();
                });

        toggle(29, Material.ENDER_EYE, "Цепь видна",
                "Звенья из блоков-отображений. Физика работает и без них.",
                settings.isChainVisible(), settings::setChainVisible);
        toggle(30, Material.LEATHER_BOOTS, "Упор присевшего",
                "Присевший на земле тянется в разы слабее: так держат висящего.",
                settings.isSneakAnchor(), settings::setSneakAnchor);
        toggle(32, Material.RED_BED, "Возрождение рядом",
                "Умерший возрождается у живого напарника по цепи.",
                settings.isRespawnTogether(), settings::setRespawnTogether);
        toggle(33, Material.ENDER_PEARL, "Телепорт вместе",
                "Портал, смена мира или телепорт далеко переносит всю цепочку.",
                settings.isTeleportTogether(), settings::setTeleportTogether);
        toggle(34, Material.NOTE_BLOCK, "Звуки цепи",
                "Звон, когда цепь резко натягивается.",
                settings.isSounds(), settings::setSounds);
    }

    private void step(int slot, boolean plus, String label, Runnable action) {
        ItemStack item = ItemBuilder.of(plus ? Material.LIME_STAINED_GLASS_PANE : Material.RED_STAINED_GLASS_PANE)
                .name((plus ? "<" + Msg.GREEN + ">" : "<" + Msg.RED + ">") + label)
                .build();
        setItem(slot, item, (p, c) -> {
            action.run();
            draw();
        });
    }

    private void toggle(int slot, Material material, String name, String description, boolean value, Consumer<Boolean> setter) {
        setItem(slot, ItemBuilder.of(material)
                        .name("<" + Msg.MAIN + ">" + name)
                        .lore("",
                                "<gray>" + description,
                                "",
                                value ? "<" + Msg.GREEN + ">● Включено" : "<" + Msg.RED + ">○ Выключено")
                        .glint(value)
                        .build(),
                (p, c) -> {
                    setter.accept(!value);
                    draw();
                });
    }
}
