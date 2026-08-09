package com.crystaltides.core.modules;

import com.crystaltides.core.CrystalCore;
import com.crystaltides.core.api.CrystalModule;
import com.crystaltides.core.network.CrystalWebSocketServer;

public class WebSocketModule extends CrystalModule {

    private CrystalWebSocketServer server;

    public WebSocketModule(CrystalCore plugin) {
        super(plugin, "WebSocket");
    }

    @Override
    public void onEnable() {
        int port = plugin.getConfig().getInt("websocket.port", 8887);
        String secret = plugin.getConfig().getString("websocket.secret-token", "changeme");

        if (secret.isEmpty() || secret.equals("change_me_to_something_secure") || secret.equals("changeme")) {
            plugin.getLogger().severe("§c[SECURITY] WebSocket secret-token is set to the default value! "
                    + "Please change it in config.yml to a secure, random string.");
            plugin.getLogger().warning("§e[SECURITY] Using default WebSocket secret is a security risk. "
                    + "The WebSocket server will still start but unauthorized access is likely.");
        }

        server = new CrystalWebSocketServer(plugin, port, secret);
        server.start();

        super.onEnable();
    }

    @Override
    public void onDisable() {
        if (server != null) {
            try {
                server.stop();
                plugin.getLogger().info("WebSocket Server stopped.");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                plugin.getSLF4JLogger().error("Error al detener el servidor WebSocket", e);
            }
        }
        super.onDisable();
    }

    public CrystalWebSocketServer getServer() {
        return server;
    }
}
