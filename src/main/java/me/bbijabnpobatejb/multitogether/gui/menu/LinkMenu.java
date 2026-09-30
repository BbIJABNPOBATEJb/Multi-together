package me.bbijabnpobatejb.multitogether.gui.menu;

import me.bbijabnpobatejb.multitogether.MultiTogether;
import me.bbijabnpobatejb.multitogether.gui.Gui;
import me.bbijabnpobatejb.multitogether.link.LinkActions;
import me.bbijabnpobatejb.multitogether.link.LinkType;
import me.bbijabnpobatejb.multitogether.link.LinkTypes;
import me.bbijabnpobatejb.multitogether.util.ItemBuilder;
import me.bbijabnpobatejb.multitogether.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Главное меню: головы онлайн-игроков, переключатели видов связи и кнопки.
 *
 * <pre>
 *  0..35  игроки (36 на страницу), ЛКМ — выбрать, ПКМ — отвязать ото всех
 *  36 ‹   38 цепь  39 здоровье  40 всё  41 голод  42 инвентарь   44 ›
 *  45 выбрать всех  46 сбросить  48 связать  49 сводка  50 разъединить  52 снять всё  53 настройки
 * </pre>
 *
 * Порядок выбора — порядок цепи: выбранные 1, 2, 3 свяжутся цепью 1—2—3.
 */
public class LinkMenu extends Gui {

    private static final int PLAYERS_PER_PAGE = 36;

    private final MultiTogether plugin;
    private final List<UUID> selected;
    private final Set<LinkType> modes;
    private int page;

    public LinkMenu(MultiTogether plugin, Player viewer) {
        this(plugin, viewer, new ArrayList<>(), EnumSet.allOf(LinkType.class), 0);
    }

    LinkMenu(MultiTogether plugin, Player viewer, List<UUID> selected, Set<LinkType> modes, int page) {
        super(viewer, 6, Msg.mm("<" + Msg.MAIN + ">⛓</" + Msg.MAIN + "> <dark_gray>Multi Together"));
        this.plugin = plugin;
        this.selected = selected;
        this.modes = modes;
        this.page = page;
    }

    @Override
    public void onOpen() {
        setUpdater(20);
    }

    @Override
    public void draw() {
        clearInventory();
        selected.removeIf(uuid -> Bukkit.getPlayer(uuid) == null);

        List<Player> online = new ArrayList<>(Bukkit.getOnlinePlayers());
        online.sort(Comparator.comparing(Player::getName, String.CASE_INSENSITIVE_ORDER));

        int pages = Math.max(1, (online.size() + PLAYERS_PER_PAGE - 1) / PLAYERS_PER_PAGE);
        page = Math.clamp(page, 0, pages - 1);

        int from = page * PLAYERS_PER_PAGE;
        int to = Math.min(online.size(), from + PLAYERS_PER_PAGE);
        for (int i = from; i < to; i++) {
            Player target = online.get(i);
            setItem(i - from, head(target), (clicker, click) -> clickPlayer(target, click));
        }

        drawModes();
        drawActions(online, pages);

        ItemStack glass = ItemBuilder.of(Material.GRAY_STAINED_GLASS_PANE).name(" ").build();
        fill(glass, 36, 53);
    }

    private ItemStack head(Player target) {
        int index = selected.indexOf(target.getUniqueId());
        boolean chosen = index >= 0;

        List<String> lore = new ArrayList<>();
        lore.add("");
        lore.addAll(plugin.getActions().describePlayer(target.getUniqueId()));
        lore.add("");
        lore.add("<" + Msg.MAIN + ">ЛКМ</" + Msg.MAIN + "> <gray>— " + (chosen ? "снять выбор" : "выбрать"));
        lore.add("<" + Msg.MAIN + ">ПКМ</" + Msg.MAIN + "> <gray>— отвязать ото всех (выбранные виды)");
        lore.add("<" + Msg.MAIN + ">Shift+ЛКМ</" + Msg.MAIN + "> <gray>— телепортироваться к игроку");

        String name = Msg.name(target.getName());
        return ItemBuilder.head(target)
                .name(chosen
                        ? "<" + Msg.GREEN + ">✔ " + name + " <gray>#" + (index + 1)
                        : "<white>" + name)
                .lore(lore)
                .amount(chosen ? index + 1 : 1)
                .glint(chosen)
                .build();
    }

