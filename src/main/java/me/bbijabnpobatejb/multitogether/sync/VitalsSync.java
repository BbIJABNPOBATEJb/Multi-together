package me.bbijabnpobatejb.multitogether.sync;

import com.destroystokyo.paper.event.player.PlayerPostRespawnEvent;
import me.bbijabnpobatejb.multitogether.i18n.Lang;
import me.bbijabnpobatejb.multitogether.link.LinkListener;
import me.bbijabnpobatejb.multitogether.link.LinkService;
import me.bbijabnpobatejb.multitogether.link.LinkType;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Общее здоровье и общий голод.
 *
 * <p>Раз в тик у каждого участника группы берётся разница с прошлым тиком, разницы
 * складываются и одно итоговое значение выставляется всем. Так общая полоска чувствует
 * любой источник — урон, еду, зелья, чужие плагины, команды — без перехвата каждого по отдельности.</p>
 *
 * <p>Смерть одного убивает группу сразу, а не через тик по нулю в полоске: умерший из
 * расчёта выпадает, и без этого остальные бы выжили.</p>
 */
public final class VitalsSync implements LinkListener, Listener {

    private static final double EPSILON = 1.0E-4;

    private final Plugin plugin;
    private final LinkService links;

    private final Map<UUID, Double> lastHealth = new HashMap<>();
    private final Map<UUID, Integer> lastFood = new HashMap<>();
    private final Map<UUID, Float> lastSaturation = new HashMap<>();

    /**
     * Кого убивает связь, и с кем вместе — для сообщения о смерти. Заодно не даёт такой смерти
     * запустить новую волну.
     */
    private final Map<UUID, String> linkedDeaths = new HashMap<>();

    public VitalsSync(Plugin plugin, LinkService links) {
        this.plugin = plugin;
        this.links = links;
    }

    @Override
    public void onLinksChanged(LinkType type, Set<UUID> affected) {
        // Состав группы поменялся: на следующем тике все начнут с общего среднего
        if (type == LinkType.HEALTH) {
            affected.forEach(lastHealth::remove);
        } else if (type == LinkType.FOOD) {
            affected.forEach(this::forgetFood);
        }
    }

    public void tick() {
        for (Set<UUID> group : links.components(LinkType.HEALTH)) {
            syncHealth(participants(group, LinkType.HEALTH));
        }
        for (Set<UUID> group : links.components(LinkType.FOOD)) {
            syncFood(participants(group, LinkType.FOOD));
        }
    }

    /**
     * Живые игроки группы в выживании или приключении. Остальных расчёт забывает:
     * вернувшись в игру, они примут общее значение, а не начнут с разницы.
     */
    private List<Player> participants(Set<UUID> group, LinkType type) {
        List<Player> result = new ArrayList<>(group.size());
        for (UUID uuid : group) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && isParticipating(player)) {
                result.add(player);
                continue;
            }

