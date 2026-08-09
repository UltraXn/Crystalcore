package com.crystaltides.core;

import com.crystaltides.core.managers.ModuleManager;
import com.crystaltides.core.modules.DatabaseModule;
import com.crystaltides.core.modules.ProfileModule;
import com.crystaltides.core.modules.WebBridgeModule;
import com.crystaltides.core.modules.BancoModule;
import com.crystaltides.core.modules.StaffStatusModule;
import com.crystaltides.core.modules.GachaModule;
import com.crystaltides.core.modules.WebSocketModule;
import com.crystaltides.core.modules.RedisModule;
import com.crystaltides.core.modules.DuelModule;
import com.crystaltides.core.commands.MoneyCommand;
import com.crystaltides.core.profile.CrystalProfile;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import com.crystaltides.core.modules.AIEventStreamModule;
import com.crystaltides.core.commands.MisionesCommand;

import com.crystaltides.core.bridge.CrystalBridgeAPI;

public class CrystalCore extends JavaPlugin {

    private ModuleManager moduleManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        validateConfig();

        CrystalBridgeAPI.init(this);

        this.moduleManager = new ModuleManager(this);

        // Register Modules (Order matters for dependencies)
        moduleManager.registerModule(new DatabaseModule(this));
        moduleManager.registerModule(new RedisModule(this));
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
        moduleManager.registerModule(new DuelModule(this));
        moduleManager.registerModule(new AIEventStreamModule(this));

        // Enable Modules
        moduleManager.enableModules();

        getCommand("crystalcore").setExecutor(this);
        getCommand("money").setExecutor(new MoneyCommand(this));
        if (getCommand(CMD_RULES) != null) getCommand(CMD_RULES).setExecutor(this);
        if (getCommand(CMD_REPORT) != null) getCommand(CMD_REPORT).setExecutor(this);
        if (getCommand(CMD_LANG) != null) getCommand(CMD_LANG).setExecutor(this);
        if (getCommand("misiones") != null) getCommand("misiones").setExecutor(new MisionesCommand(this));

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

    private void validateConfig() {
        String dbPassword = getConfig().getString("database.password", "");
        if (dbPassword.isEmpty() || dbPassword.equals("password")) {
            getLogger().severe("§c[SECURITY] Database password is set to the default value or empty! "
                    + "Please set a strong password in config.yml.");
        }

        String redisPassword = getConfig().getString("redis.password", "");
        if (redisPassword.isEmpty()) {
            getLogger().warning("§e[SECURITY] Redis password is empty. "
                    + "If Redis is exposed beyond localhost, set a password in config.yml.");
        }

        String wsToken = getConfig().getString("websocket.secret-token", "");
        if (wsToken.isEmpty() || wsToken.equals("change_me_to_something_secure") || wsToken.equals("changeme")) {
            getLogger().severe("§c[SECURITY] WebSocket secret-token is set to the default value! "
                    + "Please change it in config.yml to a secure, random string.");
        }
    }

    private static final String CMD_RULES = "rules";
    private static final String CMD_REPORT = "report";
    private static final String CMD_LANG = "lang";
    private static final String KEY_LANG = "language";

    private String getPlayerLanguage(CommandSender sender) {
        if (sender instanceof Player player) {
            ProfileModule profileModule = moduleManager.getModule(ProfileModule.class);
            if (profileModule != null) {
                CrystalProfile profile = profileModule.getProfile(player.getUniqueId());
                if (profile != null) {
                    return profile.getLanguage();
                }
            }
        }
        return getConfig().getString(KEY_LANG, "es");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String msgPrefix = getConfig().getString("messages.prefix", "§b§l[CrystalCore] §8» §7");
        String lang = getPlayerLanguage(sender);
        String name = command.getName().toLowerCase();

        if (name.equals(CMD_LANG) || name.equals(KEY_LANG) || name.equals("idioma")) {
            return handleLangCommand(sender, args, msgPrefix);
        }
        if (name.equals("crystalcore")) {
            return handleCrystalCoreCommand(sender, args, msgPrefix, lang);
        }
        if (name.equals(CMD_RULES) || name.equals("reglas")) {
            return handleRulesCommand(sender, msgPrefix, lang);
        }
        if (name.equals(CMD_REPORT) || name.equals("reportar")) {
            return handleReportCommand(sender, args, msgPrefix, lang);
        }

        return false;
    }