    private void clickPlayer(Player target, ClickType click) {
        UUID uuid = target.getUniqueId();

        if (click.isShiftClick() && click.isLeftClick()) {
            player.closeInventory();
            player.teleportAsync(target.getLocation());
            return;
        }

        if (click.isRightClick()) {
            if (modes.isEmpty()) {
                player.sendMessage(Msg.error("Не выбран ни один вид связи"));
                return;
            }
            plugin.getActions().unlinkPlayer(player, uuid, LinkTypes.of(modes));
            draw();
            return;
        }

        if (!selected.remove(uuid)) selected.add(uuid);
        draw();
    }

    private void drawModes() {
        setItem(38, modeItem(LinkType.CHAIN, "Игроки скованы цепью: дальше длины не разойтись,", "телепорт одного переносит всех."),
                (p, c) -> toggle(LinkType.CHAIN));
        setItem(39, modeItem(LinkType.HEALTH, "Одна полоска здоровья на всех.", "Умер один — умерли все."),
                (p, c) -> toggle(LinkType.HEALTH));
        setItem(41, modeItem(LinkType.FOOD, "Общий голод и насыщение:", "ест один — сыты все."),
                (p, c) -> toggle(LinkType.FOOD));
        setItem(42, modeItem(LinkType.INVENTORY, "Один инвентарь с бронёй на всех:", "предмет в руке у одного — у всех."),
                (p, c) -> toggle(LinkType.INVENTORY));

        boolean all = modes.size() == LinkType.values().length;
        setItem(40, ItemBuilder.of(Material.NETHER_STAR)
                        .name("<" + Msg.MAIN + ">Всё сразу")
                        .lore("",
                                all ? "<" + Msg.GREEN + ">● Выбраны все виды" : "<gray>○ Выбраны не все",
                                "",
                                "<gray>Нажми, чтобы " + (all ? "снять все" : "выбрать все"))
                        .glint(all)
                        .build(),
                (p, c) -> {
                    if (all) modes.clear();
                    else modes.addAll(EnumSet.allOf(LinkType.class));
                    draw();
                });
    }

    private ItemStack modeItem(LinkType type, String... description) {
        boolean enabled = modes.contains(type);

        List<String> lore = new ArrayList<>();
        lore.add("");
        for (String line : description) {
            lore.add("<gray>" + line);
        }
        lore.add("");
        lore.add(enabled ? "<" + Msg.GREEN + ">● Включено" : "<" + Msg.RED + ">○ Выключено");
        lore.add("<gray>В команде: <white>" + type.getKey());

        return ItemBuilder.of(type.getMaterial())
                .name("<" + Msg.MAIN + ">" + type.getIcon() + " " + capitalize(type.getDisplayName()))
                .lore(lore)
                .glint(enabled)
                .build();
    }

    private void toggle(LinkType type) {
        if (!modes.remove(type)) modes.add(type);
        draw();
    }

