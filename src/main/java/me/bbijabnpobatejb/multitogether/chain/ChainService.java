package me.bbijabnpobatejb.multitogether.chain;

import me.bbijabnpobatejb.multitogether.link.Edge;
import me.bbijabnpobatejb.multitogether.link.LinkService;
import me.bbijabnpobatejb.multitogether.link.LinkType;
import me.bbijabnpobatejb.multitogether.settings.Settings;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Цепь: физика, картинка и совместные телепорты.
 *
 * <p>Физика — ограничение по длине, а не пружина на всю длину: пока цепь провисает, она
 * игроков не трогает. Натянулась — каждый тик обоим выдаётся импульс навстречу друг другу:
 * часть перетяжки плюс гашение скорости, с которой они расходятся. Импульс делится по
 * «массе»: кто стоит на земле — тяжелее летящего, присевший на земле упирается ещё сильнее.
 * Так висящего над обрывом можно удержать, а убегающий тащит стоящего, но медленнее.</p>
 *
 * <p>Скорость игрока сервер не знает (движение считает клиент), поэтому берётся из смещения
 * за прошлый тик, с тем же трением, что применит клиент, — иначе каждый импульс добавлял бы
 * игроку скорость, и его бы разгоняло, как на льду.</p>
 */
public final class ChainService implements Listener {

    public static final String DISPLAY_TAG = "multitogether_chain";

    /**
     * Какая доля скорости расхождения гасится за тик.
     */
    private static final double DAMPING = 0.8;
    private static final double MAX_IMPULSE = 1.5;
    private static final double MAX_HORIZONTAL_SPEED = 1.6;
    private static final double MAX_UP_SPEED = 1.2;
    private static final double MAX_DOWN_SPEED = 2.5;

    private static final double GROUND_FRICTION = 0.6 * 0.91;
    private static final double AIR_DRAG = 0.91;
    private static final double GRAVITY = 0.08;

    /**
     * Сколько тиков после рывка цепи игрок считается висящим на ней.
     */
    private static final long PULL_MEMORY = 60;

    private final Plugin plugin;
    private final LinkService links;
    private final Settings settings;

    private final Map<Edge, ChainRenderer> renderers = new HashMap<>();
    private final Map<Edge, Long> lastSound = new HashMap<>();
    private final Map<UUID, Location> lastLocation = new HashMap<>();

    /**
     * Тик, когда цепь последний раз тянула игрока: висящего на ней не кикает за полёт.
     */
    private final Map<UUID, Long> lastPulled = new HashMap<>();

    /**
     * Кого сейчас переносит сам плагин: их телепорты не должны тащить остальных по кругу.
     */
    private final Set<UUID> teleporting = new HashSet<>();

    private long tick;

    public ChainService(Plugin plugin, LinkService links, Settings settings) {
        this.plugin = plugin;
        this.links = links;
        this.settings = settings;
    }

    public void tick() {
        tick++;
        List<Edge> edges = links.edges(LinkType.CHAIN);

        Map<UUID, Player> players = new HashMap<>();
        Map<UUID, Vector> velocities = new HashMap<>();
        for (Edge edge : edges) {
            collect(edge.first(), players, velocities);
            collect(edge.second(), players, velocities);
        }

        Map<UUID, Vector> impulses = new HashMap<>();
        Set<Edge> visible = new HashSet<>();

        for (Edge edge : edges) {
            Player a = players.get(edge.first());
            Player b = players.get(edge.second());
            if (a == null || b == null || a.getWorld() != b.getWorld()) continue;

            if (!pull(edge, a, b, velocities, impulses)) continue;

            if (settings.isChainVisible()) {
                visible.add(edge);
                renderers.computeIfAbsent(edge, e -> new ChainRenderer())
                        .update(a.getWorld(), attachPoint(a), attachPoint(b), settings.getChainLength(), settings.getStyle());
            }
        }

        renderers.entrySet().removeIf(entry -> {
            if (visible.contains(entry.getKey())) return false;
            entry.getValue().remove();
            return true;
        });
        lastSound.keySet().retainAll(edges);

        impulses.forEach((uuid, impulse) -> {
            apply(players.get(uuid), velocities.get(uuid), impulse);
            lastPulled.put(uuid, tick);
        });
        lastPulled.values().removeIf(pulled -> tick - pulled > PULL_MEMORY);

        lastLocation.keySet().retainAll(players.keySet());
        players.forEach((uuid, player) -> lastLocation.put(uuid, player.getLocation()));
    }

