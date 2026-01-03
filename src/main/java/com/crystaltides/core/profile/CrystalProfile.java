package com.crystaltides.core.profile;

import java.util.UUID;

public class CrystalProfile {

    private final UUID uuid;
    private final String playerName;

    // Status
    private boolean linked;
    private String discordId;
    private String webUserId;

    // Stats
    private long lastSeen;

    // Economy (Future placeholder)
    private long killucoins;

    public CrystalProfile(UUID uuid, String playerName) {
        this.uuid = uuid;
        this.playerName = playerName;
        this.linked = false;
        this.lastSeen = System.currentTimeMillis();
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
}
