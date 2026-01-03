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
                e.printStackTrace();
            }
        }
        super.onDisable();
    }

    public CrystalWebSocketServer getServer() {
        return server;
    }
}