    private void collect(UUID uuid, Map<UUID, Player> players, Map<UUID, Vector> velocities) {
        if (players.containsKey(uuid)) return;

        Player player = Bukkit.getPlayer(uuid);
        if (player == null || !isActive(player)) return;

        players.put(uuid, player);
        velocities.put(uuid, estimateMotion(player));
    }

    /**
     * Натягивает цепь пары. Возвращает {@code false}, если пару пришлось разнести телепортом —
     * тогда в этом тике её не рисуем.
     */
    private boolean pull(Edge edge, Player a, Player b, Map<UUID, Vector> velocities, Map<UUID, Vector> impulses) {
        Vector pa = attachPoint(a);
        Vector pb = attachPoint(b);
        Vector delta = pb.clone().subtract(pa);
        double distance = delta.length();
        double length = settings.getChainLength();

        if (distance > settings.snapDistance()) {
            snap(a, b, velocities);
            return false;
        }
        if (distance <= length || distance < 1.0E-6) return true;

        Vector normal = delta.multiply(1 / distance);
        Vector va = velocities.get(a.getUniqueId());
        Vector vb = velocities.get(b.getUniqueId());

        double separating = vb.clone().subtract(va).dot(normal);
        double strength = settings.getStiffness() * (distance - length) + DAMPING * Math.max(0, separating);
        strength = Math.min(strength, MAX_IMPULSE);

        double massA = mass(a);
        double massB = mass(b);
        double shareA = massB / (massA + massB);
        double shareB = massA / (massA + massB);

        impulses.computeIfAbsent(a.getUniqueId(), k -> new Vector()).add(normal.clone().multiply(strength * shareA));
        impulses.computeIfAbsent(b.getUniqueId(), k -> new Vector()).add(normal.clone().multiply(-strength * shareB));

        if (settings.isSounds() && strength > 0.35) {
            long last = lastSound.getOrDefault(edge, -100L);
            if (tick - last >= 10) {
                lastSound.put(edge, tick);
                Vector middle = pa.clone().add(pb).multiply(0.5);
                float pitch = 0.8f + ThreadLocalRandom.current().nextFloat() * 0.4f;
                a.getWorld().playSound(middle.toLocation(a.getWorld()), Sound.BLOCK_CHAIN_HIT, SoundCategory.PLAYERS, 0.8f, pitch);
            }
        }
        return true;
    }

    private void apply(Player player, Vector motion, Vector impulse) {
        if (player == null || motion == null) return;

        if (player.isInsideVehicle()) {
            Entity root = player.getVehicle();
            while (root.getVehicle() != null) root = root.getVehicle();
            root.setVelocity(clamp(root.getVelocity().add(impulse)));
            return;
        }

        player.setVelocity(clamp(motion.clone().add(impulse)));

        // Цепь поймала падающего — падение закончилось, копить урон дальше нечему
        if (impulse.getY() > 0.03) player.setFallDistance(0);
    }

    /**
     * Скорость, которая сейчас у игрока на клиенте: смещение за прошлый тик, после трения
     * и гравитации — ровно то, что клиент сделает со скоростью после шага.
     */
    @SuppressWarnings("deprecation") // isOnGround сообщает клиент — для физики этого и надо
    private Vector estimateMotion(Player player) {
        Location now = player.getLocation();
        Location before = lastLocation.get(player.getUniqueId());

        if (before == null || before.getWorld() != now.getWorld() || before.distanceSquared(now) > 16) {
            return player.getVelocity();
        }

        Vector moved = now.toVector().subtract(before.toVector());
        double horizontal;

        if (player.isInWater()) {
            horizontal = 0.8;
            moved.setY((moved.getY() - 0.02) * 0.8);
        } else if (player.isInLava()) {
            horizontal = 0.5;
            moved.setY((moved.getY() - 0.02) * 0.5);
        } else if (player.isFlying()) {
            horizontal = AIR_DRAG;
            moved.setY(moved.getY() * 0.6);
        } else if (player.isGliding()) {
            horizontal = 0.99;
            moved.setY(moved.getY() * 0.98);
        } else if (player.isOnGround()) {
            horizontal = GROUND_FRICTION;
            moved.setY(0);
        } else {
            horizontal = AIR_DRAG;
            moved.setY((moved.getY() - GRAVITY) * 0.98);
        }

        moved.setX(moved.getX() * horizontal);
        moved.setZ(moved.getZ() * horizontal);
        return moved;
    }

