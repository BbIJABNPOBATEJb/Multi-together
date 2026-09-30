package me.bbijabnpobatejb.multitogether.command;

import dev.rollczi.litecommands.annotations.argument.Arg;
import dev.rollczi.litecommands.annotations.command.Command;
import dev.rollczi.litecommands.annotations.context.Sender;
import dev.rollczi.litecommands.annotations.execute.Execute;
import dev.rollczi.litecommands.annotations.permission.Permission;
import me.bbijabnpobatejb.multitogether.MultiTogether;
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
        String main = "<" + Msg.MAIN + ">";
        sender.sendMessage(Msg.mm(Msg.PREFIX + "<white>Разъединение:"
                + "<newline> " + main + "/unlink <игрок1> <игрок2> [тип]</" + Msg.MAIN + "> <gray>— разъединить пару"
                + "<newline> " + main + "/unlink <игрок> [тип]</" + Msg.MAIN + "> <gray>— отвязать ото всех"
                + "<newline> " + main + "/unlink all [тип]</" + Msg.MAIN + "> <gray>— снять все связи"
                + "<newline> <gray>Типы: <white>ALL, CHAIN, HP, FOOD, INVENTORY"));
    }

    @Execute
    void unlink(@Sender CommandSender sender,
                @Arg("игрок1") Player first,
                @Arg("игрок2") Player second,
                @Arg("тип") Optional<LinkTypes> types) {
        plugin.getActions().unlink(sender, first.getUniqueId(), second.getUniqueId(), types.orElseGet(LinkTypes::all));
    }

    @Execute
    void unlinkPlayer(@Sender CommandSender sender, @Arg("игрок") Player player) {
        plugin.getActions().unlinkPlayer(sender, player.getUniqueId(), LinkTypes.all());
    }

    @Execute
    void unlinkPlayer(@Sender CommandSender sender, @Arg("игрок") Player player, @Arg("тип") LinkTypes types) {
        plugin.getActions().unlinkPlayer(sender, player.getUniqueId(), types);
    }

    @Execute(name = "all", aliases = {"все", "всех", "фдд"})
    void unlinkAll(@Sender CommandSender sender, @Arg("тип") Optional<LinkTypes> types) {
        plugin.getActions().unlinkEverything(sender, types.orElseGet(LinkTypes::all));
    }
}
