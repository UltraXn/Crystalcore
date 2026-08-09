package com.crystaltides.core.network;

import com.crystaltides.core.CrystalCore;
import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;
import net.kyori.adventure.text.Component;

import java.net.InetSocketAddress;
import java.security.MessageDigest;
import java.util.Set;
import java.util.logging.Level;

public class CrystalWebSocketServer extends WebSocketServer {

    private final CrystalCore plugin;
    private final String expectedToken;

    private static final Set<String> ALLOWED_COMMAND_PREFIXES = Set.of(
        "alert:"
    );

    public CrystalWebSocketServer(CrystalCore plugin, int port, String expectedToken) {
        super(new InetSocketAddress(port));
        this.plugin = plugin;
        this.expectedToken = expectedToken;
    }

    private boolean isAuthorized(String token) {
        if (token == null || expectedToken == null) return false;
        return MessageDigest.isEqual(token.getBytes(), expectedToken.getBytes());
    }

    @Override
    public void onOpen(WebSocket conn, ClientHandshake handshake) {
        String token = handshake.getFieldValue("Authorization");
        if (!isAuthorized(token)) {
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
        boolean allowed = false;
        for (String prefix : ALLOWED_COMMAND_PREFIXES) {
            if (message.startsWith(prefix)) {
                allowed = true;
                break;
            }
        }

        if (!allowed) {
            plugin.getLogger().log(Level.WARNING, () -> "Rejected unauthorized WebSocket message from " + conn.getRemoteSocketAddress() + ": " + message);
            return;
        }

        if (message.startsWith("alert:")) {
            String msg = message.substring(6);
            plugin.getServer().broadcast(Component.text("§c§l[ALERTA WEB] §f" + msg));
        }
    }

    @Override
    public void onError(WebSocket conn, Exception ex) {
        plugin.getLogger().log(Level.WARNING, ex, () -> "WebSocket Error: " + ex.getMessage());
    }

    @Override
    public void onStart() {
        plugin.getLogger().info("WebSocket Server started on port " + getPort());
    }
}
