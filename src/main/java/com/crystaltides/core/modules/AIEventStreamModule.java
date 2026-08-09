package com.crystaltides.core.modules;

import com.crystaltides.core.CrystalCore;
import com.crystaltides.core.api.CrystalModule;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerAdvancementDoneEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.inventory.ItemStack;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Level;

/**
 * Módulo de Captura de Telemetría e Eventos In-Game para el ciclo de IA de CrystalTides SMP.
 * Transmite muertes de bosses, logros y eventos comunitarios a la tabla ai_event_stream de Supabase.
 */
public class AIEventStreamModule extends CrystalModule {

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();
    private String supabaseUrl;
    private String supabaseKey;

    // Bosses y Mobs Míticos en la Whitelist de Telemetría
    private static final Set<String> BOSS_WHITELIST = new HashSet<>(Arrays.asList(
            "cataclysm:ignis",
            "cataclysm:netherite_monstrosity",
            "cataclysm:ender_guardian",
            "cataclysm:the_harbringer",
            "cataclysm:the_leviathan",
            "cataclysm:maledictus",
            "mowziesmobs:frostmaw",
            "mowziesmobs:ferrous_wroughtnaut",
            "mowziesmobs:barako",
            "minecraft:wither",
            "minecraft:ender_dragon"
    ));

    public AIEventStreamModule(CrystalCore plugin) {
        super(plugin, "AIEventStream");
    }

    @Override
    public void onEnable() {
        super.onEnable();
        boolean enabledInConfig = plugin.getConfig().getBoolean("modules.AIEventStream", true);

        if (!enabledInConfig) {
            plugin.getLogger().info("⚠️ Módulo AIEventStream desactivado en config.yml.");
            return;
        }

        this.supabaseUrl = plugin.getConfig().getString("supabase.url", "https://gyoqnqvqhuxlcbrvtfia.supabase.co");
        this.supabaseKey = plugin.getConfig().getString("supabase.service-key", "");

        Bukkit.getPluginManager().registerEvents(this, plugin);
        plugin.getLogger().info("📡 Módulo AIEventStream activado (Captura de Eventos In-Game para IA).");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDeath(EntityDeathEvent event) {
        LivingEntity entity = event.getEntity();
        Player killer = entity.getKiller();

        if (killer == null) return;

        String entityCustomName = entity.getName();
        String entityTypeId = entity.getType().getKey().toString().toLowerCase();

        // Verificar si es un boss en la whitelist o una entidad personalizada mítica
        boolean isBoss = BOSS_WHITELIST.contains(entityTypeId) 
                || entityCustomName.toLowerCase().contains("boss") 
                || entityCustomName.toLowerCase().contains("jefe");

        if (!isBoss) return;

        ItemStack weapon = killer.getInventory().getItemInMainHand();
        String weaponName = (weapon.getType() != Material.AIR) ? weapon.getType().name() : "Desarmado";

        String payloadJson = String.format(
                "{\"event_type\":\"boss_fight_kill\",\"player_uuid\":\"%s\",\"details\":{\"player_name\":\"%s\",\"boss_id\":\"%s\",\"boss_name\":\"%s\",\"world\":\"%s\",\"x\":%d,\"y\":%d,\"z\":%d,\"weapon\":\"%s\"}}",
                killer.getUniqueId().toString(),
                escapeJson(killer.getName()),
                escapeJson(entityTypeId),
                escapeJson(entityCustomName),
                escapeJson(entity.getWorld().getName()),
                entity.getLocation().getBlockX(),
                entity.getLocation().getBlockY(),
                entity.getLocation().getBlockZ(),
                escapeJson(weaponName)
        );

        sendToSupabaseAsync(payloadJson);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onAdvancementDone(PlayerAdvancementDoneEvent event) {
        String key = event.getAdvancement().getKey().getKey();
        if (!key.startsWith("story/") && !key.startsWith("nether/") && !key.startsWith("end/")) {
            return;
        }

        Player player = event.getPlayer();
        String payloadJson = String.format(
                "{\"event_type\":\"achievement\",\"player_uuid\":\"%s\",\"details\":{\"player_name\":\"%s\",\"advancement\":\"%s\"}}",
                player.getUniqueId().toString(),
                escapeJson(player.getName()),
                escapeJson(key)
        );

        sendToSupabaseAsync(payloadJson);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerFish(PlayerFishEvent event) {
        if (event.getState() != PlayerFishEvent.State.CAUGHT_FISH) return;
        Entity caught = event.getCaught();
        if (caught == null) return;

        Player player = event.getPlayer();
        String payloadJson = String.format(
                "{\"event_type\":\"fish_caught\",\"player_uuid\":\"%s\",\"details\":{\"player_name\":\"%s\",\"caught_item\":\"%s\"}}",
                player.getUniqueId().toString(),
                escapeJson(player.getName()),
                escapeJson(caught.getName())
        );

        sendToSupabaseAsync(payloadJson);
    }

    private void sendToSupabaseAsync(String jsonPayload) {
        if (supabaseUrl.isEmpty() || supabaseKey.isEmpty()) {
            return;
        }

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(supabaseUrl + "/rest/v1/ai_event_stream"))
                        .header("Content-Type", "application/json")
                        .header("apikey", supabaseKey)
                        .header("Authorization", "Bearer " + supabaseKey)
                        .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                        .timeout(Duration.ofSeconds(5))
                        .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() >= 200 && response.statusCode() < 300) {
                    plugin.getLogger().fine(() -> "✅ Evento AI enviado exitosamente a Supabase.");
                } else {
                    plugin.getLogger().warning(() -> "⚠️ Error enviando evento a Supabase HTTP " + response.statusCode() + ": " + response.body());
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                plugin.getLogger().log(Level.WARNING, "❌ Petición a Supabase interrumpida", e);
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "❌ Error asíncrono enviando evento a Supabase", e);
            }
        });
    }

    private String escapeJson(String input) {
        if (input == null) return "";
        return input.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
