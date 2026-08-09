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
    private volatile int blocksPlaced;
    private volatile int kills;
    private volatile int mobKills;
    private volatile int deaths;
    private volatile long playtimeSeconds;
    private volatile int streakDays;

    // Economy
    private volatile long killucoins;

    public CrystalProfile(UUID uuid, String playerName) {
        this.uuid = uuid;
        this.playerName = playerName;
        this.linked = false;
        this.lastSeen = System.currentTimeMillis();
        this.blocksMined = 0;
        this.blocksPlaced = 0;
        this.kills = 0;
        this.mobKills = 0;
        this.deaths = 0;
        this.playtimeSeconds = 0;
        this.streakDays = 1;
        this.killucoins = 0;
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

    public int getBlocksPlaced() {
        return blocksPlaced;
    }

    public void setBlocksPlaced(int blocksPlaced) {
        this.blocksPlaced = blocksPlaced;
    }

    public void addBlockPlaced() {
        this.blocksPlaced++;
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

    public int getMobKills() {
        return mobKills;
    }

    public void setMobKills(int mobKills) {
        this.mobKills = mobKills;
    }

    public void addMobKill() {
        this.mobKills++;
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

    public long getPlaytimeSeconds() {
        return playtimeSeconds;
    }

    public void setPlaytimeSeconds(long playtimeSeconds) {
        this.playtimeSeconds = playtimeSeconds;
    }

    public void addPlaytimeSeconds(long seconds) {
        this.playtimeSeconds += seconds;
    }

    public int getStreakDays() {
        return streakDays;
    }

    public void setStreakDays(int streakDays) {
        this.streakDays = streakDays;
    }

    private volatile String language = "es";

    public String getLanguage() {
        return language != null ? language : "es";
    }

    public void setLanguage(String language) {
        this.language = language;
    }
}