    @SuppressWarnings("deprecation")
    private double mass(Player player) {
        if (player.isInsideVehicle()) return 3;
        if (player.isFlying() || player.isGliding()) return 1;

        double mass = 1;
        if (player.isOnGround()) {
            mass += 1;
            if (settings.isSneakAnchor() && player.isSneaking()) mass += 4;
        }
        return mass;
    }

    /**
     * Физика уже не вытянет (лаг, телепорт чужим плагином, скакун): убежавшего возвращает
     * на длину цепи от напарника. Если там стена — прямо к напарнику.
     */
    private void snap(Player a, Player b, Map<UUID, Vector> velocities) {
        Vector pa = attachPoint(a);
        Vector pb = attachPoint(b);
        Vector normal = pb.clone().subtract(pa).normalize();

        double awayA = -velocities.get(a.getUniqueId()).dot(normal);
        double awayB = velocities.get(b.getUniqueId()).dot(normal);

        Player runner = awayB >= awayA ? b : a;
        Player anchor = runner == b ? a : b;
        Vector direction = runner == b ? normal : normal.clone().multiply(-1);

        Location target = anchor.getLocation().add(direction.multiply(settings.getChainLength() * 0.9));
        target.setYaw(runner.getLocation().getYaw());
        target.setPitch(runner.getLocation().getPitch());
        if (!isSafe(target)) {
            target = anchor.getLocation();
            target.setYaw(runner.getLocation().getYaw());
            target.setPitch(runner.getLocation().getPitch());
        }

        teleport(runner, target);
    }

    private static boolean isSafe(Location location) {
        Block feet = location.getBlock();
        Block head = feet.getRelative(0, 1, 0);
        return feet.isPassable() && head.isPassable();
    }

    /**
     * Точка крепления цепи — середина тела: у стоящего пояс, у ползущего и плывущего центр.
     */
    public static Vector attachPoint(Player player) {
        return player.getLocation().toVector().add(new Vector(0, player.getHeight() * 0.55, 0));
    }

    private boolean isActive(Player player) {
        return player.isOnline()
                && !player.isDead()
                && player.getGameMode() != GameMode.SPECTATOR
                && !teleporting.contains(player.getUniqueId());
    }

    private static Vector clamp(Vector velocity) {
        double horizontal = Math.hypot(velocity.getX(), velocity.getZ());
        if (horizontal > MAX_HORIZONTAL_SPEED) {
            double scale = MAX_HORIZONTAL_SPEED / horizontal;
            velocity.setX(velocity.getX() * scale);
            velocity.setZ(velocity.getZ() * scale);
        }
        velocity.setY(Math.clamp(velocity.getY(), -MAX_DOWN_SPEED, MAX_UP_SPEED));
        return velocity;
    }

    // ---------------------------------------------------------------- телепорты

    /**
     * Переносит игрока силами плагина: на время переноса он выпадает из физики, а его
     * телепорт не тащит остальных.
     */
    private void teleport(Player player, Location target) {
        UUID uuid = player.getUniqueId();
        teleporting.add(uuid);
        lastLocation.remove(uuid);

        // Попав в портал на той стороне, не улететь обратно, пока не отошёл
        player.setPortalCooldown(Math.max(player.getPortalCooldown(), 100));
        player.setFallDistance(0);

        player.teleportAsync(target, PlayerTeleportEvent.TeleportCause.PLUGIN)
                .whenComplete((result, error) -> Bukkit.getScheduler().runTask(plugin, () -> {
                    teleporting.remove(uuid);
                    lastLocation.remove(uuid);
                }));
    }

