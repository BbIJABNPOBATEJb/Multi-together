package me.bbijabnpobatejb.multitogether.command;

import dev.rollczi.litecommands.argument.Argument;
import dev.rollczi.litecommands.argument.parser.ParseResult;
import dev.rollczi.litecommands.argument.resolver.ArgumentResolver;
import dev.rollczi.litecommands.invocation.Invocation;
import dev.rollczi.litecommands.suggestion.SuggestionContext;
import dev.rollczi.litecommands.suggestion.SuggestionResult;
import me.bbijabnpobatejb.multitogether.i18n.Lang;
import me.bbijabnpobatejb.multitogether.link.LinkTypes;
import me.bbijabnpobatejb.multitogether.util.Msg;
import org.bukkit.command.CommandSender;

/**
 * {@code ALL}, {@code CHAIN}, {@code HP}, {@code FOOD}, {@code INVENTORY}, названия на русском
 * и на языке сервера, несколько через запятую: {@code HP,FOOD}.
 */
public class LinkTypesArgument extends ArgumentResolver<CommandSender, LinkTypes> {

    @Override
    protected ParseResult<LinkTypes> parse(Invocation<CommandSender> invocation, Argument<LinkTypes> context, String argument) {
        return LinkTypes.parse(argument)
                .map(ParseResult::success)
                .orElseGet(() -> ParseResult.failure(Msg.error(Lang.mm("command.unknown-type",
                        "input", Msg.white(argument),
                        "types", Msg.white(String.join(", ", LinkTypes.SUGGESTIONS))))));
    }

    @Override
    public SuggestionResult suggest(Invocation<CommandSender> invocation, Argument<LinkTypes> argument, SuggestionContext context) {
        return SuggestionResult.of(LinkTypes.SUGGESTIONS);
    }
}
