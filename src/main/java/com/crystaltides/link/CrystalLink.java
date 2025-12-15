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

        // Register Command
        getCommand("link").setExecutor(this);

        getLogger().info("CrystalLink has been enabled!");
    }

    @Override
    public void onDisable() {
        // Cancel all async tasks to prevent leaks or errors during reload
        Bukkit.getScheduler().cancelTasks(this);

        if (dataSource != null && !dataSource.isClosed()) {
            try {
                dataSource.close();
            } catch (Exception e) {
                getLogger().warning("Error closing database connection: " + e.getMessage());
            }
        }
        getLogger().info("CrystalLink has been disabled!");
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
                                "code VARCHAR(36), " + // Changed to VARCHAR(36) to accommodate UUID
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
        if (!(sender instanceof Player)) {
            sender.sendMessage(net.kyori.adventure.text.minimessage.MiniMessage.miniMessage()
                    .deserialize("<red>Este comando solo es para jugadores."));
            return true;
        }

        Player player = (Player) sender;

        if (dataSource == null || dataSource.isClosed()) {
            player.sendMessage(net.kyori.adventure.text.minimessage.MiniMessage.miniMessage()
                    .deserialize("<red>No has conectado el plugin a la base de datos."));
            return true;
        }

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
                player.sendMessage(net.kyori.adventure.text.Component.empty());
                player.sendMessage(net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize(
                        "<aqua><bold>[CrystalLink] <dark_gray>» <gray>Vinculación Web"));
                player.sendMessage(net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize(
                        "<gray>Haz clic en el siguiente enlace para vincular tu cuenta:"));
                player.sendMessage(net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize(
                        "<click:open_url:'" + link + "'><hover:show_text:'<aqua>Clic para abrir'><aqua><u>" + link
                                + "</u></hover></click>"));
                player.sendMessage(net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize(
                        "<red>Este enlace expira en 10 minutos."));
                player.sendMessage(net.kyori.adventure.text.Component.empty());

            } catch (SQLException e) {
                player.sendMessage(net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize(
                        "<red>Error al conectar con la base de datos."));
                e.printStackTrace();
            }
        });

        return true;
    }
}
