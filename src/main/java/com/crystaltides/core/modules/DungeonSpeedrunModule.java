package com.crystaltides.core.modules;

import com.crystaltides.core.CrystalCore;
import com.crystaltides.core.api.CrystalModule;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Módulo de Cronometraje y Leaderboard de Speedruns en Mazmorras (Dungeon Speedrun).
 * Registra tiempos de completado en dimensiones rotativas y mazmorras míticas.
 */
public class DungeonSpeedrunModule extends CrystalModule {

    public static class SpeedrunAttempt {
        private final UUID playerUuid;
        private final String playerName;
        private final String dungeonId;
        private final long startTime;

        public SpeedrunAttempt(Player player, String dungeonId) {
            this.playerUuid = player.getUniqueId();
            this.playerName = player.getName();
            this.dungeonId = dungeonId;
            this.startTime = System.currentTimeMillis();
        }

        public UUID getPlayerUuid() { return playerUuid; }
        public String getPlayerName() { return playerName; }
        public String getDungeonId() { return dungeonId; }
        public long getElapsedTimeMs() { return System.currentTimeMillis() - startTime; }

        public String getFormattedTime() {
            long totalMs = getElapsedTimeMs();
            long minutes = (totalMs / 1000) / 60;
            long seconds = (totalMs / 1000) % 60;
            long millis = totalMs % 1000;
            return String.format(Locale.US, "%02d:%02d.%03d", minutes, seconds, millis);
        }
    }

    public static class DungeonRecord {
        private final String playerName;
        private final long durationMs;
        private final String formattedTime;

        public DungeonRecord(String playerName, long durationMs) {
            this.playerName = playerName;
            this.durationMs = durationMs;
            long minutes = (durationMs / 1000) / 60;
            long seconds = (durationMs / 1000) % 60;
            long millis = durationMs % 1000;
            this.formattedTime = String.format(Locale.US, "%02d:%02d.%03d", minutes, seconds, millis);
        }

        public String getPlayerName() { return playerName; }
        public long getDurationMs() { return durationMs; }
        public String getFormattedTime() { return formattedTime; }
    }

    private final Map<UUID, SpeedrunAttempt> activeAttempts = new ConcurrentHashMap<>();
    private DatabaseModule databaseModule;

    public DungeonSpeedrunModule(CrystalCore plugin) {
        super(plugin, "DungeonSpeedrun");
    }

    @Override
    public void onEnable() {
        super.onEnable();
        this.databaseModule = plugin.getModuleManager().getModule(DatabaseModule.class);
        initDatabaseTable();
        plugin.getLogger().info("⏱️ Módulo DungeonSpeedrun (Cronometraje de Mazmorras) habilitado.");
    }

