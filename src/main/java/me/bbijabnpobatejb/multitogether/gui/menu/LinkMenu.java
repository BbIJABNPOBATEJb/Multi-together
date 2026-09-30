package me.bbijabnpobatejb.multitogether.gui.menu;

import me.bbijabnpobatejb.multitogether.MultiTogether;
import me.bbijabnpobatejb.multitogether.gui.Gui;
import me.bbijabnpobatejb.multitogether.i18n.Lang;
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
        super(viewer, 6, Msg.mm("<" + Msg.MAIN + ">⛓</" + Msg.MAIN + "> <dark_gray>" + Lang.mm("menu.title")));
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

        List<String> links = new ArrayList<>();
        links.add("");
        links.addAll(plugin.getActions().describePlayer(target.getUniqueId()));
        links.add("");

        String name = Msg.name(target.getName());
        return ItemBuilder.head(target)
                .name(chosen
                        ? "<" + Msg.GREEN + ">✔ " + name + " <gray>#" + (index + 1)
                        : "<white>" + name)
                .lore(links)
                .hint(Lang.plain(chosen ? "menu.deselect" : "menu.select"))
                .hint(Lang.plain("menu.unlink-player"))
                .hint(Lang.plain("menu.teleport"))
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
                player.sendMessage(Msg.error(Lang.mm("menu.no-types")));
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
        setItem(38, modeItem(LinkType.CHAIN), (p, c) -> toggle(p, LinkType.CHAIN));
        setItem(39, modeItem(LinkType.HEALTH), (p, c) -> toggle(p, LinkType.HEALTH));
        setItem(41, modeItem(LinkType.FOOD), (p, c) -> toggle(p, LinkType.FOOD));
        setItem(42, modeItem(LinkType.INVENTORY), (p, c) -> toggle(p, LinkType.INVENTORY));

        boolean all = modes.size() == LinkType.values().length;
        setItem(40, ItemBuilder.of(Material.NETHER_STAR)
                        .name("<" + Msg.MAIN + ">" + Lang.mm("menu.all"))
                        .lore("", all
                                ? "<" + Msg.GREEN + ">" + Lang.mm("menu.all-selected")
                                : "<gray>" + Lang.mm("menu.all-not-selected"), "")
                        .description(Lang.plain(all ? "menu.all-click-deselect" : "menu.all-click-select"))
                        .glint(all)
                        .build(),
                (p, c) -> {
                    if (all) modes.clear();
                    else modes.addAll(EnumSet.allOf(LinkType.class));
                    p.sendMessage(Msg.info(Lang.mm(all ? "menu.type-off" : "menu.type-on",
                            "type", Msg.white(Lang.plain("menu.all")))));
                    draw();
                });
    }

    private ItemStack modeItem(LinkType type) {
        boolean enabled = modes.contains(type);

        return ItemBuilder.of(type.getMaterial())
                .name("<" + Msg.MAIN + ">" + type.getIcon() + " " + Msg.name(type.title()))
                .lore("")
                .description(type.description())
                .lore("",
                        enabled ? "<" + Msg.GREEN + ">" + Lang.mm("menu.enabled") : "<" + Msg.RED + ">" + Lang.mm("menu.disabled"),
                        "<gray>" + Lang.mm("menu.in-commands", "key", Msg.white(type.getKey())))
                .glint(enabled)
                .build();
    }

    private void toggle(Player clicker, LinkType type) {
        boolean enabled = !modes.remove(type);
        if (enabled) modes.add(type);

        clicker.sendMessage(Msg.info(Lang.mm(enabled ? "menu.type-on" : "menu.type-off",
                "type", Msg.white(type.getIcon() + " " + type.title()))));
        draw();
    }

    private void drawActions(List<Player> online, int pages) {
        if (page > 0) {
            setItem(36, ItemBuilder.of(Material.ARROW).name("<white>" + Lang.mm("menu.page-previous", "page", page)).build(),
                    (p, c) -> {
                        page--;
                        draw();
                    });
        }
        if (page < pages - 1) {
            setItem(44, ItemBuilder.of(Material.ARROW).name("<white>" + Lang.mm("menu.page-next", "page", page + 2)).build(),
                    (p, c) -> {
                        page++;
                        draw();
                    });
        }

        setItem(45, ItemBuilder.of(Material.LIME_DYE)
                        .name("<" + Msg.GREEN + ">" + Lang.mm("menu.select-all"))
                        .lore("")
                        .description(Lang.plain("menu.select-all-hint"))
                        .build(),
                (p, c) -> {
                    for (Player target : online) {
                        if (!selected.contains(target.getUniqueId())) selected.add(target.getUniqueId());
                    }
                    draw();
                });

        setItem(46, ItemBuilder.of(Material.GRAY_DYE)
                        .name("<white>" + Lang.mm("menu.clear-selection"))
                        .build(),
                (p, c) -> {
                    selected.clear();
                    draw();
                });

        drawLinkButton();

        List<String> summary = new ArrayList<>();
        summary.add("");
        summary.addAll(plugin.getActions().describeAll());
        summary.add("");
        summary.add("<gray>" + Lang.mm("menu.chain-length",
                "length", Msg.white(LinkActions.format(plugin.getSettings().getChainLength()))));
        setItem(49, ItemBuilder.of(Material.BOOK).name("<" + Msg.MAIN + ">" + Lang.mm("menu.current-links")).lore(summary).build());

        LinkTypes types = modes.isEmpty() ? null : LinkTypes.of(modes);
        setItem(50, ItemBuilder.of(Material.SHEARS)
                        .name("<" + Msg.YELLOW + ">" + Lang.mm("menu.unlink"))
                        .lore("")
                        .description(Lang.plain("menu.unlink-hint"))
                        .build(),
                (p, c) -> {
                    if (types == null) {
                        p.sendMessage(Msg.error(Lang.mm("menu.no-types")));
                        return;
                    }
                    if (selected.isEmpty()) {
                        p.sendMessage(Msg.error(Lang.mm("menu.nobody-selected")));
                        return;
                    }
                    plugin.getActions().unlinkAmong(p, new ArrayList<>(selected), types);
                    draw();
                });

        setItem(52, ItemBuilder.of(Material.TNT)
                        .name("<" + Msg.RED + ">" + Lang.mm("menu.unlink-all"))
                        .lore("")
                        .description(Lang.plain("menu.unlink-all-hint"))
                        .lore("")
                        .hint(Lang.plain("menu.shift-confirm"))
                        .build(),
                (p, c) -> {
                    if (!c.isShiftClick()) {
                        p.sendMessage(Msg.warn(Lang.mm("menu.unlink-all-shift")));
                        return;
                    }
                    if (types == null) {
                        p.sendMessage(Msg.error(Lang.mm("menu.no-types")));
                        return;
                    }
                    plugin.getActions().unlinkEverything(p, types);
                    draw();
                });

        setItem(53, ItemBuilder.of(Material.COMPARATOR)
                        .name("<" + Msg.MAIN + ">" + Lang.mm("menu.settings"))
                        .lore("")
                        .description(Lang.plain("menu.settings-hint"))
                        .build(),
                (p, c) -> switchTo(new SettingsMenu(plugin, p, selected, modes, page)));
    }

    private void drawLinkButton() {
        LinkTypes types = modes.isEmpty() ? null : LinkTypes.of(modes);

        List<String> order = new ArrayList<>();
        for (UUID uuid : selected) {
            order.add(plugin.getLinks().name(uuid));
        }

        ItemBuilder button = ItemBuilder.of(Material.LEAD)
                .name("<" + Msg.GREEN + ">" + Lang.mm("menu.link"))
                .lore("", "<gray>" + Lang.mm("menu.selected", "count", Msg.white(String.valueOf(selected.size()))))
                .description(Lang.plain("menu.types", "types", types == null ? Lang.plain("menu.types-none") : types.describe()))
                .description(selected.size() >= 2
                        ? Lang.plain("menu.order", "order", String.join(" — ", order))
                        : Lang.plain("menu.need-two"))
                .lore("")
                .description(Lang.plain("menu.order-hint"))
                .glint(selected.size() >= 2 && types != null);

        setItem(48, button.build(), (p, c) -> {
            if (types == null) {
                p.sendMessage(Msg.error(Lang.mm("menu.no-types")));
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
    }
}
