package com.crystaltides.core.modules;

import com.crystaltides.core.CrystalCore;
import com.crystaltides.core.api.CrystalModule;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Módulo de Telemetría Dinámica y Combate de Jefes (Boss Encounters).
 * Rastrea sesiones de combate contra jefes (Cataclysm, Mowzie's Mobs, Vainilla),
 * calcula daño acumulado, detecta el clímax de vida (<= 20% HP) y emite alertas cinemáticas.
 */
public class BossTelemetryModule extends CrystalModule {

    public static class BossCombatSession {
        private final UUID bossUniqueId;
        private final String bossTypeId;
        private final String bossDisplayName;
        private final double maxHealth;
        private final long startTime;
        private long lastDamageTime;
        private boolean climaxTriggered = false;
        private final Map<UUID, Double> playerDamageMap = new ConcurrentHashMap<>();
        private final Map<UUID, String> playerWeaponsMap = new ConcurrentHashMap<>();

        public BossCombatSession(LivingEntity entity, String typeId, String name, double maxHp) {
            this.bossUniqueId = entity.getUniqueId();
            this.bossTypeId = typeId;
            this.bossDisplayName = name;
            this.maxHealth = maxHp;
            this.startTime = System.currentTimeMillis();
            this.lastDamageTime = this.startTime;
        }

        public synchronized void recordDamage(Player player, double damage, String weapon) {
            this.lastDamageTime = System.currentTimeMillis();
            playerDamageMap.merge(player.getUniqueId(), damage, (oldVal, newVal) -> {
                double a = (oldVal != null) ? oldVal : 0.0;
                double b = (newVal != null) ? newVal : 0.0;
                return a + b;
            });
            if (weapon != null && !weapon.isEmpty()) {
                playerWeaponsMap.put(player.getUniqueId(), weapon);
            }
        }

        public UUID getBossUniqueId() { return bossUniqueId; }
        public String getBossTypeId() { return bossTypeId; }
        public boolean isClimaxTriggered() { return climaxTriggered; }
        public void setClimaxTriggered(boolean triggered) { this.climaxTriggered = triggered; }
        public String getBossDisplayName() { return bossDisplayName; }
        public double getMaxHealth() { return maxHealth; }
        public long getDurationSeconds() { return (System.currentTimeMillis() - startTime) / 1000; }
        public Map<UUID, Double> getPlayerDamageMap() { return playerDamageMap; }
        public Map<UUID, String> getPlayerWeaponsMap() { return playerWeaponsMap; }
    }

    private final Map<UUID, BossCombatSession> activeBossSessions = new ConcurrentHashMap<>();

    private static final Set<String> BOSS_ENTITY_TYPES = new HashSet<>(Arrays.asList(
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
            "minecraft:ender_dragon",
            "minecraft:warden",
            "minecraft:elder_guardian"
    ));

    public BossTelemetryModule(CrystalCore plugin) {
        super(plugin, "BossTelemetry");
    }

    @Override
    public void onEnable() {
        super.onEnable();
        startSessionCleanupTask();
        plugin.getLogger().info("⚔️ Módulo BossTelemetry (Telemetría de Combate de Jefes) habilitado.");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBossDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof LivingEntity)) return;

        LivingEntity victim = (LivingEntity) event.getEntity();
        String typeId = victim.getType().getKey().toString().toLowerCase();

        boolean isBoss = BOSS_ENTITY_TYPES.contains(typeId) 
                || victim.getName().toLowerCase().contains("boss") 
                || victim.getName().toLowerCase().contains("jefe");

        if (!isBoss) return;

        Player attacker = resolvePlayerAttacker(event.getDamager());
        if (attacker == null) return;

        AttributeInstance maxHpAttr = victim.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        double maxHp = maxHpAttr != null ? maxHpAttr.getValue() : 100.0;
        double currentHp = Math.max(0, victim.getHealth() - event.getFinalDamage());

        BossCombatSession session = activeBossSessions.computeIfAbsent(
                victim.getUniqueId(),
                k -> new BossCombatSession(victim, typeId, victim.getName(), maxHp)
        );

        String weaponName = attacker.getInventory().getItemInMainHand().getType().name();
        session.recordDamage(attacker, event.getFinalDamage(), weaponName);

        // Climax Trigger: Vida del boss <= 20%
        if (!session.isClimaxTriggered() && currentHp > 0 && (currentHp / maxHp) <= 0.20) {
            session.setClimaxTriggered(true);
            triggerClimaxAlert(attacker, session, currentHp, maxHp);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBossDefeated(EntityDeathEvent event) {
        LivingEntity victim = event.getEntity();
        BossCombatSession session = activeBossSessions.remove(victim.getUniqueId());

        if (session == null) return;

        Player killer = victim.getKiller();
        String killerName = killer != null ? killer.getName() : "Un escuadrón valiente";

        // Anuncio de Victoria Épica
        Component victoryMsg = Component.text("⚔️ ¡JEFE DERROTADO! ", NamedTextColor.GOLD, TextDecoration.BOLD)
                .append(Component.text(session.getBossDisplayName(), NamedTextColor.RED, TextDecoration.BOLD))
                .append(Component.text(" ha caído ante ", NamedTextColor.YELLOW))
                .append(Component.text(killerName, NamedTextColor.WHITE, TextDecoration.BOLD))
                .append(Component.text(" tras " + session.getDurationSeconds() + "s de combate!", NamedTextColor.GRAY));

        Bukkit.broadcast(victoryMsg);

        for (Player p : Bukkit.getOnlinePlayers()) {
            p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
        }
    }

    private void triggerClimaxAlert(Player player, BossCombatSession session, double currentHp, double maxHp) {
        String bossName = session.getBossDisplayName();

        Component alertMsg = Component.text("🩸 ¡CLÍMAX DE COMBATE! ", NamedTextColor.DARK_RED, TextDecoration.BOLD)
                .append(Component.text(bossName, NamedTextColor.RED, TextDecoration.BOLD))
                .append(Component.text(String.format(" está en estado crítico (%.1f%% HP) frente a ", (currentHp / maxHp) * 100), NamedTextColor.YELLOW))
                .append(Component.text(player.getName(), NamedTextColor.WHITE, TextDecoration.BOLD))
                .append(Component.text("!", NamedTextColor.YELLOW));

        Bukkit.broadcast(alertMsg);

        // Título visual de advertencia para el jugador en combate
        Title title = Title.title(
                Component.text("¡CLÍMAX DE BATALLA!", NamedTextColor.RED, TextDecoration.BOLD),
                Component.text("El " + bossName + " está furioso (<20% HP)", NamedTextColor.GOLD),
                Title.Times.times(Duration.ofMillis(200), Duration.ofMillis(2500), Duration.ofMillis(500))
        );
        player.showTitle(title);
        player.playSound(player.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 1.0f, 0.8f);
    }

    private Player resolvePlayerAttacker(Entity damager) {
        if (damager instanceof Player) {
            return (Player) damager;
        } else if (damager instanceof Projectile) {
            Projectile proj = (Projectile) damager;
            if (proj.getShooter() instanceof Player) {
                return (Player) proj.getShooter();
            }
        }
        return null;
    }

    private void startSessionCleanupTask() {
        // Limpiar sesiones inactivas de combate (>90s sin recibir daño)
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            long now = System.currentTimeMillis();
            activeBossSessions.entrySet().removeIf(entry -> (now - entry.getValue().lastDamageTime) > 90_000);
        }, 20L * 30, 20L * 30);
    }
}
