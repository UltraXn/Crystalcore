package com.crystaltides.core.modules;

import com.crystaltides.core.CrystalCore;
import com.crystaltides.core.api.CrystalModule;
import com.crystaltides.core.profile.CrystalProfile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.entity.Player;
import redis.clients.jedis.Jedis;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
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
        
        plugin.getServer().getPluginManager().registerEvents(this, plugin);

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

    public void reloadProfile(UUID uuid) {
        Player player = plugin.getServer().getPlayer(uuid);
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

        UUID uuid = event.getUniqueId();
        String name = event.getName();

        CrystalProfile profile = loadProfile(uuid, name);
        profiles.put(uuid, profile);
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
    public void onBlockPlace(BlockPlaceEvent event) {
        CrystalProfile profile = getProfile(event.getPlayer().getUniqueId());
        if (profile != null) {
            profile.addBlockPlaced();
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDeath(EntityDeathEvent event) {
        Player killer = event.getEntity().getKiller();
        if (killer != null) {
            CrystalProfile killerProfile = getProfile(killer.getUniqueId());
            if (killerProfile != null) {
                if (event.getEntity() instanceof Player) {
                    killerProfile.addKill();
                } else {
                    killerProfile.addMobKill();
                }
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

        profile.setLastSeen(System.currentTimeMillis());
        try (Jedis jedis = redisModule.getResource()) {
            if (jedis == null) return;

            String key = "player:stats:" + profile.getUuid().toString();
            Map<String, String> stats = new HashMap<>();
            stats.put("name", profile.getPlayerName());
            stats.put("blocksMined", String.valueOf(profile.getBlocksMined()));
            stats.put("blocksPlaced", String.valueOf(profile.getBlocksPlaced()));
            stats.put("kills", String.valueOf(profile.getKills()));
            stats.put("mobKills", String.valueOf(profile.getMobKills()));
            stats.put("deaths", String.valueOf(profile.getDeaths()));
            stats.put("playtimeSeconds", String.valueOf(profile.getPlaytimeSeconds()));
            stats.put("streakDays", String.valueOf(profile.getStreakDays()));
            stats.put("killucoins", String.valueOf(profile.getKillucoins()));
            stats.put("lastSeen", String.valueOf(profile.getLastSeen()));
            
            jedis.hmset(key, stats);
            jedis.expire(key, 3600); // 1 hour TTL
            
            // Set mapping from username to UUID key
            jedis.set("player:uuid:" + profile.getPlayerName().toLowerCase(), profile.getUuid().toString());
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to push stats to Redis for " + profile.getPlayerName() + ": " + e.getMessage());
        }
    }

    private CrystalProfile loadProfile(UUID uuid, String name) {
        if (databaseModule == null)
            return new CrystalProfile(uuid, name);

        CrystalProfile profile = new CrystalProfile(uuid, name);

        try (Connection conn = databaseModule.getConnection();
                PreparedStatement ps = conn.prepareStatement(
                        "SELECT discord_id, web_user_id, blocks_mined, kills, deaths, gacha_balance FROM linked_accounts WHERE minecraft_uuid = ?")) {

            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    profile.setLinked(true);
                    profile.setDiscordId(rs.getString("discord_id"));
                    profile.setWebUserId(rs.getString("web_user_id"));
                    profile.setBlocksMined(rs.getInt("blocks_mined"));
                    profile.setKills(rs.getInt("kills"));
                    profile.setDeaths(rs.getInt("deaths"));
                    profile.setKillucoins(rs.getLong("gacha_balance"));
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to load profile for " + name + ": " + e.getMessage());
        }

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
                     "UPDATE linked_accounts SET blocks_mined = ?, kills = ?, deaths = ?, last_seen = ?, gacha_balance = ? WHERE minecraft_uuid = ?")) {
            
            ps.setInt(1, profile.getBlocksMined());
            ps.setInt(2, profile.getKills());
            ps.setInt(3, profile.getDeaths());
            ps.setLong(4, profile.getLastSeen());
            ps.setLong(5, profile.getKillucoins());
            ps.setString(6, profile.getUuid().toString());
            
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to save profile for " + profile.getPlayerName() + ": " + e.getMessage());
        }
    }
}
