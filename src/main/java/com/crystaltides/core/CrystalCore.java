package com.crystaltides.core;

import com.crystaltides.core.managers.ModuleManager;
import com.crystaltides.core.modules.DatabaseModule;
import com.crystaltides.core.modules.ProfileModule;
import com.crystaltides.core.modules.WebBridgeModule;
import com.crystaltides.core.modules.BancoModule;
import com.crystaltides.core.modules.StaffStatusModule;
import com.crystaltides.core.modules.GachaModule;
import com.crystaltides.core.modules.WebSocketModule;
import com.crystaltides.core.commands.MoneyCommand;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

public class CrystalCore extends JavaPlugin {

    private ModuleManager moduleManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        this.moduleManager = new ModuleManager(this);

        // Register Modules (Order matters for dependencies)
        moduleManager.registerModule(new DatabaseModule(this));
        // Banco Bridge (SQLite Economy)
        moduleManager.registerModule(new BancoModule(this));
        // Staff Status System
        moduleManager.registerModule(new StaffStatusModule(this));
        // Gacha Inventory Scanner
        moduleManager.registerModule(new GachaModule(this));
        // WebSocket Server (Realtime)
        moduleManager.registerModule(new WebSocketModule(this));

        moduleManager.registerModule(new ProfileModule(this));
        moduleManager.registerModule(new WebBridgeModule(this));

        // Enable Modules
        moduleManager.enableModules();

        getCommand("crystalcore").setExecutor(this);
        getCommand("money").setExecutor(new MoneyCommand(this));

        getLogger().info("CrystalCore has been enabled (Modular Mode)!");
    }

    @Override
    public void onDisable() {
        if (moduleManager != null) {
            moduleManager.disableModules();
        }
        getLogger().info("CrystalCore has been disabled!");
    }

    public ModuleManager getModuleManager() {
        return moduleManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String msgPrefix = getConfig().getString("messages.prefix", "§b§l[CrystalCore] §8» §7");

        if (command.getName().equalsIgnoreCase("crystalcore")) {
            if (!sender.hasPermission("crystalcore.admin")) {
                sender.sendMessage(msgPrefix + "§cNo tienes permiso.");
                return true;
            }
            if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
                reloadConfig();
                moduleManager.disableModules();
                moduleManager.enableModules();
                sender.sendMessage(msgPrefix + "§aConfiguración y Módulos recargados.");
                return true;
            }
            if (args.length > 0 && args[0].equalsIgnoreCase("scan")) {
                if (sender instanceof org.bukkit.entity.Player) {
                    GachaModule gacha = moduleManager.getModule(GachaModule.class);
                    if (gacha != null && gacha.isEnabled()) {
                        gacha.scanAndSync((org.bukkit.entity.Player) sender);
                        sender.sendMessage(msgPrefix + "§aEscaneo de inventario iniciado.");
                    } else {
                        sender.sendMessage(msgPrefix + "§cEl módulo GachaScanner está desactivado.");
                    }
                } else {
                    sender.sendMessage(msgPrefix + "§cSolo jugadores.");
                }
                return true;
            }
            if (args.length > 1 && args[0].equalsIgnoreCase("sync")) {
                String targetName = args[1];
                org.bukkit.entity.Player target = org.bukkit.Bukkit.getPlayer(targetName);
                if (target != null) {
                    ProfileModule profileModule = moduleManager.getModule(ProfileModule.class);
                    if (profileModule != null) {
                        profileModule.reloadProfile(target.getUniqueId());
                        sender.sendMessage(msgPrefix + "§aPerfil de " + target.getName() + " sincronizado.");
                    }
                } else {
                    sender.sendMessage(msgPrefix + "§cJugador no encontrado o desconectado.");
                }
                return true;
            }
            sender.sendMessage(msgPrefix + "Help:");
            sender.sendMessage(msgPrefix + "§e/crystalcore reload");
            sender.sendMessage(msgPrefix + "§e/crystalcore scan");
            return true;
        }
        return false;
    }
}
