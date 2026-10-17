package com.crystaltides.core.profile;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class CrystalProfileTest {

    @Test
    @DisplayName("Garantiza inicialización correcta de estadísticas y contadores atómicos")
    void testInitialStats() {
        UUID uuid = UUID.randomUUID();
        CrystalProfile profile = new CrystalProfile(uuid, "Steve");

        assertEquals(uuid, profile.getUuid());
        assertEquals("Steve", profile.getPlayerName());
        assertFalse(profile.isLinked());
        assertEquals(0, profile.getBlocksMined());
        assertEquals(0, profile.getBlocksPlaced());
        assertEquals(0, profile.getKills());
        assertEquals(0, profile.getMobKills());
        assertEquals(0, profile.getDeaths());
        assertEquals(0L, profile.getPlaytimeSeconds());
        assertEquals(1, profile.getStreakDays());
        assertEquals("es", profile.getLanguage());
    }

    @Test
    @DisplayName("Garantiza incremento y actualización de bloques minados y colocados")
    void testBlocksMinedAndPlaced() {
        UUID uuid = UUID.randomUUID();
        CrystalProfile profile = new CrystalProfile(uuid, "Alex");

        profile.setBlocksMined(50);
        assertEquals(50, profile.getBlocksMined());
        profile.addBlockMined();
        assertEquals(51, profile.getBlocksMined());

        profile.setBlocksPlaced(100);
        assertEquals(100, profile.getBlocksPlaced());
        profile.addBlockPlaced();
        assertEquals(101, profile.getBlocksPlaced());
    }

    @Test
    @DisplayName("Garantiza incremento y actualización de kills, mobKills y muertes")
    void testCombatStats() {
        UUID uuid = UUID.randomUUID();
        CrystalProfile profile = new CrystalProfile(uuid, "Warrior");

        profile.setKills(5);
        assertEquals(5, profile.getKills());
        profile.addKill();
        assertEquals(6, profile.getKills());

        profile.setMobKills(42);
        assertEquals(42, profile.getMobKills());
        profile.addMobKill();
        assertEquals(43, profile.getMobKills());

        profile.setDeaths(3);
        assertEquals(3, profile.getDeaths());
        profile.addDeath();
        assertEquals(4, profile.getDeaths());
    }

    @Test
    @DisplayName("Garantiza incremento y actualización de tiempo de juego y configuración de perfil")
    void testPlaytimeAndSettings() {
        UUID uuid = UUID.randomUUID();
        CrystalProfile profile = new CrystalProfile(uuid, "Speedrunner");

        profile.setPlaytimeSeconds(3600L);
        assertEquals(3600L, profile.getPlaytimeSeconds());
        profile.addPlaytimeSeconds(120L);
        assertEquals(3720L, profile.getPlaytimeSeconds());

        profile.setStreakDays(7);
        assertEquals(7, profile.getStreakDays());

        profile.setLanguage("en");
        assertEquals("en", profile.getLanguage());

        profile.setLinked(true);
        assertTrue(profile.isLinked());

        profile.setKillucoins(1500L);
        assertEquals(1500L, profile.getKillucoins());
    }
}
