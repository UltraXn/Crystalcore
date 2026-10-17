package com.crystaltides.core.profile;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class CrystalProfile {

    private final UUID uuid;
    private final String playerName;

    // Status
    private volatile boolean linked;
    private volatile String discordId;
    private volatile String webUserId;

    // Stats
    private volatile long lastSeen;
    private final AtomicInteger blocksMined;
    private final AtomicInteger blocksPlaced;
    private final AtomicInteger kills;
    private final AtomicInteger mobKills;
    private final AtomicInteger deaths;
    private final AtomicLong playtimeSeconds;
    private volatile int streakDays;

    // Economy
    private volatile long killucoins;

    public CrystalProfile(UUID uuid, String playerName) {
        this.uuid = uuid;
        this.playerName = playerName;
        this.linked = false;
        this.lastSeen = System.currentTimeMillis();
        this.blocksMined = new AtomicInteger(0);
        this.blocksPlaced = new AtomicInteger(0);
        this.kills = new AtomicInteger(0);
        this.mobKills = new AtomicInteger(0);
        this.deaths = new AtomicInteger(0);
        this.playtimeSeconds = new AtomicLong(0L);
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
        return blocksMined.get();
    }

    public void setBlocksMined(int blocksMined) {
        this.blocksMined.set(blocksMined);
    }

    public void addBlockMined() {
        this.blocksMined.incrementAndGet();
    }

    public int getBlocksPlaced() {
        return blocksPlaced.get();
    }

    public void setBlocksPlaced(int blocksPlaced) {
        this.blocksPlaced.set(blocksPlaced);
    }

    public void addBlockPlaced() {
        this.blocksPlaced.incrementAndGet();
    }

    public int getKills() {
        return kills.get();
    }

    public void setKills(int kills) {
        this.kills.set(kills);
    }

    public void addKill() {
        this.kills.incrementAndGet();
    }

    public int getMobKills() {
        return mobKills.get();
    }

    public void setMobKills(int mobKills) {
        this.mobKills.set(mobKills);
    }

    public void addMobKill() {
        this.mobKills.incrementAndGet();
    }

    public int getDeaths() {
        return deaths.get();
    }

    public void setDeaths(int deaths) {
        this.deaths.set(deaths);
    }

    public void addDeath() {
        this.deaths.incrementAndGet();
    }

    public long getPlaytimeSeconds() {
        return playtimeSeconds.get();
    }

    public void setPlaytimeSeconds(long playtimeSeconds) {
        this.playtimeSeconds.set(playtimeSeconds);
    }

    public void addPlaytimeSeconds(long seconds) {
        this.playtimeSeconds.addAndGet(seconds);
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
