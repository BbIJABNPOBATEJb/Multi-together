package me.bbijabnpobatejb.multitogether.gui;

import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryInteractEvent;

import java.util.Collection;

public interface GuiService {

    void addGui(IGui gui);

    void removeGui(IGui gui);

    IGui getGui(InventoryInteractEvent event);

    IGui getGui(Player player);

    Collection<IGui> getAll();
}
