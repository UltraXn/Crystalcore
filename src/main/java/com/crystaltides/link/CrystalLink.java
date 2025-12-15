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

        getLogger().info("CrystalLink has been enabled!");
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

        // Create Table if not exists
        try (Connection conn = dataSource.getConnection();
                PreparedStatement stmt = conn.prepareStatement(
                        "CREATE TABLE IF NOT EXISTS web_verifications (" +
                                "uuid VARCHAR(36) PRIMARY KEY, " +
                                "player_name VARCHAR(16), " +
                                "code VARCHAR(36), " +
                                "expires_at BIGINT" +
                                ");")) {
            stmt.execute();
            getLogger().info("Database connected & table verified.");
            return true;
        } catch (SQLException e) {
            getLogger().severe("Could not connect to MySQL! Plugin will not work.");
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
                try (PreparedStatement deleteStmt = conn
                        .prepareStatement("DELETE FROM web_verifications WHERE uuid = ?")) {
                    deleteStmt.setString(1, player.getUniqueId().toString());
                    int rows = deleteStmt.executeUpdate();

                    if (rows > 0) {
                        player.sendMessage(net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize(
                                prefix + getConfig().getString("messages.unlink-success",
                                        "<green>Tu solicitud de vinculación ha sido cancelada.")));
                    } else {
                        player.sendMessage(net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize(
                                prefix + getConfig().getString("messages.unlink-not-found",
                                        "<red>No tienes ninguna solicitud pendiente.")));
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
        // Generate Token (UUID)
        String token = java.util.UUID.randomUUID().toString();
        long expiresAt = System.currentTimeMillis() + (10 * 60 * 1000); // 10 minutes expiration
        String domain = getConfig().getString("domain", "crystaltidesSMP.net");
        // Ensure domain has protocol
        if (!domain.startsWith("http")) {
            domain = "https://" + domain;
        }
        // Remove trailing slash if present
        if (domain.endsWith("/")) {
            domain = domain.substring(0, domain.length() - 1);
        }

        String link = domain + "/verify?token=" + token;

        // Run Async
        Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
            try (Connection conn = dataSource.getConnection()) {
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

                // Send clickable message to player
                String prefix = getConfig().getString("messages.prefix",
                        "<aqua><bold>[CrystalCore] <dark_gray>» <gray>");

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
                String prefix = getConfig().getString("messages.prefix",
                        "<aqua><bold>[CrystalCore] <dark_gray>» <gray>");
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
                // TODO: Check real verification status from DB when we have the users table
                // For now, return "Not Verified" or check pending
                return "Not Verified";
            }

            return null;
        }
    }
}
