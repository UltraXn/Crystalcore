package com.crystaltides.core.commands;

import com.crystaltides.core.CrystalCore;
import com.crystaltides.core.modules.MarketplaceModule;
import com.crystaltides.core.modules.MarketplaceModule.MarketItem;
import com.crystaltides.core.managers.ModuleManager;
import net.kyori.adventure.text.Component;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class BolsaCommandTest {

    private CrystalCore plugin;
    private ModuleManager moduleManager;
    private MarketplaceModule market;
    private BolsaCommand command;
    private Command mockCmd;

    @BeforeEach
    void setUp() {
        plugin = mock(CrystalCore.class);
        moduleManager = mock(ModuleManager.class);
        market = mock(MarketplaceModule.class);
        mockCmd = mock(Command.class);

        when(plugin.getModuleManager()).thenReturn(moduleManager);
        when(moduleManager.getModule(MarketplaceModule.class)).thenReturn(market);
        when(market.isEnabled()).thenReturn(true);

        command = new BolsaCommand(plugin);
    }

    @Test
    @DisplayName("Rechaza ejecución si el sender no es un jugador")
    void testNonPlayerSender() {
        CommandSender sender = mock(CommandSender.class);
        boolean result = command.onCommand(sender, mockCmd, "bolsa", new String[0]);

        assertTrue(result);
        verify(sender).sendMessage("§cSolo los jugadores pueden acceder a la bolsa de valores.");
    }

    @Test
    @DisplayName("Muestra mensaje si el módulo de mercado está deshabilitado")
    void testMarketDisabled() {
        when(market.isEnabled()).thenReturn(false);
        Player player = mock(Player.class);

        boolean result = command.onCommand(player, mockCmd, "bolsa", new String[0]);

        assertTrue(result);
        verify(player).sendMessage(any(Component.class));
    }

    @Test
    @DisplayName("Muestra catálogo cuando no se pasan argumentos")
    void testOverviewWithoutArgs() {
        Player player = mock(Player.class);
        boolean result = command.onCommand(player, mockCmd, "bolsa", new String[0]);

        assertTrue(result);
        verify(player, atLeastOnce()).sendMessage(any(Component.class));
    }

    @Test
    @DisplayName("Maneja subcomando desconocido mostrando mensaje de uso")
    void testUnknownSubcommand() {
        Player player = mock(Player.class);
        boolean result = command.onCommand(player, mockCmd, "bolsa", new String[]{"desconocido"});

        assertTrue(result);
        verify(player).sendMessage(any(Component.class));
    }

    @Test
    @DisplayName("Maneja compra y venta válidas en el mercado")
    void testBuyAndSellTrade() {
        Player player = mock(Player.class);
        MarketItem item = mock(MarketItem.class);
        when(market.getItem("diamante")).thenReturn(item);

        // Compra con cantidad especificada
        command.onCommand(player, mockCmd, "bolsa", new String[]{"buy", "diamante", "5"});
        verify(market).buyItem(player, item, 5);

        // Venta con cantidad por defecto (1)
        command.onCommand(player, mockCmd, "bolsa", new String[]{"sell", "diamante"});
        verify(market).sellItem(player, item, 1);
    }

    @Test
    @DisplayName("Maneja validación de argumentos insuficientes o ítem no existente")
    void testTradeInvalidArgs() {
        Player player = mock(Player.class);

        // Sin recurso especificado
        command.onCommand(player, mockCmd, "bolsa", new String[]{"buy"});
        verify(player).sendMessage(any(Component.class));

        // Recurso inexistente
        when(market.getItem("inexistente")).thenReturn(null);
        command.onCommand(player, mockCmd, "bolsa", new String[]{"buy", "inexistente"});
        verify(player, atLeast(2)).sendMessage(any(Component.class));

        // Cantidad con formato numérico inválido
        MarketItem item = mock(MarketItem.class);
        when(market.getItem("oro")).thenReturn(item);
        command.onCommand(player, mockCmd, "bolsa", new String[]{"buy", "oro", "no_es_numero"});
        verify(market, never()).buyItem(eq(player), eq(item), anyInt());
    }
}
