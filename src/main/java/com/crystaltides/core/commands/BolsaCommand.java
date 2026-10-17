package com.crystaltides.core.commands;

import com.crystaltides.core.CrystalCore;
import com.crystaltides.core.modules.MarketplaceModule;
import com.crystaltides.core.modules.MarketplaceModule.MarketItem;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class BolsaCommand implements CommandExecutor, TabCompleter {

    private final CrystalCore plugin;

    public BolsaCommand(CrystalCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cSolo los jugadores pueden acceder a la bolsa de valores.");
            return true;
        }

        MarketplaceModule market = plugin.getModuleManager().getModule(MarketplaceModule.class);
        if (market == null || !market.isEnabled()) {
            player.sendMessage(Component.text("❌ El mercado dinámico no está disponible actualmente.", NamedTextColor.RED));
            return true;
        }

        if (args.length == 0 || "ver".equalsIgnoreCase(args[0]) || "list".equalsIgnoreCase(args[0])) {
            displayMarketOverview(player, market);
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "sell", "vender" -> handleTrade(player, market, "sell", args, false);
            case "buy", "comprar" -> handleTrade(player, market, "buy", args, true);
            default -> player.sendMessage(Component.text("Uso: /bolsa [ver|sell|buy] [recurso] [cantidad]", NamedTextColor.YELLOW));
        }

        return true;
    }

    private void handleTrade(Player player, MarketplaceModule market, String action, String[] args, boolean isBuy) {
        if (args.length < 2) {
            player.sendMessage(Component.text("Uso: /bolsa " + action + " <recurso> [cantidad]", NamedTextColor.RED));
            return;
        }
        MarketItem item = market.getItem(args[1]);
        if (item == null) {
            player.sendMessage(Component.text("❌ Recurso no válido. Usa /bolsa para ver el catálogo.", NamedTextColor.RED));
            return;
        }
        int amount = parseAmount(player, args);
        if (amount <= 0) {
            return;
        }
        if (isBuy) {
            market.buyItem(player, item, amount);
        } else {
            market.sellItem(player, item, amount);
        }
    }

    private int parseAmount(Player player, String[] args) {
        if (args.length < 3) {
            return 1;
        }
        try {
            return Integer.parseInt(args[2]);
        } catch (NumberFormatException e) {
            player.sendMessage(Component.text("❌ Cantidad inválida.", NamedTextColor.RED));
            return -1;
        }
    }

    private void displayMarketOverview(Player player, MarketplaceModule market) {
        player.sendMessage(Component.text("══════════ ", NamedTextColor.DARK_AQUA)
                .append(Component.text("📈 BOLSA DE RECURSOS CRYSTALTIDES", NamedTextColor.GOLD, TextDecoration.BOLD))
                .append(Component.text(" ══════════", NamedTextColor.DARK_AQUA)));
        player.sendMessage(Component.text("Los precios fluctúan según la oferta y demanda de los jugadores.", NamedTextColor.GRAY));

        for (MarketItem item : market.getCatalog().values()) {
            Component line = Component.text("• ", NamedTextColor.DARK_GRAY)
                    .append(Component.text(item.getDisplayName() + " ", NamedTextColor.WHITE, TextDecoration.BOLD))
                    .append(Component.text(String.format(Locale.US, "[%.2f KC] ", item.getCurrentPrice()), NamedTextColor.GOLD))
                    .append(Component.text("[VENDER]", NamedTextColor.GREEN, TextDecoration.BOLD)
                            .hoverEvent(HoverEvent.showText(Component.text("Click para vender 1x " + item.getDisplayName())))
                            .clickEvent(ClickEvent.runCommand("/bolsa sell " + item.getId() + " 1")))
                    .append(Component.text(" "))
                    .append(Component.text("[COMPRAR]", NamedTextColor.AQUA, TextDecoration.BOLD)
                            .hoverEvent(HoverEvent.showText(Component.text("Click para comprar 1x " + item.getDisplayName())))
                            .clickEvent(ClickEvent.runCommand("/bolsa buy " + item.getId() + " 1")));
            player.sendMessage(line);
        }
        player.sendMessage(Component.text("═══════════════════════════════════════════", NamedTextColor.DARK_AQUA));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> suggestions = new ArrayList<>();
        MarketplaceModule market = plugin.getModuleManager().getModule(MarketplaceModule.class);

        if (args.length == 1) {
            suggestions.add("ver");
            suggestions.add("sell");
            suggestions.add("buy");
        } else if (args.length == 2 && ("sell".equalsIgnoreCase(args[0]) || "buy".equalsIgnoreCase(args[0]))) {
            if (market != null) {
                suggestions.addAll(market.getCatalog().keySet());
            }
        } else if (args.length == 3) {
            suggestions.add("1");
            suggestions.add("16");
            suggestions.add("64");
        }
        return suggestions;
    }
}