    /**
     * Все, кто связан с игроком цепью (и дальше по цепочке) и оказался в другом мире или
     * дальше {@link Settings#followDistance()}, переносятся к нему.
     */
    public int gather(Player leader) {
        if (!leader.isOnline() || leader.isDead()) return 0;

        Location destination = leader.getLocation();
        double follow = settings.followDistance();
        int moved = 0;

        for (UUID uuid : links.component(LinkType.CHAIN, leader.getUniqueId())) {
            if (uuid.equals(leader.getUniqueId()) || teleporting.contains(uuid)) continue;

            Player member = Bukkit.getPlayer(uuid);
            if (member == null || member.isDead() || member.getGameMode() == GameMode.SPECTATOR) continue;

            Location location = member.getLocation();
            if (location.getWorld() == destination.getWorld() && location.distance(destination) <= follow) continue;

            Location target = destination.clone();
            target.setYaw(location.getYaw());
            target.setPitch(location.getPitch());
            teleport(member, target);
            moved++;
        }
        return moved;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    private void onTeleport(PlayerTeleportEvent event) {
        if (!settings.isTeleportTogether()) return;

        Player player = event.getPlayer();
        if (teleporting.contains(player.getUniqueId())) return;
        if (!links.hasLinks(LinkType.CHAIN, player.getUniqueId())) return;

        switch (event.getCause()) {
            case DISMOUNT, EXIT_BED, SPECTATE -> {
                return;
            }
            default -> {
            }
        }

        // Смену мира ловит PlayerChangedWorldEvent — он приходит и от порталов
        if (event.getTo().getWorld() != event.getFrom().getWorld()) return;

        // Следующим тиком: игрок уже на месте, и его точку знаем точно
        Bukkit.getScheduler().runTask(plugin, () -> gather(player));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    private void onChangedWorld(PlayerChangedWorldEvent event) {
        if (!settings.isTeleportTogether()) return;

        Player player = event.getPlayer();
        if (teleporting.contains(player.getUniqueId())) return;
        if (!links.hasLinks(LinkType.CHAIN, player.getUniqueId())) return;

        Bukkit.getScheduler().runTask(plugin, () -> gather(player));
    }

    @EventHandler(priority = EventPriority.HIGH)
    private void onRespawn(PlayerRespawnEvent event) {
        if (!settings.isRespawnTogether()) return;
        // Выход из Энда — это тоже «возрождение»: здесь напарников тянет следом, а не наоборот
        if (event.getRespawnReason() == PlayerRespawnEvent.RespawnReason.END_PORTAL) return;

        Player player = event.getPlayer();
        if (!links.hasLinks(LinkType.CHAIN, player.getUniqueId())) return;

        Location spawn = event.getRespawnLocation();
        Player nearest = null;
        double best = Double.MAX_VALUE;

        for (UUID uuid : links.component(LinkType.CHAIN, player.getUniqueId())) {
            if (uuid.equals(player.getUniqueId())) continue;

            Player member = Bukkit.getPlayer(uuid);
            if (member == null || member.isDead() || member.getGameMode() == GameMode.SPECTATOR) continue;

            Location location = member.getLocation();
            double score = location.getWorld() == spawn.getWorld() ? location.distanceSquared(spawn) : Double.MAX_VALUE / 2;
            if (score < best) {
                best = score;
                nearest = member;
            }
        }

        if (nearest != null) {
            Location target = nearest.getLocation();
            target.setYaw(spawn.getYaw());
            target.setPitch(spawn.getPitch());
            event.setRespawnLocation(target);
        }
    }

    /**
     * Висящий на натянутой цепи над пропастью для сервера «летает»: без этого через
     * четыре секунды его выкинуло бы за полёт, если на сервере allow-flight=false.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    private void onKick(PlayerKickEvent event) {
        if (event.getCause() != PlayerKickEvent.Cause.FLYING_PLAYER) return;

        Long pulled = lastPulled.get(event.getPlayer().getUniqueId());
        if (pulled != null && tick - pulled <= PULL_MEMORY) event.setCancelled(true);
    }

    @EventHandler
    private void onQuit(PlayerQuitEvent event) {
        lastLocation.remove(event.getPlayer().getUniqueId());
        lastPulled.remove(event.getPlayer().getUniqueId());
        teleporting.remove(event.getPlayer().getUniqueId());
    }

    // ---------------------------------------------------------------- служебное

    public void refreshVisuals() {
        renderers.values().forEach(ChainRenderer::remove);
        renderers.clear();
    }

    public int displayCount() {
        int total = 0;
        for (ChainRenderer renderer : renderers.values()) {
            total += renderer.size();
        }
        return total;
    }

    public void shutdown() {
        refreshVisuals();
        lastLocation.clear();
        lastPulled.clear();
        teleporting.clear();
    }

    /**
     * Звенья от прошлого запуска (например, после /reload), если сервер не успел их убрать.
     */
    public static int removeStrayDisplays() {
        int removed = 0;
        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : new ArrayList<>(world.getEntities())) {
                if (entity.getScoreboardTags().contains(DISPLAY_TAG)) {
                    entity.remove();
                    removed++;
                }
            }
        }
        return removed;
    }
}
