package me.bbijabnpobatejb.multitogether.link;

import me.bbijabnpobatejb.multitogether.chain.ChainService;
import me.bbijabnpobatejb.multitogether.i18n.Lang;
import me.bbijabnpobatejb.multitogether.settings.Settings;
import me.bbijabnpobatejb.multitogether.util.Msg;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Связать и развязать с сообщениями всем причастным. Этим пользуются и команды, и меню.
 */
public final class LinkActions {

    private final LinkService links;
    private final ChainService chains;
    private final Settings settings;

    public LinkActions(LinkService links, ChainService chains, Settings settings) {
        this.links = links;
        this.chains = chains;
        this.settings = settings;
    }

    public void link(CommandSender actor, Player first, Player second, LinkTypes types) {
        if (first.getUniqueId().equals(second.getUniqueId())) {
            actor.sendMessage(Msg.error(Lang.mm("link.self")));
            return;
        }

        Set<LinkType> added = links.link(first, second, types.types());
        if (added.isEmpty()) {
            actor.sendMessage(Msg.warn(Lang.mm("link.already",
                    "first", Msg.white(first.getName()),
                    "second", Msg.white(second.getName()),
                    "types", Msg.name(types.describe()))));
            return;
        }

        if (added.contains(LinkType.CHAIN)) chains.gather(first);

        String what = LinkTypes.of(added).describe();
        actor.sendMessage(Msg.success(Lang.mm("link.done",
                "first", Msg.white(first.getName()),
                "second", Msg.white(second.getName()),
                "types", Msg.name(what))));

        notifyLinked(first, second, what);
        notifyLinked(second, first, what);
    }

    /**
     * Связывает игроков по порядку: 1—2, 2—3, ... Для цепи это цепочка, для остального —
     * одна общая группа на всех.
     */
    public void linkSequence(CommandSender actor, List<Player> players, LinkTypes types) {
        if (players.size() < 2) {
            actor.sendMessage(Msg.error(Lang.mm("link.too-few")));
            return;
        }

        int pairs = 0;
        Set<LinkType> added = EnumSet.noneOf(LinkType.class);
        for (int i = 0; i + 1 < players.size(); i++) {
            Player first = players.get(i);
            Player second = players.get(i + 1);
            Set<LinkType> result = links.link(first, second, types.types());
            if (result.isEmpty()) continue;

            pairs++;
            added.addAll(result);
            String what = LinkTypes.of(result).describe();
            notifyLinked(first, second, what);
            notifyLinked(second, first, what);
        }

        if (pairs == 0) {
            actor.sendMessage(Msg.warn(Lang.mm("link.all-already", "types", Msg.name(types.describe()))));
            return;
        }

        if (added.contains(LinkType.CHAIN)) chains.gather(players.get(0));

        String order = players.stream().map(p -> Msg.name(p.getName())).collect(Collectors.joining(" — "));
        actor.sendMessage(Msg.success(Lang.mm("link.pairs-done",
                "count", Msg.white(String.valueOf(pairs)),
                "types", Msg.name(LinkTypes.of(added).describe()))
                + "<newline><gray>" + order));
    }

    /**
     * Все онлайн-игроки по алфавиту.
     */
    public void linkEveryone(CommandSender actor, LinkTypes types) {
        List<Player> players = new ArrayList<>(Bukkit.getOnlinePlayers());
        players.sort(Comparator.comparing(Player::getName, String.CASE_INSENSITIVE_ORDER));
        linkSequence(actor, players, types);
    }

    public void unlink(CommandSender actor, UUID first, UUID second, LinkTypes types) {
        if (first.equals(second)) {
            actor.sendMessage(Msg.error(Lang.mm("unlink.different")));
            return;
        }

        Set<LinkType> removed = links.unlink(first, second, types.types());
        String firstName = links.name(first);
        String secondName = links.name(second);

        if (removed.isEmpty()) {
            actor.sendMessage(Msg.warn(Lang.mm("unlink.not-linked",
                    "first", Msg.white(firstName),
                    "second", Msg.white(secondName),
                    "types", Msg.name(types.describe()))));
            return;
        }

        String what = LinkTypes.of(removed).describe();
        actor.sendMessage(Msg.success(Lang.mm("unlink.done",
                "first", Msg.white(firstName),
                "second", Msg.white(secondName),
                "types", Msg.name(what))));
        notifyUnlinked(first, secondName, what);
        notifyUnlinked(second, firstName, what);
    }

