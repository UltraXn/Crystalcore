package com.crystaltides.core.profile;

import java.util.UUID;

public class CrystalProfile {

    private final UUID uuid;
    private final String playerName;

    // Status
    private volatile boolean linked;
    private volatile String discordId;
    private volatile String webUserId;

    // Stats
    private volatile long lastSeen;
    private volatile int blocksMined;
    private volatile int kills;
    private volatile int deaths;

    // Economy (Future placeholder)
    private volatile long killucoins;

    public CrystalProfile(UUID uuid, String playerName) {
        this.uuid = uuid;
        this.playerName = playerName;
        this.linked = false;
        this.lastSeen = System.currentTimeMillis();
        this.blocksMined = 0;
        this.kills = 0;
        this.deaths = 0;
    }

    public UUID getUuid() {
        return uuid;
    }

    public String getPlayerName() {
        return playerName;
    }

    public boolean isLinked() {
        return linked;
    }

    public void setLinked(boolean linked) {
        this.linked = linked;
    }

    public String getDiscordId() {
        return discordId;
    }

    public void setDiscordId(String discordId) {
        this.discordId = discordId;
    }

    public String getWebUserId() {
        return webUserId;
    }

    public void setWebUserId(String webUserId) {
        this.webUserId = webUserId;
    }

    public long getLastSeen() {
        return lastSeen;
    }

    public void setLastSeen(long lastSeen) {
        this.lastSeen = lastSeen;
    }

    public long getKillucoins() {
        return killucoins;
    }

    public void setKillucoins(long killucoins) {
        this.killucoins = killucoins;
    }

    public int getBlocksMined() {
        return blocksMined;
    }

    public void setBlocksMined(int blocksMined) {
        this.blocksMined = blocksMined;
    }

    public void addBlockMined() {
        this.blocksMined++;
    }

    public int getKills() {
        return kills;
    }

    public void setKills(int kills) {
        this.kills = kills;
    }

    public void addKill() {
        this.kills++;
    }

    public int getDeaths() {
        return deaths;
    }

    public void setDeaths(int deaths) {
        this.deaths = deaths;
    }

    public void addDeath() {
        this.deaths++;
    }
}