    private boolean handleLangCommand(CommandSender sender, String[] args, String msgPrefix) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(msgPrefix + "Server default language: " + getConfig().getString(KEY_LANG, "es"));
            return true;
        }

        ProfileModule profileModule = moduleManager.getModule(ProfileModule.class);
        CrystalProfile profile = profileModule != null ? profileModule.getProfile(player.getUniqueId()) : null;

        if (args.length == 0) {
            String currentLang = profile != null ? profile.getLanguage().toUpperCase() : "ES";
            sender.sendMessage(msgPrefix + "§7Idioma actual / Current language: §e" + currentLang);
            sender.sendMessage(msgPrefix + "§7Cambiar / Change: §e/lang es §7o §e/lang en");
            return true;
        }

        String chosen = args[0].toLowerCase();
        if (chosen.equals("en") || chosen.equals("english")) {
            if (profile != null) profile.setLanguage("en");
            sender.sendMessage(msgPrefix + "§aLanguage set to English (EN).");
            return true;
        }
        if (chosen.equals("es") || chosen.equals("spanish") || chosen.equals("español")) {
            if (profile != null) profile.setLanguage("es");
            sender.sendMessage(msgPrefix + "§aIdioma establecido a Español (ES).");
            return true;
        }

        sender.sendMessage(msgPrefix + "§cUso / Usage: /lang [es|en]");
        return false;
    }

    private boolean handleCrystalCoreCommand(CommandSender sender, String[] args, String msgPrefix, String lang) {
        if (!sender.hasPermission("crystalcore.admin")) {
            sender.sendMessage(msgPrefix + ("en".equals(lang) ? "§cYou do not have permission." : "§cNo tienes permiso."));
            return false;
        }
        if (args.length > 0 && "reload".equalsIgnoreCase(args[0])) {
            reloadConfig();
            moduleManager.disableModules();
            moduleManager.enableModules();
            sender.sendMessage(msgPrefix + ("en".equals(lang) ? "§aConfig and Modules reloaded." : "§aConfiguración y Módulos recargados."));
            return true;
        }
        if (args.length > 0 && "scan".equalsIgnoreCase(args[0])) {
            return executeScanCommand(sender, msgPrefix, lang);
        }

        sender.sendMessage(msgPrefix + "Help:");
        sender.sendMessage(msgPrefix + "§e/crystalcore reload");
        sender.sendMessage(msgPrefix + "§e/crystalcore scan");
        return true;
    }

    private boolean executeScanCommand(CommandSender sender, String msgPrefix, String lang) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(msgPrefix + ("en".equals(lang) ? "§cPlayers only." : "§cSolo jugadores."));
            return false;
        }

        GachaModule gacha = moduleManager.getModule(GachaModule.class);
        if (gacha != null && gacha.isEnabled()) {
            gacha.scanAndSync(player);
            sender.sendMessage(msgPrefix + ("en".equals(lang) ? "§aInventory scan started." : "§aEscaneo de inventario iniciado."));
            return true;
        }

        sender.sendMessage(msgPrefix + ("en".equals(lang) ? "§cGachaScanner module is disabled." : "§cEl módulo GachaScanner está desactivado."));
        return false;
    }

    private boolean handleRulesCommand(CommandSender sender, String msgPrefix, String lang) {
        if ("en".equals(lang)) {
            sender.sendMessage(msgPrefix + "§b§lCRYSTALTIDES SMP OFFICIAL RULES");
            sender.sendMessage("§7Rule 1: Respect staff and the community.");
            sender.sendMessage("§7Rule 2: No hacks, dupes, or toxic behavior.");
            sender.sendMessage("§7View full rulebook at: §ahttps://crystaltidessmp.net/rules");
        } else {
            sender.sendMessage(msgPrefix + "§b§lREGLAMENTO OFICIAL DE CRYSTALTIDES SMP");
            sender.sendMessage("§7Paso 1: Respeta al staff y a la comunidad.");
            sender.sendMessage("§7Paso 2: Sin hacks, dupes ni comportamiento tóxico.");
            sender.sendMessage("§7Consulta la lista completa en: §ahttps://crystaltidessmp.net/rules");
        }
        return true;
    }

    private boolean handleReportCommand(CommandSender sender, String[] args, String msgPrefix, String lang) {
        if (args.length < 2) {
            sender.sendMessage(msgPrefix + ("en".equals(lang) ? "§cUsage: /report <player> <reason>" : "§cUso correcto: /report <jugador> <motivo>"));
            return false;
        }
        String target = args[0];
        sender.sendMessage(msgPrefix + ("en".equals(lang) 
            ? "§aReport submitted against §e" + target + "§a. Staff will review it shortly."
            : "§aReporte enviado sobre §e" + target + "§a. El staff lo revisará pronto."));
        return true;
    }
}
