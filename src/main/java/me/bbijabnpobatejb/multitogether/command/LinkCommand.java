package me.bbijabnpobatejb.multitogether.command;

import dev.rollczi.litecommands.annotations.argument.Arg;
import dev.rollczi.litecommands.annotations.command.Command;
import dev.rollczi.litecommands.annotations.context.Sender;
import dev.rollczi.litecommands.annotations.execute.Execute;
import dev.rollczi.litecommands.annotations.permission.Permission;
import me.bbijabnpobatejb.multitogether.MultiTogether;
import me.bbijabnpobatejb.multitogether.gui.menu.LinkMenu;
import me.bbijabnpobatejb.multitogether.gui.menu.SettingsMenu;
import me.bbijabnpobatejb.multitogether.link.LinkActions;
import me.bbijabnpobatejb.multitogether.link.LinkType;
import me.bbijabnpobatejb.multitogether.link.LinkTypes;
import me.bbijabnpobatejb.multitogether.settings.Settings;
import me.bbijabnpobatejb.multitogether.util.Msg;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Optional;

/**
 * {@code /multitogether}, она же {@code /together}, {@code /link} и они же в русской раскладке.
 */
@Command(name = "multitogether", aliases = {
        "together", "link", "mt", "mtogether",
        "связать", "связь", "сковать",
        "ьгдешещпуерук", "ещпуерук", "дштл", "ье"
})
@Permission(MultiTogether.PERMISSION)
public class LinkCommand {

    private final MultiTogether plugin;

    public LinkCommand(MultiTogether plugin) {
        this.plugin = plugin;
    }

    @Execute
    void menu(@Sender CommandSender sender) {
        if (sender instanceof Player player) {
            new LinkMenu(plugin, player).open();
            return;
        }
        help(sender);
    }

    @Execute
    void link(@Sender CommandSender sender,
              @Arg("игрок1") Player first,
              @Arg("игрок2") Player second,
              @Arg("тип") Optional<LinkTypes> types) {
        plugin.getActions().link(sender, first, second, types.orElseGet(LinkTypes::all));
    }

    @Execute(name = "all", aliases = {"все", "всех", "фдд"})
    void linkAll(@Sender CommandSender sender, @Arg("тип") Optional<LinkTypes> types) {
        plugin.getActions().linkEveryone(sender, types.orElseGet(LinkTypes::all));
    }

    @Execute(name = "list", aliases = {"список", "дшые"})
    void list(@Sender CommandSender sender) {
        sender.sendMessage(plugin.getActions().listMessage());
    }

    @Execute(name = "length", aliases = {"длина", "дутпер"})
    void length(@Sender CommandSender sender, @Arg("блоки") double length) {
        plugin.getActions().setLength(sender, length);
    }

    @Execute(name = "settings", aliases = {"настройки", "ыуеештпы"})
    void settings(@Sender Player sender) {
        new SettingsMenu(plugin, sender).open();
    }

    @Execute(name = "help", aliases = {"помощь", "рудз"})
    void help(@Sender CommandSender sender) {
        String main = "<" + Msg.MAIN + ">";
        sender.sendMessage(Msg.mm(Msg.PREFIX + "<white>Multi Together — связь игроков"
                + "<newline> " + main + "/link</" + Msg.MAIN + "> <gray>— меню"
                + "<newline> " + main + "/link <игрок1> <игрок2> [тип]</" + Msg.MAIN + "> <gray>— связать"
                + "<newline> " + main + "/link all [тип]</" + Msg.MAIN + "> <gray>— связать всех онлайн цепочкой"
                + "<newline> " + main + "/link list</" + Msg.MAIN + "> <gray>— все связи"
                + "<newline> " + main + "/link length <блоки></" + Msg.MAIN + "> <gray>— длина цепи ("
                + LinkActions.format(Settings.MIN_LENGTH) + "–" + LinkActions.format(Settings.MAX_LENGTH) + ")"
                + "<newline> " + main + "/unlink <игрок1> <игрок2> [тип]</" + Msg.MAIN + "> <gray>— разъединить пару"
                + "<newline> " + main + "/unlink <игрок> [тип]</" + Msg.MAIN + "> <gray>— отвязать ото всех"
                + "<newline> " + main + "/unlink all [тип]</" + Msg.MAIN + "> <gray>— снять все связи"
                + "<newline> <gray>Типы: <white>ALL</white> (по умолчанию), <white>CHAIN</white>, <white>HP</white>, "
                + "<white>FOOD</white>, <white>INVENTORY</white>, можно несколько: <white>HP,FOOD"));
    }

    @Execute(name = "debug")
    void debug(@Sender CommandSender sender) {
        StringBuilder text = new StringBuilder(Msg.PREFIX + "<white>Отладка:");
        for (LinkType type : LinkType.values()) {
            text.append("<newline> <gray>").append(type.getKey()).append(": <white>")
                    .append(plugin.getLinks().edges(type).size()).append(" пар, ")
                    .append(plugin.getLinks().components(type).size()).append(" групп");
        }
        text.append("<newline> <gray>Звеньев цепи на сервере: <white>").append(plugin.getChains().displayCount());
        text.append("<newline> <gray>Длина: <white>").append(plugin.getSettings().getChainLength())
                .append("<gray>, жёсткость: <white>").append(plugin.getSettings().getStiffness());
        sender.sendMessage(Msg.mm(text.toString()));
    }
}
