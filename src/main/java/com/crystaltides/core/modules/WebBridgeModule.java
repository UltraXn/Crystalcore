package com.crystaltides.core.modules;

import com.crystaltides.core.CrystalCore;
import com.crystaltides.core.api.CrystalModule;
import com.crystaltides.core.profile.CrystalProfile;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.logging.Level;

public class WebBridgeModule extends CrystalModule implements CommandExecutor {

    private DatabaseModule databaseModule;
    private ProfileModule profileModule;

    public WebBridgeModule(CrystalCore plugin) {
        super(plugin, "WebBridge");
    }

    @Override
    public void onEnable() {
        super.onEnable();
        this.databaseModule = plugin.getModuleManager().getModule(DatabaseModule.class);
        this.profileModule = plugin.getModuleManager().getModule(ProfileModule.class);

        plugin.getCommand("link").setExecutor(this);
        plugin.getCommand("unlink").setExecutor(this);

        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new CrystalCoreExpansion(plugin).register();
        }

        startCleanupTask();
        startCommandQueueTask();
    }

    @Override
    public void onDisable() {
        super.onDisable();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
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
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try (Connection conn = databaseModule.getConnection()) {
                String source = null;
                String sourceId = null;

                try (PreparedStatement stmt = conn
                        .prepareStatement("SELECT source, source_id, expires_at FROM universal_links WHERE code = ?")) {
                    stmt.setString(1, code.toUpperCase());
                    ResultSet rs = stmt.executeQuery();
                    if (rs.next()) {
                        if (System.currentTimeMillis() > rs.getLong("expires_at")) {
                            player.sendMessage("§cCódigo expirado.");
                            return;
                        }
                        source = rs.getString("source");
                        sourceId = rs.getString("source_id");
                    } else {
                        player.sendMessage("§cCódigo inválido.");
                        return;
                    }
                }

                if (source != null && sourceId != null) {
                    processLinkAttempt(player, conn, source, sourceId, code);
                }
            } catch (SQLException e) {
                player.sendMessage("§cError de base de datos durante el enlace.");
                e.printStackTrace();
            }
        });
    }

    private void processLinkAttempt(Player player, Connection conn, String source, String sourceId, String code)
            throws SQLException {

        String uuidStr = player.getUniqueId().toString();
        String playerName = player.getName();

        // Robust cleanup to avoid UNIQUE key collisions (web_user_id or discord_id)
        if (source.equalsIgnoreCase("discord")) {
            // Unlink anyone else from this discord account
            try (PreparedStatement clean = conn.prepareStatement(
                    "UPDATE linked_accounts SET discord_id = NULL, discord_tag = NULL WHERE discord_id = ?")) {
                clean.setString(1, sourceId);
                clean.executeUpdate();
            }
            // Unlink this player from any other discord
            try (PreparedStatement clean = conn.prepareStatement(
                    "UPDATE linked_accounts SET discord_id = NULL, discord_tag = NULL WHERE minecraft_uuid = ?")) {
                clean.setString(1, uuidStr);
                clean.executeUpdate();
            }
        } else if (source.equalsIgnoreCase("web")) {
            // Unlink anyone else from this web account
            try (PreparedStatement clean = conn
                    .prepareStatement("UPDATE linked_accounts SET web_user_id = NULL WHERE web_user_id = ?")) {
                clean.setString(1, sourceId);
                clean.executeUpdate();
            }
            // Unlink this player from any other web account
            try (PreparedStatement clean = conn
                    .prepareStatement("UPDATE linked_accounts SET web_user_id = NULL WHERE minecraft_uuid = ?")) {
                clean.setString(1, uuidStr);
                clean.executeUpdate();
            }
        }

        String query = "";
        if (source.equalsIgnoreCase("discord")) {
            query = "INSERT INTO linked_accounts (minecraft_uuid, minecraft_name, discord_id) VALUES (?, ?, ?) "
                    + "ON DUPLICATE KEY UPDATE minecraft_name = ?, discord_id = ?";
        } else if (source.equalsIgnoreCase("web")) {
            query = "INSERT INTO linked_accounts (minecraft_uuid, minecraft_name, web_user_id) VALUES (?, ?, ?) "
                    + "ON DUPLICATE KEY UPDATE minecraft_name = ?, web_user_id = ?";
        }

        if (!query.isEmpty()) {
            try (PreparedStatement insert = conn.prepareStatement(query)) {
                insert.setString(1, uuidStr);
                insert.setString(2, playerName);
                insert.setString(3, sourceId);
                insert.setString(4, playerName);
                insert.setString(5, sourceId);
                insert.executeUpdate();

                // Clean up empty rows
                try (PreparedStatement cleanupEmpty = conn.prepareStatement(
                        "DELETE FROM linked_accounts WHERE minecraft_uuid IS NULL AND discord_id IS NULL AND web_user_id IS NULL")) {
                    cleanupEmpty.executeUpdate();
                }

                // Update Profile Cache
                CrystalProfile profile = profileModule.getProfile(player.getUniqueId());
                if (profile != null) {
                    profile.setLinked(true);
                    if (source.equalsIgnoreCase("discord"))
                        profile.setDiscordId(sourceId);
                    if (source.equalsIgnoreCase("web"))
                        profile.setWebUserId(sourceId);
                }

                player.sendMessage("§a¡Cuenta vinculada con " + source + " exitosamente!");

                try (PreparedStatement cleanupCode = conn
                        .prepareStatement("DELETE FROM universal_links WHERE code = ?")) {
                    cleanupCode.setString(1, code.toUpperCase());
                    cleanupCode.executeUpdate();
                }
            }
        }
    }

    private void handleLinkRequest(Player player) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            String chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
            StringBuilder code = new StringBuilder();
            java.util.Random rnd = new java.util.Random();
            for (int i = 0; i < 6; i++) {
                code.append(chars.charAt(rnd.nextInt(chars.length())));
            }

            long expiresAt = System.currentTimeMillis() + (15 * 60 * 1000); // 15 min

            try (Connection conn = databaseModule.getConnection();
                    PreparedStatement stmt = conn.prepareStatement(
                            "INSERT INTO universal_links (code, source, source_id, player_name, expires_at) VALUES (?, ?, ?, ?, ?) "
                                    + "ON DUPLICATE KEY UPDATE expires_at = ?")) {
                stmt.setString(1, code.toString());
                stmt.setString(2, "minecraft");
                stmt.setString(3, player.getUniqueId().toString());
                stmt.setString(4, player.getName());
                stmt.setLong(5, expiresAt);
                stmt.setLong(6, expiresAt);
                stmt.executeUpdate();

                String codeStr = code.toString();
                Component message = Component.text("[CrystalCore] ", NamedTextColor.AQUA)
                        .append(Component.text("Tu código de vinculación es: ", NamedTextColor.GRAY))
                        .append(Component.text(codeStr, NamedTextColor.YELLOW, TextDecoration.BOLD)
                                .clickEvent(ClickEvent.copyToClipboard(codeStr))
                                .hoverEvent(HoverEvent.showText(
                                        Component.text("¡Haz clic para copiar el código!", NamedTextColor.GREEN))));

                player.sendMessage(message);
                player.sendMessage(Component.text("Úsalo en Discord (/link) o en la Web para conectar tus cuentas.",
                        NamedTextColor.GRAY));
                player.sendMessage(Component.text("(Expira en 15 minutos)", NamedTextColor.DARK_GRAY));
            } catch (SQLException e) {
                player.sendMessage("§cError al generar código de vinculación.");
                e.printStackTrace();
            }
        });
    }

    private void handleUnlink(Player player) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try (Connection conn = databaseModule.getConnection();
                    PreparedStatement stmt = conn
                            .prepareStatement("DELETE FROM linked_accounts WHERE minecraft_uuid = ?")) {
                stmt.setString(1, player.getUniqueId().toString());
                if (stmt.executeUpdate() > 0) {
                    CrystalProfile profile = profileModule.getProfile(player.getUniqueId());
                    if (profile != null) {
                        profile.setLinked(false);
                        profile.setDiscordId(null);
                        profile.setWebUserId(null);
                    }
                    player.sendMessage("§aCuenta desvinculada.");
                } else {
                    player.sendMessage("§cNo tienes una cuenta vinculada.");
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
        });
    }

    private void startCleanupTask() {
        long interval = 20L * 60 * 30; // 30 minutes
        new BukkitRunnable() {
            @Override
            public void run() {
                try (Connection conn = databaseModule.getConnection();
                        PreparedStatement stmt = conn
                                .prepareStatement("DELETE FROM universal_links WHERE expires_at < ?")) {
                    stmt.setLong(1, System.currentTimeMillis());
                    stmt.executeUpdate();
                } catch (SQLException e) {
                    // Ignore if db closed
                }
            }
        }.runTaskTimerAsynchronously(plugin, interval, interval);
    }

    private void startCommandQueueTask() {
        long interval = plugin.getConfig().getLong("polling-interval", 40L);
        new BukkitRunnable() {
            @Override
            public void run() {
                try (Connection conn = databaseModule.getConnection();
                        PreparedStatement psSelection = conn.prepareStatement(
                                "SELECT id, command FROM web_pending_commands WHERE executed = FALSE ORDER BY created_at ASC LIMIT 5");
                        ResultSet rs = psSelection.executeQuery()) {

                    while (rs.next()) {
                        int id = rs.getInt("id");
                        String cmd = rs.getString("command");

                        Bukkit.getScheduler().runTask(plugin, () -> {
                            plugin.getLogger().info("⚡ Executing web command: " + cmd);
                            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
                            markCommandAsExecuted(id);
                        });
                    }
                } catch (SQLException e) {
                    plugin.getLogger().log(Level.WARNING, "Error querying command queue", e);
                }
            }
        }.runTaskTimerAsynchronously(plugin, 100L, interval);
    }

    private void markCommandAsExecuted(int id) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try (Connection conn = databaseModule.getConnection();
                    PreparedStatement ps = conn.prepareStatement(
                            "UPDATE web_pending_commands SET executed = TRUE, executed_at = CURRENT_TIMESTAMP WHERE id = ?")) {
                ps.setInt(1, id);
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to mark command " + id + " as executed", e);
            }
        });
    }

    // Inner class for PAPI
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
            if (identifier.equals("status")) {
                ProfileModule pm = plugin.getModuleManager().getModule(ProfileModule.class);
                if (pm != null) {
                    CrystalProfile profile = pm.getProfile(player.getUniqueId());
                    if (profile != null)
                        return profile.isLinked() ? "§aLinked" : "§cUnlinked";
                }
                return "§cLoading";
            }
            return null;
        }
    }
}
