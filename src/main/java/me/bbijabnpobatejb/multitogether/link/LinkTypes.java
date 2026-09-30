package me.bbijabnpobatejb.multitogether.link;

import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Набор видов связи из команды: {@code ALL}, {@code CHAIN}, {@code HP}, ... или несколько
 * сразу через запятую или плюс — {@code HP,FOOD}, {@code chain+inv}.
 */
public record LinkTypes(Set<LinkType> types) {

    public static final List<String> SUGGESTIONS = List.of("ALL", "CHAIN", "HP", "FOOD", "INVENTORY");

    private static final Set<String> ALL_ALIASES = Set.of("all", "*", "все", "всё", "всех", "фдд");

    public LinkTypes {
        types = Collections.unmodifiableSet(EnumSet.copyOf(types));
    }

    public static LinkTypes all() {
        return new LinkTypes(EnumSet.allOf(LinkType.class));
    }

    public static LinkTypes of(Set<LinkType> types) {
        return new LinkTypes(types.isEmpty() ? EnumSet.noneOf(LinkType.class) : EnumSet.copyOf(types));
    }

    public static Optional<LinkTypes> parse(String input) {
        if (input == null || input.isBlank()) return Optional.empty();

        EnumSet<LinkType> result = EnumSet.noneOf(LinkType.class);
        for (String part : input.split("[,+|/]")) {
            String token = part.toLowerCase(Locale.ROOT).trim();
            if (token.isEmpty()) continue;

            if (ALL_ALIASES.contains(token)) {
                result.addAll(EnumSet.allOf(LinkType.class));
                continue;
            }

            LinkType type = LinkType.byAlias(token);
            if (type == null) return Optional.empty();
            result.add(type);
        }

        return result.isEmpty() ? Optional.empty() : Optional.of(new LinkTypes(result));
    }

    public boolean isAll() {
        return types.size() == LinkType.values().length;
    }

    public boolean contains(LinkType type) {
        return types.contains(type);
    }

    /**
     * «цепь, здоровье» — для сообщений.
     */
    public String describe() {
        if (isAll()) return "всё (цепь, здоровье, голод, инвентарь)";
        return types.stream().map(LinkType::getDisplayName).collect(Collectors.joining(", "));
    }
}
