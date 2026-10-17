package com.crystaltides.core.commands;

import com.crystaltides.core.CrystalCore;
import com.crystaltides.core.modules.DungeonSpeedrunModule;
import com.crystaltides.core.managers.ModuleManager;
import net.kyori.adventure.text.Component;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class SpeedrunCommandTest {

    private CrystalCore plugin;
    private ModuleManager moduleManager;
    private DungeonSpeedrunModule speedrun;
    private SpeedrunCommand command;
    private Command mockCmd;

    @BeforeEach
    void setUp() {
        plugin = mock(CrystalCore.class);
        moduleManager = mock(ModuleManager.class);
        speedrun = mock(DungeonSpeedrunModule.class);
        mockCmd = mock(Command.class);

        when(plugin.getModuleManager()).thenReturn(moduleManager);
        when(moduleManager.getModule(DungeonSpeedrunModule.class)).thenReturn(speedrun);
        when(speedrun.isEnabled()).thenReturn(true);

        command = new SpeedrunCommand(plugin);
    }

    @Test
    @DisplayName("Rechaza ejecución si el sender no es un jugador")
    void testNonPlayerSender() {
        CommandSender sender = mock(CommandSender.class);
        boolean result = command.onCommand(sender, mockCmd, "speedrun", new String[0]);

        assertTrue(result);
        verify(sender).sendMessage("§cSolo los jugadores pueden usar el sistema de speedruns.");
    }

    @Test
    @DisplayName("Muestra mensaje si el módulo de speedruns está deshabilitado")
    void testSpeedrunDisabled() {
        when(speedrun.isEnabled()).thenReturn(false);
        Player player = mock(Player.class);

        boolean result = command.onCommand(player, mockCmd, "speedrun", new String[0]);

        assertTrue(result);
        verify(player).sendMessage(any(Component.class));
    }

    @Test
    @DisplayName("Muestra estado actual cuando se ejecuta sin argumentos")
    void testStatusWithoutArgs() {
        Player player = mock(Player.class);
        boolean result = command.onCommand(player, mockCmd, "speedrun", new String[0]);

        assertTrue(result);
        verify(player, atLeastOnce()).sendMessage(any(Component.class));
    }

    @Test
    @DisplayName("Ejecuta start, complete y cancel correctamente")
    void testStartCompleteCancel() {
        Player player = mock(Player.class);

        // start
        command.onCommand(player, mockCmd, "speedrun", new String[]{"start", "Catacombs"});
        verify(speedrun).startAttempt(player, "Catacombs");

        // complete
        command.onCommand(player, mockCmd, "speedrun", new String[]{"complete", "Catacombs"});
        verify(speedrun).completeAttempt(player, "Catacombs");

        // cancel
        command.onCommand(player, mockCmd, "speedrun", new String[]{"cancel"});
        verify(speedrun).cancelAttempt(player);
    }

    @Test
    @DisplayName("Ejecuta top y maneja subcomando desconocido")
    void testTopAndUnknown() {
        Player player = mock(Player.class);
        // top
        command.onCommand(player, mockCmd, "speedrun", new String[]{"top"});
        verify(speedrun).fetchTopRecords(eq("Trial_Chamber"), eq(5), any());
        verify(player, atLeastOnce()).sendMessage(any(Component.class));

        // desconocido
        command.onCommand(player, mockCmd, "speedrun", new String[]{"desconocido"});
        verify(player, atLeastOnce()).sendMessage(any(Component.class));
    }

    @Test
    @DisplayName("Ofrece autocompletado en la pestaña para subcomandos y mazmorras")
    void testTabCompletion() {
        Player player = mock(Player.class);

        List<String> subSuggestions = command.onTabComplete(player, mockCmd, "speedrun", new String[]{""});
        assertTrue(subSuggestions != null && !subSuggestions.isEmpty());

        List<String> dungeonSuggestions = command.onTabComplete(player, mockCmd, "speedrun", new String[]{"start", ""});
        assertTrue(dungeonSuggestions != null && !dungeonSuggestions.isEmpty());
    }
}
