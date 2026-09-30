package me.bbijabnpobatejb.multitogether.chain;

import me.bbijabnpobatejb.multitogether.settings.ChainStyle;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * Цепь между двумя точками из блоков-отображений цепи.
 *
 * <p>Каждое звено — отдельный {@link BlockDisplay}: он стоит в начале отрезка, а поворот и
 * растяжение модели кладут его вдоль отрезка. Позиция сглаживается через teleport duration,
 * поворот и длина — через интерполяцию трансформации, поэтому цепь движется плавно, хотя
 * обновляется раз в тик.</p>
 *
 * <p>Пока цепь не натянута, она провисает параболой; где провис уходит в блок, звено
 * ложится на его верх — цепь лежит на земле, а не проходит сквозь неё.</p>
 */
final class ChainRenderer {

    /**
     * Сколько тиков клиент догоняет новое положение. Обновление каждый тик, поэтому 2 — плавно
     * и почти без запаздывания.
     */
    private static final int SMOOTH_TICKS = 2;

    /**
     * Желаемая длина звена в блоках. Модель цепи — блок высотой 1, при такой длине звенья не
     * выглядят сплющенными.
     */
    private static final double SEGMENT_LENGTH = 0.8;
    private static final float WIDTH = 0.9f;
    private static final int MAX_SEGMENTS = 48;

    private final List<BlockDisplay> segments = new ArrayList<>();
    private final List<Transformation> lastTransformations = new ArrayList<>();
    private World world;
    private ChainStyle style;

    void update(World world, Vector from, Vector to, double length, ChainStyle style) {
        if (this.world != world) {
            remove();
            this.world = world;
        }
        if (this.style != style) {
            this.style = style;
            for (BlockDisplay display : segments) {
                if (display != null && display.isValid()) display.setBlock(style.getMaterial().createBlockData());
            }
        }

        int count = Math.clamp((int) Math.ceil(length / SEGMENT_LENGTH), 2, MAX_SEGMENTS);
        resize(count);

        Vector[] points = curve(world, from, to, length, count);
        for (int i = 0; i < count; i++) {
            place(i, points[i], points[i + 1]);
        }
    }

    private void resize(int count) {
        while (segments.size() > count) {
            BlockDisplay display = segments.removeLast();
            lastTransformations.removeLast();
            if (display != null) display.remove();
        }
        while (segments.size() < count) {
            segments.add(null);
            lastTransformations.add(null);
        }
    }

    private void place(int index, Vector start, Vector end) {
        Vector direction = end.clone().subtract(start);
        float length = (float) direction.length();

        Quaternionf rotation = new Quaternionf();
        if (length > 1.0E-4f) {
            // Модель цепи вертикальна: её ось Y поворачивается вдоль отрезка
            rotation.rotationTo(0, 1, 0,
                    (float) (direction.getX() / length),
                    (float) (direction.getY() / length),
                    (float) (direction.getZ() / length));
        }

        // Модель блока занимает куб 0..1 — центр основания переносится в точку сущности
        Vector3f scale = new Vector3f(WIDTH, Math.max(length, 0.01f), WIDTH);
        Vector3f translation = rotation.transform(new Vector3f(-WIDTH / 2, 0, -WIDTH / 2));
        Transformation transformation = new Transformation(translation, rotation, scale, new Quaternionf());

        Location location = new Location(world, start.getX(), start.getY(), start.getZ(), 0, 0);
        BlockDisplay display = segments.get(index);

        if (display == null || !display.isValid()) {
            display = spawn(location, transformation);
            segments.set(index, display);
            lastTransformations.set(index, transformation);
            return;
        }

        if (display.getLocation().toVector().distanceSquared(start) > 1.0E-6) {
            display.teleport(location);
        }

        if (!transformation.equals(lastTransformations.get(index))) {
            display.setInterpolationDelay(0);
            display.setInterpolationDuration(SMOOTH_TICKS);
            display.setTransformation(transformation);
            lastTransformations.set(index, transformation);
        }
    }

    private BlockDisplay spawn(Location location, Transformation transformation) {
        Material material = style.getMaterial();
        return world.spawn(location, BlockDisplay.class, display -> {
            display.setPersistent(false);
            display.addScoreboardTag(ChainService.DISPLAY_TAG);
            display.setBlock(material.createBlockData());
            display.setBillboard(Display.Billboard.FIXED);
            display.setShadowRadius(0);
            display.setViewRange(2f);
            display.setTransformation(transformation);
            display.setTeleportDuration(SMOOTH_TICKS);
            display.setInterpolationDuration(SMOOTH_TICKS);
        });
    }

    /**
     * Точки цепи от {@code from} до {@code to}: {@code count + 1} штук.
     *
     * <p>Провис — парабола, глубина из того, насколько цепь длиннее расстояния между концами:
     * у параболы с провисом {@code s} на пролёте {@code d} длина ≈ {@code d + 8s²/(3d)}.
     * Провисает цепь вниз, поперёк самой себя; у почти вертикальной цепи уходит вбок.</p>
     */
    private static Vector[] curve(World world, Vector from, Vector to, double length, int count) {
        Vector chord = to.clone().subtract(from);
        double distance = chord.length();
        double slack = Math.max(0, length - distance);

        double sag = distance > 0.05 ? Math.sqrt(3 * distance * slack / 8) : slack / 2;
        sag = Math.min(sag, length / 2);

        Vector sagDirection = new Vector(0, -1, 0);
        if (distance > 1.0E-3) {
            Vector axis = chord.clone().multiply(1 / distance);
            sagDirection.subtract(axis.clone().multiply(sagDirection.dot(axis)));
            double horizontal = sagDirection.length();
            if (horizontal < 1.0E-3) {
                sagDirection = new Vector(1, 0, 0);
                horizontal = 0;
            } else {
                sagDirection.multiply(1 / horizontal);
            }
            // Вертикальная цепь провисает меньше: ей некуда, кроме как в сторону
            sag *= Math.max(horizontal, 0.35);
        }

        Vector[] points = new Vector[count + 1];
        for (int i = 0; i <= count; i++) {
            double t = (double) i / count;
            Vector point = from.clone().add(chord.clone().multiply(t));
            point.add(sagDirection.clone().multiply(sag * 4 * t * (1 - t)));

            if (i > 0 && i < count) liftOutOfBlocks(world, point);
            points[i] = point;
        }
        return points;
    }

    /**
     * Если точка внутри твёрдого блока — кладёт её на его верх. Не больше трёх блоков вверх
     * и только в загруженном чанке: грузить чанки ради картинки нельзя.
     */
    private static void liftOutOfBlocks(World world, Vector point) {
        int x = point.getBlockX();
        int z = point.getBlockZ();
        if (!world.isChunkLoaded(x >> 4, z >> 4)) return;

        for (int step = 0; step < 3; step++) {
            Block block = world.getBlockAt(x, point.getBlockY(), z);
            if (block.isPassable()) return;

            BoundingBox box = block.getBoundingBox();
            if (point.getY() >= box.getMaxY()) return;

            point.setY(box.getMaxY() + 0.03);
        }
    }

    void remove() {
        for (BlockDisplay display : segments) {
            if (display != null) display.remove();
        }
        segments.clear();
        lastTransformations.clear();
    }

    int size() {
        int alive = 0;
        for (BlockDisplay display : segments) {
            if (display != null && display.isValid()) alive++;
        }
        return alive;
    }
}
