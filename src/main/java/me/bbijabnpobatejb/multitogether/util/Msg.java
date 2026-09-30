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

    /**
     * Значение в сообщении — белым поверх цвета самого сообщения.
     */
    public String white(String plain) {
        return "<white>" + MM.escapeTags(plain) + "</white>";
    }

    /**
     * Переносит текст по словам, чтобы описание в меню не растягивалось на пол-экрана.
     * Иероглифы и кана вдвое шире латиницы, а текст без пробелов режется по символам.
     */
    public List<String> wrap(String text, int width) {
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        int lineWidth = 0;

        for (String word : text.split(" ")) {
            for (String piece : splitLong(word, width)) {
                int pieceWidth = width(piece);
                if (!line.isEmpty() && lineWidth + 1 + pieceWidth > width) {
                    lines.add(line.toString());
                    line.setLength(0);
                    lineWidth = 0;
                }
                if (!line.isEmpty()) {
                    line.append(' ');
                    lineWidth++;
                }
                line.append(piece);
                lineWidth += pieceWidth;
            }
        }
        if (!line.isEmpty()) lines.add(line.toString());
        return lines;
    }

    private List<String> splitLong(String word, int width) {
        if (width(word) <= width) return List.of(word);

        List<String> pieces = new ArrayList<>();
        StringBuilder piece = new StringBuilder();
        int pieceWidth = 0;
        for (int i = 0; i < word.length(); ) {
            int codePoint = word.codePointAt(i);
            int charWidth = width(codePoint);
            if (pieceWidth + charWidth > width) {
                pieces.add(piece.toString());
                piece.setLength(0);
                pieceWidth = 0;
            }
            piece.appendCodePoint(codePoint);
            pieceWidth += charWidth;
            i += Character.charCount(codePoint);
        }
        if (!piece.isEmpty()) pieces.add(piece.toString());
        return pieces;
    }

    private int width(String text) {
        return text.codePoints().map(Msg::width).sum();
    }

    private int width(int codePoint) {
        Character.UnicodeScript script = Character.UnicodeScript.of(codePoint);
        boolean wide = script == Character.UnicodeScript.HAN
                || script == Character.UnicodeScript.HIRAGANA
                || script == Character.UnicodeScript.KATAKANA
                || script == Character.UnicodeScript.HANGUL
                || (codePoint >= 0x3000 && codePoint <= 0x303F)
                || (codePoint >= 0xFF00 && codePoint <= 0xFFEF);
        return wide ? 2 : 1;
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
