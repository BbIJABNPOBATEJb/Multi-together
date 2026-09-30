package me.bbijabnpobatejb.multitogether.gui;

import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;

/**
 * Меню в сундуке — тот же каркас, что в core-lib-bukkit: {@link #draw()} раскладывает
 * предметы, клики приходят в {@link GuiItem}, {@link #setUpdater(long)} перерисовывает по таймеру.
 */
@Getter
public abstract class Gui implements IGui {

    private static GuiService guiService;
    private static Plugin plugin;

    public static void init(Plugin plugin, GuiService guiService) {
        Gui.guiService = guiService;
        Gui.plugin = plugin;
    }

    protected final int size;
    protected final Component title;

    protected Inventory inventory;
    protected Player player;
    protected BukkitTask updater = null;

    @Setter
    protected boolean disableAction = true;

    @Setter
    protected boolean allowItemMovement = false;

    private final Map<Integer, GuiItem> items = new HashMap<>();

    public Gui(@NonNull Player player, int rows, Component title) {
        this.player = player;
        this.size = rows * 9;
        this.title = title;
        this.inventory = Bukkit.createInventory(null, this.size, title);
    }

    @Override
    public void clearInventory() {
        items.clear();
        inventory.clear();
    }

    public void open() {
        draw();
        player.openInventory(inventory);

        onOpen();

        guiService.addGui(this);
    }

    @Override
    public void setUpdater(long ticks) {
        if (updater != null) {
            updater.cancel();
        }

        updater = Bukkit.getScheduler().runTaskTimer(plugin, this::draw, ticks, ticks);
    }

    @Override
    public void removeUpdater() {
        if (updater == null) {
            return;
        }

        updater.cancel();
        updater = null;
    }

    public void setItem(int slot, @NonNull ItemStack itemStack, BiConsumer<Player, ClickType> onClick) {
        setItem(slot, new GuiItem(itemStack, onClick));
    }

    public void setItem(int slot, @NonNull ItemStack itemStack) {
        setItem(slot, new GuiItem(itemStack));
    }

    public void setItem(int slot, @NonNull GuiItem item) {
        inventory.setItem(slot, item.getItem());
        items.put(slot, item);
    }

    public void removeItem(int slot) {
        GuiItem item = items.remove(slot);

        if (item == null) {
            return;
        }

        inventory.setItem(slot, null);
    }

    public void fill(@NonNull ItemStack itemStack, int from, int to) {
        for (int slot = from; slot <= to; slot++) {
            if (!items.containsKey(slot)) setItem(slot, itemStack);
        }
    }

    /**
     * Переключиться на другое меню, не закрывая окно: курсор остаётся на месте.
     */
    protected void switchTo(Gui other) {
        removeUpdater();
        guiService.removeGui(this);
        other.open();
    }

    public abstract void draw();

    public void onClose() {

    }

    public void onOpen() {

    }
}
