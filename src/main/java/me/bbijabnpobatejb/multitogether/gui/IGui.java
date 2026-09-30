package me.bbijabnpobatejb.multitogether.gui;

import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

import java.util.Map;

public interface IGui {

    void onClose();

    void clearInventory();

    int getSize();

    boolean isDisableAction();

    void setDisableAction(boolean isDisableAction);

    void setUpdater(long ticks);

    void removeUpdater();

    Player getPlayer();

    Map<Integer, GuiItem> getItems();

    boolean isAllowItemMovement();

    void setAllowItemMovement(boolean allow);

    Inventory getInventory();
}
