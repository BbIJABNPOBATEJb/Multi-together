package me.bbijabnpobatejb.multitogether.link;

import java.util.Set;
import java.util.UUID;

public interface LinkListener {

    /**
     * Граф одного вида поменялся. {@code affected} — все, чья группа могла измениться:
     * участники прежних групп и новых.
     */
    void onLinksChanged(LinkType type, Set<UUID> affected);
}
