package me.bbijabnpobatejb.multitogether.gui;

import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class GuiListener implements Listener {

    private final GuiService guiService;

    public GuiListener(GuiService guiService) {
        this.guiService = guiService;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    private void onInventoryClick(InventoryClickEvent event) {
        Player player = (Player) event.getWhoClicked();
        IGui gui = guiService.getGui(event);

        if (gui == null) return;

        if (gui.isAllowItemMovement()) {
            if (event.getClickedInventory() == player.getInventory()) {
                event.setCancelled(false);
                return;
            }

            if (event.getClickedInventory() == gui.getInventory()) {
                event.setCancelled(true);
                return;
            }

            return;
        }

        if (!gui.getPlayer().getUniqueId().equals(player.getUniqueId())) {
            return;
        }

        event.setCancelled(event.getClick().isShiftClick() || gui.isDisableAction());

        int slot = event.getRawSlot();

        if (slot >= 0 && slot < gui.getInventory().getSize()) {
            event.setCancelled(true);

            GuiItem guiItem = gui.getItems().get(slot);
            if (guiItem == null || gui.getInventory().getItem(slot) == null) {
                return;
            }

            if (DelayUtil.hasDelay("gui_click", player)) {
                return;
            }

            DelayUtil.putDelay("gui_click", player, 200L);
            guiItem.getOnClick().accept(player, event.getClick());
            player.playSound(player.getLocation(), Sound.BLOCK_LEVER_CLICK, 1, 1);
        }
    }

    @EventHandler
    private void onInventoryDrag(InventoryDragEvent event) {
        IGui gui = guiService.getGui(event);

        if (gui == null) return;

        if (gui.isAllowItemMovement()) {
            boolean isPlayerInventory = event.getWhoClicked().getInventory() == event.getInventory();
            if (isPlayerInventory) {
                event.setCancelled(false);
                return;
            }

            event.setCancelled(true);
            return;
        }

        if (!event.getWhoClicked().getUniqueId().equals(gui.getPlayer().getUniqueId())) {
            return;
        }

        if (gui.isDisableAction()) {
            event.setCancelled(true);
        }

        for (int slot : event.getRawSlots()) {
            if (slot >= 0 && slot < gui.getSize()) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler
    private void onInventoryClose(InventoryCloseEvent e) {
        Player player = (Player) e.getPlayer();

        IGui gui = guiService.getGui(player);

        if (gui == null || e.getInventory() != gui.getInventory()) {
            return;
        }

        guiService.removeGui(gui);

        gui.removeUpdater();
        gui.onClose();
    }

    @EventHandler
    private void onQuit(PlayerQuitEvent event) {
        IGui gui = guiService.getGui(event.getPlayer());
        if (gui == null) return;

        guiService.removeGui(gui);
        gui.removeUpdater();
    }
}
