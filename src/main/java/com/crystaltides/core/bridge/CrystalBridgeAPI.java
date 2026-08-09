package com.crystaltides.core.bridge;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.messaging.PluginMessageListener;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.logging.Level;

/**
 * Bridge Intercomunicador bidireccional entre Plugins (Paper/Spigot) y Mods (NeoForge/KubeJS).
 * Permite la comunicación en tiempo real en la JVM vía Plugin Messaging Channels ("crystaltides:bridge")
 * y suscripción a eventos compartidos.
 */
public class CrystalBridgeAPI implements PluginMessageListener {

    public static final String CHANNEL = "crystaltides:bridge";
    private static CrystalBridgeAPI instance;

    private final Plugin plugin;
    private final Map<String, List<Consumer<Map<String, Object>>>> listeners = new ConcurrentHashMap<>();

    private CrystalBridgeAPI(Plugin plugin) {
        this.plugin = plugin;
        
        // Registrar canal de mensajería nativo de Minecraft (Plugin <-> Mod)
        Bukkit.getMessenger().registerOutgoingPluginChannel(plugin, CHANNEL);
        Bukkit.getMessenger().registerIncomingPluginChannel(plugin, CHANNEL, this);
        
        plugin.getLogger().info("⚡ [CrystalBridgeAPI] Canal de comunicación Plugin <-> Mod ('" + CHANNEL + "') registrado.");
    }

    public static synchronized void init(Plugin plugin) {
        if (instance == null) {
            instance = new CrystalBridgeAPI(plugin);
        }
    }

    public static CrystalBridgeAPI getInstance() {
        return instance;
    }

    /**
     * Emite un evento desde un Plugin hacia un Mod (o KubeJS) a través del canal nativo.
     */
    public void sendToMod(Player player, String eventName, String jsonPayload) {
        if (player == null || !player.isOnline()) return;

        try {
            ByteArrayOutputStream b = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(b);
            out.writeUTF(eventName);
            out.writeUTF(jsonPayload);

            player.sendPluginMessage(plugin, CHANNEL, b.toByteArray());
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, e, () -> "Error enviando mensaje al canal de mod: " + eventName);
        }
    }

    /**
     * Emite un evento en la memoria de la JVM accesible por mods NeoForge o plugins.
     */
    public static void emitBridgeEvent(String eventName, Map<String, Object> data) {
        if (instance == null) return;

        List<Consumer<Map<String, Object>>> eventListeners = instance.listeners.get(eventName);
        if (eventListeners != null) {
            for (Consumer<Map<String, Object>> listener : eventListeners) {
                try {
                    listener.accept(data);
                } catch (Exception e) {
                    instance.plugin.getLogger().log(Level.WARNING, e, () -> "Error ejecutando listener de bridge para: " + eventName);
                }
            }
        }
    }

    /**
     * Suscribe un listener a eventos del Bridge.
     */
    public void subscribe(String eventName, Consumer<Map<String, Object>> callback) {
        listeners.computeIfAbsent(eventName, k -> new ArrayList<>()).add(callback);
    }

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        if (!CHANNEL.equals(channel)) return;

        try {
            DataInputStream in = new DataInputStream(new ByteArrayInputStream(message));
            String eventName = in.readUTF();
            String jsonPayload = in.readUTF();

            Map<String, Object> data = new HashMap<>();
            data.put("player", player);
            data.put("eventName", eventName);
            data.put("payload", jsonPayload);

            emitBridgeEvent(eventName, data);
            plugin.getLogger().info(() -> "📩 [Bridge Event Recibido de Mod] " + eventName + " de " + player.getName());
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Error leyendo mensaje entrante del mod", e);
        }
    }
}
