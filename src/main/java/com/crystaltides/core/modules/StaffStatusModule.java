package com.crystaltides.core.modules;

import com.crystaltides.core.CrystalCore;
import com.crystaltides.core.api.CrystalModule;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.UUID;

public class StaffStatusModule extends CrystalModule {

    private DatabaseModule databaseModule;
    private final String SERVER_NAME = "survival"; // Can be moved to config later

    public StaffStatusModule(CrystalCore plugin) {
        super(plugin, "StaffStatus");
    }

    @Override
    public void onEnable() {
        this.databaseModule = plugin.getModuleManager().getModule(DatabaseModule.class);
        if (databaseModule == null) {
            plugin.getLogger().severe("StaffStatusModule requires DatabaseModule!");
            return;
        }

        // Verify Table
        try (Connection conn = databaseModule.getConnection()) {
            conn.prepareStatement(
                    "CREATE TABLE IF NOT EXISTS staff_status (" +
                            "uuid VARCHAR(36) PRIMARY KEY, " +
                            "name VARCHAR(16), " +
                            "status VARCHAR(10), " +
                            "server_id VARCHAR(20), " +
                            "last_update TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP" +
                            ");")
                    .execute();
        } catch (SQLException e) {
            plugin.getLogger().severe("Could not create staff_status table: " + e.getMessage());
            return;
        }

        super.onEnable();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        updateStatus(event.getPlayer().getUniqueId(), event.getPlayer().getName(), "ONLINE");
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        updateStatus(event.getPlayer().getUniqueId(), event.getPlayer().getName(), "OFFLINE");
    }

    private void updateStatus(UUID uuid, String name, String status) {
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try (Connection conn = databaseModule.getConnection();
                    PreparedStatement ps = conn.prepareStatement(
                            "INSERT INTO staff_status (uuid, name, status, server_id) VALUES (?, ?, ?, ?) " +
                                    "ON DUPLICATE KEY UPDATE name = ?, status = ?, server_id = ?, last_update = CURRENT_TIMESTAMP")) {

                ps.setString(1, uuid.toString());
                ps.setString(2, name);
                ps.setString(3, status);
                ps.setString(4, SERVER_NAME);

                // Update part
                ps.setString(5, name);
                ps.setString(6, status);
                ps.setString(7, SERVER_NAME);

                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().warning("Failed to update staff status for " + name + ": " + e.getMessage());
            }
        });
    }
}
