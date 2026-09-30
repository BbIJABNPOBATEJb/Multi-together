package me.bbijabnpobatejb.multitogether.gui;

import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryInteractEvent;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class GuiServiceImpl implements GuiService {

    private final Map<UUID, IGui> inventoryMap = new HashMap<>();

    @Override
    public void addGui(IGui gui) {
        inventoryMap.put(gui.getPlayer().getUniqueId(), gui);
    }

    @Override
    public void removeGui(IGui gui) {
        inventoryMap.remove(gui.getPlayer().getUniqueId(), gui);
    }

    @Override
    public IGui getGui(InventoryInteractEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            event.setCancelled(true);
            return null;
        }

        IGui gui = inventoryMap.get(player.getUniqueId());
        // Меню в карте, но открыто уже другое окно — это не наш клик. Верхнее окно берётся у
        // события, а не у InventoryView: в 1.20 это класс, с 1.21 — интерфейс, и вызов бы не слинковался
        if (gui == null || event.getInventory() != gui.getInventory()) return null;
        return gui;
    }

    @Override
    public IGui getGui(Player player) {
        return inventoryMap.get(player.getUniqueId());
    }

    @Override
    public Collection<IGui> getAll() {
        return new ArrayList<>(inventoryMap.values());
    }
}
