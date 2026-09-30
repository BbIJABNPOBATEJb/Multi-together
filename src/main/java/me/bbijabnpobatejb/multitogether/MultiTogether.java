package me.bbijabnpobatejb.multitogether;

import dev.rollczi.litecommands.LiteCommands;
import dev.rollczi.litecommands.bukkit.LiteBukkitFactory;
import dev.rollczi.litecommands.bukkit.LiteBukkitMessages;
import dev.rollczi.litecommands.handler.result.ResultHandlerChain;
import dev.rollczi.litecommands.invalidusage.InvalidUsage;
import dev.rollczi.litecommands.invocation.Invocation;
import dev.rollczi.litecommands.message.Message;
import dev.rollczi.litecommands.permission.MissingPermissions;
import dev.rollczi.litecommands.schematic.Schematic;
import dev.rollczi.litecommands.schematic.SchematicFormat;
import lombok.Getter;
import me.bbijabnpobatejb.multitogether.chain.ChainService;
import me.bbijabnpobatejb.multitogether.command.LinkCommand;
import me.bbijabnpobatejb.multitogether.command.LinkTypesArgument;
import me.bbijabnpobatejb.multitogether.command.UnlinkCommand;
import me.bbijabnpobatejb.multitogether.gui.Gui;
import me.bbijabnpobatejb.multitogether.gui.GuiListener;
import me.bbijabnpobatejb.multitogether.gui.GuiService;
import me.bbijabnpobatejb.multitogether.gui.GuiServiceImpl;
import me.bbijabnpobatejb.multitogether.gui.IGui;
import me.bbijabnpobatejb.multitogether.link.LinkActions;
import me.bbijabnpobatejb.multitogether.link.LinkService;
import me.bbijabnpobatejb.multitogether.link.LinkTypes;
import me.bbijabnpobatejb.multitogether.settings.Settings;
import me.bbijabnpobatejb.multitogether.sync.InventoryLinker;
import me.bbijabnpobatejb.multitogether.sync.VitalsSync;
import me.bbijabnpobatejb.multitogether.util.Msg;
import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.logging.Level;

@Getter
public final class MultiTogether extends JavaPlugin {

    public static final String PERMISSION = "multitogether.admin";

    @Getter
    private static MultiTogether instance;

    private Settings settings;
    private LinkService links;
    private LinkActions actions;
    private ChainService chains;
    private VitalsSync vitals;
    private InventoryLinker inventories;
    private GuiService guiService;

    private LiteCommands<CommandSender> liteCommands;
    private BukkitTask ticker;

    @Override
    public void onEnable() {
        instance = this;

        int stray = ChainService.removeStrayDisplays();
        if (stray > 0) getLogger().info("Убраны звенья цепей от прошлого запуска: " + stray);

        settings = new Settings();
        links = new LinkService();
        chains = new ChainService(this, links, settings);
        vitals = new VitalsSync(this, links);
        actions = new LinkActions(links, chains, settings);

        links.addListener(vitals);
        registerListener(chains);
        registerListener(vitals);

        try {
            inventories = new InventoryLinker(this, links);
            links.addListener(inventories);
            registerListener(inventories);
        } catch (Throwable e) {
            // Сервер не той версии: без общего инвентаря плагин всё равно полезен
            inventories = null;
            getLogger().log(Level.SEVERE, "Общий инвентарь недоступен на этой версии сервера", e);
        }

        guiService = new GuiServiceImpl();
        Gui.init(this, guiService);
        registerListener(new GuiListener(guiService));

        ticker = getServer().getScheduler().runTaskTimer(this, this::tick, 1L, 1L);

        registerCommands();
    }

    private void tick() {
        try {
            vitals.tick();
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Ошибка синхронизации здоровья и голода", e);
        }
        try {
            chains.tick();
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Ошибка физики цепей", e);
        }
    }

    private void registerCommands() {
        liteCommands = LiteBukkitFactory.builder("multitogether", this)
                .commands(new LinkCommand(this), new UnlinkCommand(this))
                .argument(LinkTypes.class, new LinkTypesArgument())
                .message(LiteBukkitMessages.PLAYER_NOT_FOUND, (Message<Component, String>) input ->
                        Msg.error("Игрок <white>" + Msg.name(input) + "</white> не найден или не в сети"))
                .message(LiteBukkitMessages.PLAYER_ONLY, Msg.error("Эта команда только для игроков"))
                .message(LiteBukkitMessages.MISSING_PERMISSIONS, (Message<Component, MissingPermissions>) missing ->
                        Msg.error("Недостаточно прав: <white>" + missing.asJoinedText()))
                .message(LiteBukkitMessages.INVALID_NUMBER, (Message<Component, String>) input ->
                        Msg.error("<white>" + Msg.name(input) + "</white> — не число"))
                .result(Component.class, (Invocation<CommandSender> invocation, Component result, ResultHandlerChain<CommandSender> chain) ->
                        invocation.sender().sendMessage(result))
                .invalidUsage(this::invalidUsage)
                .schematicGenerator(SchematicFormat.angleBrackets())
                .build();
    }

    private void invalidUsage(Invocation<CommandSender> invocation, InvalidUsage<CommandSender> result, ResultHandlerChain<CommandSender> chain) {
        CommandSender sender = invocation.sender();
        Schematic schematic = result.getSchematic();

        if (schematic.isOnlyFirst()) {
            sender.sendMessage(Msg.error("Использование: <white>" + Msg.name(schematic.first())));
            return;
        }

        sender.sendMessage(Msg.error("Использование:"));
        for (String scheme : schematic.all()) {
            sender.sendMessage(Msg.mm(" <gray>•</gray> <white>" + Msg.name(scheme)));
        }
    }

    @Override
    public void onDisable() {
        if (liteCommands != null) liteCommands.unregister();
        if (ticker != null) ticker.cancel();

        if (guiService != null) {
            for (IGui gui : guiService.getAll()) {
                gui.removeUpdater();
                gui.getPlayer().closeInventory();
            }
        }

        if (chains != null) chains.shutdown();
        if (inventories != null) inventories.shutdown();

        instance = null;
    }

    private void registerListener(Listener listener) {
        getServer().getPluginManager().registerEvents(listener, this);
    }
}
