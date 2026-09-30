package me.bbijabnpobatejb.multitogether.command;

import dev.rollczi.litecommands.annotations.argument.Arg;
import dev.rollczi.litecommands.annotations.command.Command;
import dev.rollczi.litecommands.annotations.context.Sender;
import dev.rollczi.litecommands.annotations.execute.Execute;
import dev.rollczi.litecommands.annotations.permission.Permission;
import me.bbijabnpobatejb.multitogether.MultiTogether;
import me.bbijabnpobatejb.multitogether.gui.menu.LinkMenu;
import me.bbijabnpobatejb.multitogether.gui.menu.SettingsMenu;
import me.bbijabnpobatejb.multitogether.i18n.Lang;
import me.bbijabnpobatejb.multitogether.link.LinkActions;
import me.bbijabnpobatejb.multitogether.link.LinkType;
import me.bbijabnpobatejb.multitogether.link.LinkTypes;
import me.bbijabnpobatejb.multitogether.settings.Settings;
import me.bbijabnpobatejb.multitogether.util.Msg;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
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
              @Arg("player1") Player first,
              @Arg("player2") Player second,
              @Arg("type") Optional<LinkTypes> types) {
        plugin.getActions().link(sender, first, second, types.orElseGet(LinkTypes::all));
    }

    @Execute(name = "all", aliases = {"все", "всех", "фдд"})
    void linkAll(@Sender CommandSender sender, @Arg("type") Optional<LinkTypes> types) {
        plugin.getActions().linkEveryone(sender, types.orElseGet(LinkTypes::all));
    }

    @Execute(name = "list", aliases = {"список", "дшые"})
    void list(@Sender CommandSender sender) {
        sender.sendMessage(plugin.getActions().listMessage());
    }

    @Execute(name = "length", aliases = {"длина", "дутпер"})
    void length(@Sender CommandSender sender, @Arg("blocks") double length) {
        plugin.getActions().setLength(sender, length);
    }

    @Execute(name = "settings", aliases = {"настройки", "ыуеештпы"})
    void settings(@Sender Player sender) {
        new SettingsMenu(plugin, sender).open();
    }

    @Execute(name = "language", aliases = {"lang", "язык", "дфтп", "дфтпгфпу"})
    void language(@Sender CommandSender sender) {
        List<String> languages = new ArrayList<>();
        for (String code : Lang.available()) {
            languages.add(code + " (" + Lang.name(code) + ")");
        }
        sender.sendMessage(Msg.info(Lang.mm("language.current",
                "language", Msg.white(Lang.name(Lang.code()) + " (" + Lang.code() + ")"))));
        sender.sendMessage(Msg.mm(" <gray>" + Lang.mm("language.available",
                "languages", Msg.white(String.join(", ", languages)))));
    }

    @Execute(name = "language", aliases = {"lang", "язык", "дфтп", "дфтпгфпу"})
    void language(@Sender CommandSender sender, @Arg("language") LanguageArgument.Code code) {
        plugin.setLanguage(code.value());
        sender.sendMessage(Msg.success(Lang.mm("language.changed", "language", Msg.white(Lang.name(code.value())))));
    }

    @Execute(name = "reload", aliases = {"перезагрузить", "кудщфв"})
    void reload(@Sender CommandSender sender) {
        plugin.reloadLanguage();
        sender.sendMessage(Msg.success(Lang.mm("language.current",
                "language", Msg.white(Lang.name(Lang.code()) + " (" + Lang.code() + ")"))));
    }

    @Execute(name = "help", aliases = {"помощь", "рудз"})
    void help(@Sender CommandSender sender) {
        String player1 = Lang.plain("args.player1");
        String player2 = Lang.plain("args.player2");
        String player = Lang.plain("args.player");
        String type = Lang.plain("args.type");

        StringBuilder text = new StringBuilder(Msg.PREFIX + "<white>" + Lang.mm("help.header"));
        line(text, "/link", Lang.mm("help.menu"));
        line(text, "/link <" + player1 + "> <" + player2 + "> [" + type + "]", Lang.mm("help.link"));
        line(text, "/link all [" + type + "]", Lang.mm("help.link-all"));
        line(text, "/link list", Lang.mm("help.list"));
        line(text, "/link length <" + Lang.plain("args.blocks") + ">", Lang.mm("help.length",
                "min", LinkActions.format(Settings.MIN_LENGTH), "max", LinkActions.format(Settings.MAX_LENGTH)));
        line(text, "/link language <" + Lang.plain("args.language") + ">", Lang.mm("help.language"));
        line(text, "/unlink <" + player1 + "> <" + player2 + "> [" + type + "]", Lang.mm("help.unlink"));
        line(text, "/unlink <" + player + "> [" + type + "]", Lang.mm("help.unlink-player"));
        line(text, "/unlink all [" + type + "]", Lang.mm("help.unlink-all"));

        List<String> others = new ArrayList<>();
        for (LinkType linkType : LinkType.values()) {
            others.add(Msg.white(linkType.getKey()));
        }
        text.append("<newline> <gray>").append(Lang.mm("help.types",
                "all", Msg.white("ALL"),
                "others", String.join(", ", others),
                "example", Msg.white("HP,FOOD")));
        sender.sendMessage(Msg.mm(text.toString()));
    }

    static void line(StringBuilder text, String usage, String description) {
        text.append("<newline> <").append(Msg.MAIN).append(">").append(Msg.name(usage))
                .append("</").append(Msg.MAIN).append("> <gray>— ").append(description).append("</gray>");
    }

    @Execute(name = "debug")
    void debug(@Sender CommandSender sender) {
        StringBuilder text = new StringBuilder(Msg.PREFIX + "<white>Debug:");
        for (LinkType type : LinkType.values()) {
            text.append("<newline> <gray>").append(type.getKey()).append(": <white>")
                    .append(plugin.getLinks().edges(type).size()).append(" pairs, ")
                    .append(plugin.getLinks().components(type).size()).append(" groups");
        }
        text.append("<newline> <gray>Chain displays: <white>").append(plugin.getChains().displayCount());
        text.append("<newline> <gray>Length: <white>").append(plugin.getSettings().getChainLength())
                .append("<gray>, stiffness: <white>").append(plugin.getSettings().getStiffness())
                .append("<gray>, language: <white>").append(Lang.code());
        sender.sendMessage(Msg.mm(text.toString()));
    }
}
