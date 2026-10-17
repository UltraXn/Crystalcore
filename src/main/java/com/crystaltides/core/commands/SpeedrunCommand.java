package com.crystaltides.core.commands;

import com.crystaltides.core.CrystalCore;
import com.crystaltides.core.modules.DungeonSpeedrunModule;
import com.crystaltides.core.modules.DungeonSpeedrunModule.DungeonRecord;
import com.crystaltides.core.modules.DungeonSpeedrunModule.SpeedrunAttempt;
import net.kyori.adventure.text.Component;
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

public class SpeedrunCommand implements CommandExecutor, TabCompleter {

    private static final String DEFAULT_DUNGEON = "Trial_Chamber";

    private final CrystalCore plugin;

    public SpeedrunCommand(CrystalCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cSolo los jugadores pueden usar el sistema de speedruns.");
            return true;
        }

        DungeonSpeedrunModule speedrun = plugin.getModuleManager().getModule(DungeonSpeedrunModule.class);
        if (speedrun == null || !speedrun.isEnabled()) {
            player.sendMessage(Component.text("❌ El sistema de Speedruns no está habilitado.", NamedTextColor.RED));
            return true;
        }

        if (args.length == 0 || "status".equalsIgnoreCase(args[0])) {
            handleStatus(player, speedrun);
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        String dungeon = (args.length >= 2) ? args[1] : DEFAULT_DUNGEON;

        switch (sub) {
            case "start", "iniciar" -> speedrun.startAttempt(player, dungeon);
            case "complete", "completar" -> speedrun.completeAttempt(player, dungeon);
            case "cancel", "cancelar" -> speedrun.cancelAttempt(player);
            case "top", "leaderboard" -> handleTop(player, speedrun, dungeon);
            default -> player.sendMessage(Component.text("Uso: /speedrun [start|complete|cancel|top] <mazmorra>", NamedTextColor.YELLOW));
        }

        return true;
    }

    private void handleStatus(Player player, DungeonSpeedrunModule speedrun) {
        SpeedrunAttempt attempt = speedrun.getAttempt(player.getUniqueId());
        if (attempt != null) {
            player.sendMessage(Component.text("⏱️ Speedrun activo: ", NamedTextColor.AQUA)
                    .append(Component.text(attempt.getDungeonId(), NamedTextColor.GOLD, TextDecoration.BOLD))
                    .append(Component.text(" | Tiempo transcurrido: ", NamedTextColor.GRAY))
                    .append(Component.text(attempt.getFormattedTime(), NamedTextColor.GREEN, TextDecoration.BOLD)));
        } else {
            player.sendMessage(Component.text("ℹ️ No tienes ningún intento de speedrun activo.", NamedTextColor.GRAY));
            player.sendMessage(Component.text("Uso: /speedrun [start|complete|cancel|top] <mazmorra>", NamedTextColor.YELLOW));
        }
    }

    private void handleTop(Player player, DungeonSpeedrunModule speedrun, String dungeon) {
        player.sendMessage(Component.text("🏆 Consultando récords para: " + dungeon + "...", NamedTextColor.GRAY));
        speedrun.fetchTopRecords(dungeon, 5, records -> {
            player.sendMessage(Component.text("═════ ", NamedTextColor.DARK_PURPLE)
                    .append(Component.text("🏆 MEJORES TIEMPOS (" + dungeon + ")", NamedTextColor.GOLD, TextDecoration.BOLD))
                    .append(Component.text(" ═════", NamedTextColor.DARK_PURPLE)));
            if (records.isEmpty()) {
                player.sendMessage(Component.text("No hay récords registrados todavía.", NamedTextColor.GRAY));
            } else {
                for (int i = 0; i < records.size(); i++) {
                    DungeonRecord r = records.get(i);
                    player.sendMessage(Component.text("#" + (i + 1) + " ", NamedTextColor.YELLOW, TextDecoration.BOLD)
                            .append(Component.text(r.getPlayerName() + ": ", NamedTextColor.WHITE))
                            .append(Component.text(r.getFormattedTime(), NamedTextColor.GREEN, TextDecoration.BOLD)));
                }
            }
            player.sendMessage(Component.text("═══════════════════════════════════", NamedTextColor.DARK_PURPLE));
        });
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> suggestions = new ArrayList<>();
        if (args.length == 1) {
            suggestions.add("status");
            suggestions.add("start");
            suggestions.add("complete");
            suggestions.add("cancel");
            suggestions.add("top");
        } else if (args.length == 2) {
            suggestions.add(DEFAULT_DUNGEON);
            suggestions.add("Cataclysm_Dungeon");
            suggestions.add("Deeper_Darker_Otherside");
            suggestions.add("End_Remastered_Citadel");
        }
        return suggestions;
    }
}
