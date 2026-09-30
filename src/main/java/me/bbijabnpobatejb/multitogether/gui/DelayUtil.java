package me.bbijabnpobatejb.multitogether.gui;

import lombok.NonNull;
import lombok.experimental.UtilityClass;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@UtilityClass
class DelayUtil {

    private final Map<String, Map<UUID, Long>> delays = new HashMap<>();

    public void putDelay(@NonNull String delayName, @NonNull Player player, long mills) {
        delays.computeIfAbsent(delayName, k -> new HashMap<>())
                .put(player.getUniqueId(), System.currentTimeMillis() + mills);
    }

    public long getDelay(@NonNull String delayName, @NonNull Player player) {
        Map<UUID, Long> byPlayer = delays.get(delayName);
        Long playerDelay = byPlayer == null ? null : byPlayer.get(player.getUniqueId());
        return playerDelay == null ? 0 : playerDelay - System.currentTimeMillis();
    }

    public void removeDelay(@NonNull String delayName, @NonNull Player player) {
        Map<UUID, Long> byPlayer = delays.get(delayName);
        if (byPlayer != null) byPlayer.remove(player.getUniqueId());
    }

    public boolean hasDelay(@NonNull String delayName, @NonNull Player player) {
        return getDelay(delayName, player) > 0;
    }
}
