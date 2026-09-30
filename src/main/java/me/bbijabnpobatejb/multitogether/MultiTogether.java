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
import me.bbijabnpobatejb.multitogether.command.LanguageArgument;
import me.bbijabnpobatejb.multitogether.command.LinkCommand;
import me.bbijabnpobatejb.multitogether.command.LinkTypesArgument;
import me.bbijabnpobatejb.multitogether.command.UnlinkCommand;
import me.bbijabnpobatejb.multitogether.gui.Gui;
import me.bbijabnpobatejb.multitogether.gui.GuiListener;
import me.bbijabnpobatejb.multitogether.gui.GuiService;
import me.bbijabnpobatejb.multitogether.gui.GuiServiceImpl;
import me.bbijabnpobatejb.multitogether.gui.IGui;
import me.bbijabnpobatejb.multitogether.i18n.Lang;
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

import java.util.Locale;
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

        saveDefaultConfig();
        reloadLanguage();

        int stray = ChainService.removeStrayDisplays();
        if (stray > 0) getLogger().info("Removed chain links left from a previous run: " + stray);

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
            getLogger().log(Level.SEVERE, "Shared inventory is not available on this server version", e);
        }

        guiService = new GuiServiceImpl();
        Gui.init(this, guiService);
        registerListener(new GuiListener(guiService));

        ticker = getServer().getScheduler().runTaskTimer(this, this::tick, 1L, 1L);

        registerCommands();
    }

    /**
     * Перечитывает config.yml и файлы переводов: после ручной правки не нужен перезапуск.
     */
    public void reloadLanguage() {
        reloadConfig();
        Lang.init(this);

        String code = getConfig().getString("language", Lang.DEFAULT).toLowerCase(Locale.ROOT);
        if (!Lang.isAvailable(code)) {
            getLogger().warning("Unknown language '" + code + "' in config.yml, using " + Lang.DEFAULT
                    + ". Available: " + String.join(", ", Lang.available()));
            code = Lang.DEFAULT;
        }
        Lang.load(code);
    }

    /**
     * Меняет язык и сохраняет его в config.yml.
     */
    public void setLanguage(String code) {
        Lang.load(code);
        getConfig().set("language", Lang.code());
        saveConfig();
    }

    private void tick() {
        try {
            vitals.tick();
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Health and hunger sync failed", e);
        }
        try {
            chains.tick();
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Chain physics failed", e);
        }
    }

    private void registerCommands() {
        liteCommands = LiteBukkitFactory.builder("multitogether", this)
                .commands(new LinkCommand(this), new UnlinkCommand(this))
                .argument(LinkTypes.class, new LinkTypesArgument())
                .argument(LanguageArgument.Code.class, new LanguageArgument())
                // Сообщения — функции, а не готовый текст: язык можно сменить на ходу
                .message(LiteBukkitMessages.PLAYER_NOT_FOUND, (Message<Component, String>) input ->
                        Msg.error(Lang.mm("command.player-not-found", "player", Msg.white(input))))
                .message(LiteBukkitMessages.PLAYER_ONLY, (Message<Component, Void>) nothing ->
                        Msg.error(Lang.mm("command.player-only")))
                .message(LiteBukkitMessages.MISSING_PERMISSIONS, (Message<Component, MissingPermissions>) missing ->
                        Msg.error(Lang.mm("command.no-permission", "permission", Msg.white(missing.asJoinedText()))))
                .message(LiteBukkitMessages.INVALID_NUMBER, (Message<Component, String>) input ->
                        Msg.error(Lang.mm("command.not-a-number", "input", Msg.white(input))))
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
            sender.sendMessage(Msg.error(Lang.mm("command.usage") + " " + Msg.white(schematic.first())));
            return;
        }

        sender.sendMessage(Msg.error(Lang.mm("command.usage")));
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
