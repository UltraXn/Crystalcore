package com.crystaltides.core.modules;

import com.crystaltides.core.CrystalCore;
import com.crystaltides.core.api.CrystalModule;
import com.crystaltides.core.profile.CrystalProfile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.entity.Player;
import redis.clients.jedis.Jedis;

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
    private RedisModule redisModule;

    public ProfileModule(CrystalCore plugin) {
        super(plugin, "Profiles");
    }

    @Override
    public void onEnable() {
        super.onEnable();
        this.databaseModule = plugin.getModuleManager().getModule(DatabaseModule.class);
        this.redisModule = plugin.getModuleManager().getModule(RedisModule.class);
        
        if (databaseModule == null) {
            plugin.getLogger().severe("ProfileModule requires DatabaseModule, but it's not loaded!");
        }
        
        // Push stats to Redis every 60 seconds
        plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            for (CrystalProfile profile : profiles.values()) {
                pushToRedis(profile);
            }
        }, 1200L, 1200L); // 60s delay, 60s period
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
            plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
                pushToRedis(profile); // Final sync to Redis
                saveProfile(profile); // Save to SQL
            });
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        CrystalProfile profile = getProfile(event.getPlayer().getUniqueId());
        if (profile != null) {
            profile.addBlockMined();
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDeath(EntityDeathEvent event) {
        Player killer = event.getEntity().getKiller();
        if (killer != null) {
            CrystalProfile killerProfile = getProfile(killer.getUniqueId());
            if (killerProfile != null) {
                killerProfile.addKill();
            }
        }

        if (event.getEntity() instanceof Player victim) {
            CrystalProfile victimProfile = getProfile(victim.getUniqueId());
            if (victimProfile != null) {
                victimProfile.addDeath();
            }
        }
    }

    private void pushToRedis(CrystalProfile profile) {
        if (redisModule == null || !redisModule.isRedisEnabled()) return;

        profile.setLastSeen(System.currentTimeMillis()); // Update timestamp
        try (Jedis jedis = redisModule.getResource()) {
            if (jedis == null) return;

            String key = "player:stats:" + profile.getUuid().toString();
            Map<String, String> stats = new java.util.HashMap<>();
            stats.put("name", profile.getPlayerName());
            stats.put("blocksMined", String.valueOf(profile.getBlocksMined()));
            stats.put("kills", String.valueOf(profile.getKills()));
            stats.put("deaths", String.valueOf(profile.getDeaths()));
            stats.put("lastSeen", String.valueOf(profile.getLastSeen()));
            
            jedis.hmset(key, stats);
            jedis.expire(key, 3600); // 1 hour TTL
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to push stats to Redis for " + profile.getPlayerName() + ": " + e.getMessage());
        }
    }

    private CrystalProfile loadProfile(UUID uuid, String name) {
        if (databaseModule == null)
            return new CrystalProfile(uuid, name);

        CrystalProfile profile = new CrystalProfile(uuid, name);

        // 1. Load Link Data (MySQL)
        try (Connection conn = databaseModule.getConnection();
                PreparedStatement ps = conn.prepareStatement(
                        "SELECT discord_id, web_user_id, blocks_mined, kills, deaths FROM linked_accounts WHERE minecraft_uuid = ?")) {

            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    profile.setLinked(true);
                    profile.setDiscordId(rs.getString("discord_id"));
                    profile.setWebUserId(rs.getString("web_user_id"));
                    profile.setBlocksMined(rs.getInt("blocks_mined"));
                    profile.setKills(rs.getInt("kills"));
                    profile.setDeaths(rs.getInt("deaths"));
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
        if (databaseModule == null) return;

        try (Connection conn = databaseModule.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "UPDATE linked_accounts SET blocks_mined = ?, kills = ?, deaths = ?, last_seen = ? WHERE minecraft_uuid = ?")) {
            
            ps.setInt(1, profile.getBlocksMined());
            ps.setInt(2, profile.getKills());
            ps.setInt(3, profile.getDeaths());
            ps.setLong(4, profile.getLastSeen());
            ps.setString(5, profile.getUuid().toString());
            
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to save profile for " + profile.getPlayerName() + ": " + e.getMessage());
        }
    }
}
