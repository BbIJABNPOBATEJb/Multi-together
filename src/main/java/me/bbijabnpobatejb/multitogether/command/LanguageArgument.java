package me.bbijabnpobatejb.multitogether.command;

import dev.rollczi.litecommands.argument.Argument;
import dev.rollczi.litecommands.argument.parser.ParseResult;
import dev.rollczi.litecommands.argument.resolver.ArgumentResolver;
import dev.rollczi.litecommands.invocation.Invocation;
import dev.rollczi.litecommands.suggestion.SuggestionContext;
import dev.rollczi.litecommands.suggestion.SuggestionResult;
import me.bbijabnpobatejb.multitogether.i18n.Lang;
import me.bbijabnpobatejb.multitogether.util.Msg;
import org.bukkit.command.CommandSender;

import java.util.Locale;

/**
 * Код языка: {@code en}, {@code ru}, ... — и всё, что админ положил в папку lang.
 */
public class LanguageArgument extends ArgumentResolver<CommandSender, LanguageArgument.Code> {

    public record Code(String value) {
    }

    @Override
    protected ParseResult<Code> parse(Invocation<CommandSender> invocation, Argument<Code> context, String argument) {
        String code = argument.toLowerCase(Locale.ROOT);
        if (Lang.isAvailable(code)) return ParseResult.success(new Code(code));

        return ParseResult.failure(Msg.error(Lang.mm("language.unknown",
                "input", Msg.white(argument),
                "languages", Msg.white(String.join(", ", Lang.available())))));
    }

    @Override
    public SuggestionResult suggest(Invocation<CommandSender> invocation, Argument<Code> argument, SuggestionContext context) {
        return SuggestionResult.of(Lang.available());
    }
}