    /**
     * Все пары внутри выбранных — для меню.
     */
    public void unlinkAmong(CommandSender actor, List<UUID> players, LinkTypes types) {
        if (players.size() == 1) {
            unlinkPlayer(actor, players.get(0), types);
            return;
        }

        int pairs = 0;
        for (int i = 0; i < players.size(); i++) {
            for (int j = i + 1; j < players.size(); j++) {
                if (!links.unlink(players.get(i), players.get(j), types.types()).isEmpty()) pairs++;
            }
        }

        String what = Msg.name(types.describe());
        if (pairs == 0) {
            actor.sendMessage(Msg.warn(Lang.mm("unlink.among-none", "types", what)));
            return;
        }

        actor.sendMessage(Msg.success(Lang.mm("unlink.among-done", "count", Msg.white(String.valueOf(pairs)), "types", what)));
        for (UUID uuid : players) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) player.sendMessage(Msg.warn(Lang.mm("unlink.among-notify", "types", what)));
        }
    }

    public void unlinkPlayer(CommandSender actor, UUID player, LinkTypes types) {
        Map<LinkType, Integer> removed = links.unlinkPlayer(player, types.types());
        String name = links.name(player);

        if (removed.isEmpty()) {
            actor.sendMessage(Msg.warn(Lang.mm("unlink.player-none",
                    "player", Msg.white(name), "types", Msg.name(types.describe()))));
            return;
        }

        String what = Msg.name(LinkTypes.of(removed.keySet()).describe());
        actor.sendMessage(Msg.success(Lang.mm("unlink.player-done", "player", Msg.white(name), "types", what)));

        Player online = Bukkit.getPlayer(player);
        if (online != null) {
            online.sendMessage(Msg.warn(Lang.mm("unlink.player-notify", "types", what)));
            online.playSound(online, Sound.BLOCK_CHAIN_BREAK, SoundCategory.PLAYERS, 1f, 1f);
        }
    }

    public void unlinkEverything(CommandSender actor, LinkTypes types) {
        int pairs = links.unlinkEverything(types.types());
        String what = Msg.name(types.describe());
        if (pairs == 0) {
            actor.sendMessage(Msg.warn(Lang.mm("unlink.everything-none", "types", what)));
            return;
        }

        actor.sendMessage(Msg.success(Lang.mm("unlink.everything-done", "types", what, "count", Msg.white(String.valueOf(pairs)))));
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.equals(actor)) continue;
            player.playSound(player, Sound.BLOCK_CHAIN_BREAK, SoundCategory.PLAYERS, 0.7f, 1f);
        }
    }

    public void setLength(CommandSender actor, double length) {
        settings.setChainLength(length);
        actor.sendMessage(Msg.success(Lang.mm("link.length-set", "length", Msg.white(format(settings.getChainLength())))));
    }

    private void notifyLinked(Player player, Player partner, String what) {
        String main = "<" + Msg.MAIN + ">";
        String close = "</" + Msg.MAIN + ">";
        player.sendMessage(Msg.info(main + "⛓" + close + " " + Lang.mm("link.notify",
                "player", main + Msg.name(partner.getName()) + close,
                "types", Msg.name(what))));
        player.playSound(player, Sound.BLOCK_CHAIN_PLACE, SoundCategory.PLAYERS, 1f, 0.8f);
    }

    private void notifyUnlinked(UUID uuid, String partnerName, String what) {
        Player player = Bukkit.getPlayer(uuid);
        if (player == null) return;

        player.sendMessage(Msg.warn(Lang.mm("unlink.notify", "player", Msg.white(partnerName), "types", Msg.name(what))));
        player.playSound(player, Sound.BLOCK_CHAIN_BREAK, SoundCategory.PLAYERS, 1f, 1f);
    }

    // ---------------------------------------------------------------- описание связей

    /**
     * Все связи построчно — для /link list и книжки в меню.
     */
    public List<String> describeAll() {
        List<String> lines = new ArrayList<>();
        String none = "<dark_gray>" + Lang.mm("list.none");

        for (LinkType type : LinkType.values()) {
            String header = "<" + Msg.MAIN + ">" + type.getIcon() + " " + Msg.name(type.title()) + ":</" + Msg.MAIN + "> ";

            if (type == LinkType.CHAIN) {
                List<Edge> edges = links.edges(type);
                if (edges.isEmpty()) {
                    lines.add(header + none);
                    continue;
                }
                String pairs = edges.stream()
                        .map(edge -> nameMarked(edge.first()) + " <gray>—</gray> " + nameMarked(edge.second()))
                        .collect(Collectors.joining("<gray>, </gray>"));
                lines.add(header + pairs);
                continue;
            }

            List<Set<UUID>> groups = links.components(type);
            if (groups.isEmpty()) {
                lines.add(header + none);
                continue;
            }
            String text = groups.stream()
                    .map(group -> "<gray>[</gray>" + group.stream().map(this::nameMarked)
                            .collect(Collectors.joining("<gray>, </gray>")) + "<gray>]</gray>")
                    .collect(Collectors.joining(" "));
            lines.add(header + text);
        }
        return lines;
    }

    /**
     * Связи одного игрока — для описания его головы в меню.
     */
    public List<String> describePlayer(UUID player) {
        List<String> lines = new ArrayList<>();
        for (LinkType type : LinkType.values()) {
            Set<UUID> partners = type == LinkType.CHAIN
                    ? links.neighbors(type, player)
                    : links.component(type, player);

            List<String> names = new ArrayList<>();
            for (UUID uuid : partners) {
                if (!uuid.equals(player)) names.add(nameMarked(uuid));
            }

            String label = "<gray>" + type.getIcon() + " " + Msg.name(type.title()) + ": ";
            lines.add(names.isEmpty()
                    ? label + "<dark_gray>" + Lang.mm("list.none")
                    : label + "<white>" + String.join("<gray>, </gray>", names));
        }
        return lines;
    }

    public Component listMessage() {
        StringBuilder text = new StringBuilder(Msg.PREFIX + "<white>" + Lang.mm("list.header"));
        for (String line : describeAll()) {
            text.append("<newline> ").append(line);
        }
        text.append("<newline> <gray>")
                .append(Lang.mm("list.length", "length", Msg.white(format(settings.getChainLength()))));
        return Msg.mm(text.toString());
    }

    private String nameMarked(UUID uuid) {
        String name = Msg.name(links.name(uuid));
        return Bukkit.getPlayer(uuid) == null ? "<dark_gray>" + name + "</dark_gray>" : "<white>" + name + "</white>";
    }

    public static String format(double value) {
        return value == Math.floor(value) ? String.valueOf((int) value) : String.valueOf(value);
    }
}
