package com.crystaltides.core.modules;

import com.crystaltides.core.CrystalCore;
import com.crystaltides.core.api.CrystalModule;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

public class RedisModule extends CrystalModule {

    private JedisPool jedisPool;
    private boolean enabled;

    public RedisModule(CrystalCore plugin) {
        super(plugin, "Redis");
    }

    @Override
    public void onEnable() {
        this.enabled = plugin.getConfig().getBoolean("redis.enabled", false);
        if (!enabled) {
            plugin.getLogger().info("Redis is disabled in config.");
            return;
        }

        String host = plugin.getConfig().getString("redis.host", "127.0.0.1");
        int port = plugin.getConfig().getInt("redis.port", 6379);
        String password = plugin.getConfig().getString("redis.password", "");
        int timeout = plugin.getConfig().getInt("redis.timeout", 2000);

        JedisPoolConfig poolConfig = new JedisPoolConfig();
        poolConfig.setMaxTotal(8);
        poolConfig.setMaxIdle(8);
        poolConfig.setMinIdle(0);

        try {
            if (password != null && !password.isEmpty()) {
                this.jedisPool = new JedisPool(poolConfig, host, port, timeout, password);
            } else {
                this.jedisPool = new JedisPool(poolConfig, host, port, timeout);
            }
            
            // Test connection
            try (Jedis jedis = jedisPool.getResource()) {
                jedis.ping();
            }
            
            plugin.getLogger().info("Redis connection pool initialized.");
            super.onEnable();
        } catch (Exception e) {
            plugin.getLogger().severe("Could not initialize Redis pool: " + e.getMessage());
            this.enabled = false;
        }
    }

    @Override
    public void onDisable() {
        if (jedisPool != null && !jedisPool.isClosed()) {
            jedisPool.close();
        }
        super.onDisable();
    }

    public Jedis getResource() {
        if (!enabled || jedisPool == null) return null;
        return jedisPool.getResource();
    }

    public boolean isRedisEnabled() {
        return enabled;
    }
}
