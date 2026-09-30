package me.bbijabnpobatejb.multitogether.command;

import dev.rollczi.litecommands.annotations.argument.Arg;
import dev.rollczi.litecommands.annotations.command.Command;
import dev.rollczi.litecommands.annotations.context.Sender;
import dev.rollczi.litecommands.annotations.execute.Execute;
import dev.rollczi.litecommands.annotations.permission.Permission;
import me.bbijabnpobatejb.multitogether.MultiTogether;
import me.bbijabnpobatejb.multitogether.i18n.Lang;
import me.bbijabnpobatejb.multitogether.link.LinkTypes;
import me.bbijabnpobatejb.multitogether.util.Msg;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Optional;

@Command(name = "unlink", aliases = {
        "multiunlink", "untogether",
        "отвязать", "развязать", "расковать",
        "гтдштл", "гтещпуерук"
})
@Permission(MultiTogether.PERMISSION)
public class UnlinkCommand {

    private final MultiTogether plugin;

    public UnlinkCommand(MultiTogether plugin) {
        this.plugin = plugin;
    }

    @Execute
    void usage(@Sender CommandSender sender) {
        String player1 = Lang.plain("args.player1");
        String player2 = Lang.plain("args.player2");
        String type = Lang.plain("args.type");

        StringBuilder text = new StringBuilder(Msg.PREFIX + "<white>" + Lang.mm("help.unlink-header"));
        LinkCommand.line(text, "/unlink <" + player1 + "> <" + player2 + "> [" + type + "]", Lang.mm("help.unlink"));
        LinkCommand.line(text, "/unlink <" + Lang.plain("args.player") + "> [" + type + "]", Lang.mm("help.unlink-player"));
        LinkCommand.line(text, "/unlink all [" + type + "]", Lang.mm("help.unlink-all"));
        sender.sendMessage(Msg.mm(text.toString()));
    }

    @Execute
    void unlink(@Sender CommandSender sender,
                @Arg("player1") Player first,
                @Arg("player2") Player second,
                @Arg("type") Optional<LinkTypes> types) {
        plugin.getActions().unlink(sender, first.getUniqueId(), second.getUniqueId(), types.orElseGet(LinkTypes::all));
    }

    @Execute
    void unlinkPlayer(@Sender CommandSender sender, @Arg("player") Player player) {
        plugin.getActions().unlinkPlayer(sender, player.getUniqueId(), LinkTypes.all());
    }

    @Execute
    void unlinkPlayer(@Sender CommandSender sender, @Arg("player") Player player, @Arg("type") LinkTypes types) {
        plugin.getActions().unlinkPlayer(sender, player.getUniqueId(), types);
    }

    @Execute(name = "all", aliases = {"все", "всех", "фдд"})
    void unlinkAll(@Sender CommandSender sender, @Arg("type") Optional<LinkTypes> types) {
        plugin.getActions().unlinkEverything(sender, types.orElseGet(LinkTypes::all));
    }
}
