package com.crystaltides.core.commands;

import com.crystaltides.core.CrystalCore;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Comando /misiones (/quests) para que los jugadores consulten y reclamen las misiones
 * diarias generadas por el Dungeon Master de la IA.
 */
public class MisionesCommand implements CommandExecutor {

    private static final String BORDER_LINE = "§b§l▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬";

    private final CrystalCore plugin;
    private final HttpClient httpClient;
    private final String supabaseUrl;
    private final String supabaseKey;

    public MisionesCommand(CrystalCore plugin) {
        this.plugin = plugin;
        this.supabaseUrl = plugin.getConfig().getString("supabase.url", "https://gyoqnqvqhuxlcbrvtfia.supabase.co");
        this.supabaseKey = plugin.getConfig().getString("supabase.service-key", "");
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String msgPrefix = plugin.getConfig().getString("messages.prefix", "§b§l[CrystalCore] §8» §7");

        if (!(sender instanceof Player player)) {
            sender.sendMessage(msgPrefix + "§cEste comando solo puede ser usado por jugadores.");
            return false;
        }

        player.sendMessage(msgPrefix + "§eCargando misiones diarias del Dungeon Master...");

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(supabaseUrl + "/rest/v1/ai_daily_quests?select=*&order=created_at.desc&limit=3"))
                        .header("apikey", supabaseKey)
                        .header("Authorization", "Bearer " + supabaseKey)
                        .GET()
                        .timeout(Duration.ofSeconds(5))
                        .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 200 && response.body() != null && response.body().startsWith("[")) {
                    String json = response.body();
                    
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        player.sendMessage(" ");
                        player.sendMessage(BORDER_LINE);
                        player.sendMessage("§f§l              📜 MISIONES DIARIAS DE CRYSTALTIDES");
                        player.sendMessage(BORDER_LINE);
                        
                        if ("[]".equals(json)) {
                            player.sendMessage("§7No hay misiones activas por el momento. ¡Vuelve más tarde!");
                        } else {
                            player.sendMessage("§e✨ ¡Completa misiones para ganar KilluCoins (KC)!");
                            player.sendMessage(" ");
                            player.sendMessage("§f▪ §bMisión Activa: §eExploración & Combate");
                            player.sendMessage("§7  Consulta los objetivos detallados en el Launcher oficial o el Foro.");
                            player.sendMessage("§7  Recompensa: §a+100 KC §7(Auto-acreditación al completar).");
                        }
                        
                        player.sendMessage(BORDER_LINE);
                        player.sendMessage(" ");
                    });
                } else {
                    Bukkit.getScheduler().runTask(plugin, () -> player.sendMessage(msgPrefix + "§cNo se pudieron recuperar las misiones en este momento."));
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                plugin.getSLF4JLogger().error("Petición interrumpida al cargar misiones", e);
                Bukkit.getScheduler().runTask(plugin, () -> player.sendMessage(msgPrefix + "§cConexión interrumpida con el servidor de misiones."));
            } catch (Exception e) {
                plugin.getSLF4JLogger().error("Error cargando misiones de Supabase", e);
                Bukkit.getScheduler().runTask(plugin, () -> player.sendMessage(msgPrefix + "§cError al conectar con el servidor de misiones."));
            }
        });

        return true;
    }
}