    private void initDatabaseTable() {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            if (databaseModule == null) return;
            try (Connection conn = databaseModule.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(
                         "CREATE TABLE IF NOT EXISTS dungeon_leaderboards (" +
                                 "id INT AUTO_INCREMENT PRIMARY KEY, " +
                                 "dungeon_id VARCHAR(64) NOT NULL, " +
                                 "player_uuid VARCHAR(36) NOT NULL, " +
                                 "player_name VARCHAR(50) NOT NULL, " +
                                 "duration_ms BIGINT NOT NULL, " +
                                 "formatted_time VARCHAR(20) NOT NULL, " +
                                 "completed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                                 "INDEX idx_dungeon_time (dungeon_id, duration_ms)" +
                                 ")")) {
                stmt.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING, "No se pudo inicializar la tabla dungeon_leaderboards", e);
            }
        });
    }

    public void startAttempt(Player player, String dungeonId) {
        SpeedrunAttempt attempt = new SpeedrunAttempt(player, dungeonId);
        activeAttempts.put(player.getUniqueId(), attempt);

        player.sendMessage(Component.text("⏱️ ¡Cronómetro iniciado para la mazmorra: ", NamedTextColor.GREEN)
                .append(Component.text(dungeonId, NamedTextColor.GOLD, TextDecoration.BOLD))
                .append(Component.text("! ¡A toda velocidad!", NamedTextColor.GREEN)));
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 1.0f, 1.2f);
    }

    public SpeedrunAttempt getAttempt(UUID uuid) {
        return activeAttempts.get(uuid);
    }

    public void cancelAttempt(Player player) {
        if (activeAttempts.remove(player.getUniqueId()) != null) {
            player.sendMessage(Component.text("❌ Cronómetro de speedrun cancelado.", NamedTextColor.RED));
        }
    }

    public void completeAttempt(Player player, String dungeonId) {
        SpeedrunAttempt attempt = activeAttempts.remove(player.getUniqueId());
        if (attempt == null) return;

        long durationMs = attempt.getElapsedTimeMs();
        String formatted = attempt.getFormattedTime();

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            boolean isNewRecord = saveRecord(dungeonId, player.getUniqueId(), player.getName(), durationMs, formatted);

            Bukkit.getScheduler().runTask(plugin, () -> {
                if (isNewRecord) {
                    Component recordBroadcast = Component.text("🏆 ¡NUEVO RÉCORD DE MAZMORRA! ", NamedTextColor.GOLD, TextDecoration.BOLD)
                            .append(Component.text(player.getName(), NamedTextColor.WHITE, TextDecoration.BOLD))
                            .append(Component.text(" completó ", NamedTextColor.YELLOW))
                            .append(Component.text(dungeonId, NamedTextColor.AQUA, TextDecoration.BOLD))
                            .append(Component.text(" en solo ", NamedTextColor.YELLOW))
                            .append(Component.text(formatted, NamedTextColor.GREEN, TextDecoration.BOLD))
                            .append(Component.text("!", NamedTextColor.YELLOW));
                    Bukkit.broadcast(recordBroadcast);
                    player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
                } else {
                    player.sendMessage(Component.text("🏁 Mazmorra completada en: ", NamedTextColor.GREEN)
                            .append(Component.text(formatted, NamedTextColor.GOLD, TextDecoration.BOLD)));
                    player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
                }
            });
        });
    }

    private boolean saveRecord(String dungeonId, UUID uuid, String name, long durationMs, String formatted) {
        if (databaseModule == null) return false;
        try (Connection conn = databaseModule.getConnection()) {
            // Verificar mejor récord previo
            long bestPrevious = Long.MAX_VALUE;
            try (PreparedStatement check = conn.prepareStatement(
                    "SELECT MIN(duration_ms) FROM dungeon_leaderboards WHERE dungeon_id = ?")) {
                check.setString(1, dungeonId);
                try (ResultSet rs = check.executeQuery()) {
                    if (rs.next()) {
                        long val = rs.getLong(1);
                        if (!rs.wasNull()) bestPrevious = val;
                    }
                }
            }

            // Insertar el nuevo intento
            try (PreparedStatement insert = conn.prepareStatement(
                    "INSERT INTO dungeon_leaderboards (dungeon_id, player_uuid, player_name, duration_ms, formatted_time) " +
                            "VALUES (?, ?, ?, ?, ?)")) {
                insert.setString(1, dungeonId);
                insert.setString(2, uuid.toString());
                insert.setString(3, name);
                insert.setLong(4, durationMs);
                insert.setString(5, formatted);
                insert.executeUpdate();
            }

            return durationMs < bestPrevious;
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Error al guardar récord de speedrun", e);
            return false;
        }
    }

    public void fetchTopRecords(String dungeonId, int limit, java.util.function.Consumer<List<DungeonRecord>> callback) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            List<DungeonRecord> records = new ArrayList<>();
            if (databaseModule == null) {
                callback.accept(records);
                return;
            }
            try (Connection conn = databaseModule.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(
                         "SELECT player_name, MIN(duration_ms) as best_time FROM dungeon_leaderboards " +
                                 "WHERE dungeon_id = ? GROUP BY player_name, player_uuid ORDER BY best_time ASC LIMIT ?")) {
                stmt.setString(1, dungeonId);
                stmt.setInt(2, limit);
                ResultSet rs = stmt.executeQuery();
                while (rs.next()) {
                    records.add(new DungeonRecord(rs.getString("player_name"), rs.getLong("best_time")));
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING, "Error al consultar leaderboard de mazmorras", e);
            }
            callback.accept(records);
        });
    }
}
