package com.crystaltides.core;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

public class CrystalCore extends JavaPlugin implements Listener {

    private HikariDataSource dataSource;
    private final Set<UUID> linkedPlayers = new HashSet<>();

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
        getCommand("crystalcore").setExecutor(this);

        // Register PlaceholderAPI
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new CrystalCoreExpansion(this).register();
        }

        // Register Events
        getServer().getPluginManager().registerEvents(this, this);

        // Start automatic cleanup of expired codes
        startCleanupTask();

        // Start Secure Command Queue (RCON Replacement Alternative)
        startCommandQueueTask();

        getLogger().info("CrystalCore has been enabled!");
    }

    private void startCleanupTask() {
        long interval = 20L * 60 * 30; // 30 minutes
        Bukkit.getScheduler().runTaskTimerAsynchronously(this, () -> {
            if (dataSource == null || dataSource.isClosed())
                return;
            try (Connection conn = dataSource.getConnection();
                    PreparedStatement stmt = conn
                            .prepareStatement("DELETE FROM web_verifications WHERE expires_at < ?")) {
                stmt.setLong(1, System.currentTimeMillis());
                int deleted = stmt.executeUpdate();
                if (deleted > 0)
                    getLogger().info("Cleaned up " + deleted + " expired verification codes.");
            } catch (SQLException e) {
                getLogger().warning("Error cleaning up expired codes: " + e.getMessage());
            }
        }, interval, interval);
    }

    @Override
    public void onDisable() {
        Bukkit.getScheduler().cancelTasks(this);
        closeDatabase();
        getLogger().info("CrystalCore has been disabled!");
    }

    private void closeDatabase() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
    }

    private boolean setupDatabase() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:mysql://" + getConfig().getString("database.host") + ":"
                + getConfig().getString("database.port") + "/" + getConfig().getString("database.name"));
        config.setUsername(getConfig().getString("database.user"));
        config.setPassword(getConfig().getString("database.password"));
        config.addDataSourceProperty("cachePrepStmts", "true");
        config.addDataSourceProperty("prepStmtCacheSize", "250");
        config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
        config.setConnectionTimeout(5000);

        try {
            dataSource = new HikariDataSource(config);
        } catch (Exception e) {
            getLogger().severe("Could not initialize database pool: " + e.getMessage());
            return false;
        }

        try (Connection conn = dataSource.getConnection()) {
            // Verify link tables
            conn.prepareStatement(
                    "CREATE TABLE IF NOT EXISTS web_verifications (uuid VARCHAR(36) PRIMARY KEY, player_name VARCHAR(16), code VARCHAR(64), expires_at BIGINT);")
                    .execute();
            conn.prepareStatement(
                    "CREATE TABLE IF NOT EXISTS linked_accounts (uuid VARCHAR(36) PRIMARY KEY, player_name VARCHAR(16), web_user_id VARCHAR(100), linked_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP);")
                    .execute();
            conn.prepareStatement(
                    "CREATE TABLE IF NOT EXISTS pending_web_links (code VARCHAR(10) PRIMARY KEY, web_user_id VARCHAR(100), expires_at BIGINT);")
                    .execute();

            // Secure Queue Table
            conn.prepareStatement(
                    "CREATE TABLE IF NOT EXISTS web_pending_commands (id INT AUTO_INCREMENT PRIMARY KEY, command VARCHAR(512) NOT NULL, executed BOOLEAN DEFAULT FALSE, created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, executed_at TIMESTAMP NULL);")
                    .execute();

            getLogger().info("Database connected & tables verified.");
            return true;
        } catch (SQLException e) {
            getLogger().severe("Could not connect to MySQL! " + e.getMessage());
            return false;
        }
    }

    private void startCommandQueueTask() {
        long interval = getConfig().getLong("polling-interval", 100L);
        // Run checking for web commands
        new BukkitRunnable() {
            @Override
            public void run() {
                if (dataSource == null || dataSource.isClosed())
                    return;
                try (Connection conn = dataSource.getConnection();
                        PreparedStatement psSelection = conn.prepareStatement(
                                "SELECT id, command FROM web_pending_commands WHERE executed = FALSE ORDER BY created_at ASC LIMIT 5");
                        ResultSet rs = psSelection.executeQuery()) {

                    while (rs.next()) {
                        int id = rs.getInt("id");
                        String cmd = rs.getString("command");

                        Bukkit.getScheduler().runTask(CrystalCore.this, () -> {
                            getLogger().info("⚡ Executing web command: " + cmd);
                            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
                            markCommandAsExecuted(id);
                        });
                    }
                } catch (SQLException e) {
                    getLogger().log(Level.WARNING, "Error querying command queue", e);
                }
            }
        }.runTaskTimerAsynchronously(this, 100L, interval);
    }

    private void markCommandAsExecuted(int id) {
        Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
            try (Connection conn = dataSource.getConnection();
                    PreparedStatement ps = conn.prepareStatement(
                            "UPDATE web_pending_commands SET executed = TRUE, executed_at = CURRENT_TIMESTAMP WHERE id = ?")) {
                ps.setInt(1, id);
                ps.executeUpdate();
            } catch (SQLException e) {
                getLogger().log(Level.SEVERE, "Failed to mark command " + id + " as executed", e);
            }
        });
    }

    public boolean isPlayerLinked(Player player) {
        return linkedPlayers.contains(player.getUniqueId());
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
            try (Connection conn = dataSource.getConnection();
                    PreparedStatement ps = conn.prepareStatement("SELECT 1 FROM linked_accounts WHERE uuid = ?")) {
                ps.setString(1, uuid.toString());
                if (ps.executeQuery().next()) {
                    linkedPlayers.add(uuid);
                } else {
                    linkedPlayers.remove(uuid);
                }
            } catch (SQLException e) {
                getLogger().warning("Error checking link status for join: " + e.getMessage());
            }
        });
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
                sender.sendMessage(msgPrefix + "§aConfiguración recargada.");
                return true;
            }
            sender.sendMessage(msgPrefix + "§c/crystalcore reload");
            return true;
        }

        if (!(sender instanceof Player)) {
            sender.sendMessage("§cSolo jugadores.");
            return true;
        }

        Player player = (Player) sender;
        if (command.getName().equalsIgnoreCase("link")) {
            if (args.length == 1) {
                handleLinkCode(player, args[0]);
            } else {
                handleLinkRequest(player);
            }
            return true;
        }

        if (command.getName().equalsIgnoreCase("unlink")) {
            handleUnlink(player);
            return true;
        }

        return false;
    }

    private void handleLinkCode(Player player, String code) {
        // Logic for linking via code copied from web
        Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
            try (Connection conn = dataSource.getConnection()) {
                String webUserId = null;
                try (PreparedStatement stmt = conn
                        .prepareStatement("SELECT web_user_id, expires_at FROM pending_web_links WHERE code = ?")) {
                    stmt.setString(1, code);
                    ResultSet rs = stmt.executeQuery();
                    if (rs.next()) {
                        if (System.currentTimeMillis() > rs.getLong("expires_at")) {
                            player.sendMessage("§cCódigo expirado.");
                            return;
                        }
                        webUserId = rs.getString("web_user_id");
                    } else {
                        player.sendMessage("§cCódigo inválido.");
                        return;
                    }
                }

                if (webUserId != null) {
                    try (PreparedStatement insert = conn.prepareStatement(
                            "INSERT INTO linked_accounts (uuid, player_name, web_user_id) VALUES (?, ?, ?) ON DUPLICATE KEY UPDATE web_user_id = ?")) {
                        insert.setString(1, player.getUniqueId().toString());
                        insert.setString(2, player.getName());
                        insert.setString(3, webUserId);
                        insert.setString(4, webUserId);
                        insert.executeUpdate();
                        linkedPlayers.add(player.getUniqueId());
                        player.sendMessage("§a¡Cuenta vinculada exitosamente!");
                    }
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
        });
    }

    private void handleLinkRequest(Player player) {
        // Logic for generating a link or code
        player.sendMessage("§b[CrystalCore] §7Enlace de vinculación generado: §nhttps://crystaltidesSMP.net/verify");
    }

    private void handleUnlink(Player player) {
        Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
            try (Connection conn = dataSource.getConnection();
                    PreparedStatement stmt = conn.prepareStatement("DELETE FROM linked_accounts WHERE uuid = ?")) {
                stmt.setString(1, player.getUniqueId().toString());
                if (stmt.executeUpdate() > 0) {
                    linkedPlayers.remove(player.getUniqueId());
                    player.sendMessage("§aCuenta desvinculada.");
                } else {
                    player.sendMessage("§cNo tienes una cuenta vinculada.");
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
        });
    }

    // PlaceholderAPI Expansion
    public static class CrystalCoreExpansion extends me.clip.placeholderapi.expansion.PlaceholderExpansion {
        private final CrystalCore plugin;

        public CrystalCoreExpansion(CrystalCore plugin) {
            this.plugin = plugin;
        }

        @Override
        public String getIdentifier() {
            return "crystalcore";
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
            if (player == null)
                return "";

            // %crystalcore_status%
            if (identifier.equals("status")) {
                // We should ideally use a map cache here, but for now we check the source
                return plugin.isPlayerLinked(player) ? "§aLinked" : "§cUnlinked";
            }
            return null;
        }
    }
}
