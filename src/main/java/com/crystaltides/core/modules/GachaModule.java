package com.crystaltides.core.modules;

import com.crystaltides.core.CrystalCore;
import com.crystaltides.core.api.CrystalModule;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

public class GachaModule extends CrystalModule {

    private DatabaseModule databaseModule;
    private final Map<Integer, String> modelDataToTier = new HashMap<>();

    public GachaModule(CrystalCore plugin) {
        super(plugin, "GachaScanner");
    }

    @Override
    public void onEnable() {
        this.databaseModule = plugin.getModuleManager().getModule(DatabaseModule.class);
        if (databaseModule == null) {
            plugin.getLogger().severe("GachaModule requires DatabaseModule!");
            return;
        }

        loadConfig();
        super.onEnable();
    }

    public void loadConfig() {
        modelDataToTier.clear();
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("gacha.items");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                try {
                    int modelData = Integer.parseInt(key);
                    String tier = section.getString(key);
                    modelDataToTier.put(modelData, tier);
                } catch (NumberFormatException e) {
                    plugin.getLogger().warning("Invalid CustomModelData in config: " + key);
                }
            }
        }
        plugin.getLogger().info("Loaded " + modelDataToTier.size() + " gacha items mappings.");
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        // Run scan with a slight delay to ensure inventory is fully loaded/handled
        plugin.getServer().getScheduler().runTaskLaterAsynchronously(plugin, () -> scanAndSync(event.getPlayer()), 40L); // 2
                                                                                                                         // seconds
                                                                                                                         // delay
    }

    public void scanAndSync(Player player) {
        if (!isEnabled())
            return;

        Set<String> foundTiers = new HashSet<>();

        // Scan Inventory
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && item.getType() != Material.AIR && item.hasItemMeta()
                    && item.getItemMeta().hasCustomModelData()) {
                int modelData = item.getItemMeta().getCustomModelData();
                if (modelDataToTier.containsKey(modelData)) {
                    foundTiers.add(modelDataToTier.get(modelData));
                }
            }
        }

        if (!foundTiers.isEmpty()) {
            updateDatabase(player.getUniqueId(), foundTiers);
        }
    }

    private void updateDatabase(UUID uuid, Set<String> newTiers) {
        try (Connection conn = databaseModule.getConnection()) {
            // 1. Get current unlocked tiers
            Set<String> currentTiers = new HashSet<>();
            String currentJson = "";

            try (PreparedStatement ps = conn
                    .prepareStatement("SELECT unlocked_tiers FROM linked_accounts WHERE minecraft_uuid = ?")) {
                ps.setString(1, uuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        currentJson = rs.getString("unlocked_tiers");
                        if (currentJson != null && !currentJson.isEmpty()) {
                            // Assuming comma separated for simplicity "bronce,plata"
                            // If it matches array format like ["a","b"], we might need parsing.
                            // Let's stick to simple comma separated string for now.
                            String[] parts = currentJson.replace("[", "").replace("]", "").replace("\"", "").split(",");
                            for (String s : parts) {
                                if (!s.trim().isEmpty())
                                    currentTiers.add(s.trim());
                            }
                        }
                    } else {
                        // User not linked or not in DB, skip
                        return;
                    }
                }
            }

            // 2. Add new tiers
            boolean changed = false;
            for (String tier : newTiers) {
                if (!currentTiers.contains(tier)) {
                    currentTiers.add(tier);
                    changed = true;
                }
            }

            // 3. Save if changed
            if (changed) {
                String updatedString = String.join(",", currentTiers);
                try (PreparedStatement ps = conn
                        .prepareStatement("UPDATE linked_accounts SET unlocked_tiers = ? WHERE minecraft_uuid = ?")) {
                    ps.setString(1, updatedString);
                    ps.setString(2, uuid.toString());
                    ps.executeUpdate();
                    plugin.getLogger().info("Updated tiers for " + uuid + ": " + updatedString);
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("Error syncing Gacha tiers: " + e.getMessage());
        }
    }
}
