package com.crystaltides.core.network;

import com.crystaltides.core.CrystalCore;
import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;
import net.kyori.adventure.text.Component;

import java.net.InetSocketAddress;

public class CrystalWebSocketServer extends WebSocketServer {

    private final CrystalCore plugin;
    private final String expectedToken;

    public CrystalWebSocketServer(CrystalCore plugin, int port, String expectedToken) {
        super(new InetSocketAddress(port));
        this.plugin = plugin;
        this.expectedToken = expectedToken;
    }

    @Override
    public void onOpen(WebSocket conn, ClientHandshake handshake) {
        String token = handshake.getFieldValue("Authorization");
        if (token == null || !token.equals(expectedToken)) {
            // Check query param as fallback ?token=XYZ
            String descriptor = handshake.getResourceDescriptor(); // /?token=XYZ
            if (descriptor.contains("token=" + expectedToken)) {
                plugin.getLogger().info("WebClient connected via Query Param: " + conn.getRemoteSocketAddress());
                return;
            }

            plugin.getLogger()
                    .warning("Unauthorized WebSocket connection attempt from: " + conn.getRemoteSocketAddress());
            conn.close(1008, "Unauthorized"); // 1008 = Policy Violation
        } else {
            plugin.getLogger().info("WebClient authorized: " + conn.getRemoteSocketAddress());
        }
    }

    @Override
    public void onClose(WebSocket conn, int code, String reason, boolean remote) {
        // Silent close for now
    }

    @Override
    public void onMessage(WebSocket conn, String message) {
        // Handle incoming commands
        if (message.startsWith("alert:")) {
            String msg = message.substring(6);
            plugin.getServer().broadcast(Component.text("§c§l[ALERTA WEB] §f" + msg));
        } else if (message.startsWith("console:")) {
            String cmd = message.substring(8);
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                plugin.getServer().dispatchCommand(plugin.getServer().getConsoleSender(), cmd);
            });
        }
    }

    @Override
    public void onError(WebSocket conn, Exception ex) {
        plugin.getLogger().warning("WebSocket Error: " + ex.getMessage());
    }

    @Override
    public void onStart() {
        plugin.getLogger().info("WebSocket Server started on port " + getPort());
    }
}
