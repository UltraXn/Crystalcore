package com.crystaltides.core.modules;

import com.crystaltides.core.CrystalCore;
import com.crystaltides.core.api.CrystalModule;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.sql.SQLException;

public class DatabaseModule extends CrystalModule {

    private HikariDataSource dataSource;

    public DatabaseModule(CrystalCore plugin) {
        super(plugin, "Database");
    }

    @Override
    public void onEnable() {
        if (setupDatabase()) {
            super.onEnable();
            verifyTables();
        } else {
            plugin.getLogger().severe("Disabling DatabaseModule due to connection errors.");
        }
    }

    @Override
    public void onDisable() {
        closeDatabase();
        super.onDisable();
    }

    public Connection getConnection() throws SQLException {
        if (dataSource == null || dataSource.isClosed()) {
            throw new SQLException("Database source is closed or null.");
        }
        return dataSource.getConnection();
    }

    public HikariDataSource getDataSource() {
        return dataSource;
    }

    private boolean setupDatabase() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:mysql://" + plugin.getConfig().getString("database.host") + ":"
                + plugin.getConfig().getString("database.port") + "/" + plugin.getConfig().getString("database.name"));
        config.setUsername(plugin.getConfig().getString("database.user"));
        config.setPassword(plugin.getConfig().getString("database.password"));
        config.addDataSourceProperty("cachePrepStmts", "true");
        config.addDataSourceProperty("prepStmtCacheSize", "250");
        config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
        config.setConnectionTimeout(5000);

        try {
            dataSource = new HikariDataSource(config);
            plugin.getLogger().info("Database connection pool initialized.");
            return true;
        } catch (Exception e) {
            plugin.getLogger().severe("Could not initialize database pool: " + e.getMessage());
            return false;
        }
    }

    private void verifyTables() {
        try (Connection conn = getConnection()) {
            // Universal Verification Table
            conn.prepareStatement(
                    "CREATE TABLE IF NOT EXISTS universal_links (" +
                            "code VARCHAR(10) PRIMARY KEY, " +
                            "source VARCHAR(20) NOT NULL, " +
                            "source_id VARCHAR(100) NOT NULL, " +
                            "player_name VARCHAR(16), " +
                            "expires_at BIGINT NOT NULL, " +
                            "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP);")
                    .execute();

            // Unified Linked Accounts Table
            conn.prepareStatement(
                    "CREATE TABLE IF NOT EXISTS linked_accounts (" +
                            "minecraft_uuid VARCHAR(36) PRIMARY KEY, " +
                            "minecraft_name VARCHAR(16), " +
                            "discord_id VARCHAR(20) UNIQUE, " +
                            "discord_tag VARCHAR(100), " +
                            "web_user_id VARCHAR(100) UNIQUE, " +
                            "gacha_balance BIGINT DEFAULT 0, " +
                            "unlocked_tiers TEXT, " +
                            "linked_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP);")
                    .execute();

            // Secure Queue Table
            conn.prepareStatement(
                    "CREATE TABLE IF NOT EXISTS web_pending_commands (id INT AUTO_INCREMENT PRIMARY KEY, command VARCHAR(512) NOT NULL, executed BOOLEAN DEFAULT FALSE, created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, executed_at TIMESTAMP NULL);")
                    .execute();

            plugin.getLogger().info("Database tables verified.");
        } catch (SQLException e) {
            plugin.getLogger().severe("Could not verified database tables! " + e.getMessage());
        }
    }

    private void closeDatabase() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
    }
}
