package com.crystaltides.link;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Random;

public class CrystalLink extends JavaPlugin implements CommandExecutor {

    private HikariDataSource dataSource;
    private final Random random = new SecureRandom();
    private static final String CHARACTERS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"; // Removed confusing chars like I, 1,
                                                                                 // O, 0

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
                                "code VARCHAR(10), " +
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
            sender.sendMessage(ChatColor.RED + "Este comando solo es para jugadores.");
            return true;
        }

        Player player = (Player) sender;

        if (dataSource == null || dataSource.isClosed()) {
            player.sendMessage(ChatColor.RED + "No has conectado el plugin a la base de datos.");
            return true;
        }

        // Generate Code
        String code = generateCode(6);
        long expiresAt = System.currentTimeMillis() + (5 * 60 * 1000); // 5 minutes
        String domain = getConfig().getString("domain", "crystaltides.com");

        // Run Async
        Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
            try (Connection conn = dataSource.getConnection()) {
                // Delete old codes for this user
                try (PreparedStatement deleteStmt = conn
                        .prepareStatement("DELETE FROM web_verifications WHERE uuid = ?")) {
                    deleteStmt.setString(1, player.getUniqueId().toString());
                    deleteStmt.executeUpdate();
                }

                // Insert new code
                try (PreparedStatement insertStmt = conn.prepareStatement(
                        "INSERT INTO web_verifications (uuid, player_name, code, expires_at) VALUES (?, ?, ?, ?)")) {
                    insertStmt.setString(1, player.getUniqueId().toString());
                    insertStmt.setString(2, player.getName());
                    insertStmt.setString(3, code);
                    insertStmt.setLong(4, expiresAt);
                    insertStmt.executeUpdate();
                }

                // Send message to player
                player.sendMessage("");
                player.sendMessage(ChatColor.AQUA + "" + ChatColor.BOLD + " [CrystalLink] " + ChatColor.DARK_GRAY + "» "
                        + ChatColor.GRAY + "Vinculación Web");
                player.sendMessage(
                        ChatColor.GRAY + "Tu código de vinculación es: " + ChatColor.YELLOW + ChatColor.BOLD + code);
                player.sendMessage(
                        ChatColor.GRAY + "Ingrésalo en " + ChatColor.AQUA + ChatColor.UNDERLINE + domain + "/account");
                player.sendMessage(ChatColor.RED + "Expira en 5 minutos.");
                player.sendMessage("");

            } catch (SQLException e) {
                player.sendMessage(ChatColor.RED + "Error al conectar con la base de datos.");
                e.printStackTrace();
            }
        });

        return true;
    }

    private String generateCode(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(CHARACTERS.charAt(random.nextInt(CHARACTERS.length())));
        }
        return sb.toString();
    }
}
