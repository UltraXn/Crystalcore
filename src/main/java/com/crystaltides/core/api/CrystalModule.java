package com.crystaltides.core.api;

import com.crystaltides.core.CrystalCore;
import org.bukkit.event.Listener;

public abstract class CrystalModule implements Module, Listener {

    protected final CrystalCore plugin;
    private final String name;
    private boolean enabled = false;

    public CrystalModule(CrystalCore plugin, String name) {
        this.plugin = plugin;
        this.name = name;
    }

    @Override
    public void onEnable() {
        this.enabled = true;
        // Register events if the module is a listener
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        plugin.getLogger().info("[Module] " + name + " enabled.");
    }

    @Override
    public void onDisable() {
        this.enabled = false;
        // Unregistering events is usually handled by Bukkit on plugin disable,
        // but for individual module toggling we might need explicit handling later.
        plugin.getLogger().info("[Module] " + name + " disabled.");
    }

    @Override
    public void reload() {
        onDisable();
        onEnable();
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public String getName() {
        return name;
    }
}
