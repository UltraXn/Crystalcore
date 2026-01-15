package com.crystaltides.core.modules;

import com.crystaltides.core.CrystalCore;
import com.crystaltides.core.api.CrystalModule;
import com.crystaltides.core.profile.CrystalProfile;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.bukkit.Bukkit;
import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class BancoModule extends CrystalModule {

    private HikariDataSource sqliteDataSource;
    private final File databaseFile;

    public BancoModule(CrystalCore plugin) {
        super(plugin, "BancoBridge");
        // Path to plugins/banco/accounts.db
        this.databaseFile = new File(plugin.getDataFolder().getParentFile(), "banco/accounts.db");
    }

    @Override
    public void onEnable() {
        if (!databaseFile.exists()) {
            plugin.getLogger().warning("Banco database not found at " + databaseFile.getAbsolutePath());
            plugin.getLogger().warning("BancoBridge will be disabled.");
            return;
        }

        if (setupSQLite()) {
            super.onEnable();
            // Start sync task (optional, if we want periodic updates besides login)
        }
    }

    @Override
    public void onDisable() {
        if (sqliteDataSource != null && !sqliteDataSource.isClosed()) {
            sqliteDataSource.close();
        }
        super.onDisable();
    }

    private boolean setupSQLite() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:sqlite:" + databaseFile.getAbsolutePath());
        config.setPoolName("CrystalCore-BancoPool");
        config.setMaximumPoolSize(1); // SQLite handles 1 concurrent writer preferably

        try {
            sqliteDataSource = new HikariDataSource(config);
            plugin.getLogger().info("Connected to Banco SQLite database.");
            return true;
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to connect to Banco SQLite: " + e.getMessage());
            return false;
        }
    }

    public void syncProfile(CrystalProfile profile) {
        if (!isEnabled())
            return;

        if (Bukkit.isPrimaryThread()) {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> performSync(profile));
        } else {
            performSync(profile);
        }
    }

    private void performSync(CrystalProfile profile) {
        try (Connection conn = sqliteDataSource.getConnection();
                PreparedStatement ps = conn.prepareStatement("SELECT balance FROM accounts WHERE player_name = ?")) {

            ps.setString(1, profile.getPlayerName());

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    long balance = rs.getLong("balance");
                    profile.setKillucoins(balance);
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("Error syncing balance for " + profile.getPlayerName() + ": " + e.getMessage());
        }
    }
}
