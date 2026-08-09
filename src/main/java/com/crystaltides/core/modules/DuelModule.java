package com.crystaltides.core.modules;

import com.crystaltides.core.CrystalCore;
import com.crystaltides.core.api.CrystalModule;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.PlayerDeathEvent;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@SuppressWarnings("deprecation")
public class DuelModule extends CrystalModule implements CommandExecutor {

    private String currentMasterOfArms = null;
    private long cooldownEndTimestamp = 0;

    // Pedidos de duelos pendientes: Target UUID -> Sender UUID
    private final Map<UUID, UUID> pendingChallenges = new HashMap<>();
    private final Set<UUID> inDuel = new HashSet<>();

    public DuelModule(CrystalCore plugin) {
        super(plugin, "DuelModule");
    }

    @Override
    public void onEnable() {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        if (plugin.getCommand("duelo") != null) {
            plugin.getCommand("duelo").setExecutor(this);
        }
        plugin.getLogger().info("[DuelModule] Módulo de Duelos 1v1 y Maestro de Armas habilitado.");
    }

    @Override
    public void onDisable() {
        this.pendingChallenges.clear();
        this.inDuel.clear();
    }

    public String getCurrentMasterOfArms() {
        return this.currentMasterOfArms;
    }

    public void setCurrentMasterOfArms(String currentMasterOfArms) {
        this.currentMasterOfArms = currentMasterOfArms;
    }

    public long getCooldownEndTimestamp() {
        return this.cooldownEndTimestamp;
    }

    public void setCooldownEndTimestamp(long cooldownEndTimestamp) {
        this.cooldownEndTimestamp = cooldownEndTimestamp;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Este comando solo puede ser ejecutado por jugadores.");
            return false;
        }

        Player player = (Player) sender;

        if (args.length == 0) {
            sendHelp(player);
            return false;
        }

        String sub = args[0].toLowerCase();
        if ("retar".equals(sub) || "challenge".equals(sub)) {
            handleChallenge(player, args);
            return true;
        } else if ("aceptar".equals(sub) || "accept".equals(sub)) {
            handleAccept(player);
            return true;
        } else if ("campeon".equals(sub) || "master".equals(sub)) {
            handleMaster(player);
            return true;
        }

        sendHelp(player);
        return false;
    }

    private void handleChallenge(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(ChatColor.RED + "Uso: /duelo retar <jugador>");
            return;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null || !target.isOnline()) {
            player.sendMessage(ChatColor.RED + "El jugador '" + args[1] + "' no está en línea.");
            return;
        }

        if (target.equals(player)) {
            player.sendMessage(ChatColor.RED + "No puedes retarte a ti mismo a un duelo.");
            return;
        }

        String master = getCurrentMasterOfArms();
        if (master != null && target.getName().equalsIgnoreCase(master)) {
            long now = System.currentTimeMillis();
            long cooldownEnd = getCooldownEndTimestamp();
            if (now < cooldownEnd) {
                long remainingSec = (cooldownEnd - now) / 1000;
                long hours = remainingSec / 3600;
                player.sendMessage(ChatColor.RED + "⚔️ El Maestro de Armas tiene un Cooldown de Defensa activo de " + hours + " horas restantes.");
                return;
            }
        }

        this.pendingChallenges.put(target.getUniqueId(), player.getUniqueId());
        player.sendMessage(ChatColor.GREEN + "⚔️ Has enviado un desafío de duelo a " + ChatColor.YELLOW + target.getName() + ChatColor.GREEN + ".");
        target.sendMessage(ChatColor.GOLD + "⚔️ ¡" + player.getName() + " te ha desafiado a un duelo por el título de Maestro de Armas! Escribe " + ChatColor.GREEN + "/duelo aceptar" + ChatColor.GOLD + " para combatir.");
        target.playSound(target.getLocation(), Sound.ITEM_GOAT_HORN_SOUND_0, 1.0f, 1.0f);
    }

    private void handleAccept(Player player) {
        UUID challengerUUID = this.pendingChallenges.remove(player.getUniqueId());
        if (challengerUUID == null) {
            player.sendMessage(ChatColor.RED + "No tienes ninguna solicitud de duelo pendiente.");
            return;
        }

        Player challenger = Bukkit.getPlayer(challengerUUID);
        if (challenger == null || !challenger.isOnline()) {
            player.sendMessage(ChatColor.RED + "El retador ya no se encuentra en línea.");
            return;
        }

        this.inDuel.add(player.getUniqueId());
        this.inDuel.add(challenger.getUniqueId());

        Bukkit.broadcastMessage(ChatColor.DARK_RED + "⚔️ ¡DUELO OFICIAL DE CAMPEONES! " + ChatColor.GOLD + challenger.getName() + ChatColor.YELLOW + " vs " + ChatColor.GOLD + player.getName() + ChatColor.YELLOW + " por el Título Supremo.");
        player.playSound(player.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 1.0f, 1.0f);
        challenger.playSound(challenger.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 1.0f, 1.0f);
    }

    private void handleMaster(Player player) {
        String master = getCurrentMasterOfArms();
        if (master != null) {
            player.sendMessage(ChatColor.GOLD + "👑 El Maestro de Armas actual es: " + ChatColor.GREEN + master);
        } else {
            player.sendMessage(ChatColor.YELLOW + "👑 Aún no hay un Maestro de Armas designado.");
        }
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        Player killer = victim.getKiller();

        if (this.inDuel.contains(victim.getUniqueId())) {
            this.inDuel.remove(victim.getUniqueId());
            if (killer != null) {
                this.inDuel.remove(killer.getUniqueId());

                String master = getCurrentMasterOfArms();
                if (master != null && victim.getName().equalsIgnoreCase(master)) {
                    setCurrentMasterOfArms(killer.getName());
                    setCooldownEndTimestamp(System.currentTimeMillis() + (7L * 24 * 3600 * 1000)); // 7 días

                    Bukkit.broadcastMessage(ChatColor.GOLD + "👑 ¡NUEVO MAESTRO DE ARMAS! " + ChatColor.GREEN + killer.getName() + ChatColor.GOLD + " ha derrotado a " + ChatColor.RED + victim.getName() + ChatColor.GOLD + " en duelo oficial.");
                    killer.sendTitle(ChatColor.GOLD + "¡NUEVO MAESTRO DE ARMAS!", ChatColor.YELLOW + "Has obtenido el Título Supremo de Combate", 10, 70, 20);
                }
            }
        }
    }

    private void sendHelp(Player player) {
        player.sendMessage(ChatColor.GOLD + "=== Comandos de Duelo y Maestro de Armas ===");
        player.sendMessage(ChatColor.YELLOW + "/duelo retar <jugador> " + ChatColor.WHITE + "- Desafía a un jugador o al Maestro de Armas.");
        player.sendMessage(ChatColor.YELLOW + "/duelo aceptar " + ChatColor.WHITE + "- Acepta un desafío de duelo.");
        player.sendMessage(ChatColor.YELLOW + "/duelo campeon " + ChatColor.WHITE + "- Muestra al Maestro de Armas actual.");
    }
}