    private void drawActions(List<Player> online, int pages) {
        if (page > 0) {
            setItem(36, ItemBuilder.of(Material.ARROW).name("<white>‹ Страница " + page).build(),
                    (p, c) -> {
                        page--;
                        draw();
                    });
        }
        if (page < pages - 1) {
            setItem(44, ItemBuilder.of(Material.ARROW).name("<white>Страница " + (page + 2) + " ›").build(),
                    (p, c) -> {
                        page++;
                        draw();
                    });
        }

        setItem(45, ItemBuilder.of(Material.LIME_DYE)
                        .name("<" + Msg.GREEN + ">Выбрать всех")
                        .lore("", "<gray>По алфавиту, после уже выбранных")
                        .build(),
                (p, c) -> {
                    for (Player target : online) {
                        if (!selected.contains(target.getUniqueId())) selected.add(target.getUniqueId());
                    }
                    draw();
                });

        setItem(46, ItemBuilder.of(Material.GRAY_DYE)
                        .name("<white>Сбросить выбор")
                        .build(),
                (p, c) -> {
                    selected.clear();
                    draw();
                });

        LinkTypes types = modes.isEmpty() ? null : LinkTypes.of(modes);
        List<String> order = new ArrayList<>();
        for (UUID uuid : selected) {
            order.add(Msg.name(plugin.getLinks().name(uuid)));
        }

        setItem(48, ItemBuilder.of(Material.LEAD)
                        .name("<" + Msg.GREEN + ">Связать выбранных")
                        .lore("",
                                "<gray>Выбрано: <white>" + selected.size(),
                                "<gray>Виды: <white>" + (types == null ? "не выбраны" : types.describe()),
                                selected.size() >= 2 ? "<gray>Цепь: <white>" + String.join(" <gray>—</gray> ", order) : "<gray>Выбери хотя бы двоих",
                                "",
                                "<gray>Порядок выбора — порядок цепи")
                        .glint(selected.size() >= 2 && types != null)
                        .build(),
                (p, c) -> {
                    if (types == null) {
                        p.sendMessage(Msg.error("Не выбран ни один вид связи"));
                        return;
                    }
                    List<Player> players = new ArrayList<>();
                    for (UUID uuid : selected) {
                        Player target = Bukkit.getPlayer(uuid);
                        if (target != null) players.add(target);
                    }
                    plugin.getActions().linkSequence(p, players, types);
                    draw();
                });

        List<String> summary = new ArrayList<>();
        summary.add("");
        summary.addAll(plugin.getActions().describeAll());
        summary.add("");
        summary.add("<gray>Длина цепи: <white>" + LinkActions.format(plugin.getSettings().getChainLength()) + " бл.");
        setItem(49, ItemBuilder.of(Material.BOOK).name("<" + Msg.MAIN + ">Текущие связи").lore(summary).build());

        setItem(50, ItemBuilder.of(Material.SHEARS)
                        .name("<" + Msg.YELLOW + ">Разъединить выбранных")
                        .lore("",
                                "<gray>Рвёт связи выбранных видов",
                                "<gray>между всеми выбранными.",
                                "<gray>Выбран один — отвязывает его ото всех.")
                        .build(),
                (p, c) -> {
                    if (types == null) {
                        p.sendMessage(Msg.error("Не выбран ни один вид связи"));
                        return;
                    }
                    if (selected.isEmpty()) {
                        p.sendMessage(Msg.error("Никто не выбран"));
                        return;
                    }
                    plugin.getActions().unlinkAmong(p, new ArrayList<>(selected), types);
                    draw();
                });

        setItem(52, ItemBuilder.of(Material.TNT)
                        .name("<" + Msg.RED + ">Снять все связи")
                        .lore("",
                                "<gray>Все связи выбранных видов у всех игроков.",
                                "",
                                "<" + Msg.RED + ">Shift+клик</" + Msg.RED + "> <gray>— подтвердить")
                        .build(),
                (p, c) -> {
                    if (!c.isShiftClick()) {
                        p.sendMessage(Msg.warn("Чтобы снять все связи, нажми с Shift"));
                        return;
                    }
                    if (types == null) {
                        p.sendMessage(Msg.error("Не выбран ни один вид связи"));
                        return;
                    }
                    plugin.getActions().unlinkEverything(p, types);
                    draw();
                });

        setItem(53, ItemBuilder.of(Material.COMPARATOR)
                        .name("<" + Msg.MAIN + ">Настройки цепи")
                        .lore("", "<gray>Длина, жёсткость, вид цепи", "<gray>и совместные телепорты")
                        .build(),
                (p, c) -> switchTo(new SettingsMenu(plugin, p, selected, modes, page)));
    }

    private static String capitalize(String text) {
        return Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }
}
