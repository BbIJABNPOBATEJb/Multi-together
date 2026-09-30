package me.bbijabnpobatejb.multitogether.util;

import lombok.experimental.UtilityClass;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.CommandSender;

import java.util.ArrayList;
import java.util.List;

/**
 * Сообщения в чат. Цвета те же, что в Linked-Inventory:
 * MAIN #FF7800, GREEN #1FFF5E, RED #FF1F1F, YELLOW #FFE31F, BLUE #1F98FF.
 */
@UtilityClass
public class Msg {

    public final String MAIN = "#FF7800";
    public final String GREEN = "#1FFF5E";
    public final String RED = "#FF1F1F";
    public final String YELLOW = "#FFE31F";
    public final String BLUE = "#1F98FF";

    public final String PREFIX = "<" + MAIN + ">▶<reset> ";

    private final MiniMessage MM = MiniMessage.miniMessage();

    public Component mm(String text) {
        return MM.deserialize(text);
    }

    /**
     * Строка для предмета: у названий и описания в инвентаре курсив по умолчанию, здесь он снят.
     */
    public Component item(String text) {
        return MM.deserialize(text).decoration(TextDecoration.ITALIC, false);
    }

    public List<Component> lore(List<String> lines) {
        List<Component> result = new ArrayList<>(lines.size());
        for (String line : lines) {
            result.add(item(line));
        }
        return result;
    }

    /**
     * Ник в MiniMessage: у ников бывают подчёркивания, а в чужих данных — что угодно ещё.
     */
    public String name(String name) {
        return MM.escapeTags(name);
    }

    public Component info(String text) {
        return mm(PREFIX + "<white>" + text);
    }

    public Component success(String text) {
        return mm(PREFIX + "<" + GREEN + ">" + text);
    }

    public Component warn(String text) {
        return mm(PREFIX + "<" + YELLOW + ">" + text);
    }

    public Component error(String text) {
        return mm(PREFIX + "<" + RED + ">" + text);
    }

    public void send(CommandSender sender, Component message) {
        sender.sendMessage(message);
    }
}
