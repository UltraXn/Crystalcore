package com.crystaltides.core.modules;

import com.crystaltides.core.CrystalCore;
import com.crystaltides.core.api.CrystalModule;
import com.crystaltides.core.profile.CrystalProfile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;

import org.bukkit.event.player.PlayerQuitEvent;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ProfileModule extends CrystalModule {

    private final Map<UUID, CrystalProfile> profiles = new ConcurrentHashMap<>();
    private DatabaseModule databaseModule;

    public ProfileModule(CrystalCore plugin) {
        super(plugin, "Profiles");
    }

    @Override
    public void onEnable() {
        super.onEnable();
        this.databaseModule = plugin.getModuleManager().getModule(DatabaseModule.class);
        if (databaseModule == null) {
            plugin.getLogger().severe("ProfileModule requires DatabaseModule, but it's not loaded!");
        }
    }

    @Override
    public void onDisable() {
        // Save all online profiles
        for (CrystalProfile profile : profiles.values()) {
            saveProfile(profile);
        }
        profiles.clear();
        super.onDisable();
    }

    public CrystalProfile getProfile(UUID uuid) {
        return profiles.get(uuid);
    }

    public void reloadProfile(java.util.UUID uuid) {
        org.bukkit.entity.Player player = org.bukkit.Bukkit.getPlayer(uuid);
        if (player != null && player.isOnline()) {
            plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
                CrystalProfile newProfile = loadProfile(uuid, player.getName());
                if (newProfile != null) {
                    profiles.put(uuid, newProfile);
                }
            });
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onAsyncLogin(AsyncPlayerPreLoginEvent event) {
        if (event.getLoginResult() != AsyncPlayerPreLoginEvent.Result.ALLOWED) {
            return;
        }

        // Load data BEFORE join
        UUID uuid = event.getUniqueId();
        String name = event.getName();

        CrystalProfile profile = loadProfile(uuid, name);
        if (profile != null) {
            profiles.put(uuid, profile);
            // Update database last seen immediately or on quit?
            // Let's keep it simple for now and just load.
        } else {
            // Create new empty profile if load failed (or handle error)
            profiles.put(uuid, new CrystalProfile(uuid, name));
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        CrystalProfile profile = profiles.remove(uuid);
        if (profile != null) {
            plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> saveProfile(profile));
        }
    }

    private CrystalProfile loadProfile(UUID uuid, String name) {
        if (databaseModule == null)
            return new CrystalProfile(uuid, name);

        CrystalProfile profile = new CrystalProfile(uuid, name);

        // 1. Load Link Data (MySQL)
        try (Connection conn = databaseModule.getConnection();
                PreparedStatement ps = conn.prepareStatement(
                        "SELECT discord_id, web_user_id FROM linked_accounts WHERE minecraft_uuid = ?")) {

            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    profile.setLinked(true);
                    profile.setDiscordId(rs.getString("discord_id"));
                    profile.setWebUserId(rs.getString("web_user_id"));
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to load profile for " + name + ": " + e.getMessage());
        }

        // 2. Load Economy Data (SQLite via BancoModule)
        BancoModule bancoModule = plugin.getModuleManager().getModule(BancoModule.class);
        if (bancoModule != null && bancoModule.isEnabled()) {
            bancoModule.syncProfile(profile);
        }

        return profile;
    }

    private void saveProfile(CrystalProfile profile) {
        // For now, we only updated 'last seen' or other things if we had them in DB.
        // Since we don't have a dedicated 'users' table (only linked_accounts),
        // we might not need to save anything unless they are linked.
        // Implementation for saving future stats goes here.
    }
}