            if (type == LinkType.HEALTH) lastHealth.remove(uuid);
            else forgetFood(uuid);
        }
        return result;
    }

    private void syncHealth(List<Player> members) {
        if (members.isEmpty()) return;

        if (members.size() < 2) {
            Player single = members.getFirst();
            lastHealth.put(single.getUniqueId(), single.getHealth());
            return;
        }

        double base = -1;
        double delta = 0;
        double sum = 0;
        for (Player player : members) {
            sum += player.getHealth();
            Double last = lastHealth.get(player.getUniqueId());
            if (last == null) continue;

            // У разных игроков бывает разный максимум: общая полоска — по самому большому
            base = Math.max(base, last);
            delta += player.getHealth() - last;
        }
        if (base < 0) base = sum / members.size();

        double groupMax = 0;
        for (Player player : members) {
            groupMax = Math.max(groupMax, maxHealth(player));
        }

        double value = Math.min(base + delta, groupMax);
        if (value <= EPSILON) {
            // Урон пришёл нескольким сразу: поодиночке каждый выжил, а общей полоски не хватило
            killTogether(members, null);
            return;
        }

        for (Player player : members) {
            double target = Math.min(value, maxHealth(player));
            if (Math.abs(player.getHealth() - target) > EPSILON) {
                player.setHealth(target);
            }
            lastHealth.put(player.getUniqueId(), player.getHealth());
        }
    }

    private void syncFood(List<Player> members) {
        if (members.isEmpty()) return;

        if (members.size() < 2) {
            Player single = members.getFirst();
            rememberFood(single);
            return;
        }

        int baseFood = -1;
        float baseSaturation = -1;
        int foodDelta = 0;
        float saturationDelta = 0;
        int foodSum = 0;
        float saturationSum = 0;

        for (Player player : members) {
            foodSum += player.getFoodLevel();
            saturationSum += player.getSaturation();

            Integer food = lastFood.get(player.getUniqueId());
            Float saturation = lastSaturation.get(player.getUniqueId());
            if (food == null || saturation == null) continue;

            baseFood = Math.max(baseFood, food);
            baseSaturation = Math.max(baseSaturation, saturation);
            foodDelta += player.getFoodLevel() - food;
            saturationDelta += player.getSaturation() - saturation;
        }
        if (baseFood < 0) {
            baseFood = Math.round((float) foodSum / members.size());
            baseSaturation = saturationSum / members.size();
        }

        int food = Math.clamp(baseFood + foodDelta, 0, 20);
        float saturation = Math.clamp(baseSaturation + saturationDelta, 0f, (float) food);

        for (Player player : members) {
            if (player.getFoodLevel() != food) player.setFoodLevel(food);
            if (Math.abs(player.getSaturation() - saturation) > EPSILON) player.setSaturation(saturation);
            rememberFood(player);
        }
    }

    private void rememberFood(Player player) {
        lastFood.put(player.getUniqueId(), player.getFoodLevel());
        lastSaturation.put(player.getUniqueId(), player.getSaturation());
    }

    private void forgetFood(UUID uuid) {
        lastFood.remove(uuid);
        lastSaturation.remove(uuid);
    }

    private void forgetAll(UUID uuid) {
        lastHealth.remove(uuid);
        forgetFood(uuid);
    }

    /**
     * Убивает всех из списка, кто ещё жив. {@code cause} — с кем вместе, для сообщения.
     */
    private void killTogether(List<Player> victims, String cause) {
        List<String> names = new ArrayList<>();
        for (Player victim : victims) {
            names.add(victim.getName());
        }

        for (Player victim : victims) {
            if (!victim.isOnline() || victim.isDead() || victim.getHealth() <= 0) continue;

            String with = cause;
            if (with == null) {
                List<String> others = new ArrayList<>(names);
                others.remove(victim.getName());
                with = String.join(", ", others);
            }

            linkedDeaths.put(victim.getUniqueId(), with);
            lastHealth.remove(victim.getUniqueId());
            victim.setHealth(0);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    private void onDeath(PlayerDeathEvent event) {
        Player dead = event.getPlayer();
        UUID uuid = dead.getUniqueId();
        forgetAll(uuid);

        String with = linkedDeaths.remove(uuid);
        if (with != null) {
            event.deathMessage(Component.text(Lang.plain("death.together", "player", dead.getName(), "with", with)));
            // Волну запустил тот, кто умер первым, эта смерть её не продолжает
            return;
        }

        if (!links.hasLinks(LinkType.HEALTH, uuid)) return;

        List<Player> others = new ArrayList<>();
        for (UUID member : links.component(LinkType.HEALTH, uuid)) {
            if (member.equals(uuid)) continue;
            Player player = Bukkit.getPlayer(member);
            if (player != null && isParticipating(player)) others.add(player);
        }
        if (others.isEmpty()) return;

        String name = dead.getName();
        // Следующим тиком: не убивать изнутри чужого события смерти
        Bukkit.getScheduler().runTask(plugin, () -> killTogether(others, name));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    private void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (event.getFinalDamage() <= 0) return;
        if (!links.hasLinks(LinkType.HEALTH, player.getUniqueId())) return;

        // Остальные тоже «получают» удар: вспышка, наклон камеры и звук
        for (UUID member : links.component(LinkType.HEALTH, player.getUniqueId())) {
            if (member.equals(player.getUniqueId())) continue;

            Player other = Bukkit.getPlayer(member);
            if (other == null || !isParticipating(other)) continue;

            other.playHurtAnimation(0);
            other.getWorld().playSound(other.getLocation(), Sound.ENTITY_PLAYER_HURT, SoundCategory.PLAYERS, 1f, 1f);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    private void onRegain(EntityRegainHealthEvent event) {
        if (event.getRegainReason() != EntityRegainHealthEvent.RegainReason.SATIATED) return;
        if (!(event.getEntity() instanceof Player player)) return;
        if (!links.hasLinks(LinkType.HEALTH, player.getUniqueId())) return;

        int count = 0;
        for (UUID member : links.component(LinkType.HEALTH, player.getUniqueId())) {
            Player other = Bukkit.getPlayer(member);
            if (other != null && isParticipating(other)) count++;
        }

        // Сытость лечит каждого, а полоска одна: иначе группа из трёх лечилась бы втрое быстрее
        if (count > 1) event.setAmount(event.getAmount() / count);
    }

    @EventHandler
    private void onJoin(PlayerJoinEvent event) {
        forgetAll(event.getPlayer().getUniqueId());
    }

    @EventHandler
    private void onQuit(PlayerQuitEvent event) {
        forgetAll(event.getPlayer().getUniqueId());
        linkedDeaths.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    private void onPostRespawn(PlayerPostRespawnEvent event) {
        forgetAll(event.getPlayer().getUniqueId());
    }

    public static boolean isParticipating(Player player) {
        if (!player.isOnline() || player.isDead() || player.getHealth() <= 0) return false;

        GameMode mode = player.getGameMode();
        return mode == GameMode.SURVIVAL || mode == GameMode.ADVENTURE;
    }

    private static double maxHealth(Player player) {
        AttributeInstance attribute = player.getAttribute(Attribute.MAX_HEALTH);
        return attribute == null ? 20 : attribute.getValue();
    }
}
