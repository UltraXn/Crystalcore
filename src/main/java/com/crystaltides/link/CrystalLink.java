package com.crystaltides.link;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

public class CrystalLink extends JavaPlugin {

    private HikariDataSource dataSource;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        // Initialize Database
        if (!setupDatabase()) {
            getLogger().warning("Failed to connect to the database. Plugin will run in limited mode.");
        }

        // Register Commands
        getCommand("link").setExecutor(this);
        getCommand("unlink").setExecutor(this);
        getCommand("crystallink").setExecutor(this);

        // Register PlaceholderAPI
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new CrystalLinkExpansion(this).register();
        }

        // Start automatic cleanup of expired codes
        startCleanupTask();

        getLogger().info("CrystalLink has been enabled!");
    }

    private void startCleanupTask() {
        // Run cleanup every 30 minutes (1200 ticks * 30 = 36000 ticks)
        // 20 ticks = 1 second. 30 mins = 1800 seconds = 36000 ticks.
        long interval = 20L * 60 * 30;

        Bukkit.getScheduler().runTaskTimerAsynchronously(this, () -> {
            if (dataSource == null || dataSource.isClosed())
                return;

            try (Connection conn = dataSource.getConnection();
                    PreparedStatement stmt = conn
                            .prepareStatement("DELETE FROM web_verifications WHERE expires_at < ?")) {
                stmt.setLong(1, System.currentTimeMillis());
                int deleted = stmt.executeUpdate();
                if (deleted > 0) {
                    getLogger().info("Cleaned up " + deleted + " expired verification codes.");
                }
            } catch (SQLException e) {
                getLogger().warning("Error cleaning up expired codes: " + e.getMessage());
            }
        }, interval, interval);
    }

    @Override
    public void onDisable() {
        // Cancel all async tasks to prevent leaks or errors during reload
        Bukkit.getScheduler().cancelTasks(this);
        closeDatabase();
        getLogger().info("CrystalLink has been disabled!");
    }

    private void closeDatabase() {
        if (dataSource != null && !dataSource.isClosed()) {
            try {
                dataSource.close();
            } catch (Exception e) {
                getLogger().warning("Error closing database connection: " + e.getMessage());
            }
        }
    }

    private boolean setupDatabase() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:mysql://" + getConfig().getString("mysql.host") + ":"
                + getConfig().getString("mysql.port") + "/" + getConfig().getString("mysql.database"));
        config.setUsername(getConfig().getString("mysql.username"));
        config.setPassword(getConfig().getString("mysql.password"));
        config.addDataSourceProperty("cachePrepStmts", "true");
        config.addDataSourceProperty("prepStmtCacheSize", "250");
        config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
        config.setConnectionTimeout(5000); // 5 seconds timeout

        try {
            dataSource = new HikariDataSource(config);
        } catch (Exception e) {
            getLogger().severe("Could not initialize database pool: " + e.getMessage());
            return false;
        }

        // Create Tables if not exists
        try (Connection conn = dataSource.getConnection()) {
            // 1. Table for pending codes (Temporary)
            try (PreparedStatement stmt = conn.prepareStatement(
                    "CREATE TABLE IF NOT EXISTS web_verifications (" +
                            "uuid VARCHAR(36) PRIMARY KEY, " +
                            "player_name VARCHAR(16), " +
                            "code VARCHAR(64), " +
                            "expires_at BIGINT" +
                            ");")) {
                stmt.execute();
            }

            // 2. Table for linked accounts (Permanent)
            try (PreparedStatement stmt = conn.prepareStatement(
                    "CREATE TABLE IF NOT EXISTS linked_accounts (" +
                            "uuid VARCHAR(36) PRIMARY KEY, " +
                            "player_name VARCHAR(16), " +
                            "web_user_id VARCHAR(100), " +
                            "linked_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                            ");")) {
                stmt.execute();
            }

            // Migration for code column size
            try (PreparedStatement stmt = conn.prepareStatement(
                    "ALTER TABLE web_verifications MODIFY code VARCHAR(64)")) {
                stmt.execute();
            } catch (SQLException e) {
                getLogger().warning("Could not update 'code' column size: " + e.getMessage());
            }

            getLogger().info("Database connected & tables verified.");
            return true;
        } catch (SQLException e) {
            getLogger().severe("Could not connect to MySQL! Plugin will not work.");
            e.printStackTrace();
            return false;
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String prefix = getConfig().getString("messages.prefix", "<aqua><bold>[CrystalCore] <dark_gray>» <gray>");

        if (command.getName().equalsIgnoreCase("crystallink")) {
            if (!sender.hasPermission("crystallink.admin")) {
                sender.sendMessage(net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize(
                        prefix + getConfig().getString("messages.no-permission",
                                "<red>No tienes permiso para usar este comando.")));
                return true;
            }

            if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
                reloadConfig();
                closeDatabase();
                if (setupDatabase()) {
                    sender.sendMessage(net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize(
                            prefix + getConfig().getString("messages.reload-success",
                                    "<green>Configuración recargada y base de datos reconectada.")));
                } else {
                    sender.sendMessage(net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize(
                            prefix + "<red>Error al reconectar la base de datos. Revisa la consola."));
                }
                return true;
            }

            sender.sendMessage(net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize(
                    prefix + "<red>Uso: /crystallink reload"));
            return true;
        }

        if (!(sender instanceof Player)) {
            sender.sendMessage(net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize(
                    prefix + getConfig().getString("messages.only-players",
                            "<red>Este comando solo es para jugadores.")));
            return true;
        }

        Player player = (Player) sender;

        if (dataSource == null || dataSource.isClosed()) {
            sender.sendMessage(net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize(
                    prefix + getConfig().getString("messages.db-error",
                            "<red>No has conectado el plugin a la base de datos.")));
            return true;
        }

        if (command.getName().equalsIgnoreCase("unlink")) {
            handleUnlink(player);
            return true;
        }

        // Handle /link
        handleLink(player);
        return true;
    }

    private void handleUnlink(Player player) {
        String prefix = getConfig().getString("messages.prefix", "<aqua><bold>[CrystalCore] <dark_gray>» <gray>");
        Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
            try (Connection conn = dataSource.getConnection()) {
                // Delete from permanent table
                try (PreparedStatement deleteStmt = conn
                        .prepareStatement("DELETE FROM linked_accounts WHERE uuid = ?")) {
                    deleteStmt.setString(1, player.getUniqueId().toString());
                    int rows = deleteStmt.executeUpdate();

                    // Also delete any pending verification codes
                    try (PreparedStatement deletePending = conn
                            .prepareStatement("DELETE FROM web_verifications WHERE uuid = ?")) {
                        deletePending.setString(1, player.getUniqueId().toString());
                        deletePending.executeUpdate();
                    }

                    if (rows > 0) {
                        player.sendMessage(net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize(
                                prefix + getConfig().getString("messages.unlink-success",
                                        "<green>Tu cuenta ha sido desvinculada exitosamente.")));
                    } else {
                        player.sendMessage(net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize(
                                prefix + getConfig().getString("messages.unlink-not-found",
                                        "<red>No tienes ninguna cuenta vinculada.")));
                    }
                }
            } catch (SQLException e) {
                player.sendMessage(net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize(
                        prefix + getConfig().getString("messages.db-connection-error",
                                "<red>Error al conectar con la base de datos.")));
                e.printStackTrace();
            }
        });
    }

    private void handleLink(Player player) {
        String prefix = getConfig().getString("messages.prefix",
                "<aqua><bold>[CrystalCore] <dark_gray>» <gray>");

        Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
            try (Connection conn = dataSource.getConnection()) {
                // 1. Check if already linked
                try (PreparedStatement checkStmt = conn
                        .prepareStatement("SELECT uuid FROM linked_accounts WHERE uuid = ?")) {
                    checkStmt.setString(1, player.getUniqueId().toString());
                    if (checkStmt.executeQuery().next()) {
                        player.sendMessage(net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize(
                                prefix + getConfig().getString("messages.already-linked",
                                        "<red>Ya tienes una cuenta vinculada. Usa /unlink para desvincularla.")));
                        return;
                    }
                }

                // 2. Generate Token logic
                String token = java.util.UUID.randomUUID().toString();
                long expiresAt = System.currentTimeMillis() + (10 * 60 * 1000); // 10 minutes expiration
                String domain = getConfig().getString("domain", "crystaltidesSMP.net");
                if (!domain.startsWith("http")) {
                    domain = "https://" + domain;
                }
                if (domain.endsWith("/")) {
                    domain = domain.substring(0, domain.length() - 1);
                }

                String link = domain + "/verify?token=" + token;

                // Delete old codes for this user
                try (PreparedStatement deleteStmt = conn
                        .prepareStatement("DELETE FROM web_verifications WHERE uuid = ?")) {
                    deleteStmt.setString(1, player.getUniqueId().toString());
                    deleteStmt.executeUpdate();
                }

                // Insert new token
                try (PreparedStatement insertStmt = conn.prepareStatement(
                        "INSERT INTO web_verifications (uuid, player_name, code, expires_at) VALUES (?, ?, ?, ?)")) {
                    insertStmt.setString(1, player.getUniqueId().toString());
                    insertStmt.setString(2, player.getName());
                    insertStmt.setString(3, token);
                    insertStmt.setLong(4, expiresAt);
                    insertStmt.executeUpdate();
                }

                // Send clickable message
                player.sendMessage(net.kyori.adventure.text.Component.empty());
                player.sendMessage(net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize(
                        prefix + getConfig().getString("messages.link-generated", "Vinculación Web")));
                player.sendMessage(net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize(
                        "<gray>" + getConfig().getString("messages.link-code",
                                "Haz clic en el siguiente enlace para vincular tu cuenta:")));

                String hoverText = getConfig().getString("messages.link-hover", "<aqua>Clic para abrir");
                player.sendMessage(net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize(
                        "<click:open_url:'" + link + "'><hover:show_text:'" + hoverText + "'><aqua><u>" + link
                                + "</u></hover></click>"));

                player.sendMessage(net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize(
                        getConfig().getString("messages.link-expiration", "<red>Este enlace expira en 10 minutos.")));
                player.sendMessage(net.kyori.adventure.text.Component.empty());

            } catch (SQLException e) {
                player.sendMessage(net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize(
                        prefix + getConfig().getString("messages.db-connection-error",
                                "<red>Error al conectar con la base de datos.")));
                e.printStackTrace();
            }
        });
    }

    // Inner class for PlaceholderAPI expansion
    public static class CrystalLinkExpansion extends me.clip.placeholderapi.expansion.PlaceholderExpansion {
        private final CrystalLink plugin;

        public CrystalLinkExpansion(CrystalLink plugin) {
            this.plugin = plugin;
        }

        @Override
        public String getIdentifier() {
            return "crystallink";
        }

        @Override
        public String getAuthor() {
            return "UltraXn";
        }

        @Override
        public String getVersion() {
            return "1.0";
        }

        @Override
        public boolean persist() {
            return true;
        }

        @Override
        public String onPlaceholderRequest(Player player, String identifier) {
            if (player == null) {
                return "";
            }

            // %crystallink_status%
            if (identifier.equals("status")) {
                // Check database synchronously (Warning: PAPI requests are usually sync, so we
                // need a cached value or fast DB)
                // For better performance, we should cache this state on join/link/unlink.
                // But for now, let's do a quick check.
                if (plugin.dataSource != null && !plugin.dataSource.isClosed()) {
                    try (Connection conn = plugin.dataSource.getConnection();
                            PreparedStatement stmt = conn
                                    .prepareStatement("SELECT 1 FROM linked_accounts WHERE uuid = ?")) {
                        stmt.setString(1, player.getUniqueId().toString());
                        if (stmt.executeQuery().next()) {
                            return plugin.getConfig().getString("messages.status-verified", "Verificado");
                        }
                    } catch (SQLException e) {
                        e.printStackTrace();
                    }
                }
                return plugin.getConfig().getString("messages.status-not-verified", "No Verificado");
            }

            return null;
        }
    }
}
