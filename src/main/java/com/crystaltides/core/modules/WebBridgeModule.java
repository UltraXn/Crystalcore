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

import java.net.URI;
import java.net.HttpURLConnection;
import java.net.URL;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

public class WebBridgeModule extends CrystalModule implements CommandExecutor {

    private DatabaseModule databaseModule;
    private ProfileModule profileModule;
    private WebSocketClient wsClient;

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
        plugin.getServer().getPluginManager().registerEvents(this, plugin);

        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new CrystalCoreExpansion(plugin).register();
        }

        startCleanupTask();
        startCommandQueueTask();
        connectWebSocket();
    }

    @Override
    public void onDisable() {
        if (wsClient != null) {
            wsClient.close();
        }
        super.onDisable();
    }

    private void connectWebSocket() {
        try {
            // Default to local dev server, should be configurable
            String wsUrl = plugin.getConfig().getString("websocket-url", "ws://localhost:3001");
            URI uri = new URI(wsUrl);

            wsClient = new WebSocketClient(uri) {
                @Override
                public void onOpen(ServerHandshake handshakedata) {
                    plugin.getLogger().info("✅ Connected to Web Bridge via WebSocket!");
                    wsClient.send("ping"); // Handshake/Auth could go here
                }

                @Override
                public void onMessage(String message) {
                    // Check for command refresh signal
                    if (message.contains("REFRESH_COMMANDS")) {
                        // Use main thread scheduler to ensure sync if needed,
                        // though checkForCommands runs async DB queries.
                        // We run it async immediately.
                        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> checkForCommands());
                    } else if (message.equals("pong")) {
                        // Heartbeat response
                    }
                }

                @Override
                public void onClose(int code, String reason, boolean remote) {
                    plugin.getLogger().warning(() -> "❌ WebSocket closed: " + reason);
                    // Simple reconnect logic with delay
                    new BukkitRunnable() {
                        @Override
                        public void run() {
                            if (plugin.isEnabled()) {
                                connectWebSocket();
                            }
                        }
                    }.runTaskLater(plugin, 100L); // 5 seconds
                }

                @Override
                public void onError(Exception ex) {
                    plugin.getLogger().warning("WebSocket Error: " + ex.getMessage());
                }
            };

            wsClient.connect();
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to connect WebSocket", e);
        }
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
                plugin.getLogger().log(Level.WARNING, "Error durante enlace de cuenta para {0}", player.getName());
            }
        });
    }

    private static final String SOURCE_DISCORD = "discord";
    private static final String SOURCE_WEB = "web";

    private void processLinkAttempt(Player player, Connection conn, String source, String sourceId, String code)
            throws SQLException {

        String uuidStr = player.getUniqueId().toString();
        String playerName = player.getName();

        cleanPreviousLinks(conn, source, sourceId, uuidStr);
        executeLinkInsert(player, conn, source, sourceId, code, uuidStr, playerName);
    }

    private void cleanPreviousLinks(Connection conn, String source, String sourceId, String uuidStr) throws SQLException {
        if (source.equalsIgnoreCase(SOURCE_DISCORD)) {
            try (PreparedStatement clean = conn.prepareStatement(
                    "UPDATE linked_accounts SET discord_id = NULL, discord_tag = NULL WHERE discord_id = ?")) {
                clean.setString(1, sourceId);
                clean.executeUpdate();
            }
            try (PreparedStatement clean = conn.prepareStatement(
                    "UPDATE linked_accounts SET discord_id = NULL, discord_tag = NULL WHERE minecraft_uuid = ?")) {
                clean.setString(1, uuidStr);
                clean.executeUpdate();
            }
        } else if (source.equalsIgnoreCase(SOURCE_WEB)) {
            try (PreparedStatement clean = conn
                    .prepareStatement("UPDATE linked_accounts SET web_user_id = NULL WHERE web_user_id = ?")) {
                clean.setString(1, sourceId);
                clean.executeUpdate();
            }
            try (PreparedStatement clean = conn
                    .prepareStatement("UPDATE linked_accounts SET web_user_id = NULL WHERE minecraft_uuid = ?")) {
                clean.setString(1, uuidStr);
                clean.executeUpdate();
            }
        }
    }

    private String getLinkInsertQuery(String source) {
        if (source.equalsIgnoreCase(SOURCE_DISCORD)) {
            return "INSERT INTO linked_accounts (minecraft_uuid, minecraft_name, discord_id) VALUES (?, ?, ?) "
                    + "ON DUPLICATE KEY UPDATE minecraft_name = ?, discord_id = ?";
        }
        if (source.equalsIgnoreCase(SOURCE_WEB)) {
            return "INSERT INTO linked_accounts (minecraft_uuid, minecraft_name, web_user_id) VALUES (?, ?, ?) "
                    + "ON DUPLICATE KEY UPDATE minecraft_name = ?, web_user_id = ?";
        }
        return "";
    }

    private void updateProfileOnLink(Player player, String source, String sourceId) {
        ProfileModule pm = profileModule != null ? profileModule : plugin.getModuleManager().getModule(ProfileModule.class);
        if (pm == null) return;
        CrystalProfile profile = pm.getProfile(player.getUniqueId());
        if (profile == null) return;

        profile.setLinked(true);
        if (source.equalsIgnoreCase(SOURCE_DISCORD)) {
            profile.setDiscordId(sourceId);
        } else if (source.equalsIgnoreCase(SOURCE_WEB)) {
            profile.setWebUserId(sourceId);
        }
    }

    private void executeLinkInsert(Player player, Connection conn, String source, String sourceId, String code,
            String uuidStr, String playerName) throws SQLException {
        String query = getLinkInsertQuery(source);
        if (query.isEmpty()) {
            return;
        }

        try (PreparedStatement insert = conn.prepareStatement(query)) {
            insert.setString(1, uuidStr);
            insert.setString(2, playerName);
            insert.setString(3, sourceId);
            insert.setString(4, playerName);
            insert.setString(5, sourceId);
            insert.executeUpdate();

            try (PreparedStatement cleanupEmpty = conn.prepareStatement(
                    "DELETE FROM linked_accounts WHERE minecraft_uuid IS NULL AND discord_id IS NULL AND web_user_id IS NULL")) {
                cleanupEmpty.executeUpdate();
            }

            Bukkit.getScheduler().runTask(plugin, () -> updateProfileOnLink(player, source, sourceId));

            player.sendMessage("§a¡Cuenta vinculada con " + source + " exitosamente!");

            try (PreparedStatement cleanupCode = conn
                    .prepareStatement("DELETE FROM universal_links WHERE code = ?")) {
                cleanupCode.setString(1, code.toUpperCase());
                cleanupCode.executeUpdate();
            }
        }
    }

    private void handleLinkRequest(Player player) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            String chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
            StringBuilder code = new StringBuilder();
            java.util.concurrent.ThreadLocalRandom rnd = java.util.concurrent.ThreadLocalRandom.current();
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
                plugin.getLogger().log(Level.WARNING, "Error generando código de vinculación", e);
            }
        });
    }

    private void updateProfileOnUnlink(Player player) {
        ProfileModule pm = profileModule != null ? profileModule : plugin.getModuleManager().getModule(ProfileModule.class);
        if (pm == null) return;
        CrystalProfile profile = pm.getProfile(player.getUniqueId());
        if (profile == null) return;

        profile.setLinked(false);
        profile.setDiscordId(null);
        profile.setWebUserId(null);
    }

    private void handleUnlink(Player player) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> performUnlink(player));
    }

    private void performUnlink(Player player) {
        try (Connection conn = databaseModule.getConnection();
                PreparedStatement stmt = conn
                        .prepareStatement("DELETE FROM linked_accounts WHERE minecraft_uuid = ?")) {
            stmt.setString(1, player.getUniqueId().toString());
            boolean unlinked = stmt.executeUpdate() > 0;
            if (unlinked) {
                Bukkit.getScheduler().runTask(plugin, () -> updateProfileOnUnlink(player));
                player.sendMessage("§aCuenta desvinculada.");
            } else {
                player.sendMessage("§cNo tienes una cuenta vinculada.");
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Error al desvincular cuenta", e);
        }
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
        // Reduced polling frequency significantly since we use WebSockets (Backup
        // polling)
        // 200 ticks = 10 seconds (vs 2s before)
        new BukkitRunnable() {
            @Override
            public void run() {
                checkForCommands();
            }
        }.runTaskTimerAsynchronously(plugin, 100L, 200L);
    }

    private void checkForCommands() {
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

    @SuppressWarnings("deprecation")
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        final Player player = event.getPlayer();
        final String playerName = player.getName();
        final String messageText = event.getMessage();

        String skinName = playerName;
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            try {
                // 1. Check for custom texture URL (Skindex / MineSkin / custom URLs)
                String textureUrl = me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(player, "%skinrestorer_skin_url%");
                if (textureUrl != null && !textureUrl.isEmpty() && !textureUrl.startsWith("%")) {
                    int lastSlash = textureUrl.lastIndexOf('/');
                    if (lastSlash != -1 && lastSlash < textureUrl.length() - 1) {
                        skinName = textureUrl.substring(lastSlash + 1);
                    }
                } else {
                    // 2. Fallback to skin name
                    String parsed = me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(player, "%skinrestorer_skin%");
                    if (parsed != null && !parsed.isEmpty() && !parsed.startsWith("%")) {
                        skinName = parsed;
                    }
                }
            } catch (Exception ignored) {
                // Fallback to playerName if PAPI/SkinRestorer is unavailable
            }
        }

        final String avatarSkin = skinName;

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            sendChatToDiscordBridge(playerName, avatarSkin, messageText);
        });
    }

    private void sendChatToDiscordBridge(String playerName, String avatarSkin, String messageText) {
        String webhookUrl = plugin.getConfig().getString("discord-chat-webhook-url", "");
        String bridgeUrl = plugin.getConfig().getString("discord-bridge-url", "http://localhost:3002/chat/bridge");

        String jsonPayload;
        String targetUrl;

        if (webhookUrl != null && !webhookUrl.trim().isEmpty()) {
            targetUrl = webhookUrl.trim();
            jsonPayload = String.format(
                "{\"username\": \"%s\", \"avatar_url\": \"https://mc-heads.net/avatar/%s\", \"content\": \"%s\"}",
                escapeJson(playerName),
                escapeJson(avatarSkin),
                escapeJson(messageText)
            );
        } else {
            targetUrl = bridgeUrl;
            jsonPayload = String.format(
                "{\"username\": \"%s\", \"message\": \"%s\"}",
                escapeJson(playerName),
                escapeJson(messageText)
            );
        }

        try {
            URL url = new java.net.URI(targetUrl).toURL();
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json; utf-8");
            conn.setRequestProperty("User-Agent", "CrystalCore-Minecraft");
            conn.setConnectTimeout(3000);
            conn.setReadTimeout(3000);
            conn.setDoOutput(true);

            try (OutputStream os = conn.getOutputStream()) {
                byte[] input = jsonPayload.getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }

            int code = conn.getResponseCode();
            if (code < 200 || code >= 300) {
                plugin.getLogger().warning(() -> "Failed to send chat bridge payload: HTTP " + code);
            }
        } catch (Exception e) {
            plugin.getLogger().warning(() -> "Error forwarding chat to Discord: " + e.getMessage());
        }
    }

    private String escapeJson(String input) {
        if (input == null) return "";
        return input.replace("\\", "\\\\")
                    .replace("\"", "\\\"")
                    .replace("\b", "\\b")
                    .replace("\f", "\\f")
                    .replace("\n", "\\n")
                    .replace("\r", "\\r")
                    .replace("\t", "\\t");
    }

    private void markCommandAsExecuted(int id) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try (Connection conn = databaseModule.getConnection();
                    PreparedStatement ps = conn.prepareStatement(
                            "UPDATE web_pending_commands SET executed = TRUE, executed_at = CURRENT_TIMESTAMP WHERE id = ?")) {
                ps.setInt(1, id);
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to mark command {0} as executed", id);
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
